package com.EduePoa.EP.Transport.Boarding.internal;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.Boarding.BoardingLeg;
import com.EduePoa.EP.Transport.Boarding.BoardingLegRules;
import com.EduePoa.EP.Transport.Boarding.BoardingStatus;
import com.EduePoa.EP.Transport.Boarding.CaptureMethod;
import com.EduePoa.EP.Transport.Boarding.SchoolTimeZone;
import com.EduePoa.EP.Transport.Boarding.StudentTransportBiometric;
import com.EduePoa.EP.Transport.Boarding.StudentTransportBiometricRepository;
import com.EduePoa.EP.Transport.Boarding.TransportBoardingEvent;
import com.EduePoa.EP.Transport.Boarding.TransportBoardingEventRepository;
import com.EduePoa.EP.Transport.Boarding.Requests.BoardingEventRequest;
import com.EduePoa.EP.Transport.Transport;
import com.EduePoa.EP.Transport.TransportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Processes a single boarding event in its own {@code REQUIRES_NEW} transaction.
 *
 * <p>Isolating each event means an exception (including a concurrent unique-constraint violation)
 * rolls back only that event, never the valid siblings in the same batch. A
 * {@link DataIntegrityViolationException} on the business uniqueness constraint is translated to a
 * {@code DUPLICATE} outcome rather than surfacing as HTTP 500.
 *
 * <p>All lookups run within the authenticated tenant context (the Hibernate tenant filter is active
 * for the request), so cross-tenant students/vehicles/tokens/events are never resolvable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardingEventProcessor {

    private final StudentRepository studentRepository;
    private final TransportRepository transportRepository;
    private final AssignTransportRepository assignTransportRepository;
    private final TransportBoardingEventRepository boardingEventRepository;
    private final StudentTransportBiometricRepository biometricRepository;

    /**
     * Validate and persist one event. Never throws for expected business failures — those become
     * {@link OutcomeType#REJECTED} / {@link OutcomeType#DUPLICATE} outcomes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventOutcome process(BoardingEventRequest req) {
        String clientEventId = trimToNull(req.getClientEventId());

        // --- Step 1: structural validation (bean-validation covers @NotNull on the fields; here we
        // enforce the cross-field rules that cannot be expressed declaratively). ---
        if (req.getLeg() == null) {
            return EventOutcome.rejected(clientEventId, "leg is required.");
        }
        if (req.getMethod() == null) {
            return EventOutcome.rejected(clientEventId, "method is required.");
        }
        if (req.getVehicleId() == null) {
            return EventOutcome.rejected(clientEventId, "vehicleId is required.");
        }
        if (trimToNull(req.getCapturedAt()) == null) {
            return EventOutcome.rejected(clientEventId, "capturedAt is required.");
        }
        if (!coordinatesValid(req.getLatitude(), req.getLongitude())) {
            return EventOutcome.rejected(clientEventId,
                    "Invalid coordinates: latitude must be within [-90,90] and longitude within [-180,180].");
        }

        // Identifier presence: exactly one of studentId / admissionNumber / token.
        boolean hasStudentId = req.getStudentId() != null;
        boolean hasAdmission = req.resolveAdmissionNumber() != null;
        boolean hasToken = req.resolveToken() != null;
        int identifierCount = (hasStudentId ? 1 : 0) + (hasAdmission ? 1 : 0) + (hasToken ? 1 : 0);
        if (identifierCount == 0) {
            return EventOutcome.rejected(clientEventId,
                    "A student identifier is required (one of studentId, admissionNumber, biometricId).");
        }
        if (identifierCount > 1) {
            return EventOutcome.rejected(clientEventId,
                    "Provide exactly one student identifier (studentId, admissionNumber, or biometricId).");
        }

        // Identifier must be consistent with the capture method.
        CaptureMethod method = req.getMethod();
        if (method.requiresToken() && !hasToken) {
            return EventOutcome.rejected(clientEventId,
                    "Method " + method + " requires an identification token.");
        }
        if (method == CaptureMethod.MANUAL && hasToken) {
            return EventOutcome.rejected(clientEventId,
                    "MANUAL capture must use studentId or admissionNumber, not a token.");
        }

        // --- Step 2: resolve student ---
        Student student;
        if (hasStudentId) {
            Optional<Student> found = studentRepository.findById(req.getStudentId());
            if (found.isEmpty()) {
                return EventOutcome.rejected(clientEventId, "Student not found.");
            }
            student = found.get();
        } else if (hasAdmission) {
            Optional<Student> found = studentRepository.findByAdmissionNumber(req.resolveAdmissionNumber());
            if (found.isEmpty()) {
                return EventOutcome.rejected(clientEventId, "Student not found for the supplied admission number.");
            }
            student = found.get();
        } else {
            Optional<StudentTransportBiometric> mapping =
                    biometricRepository.findByBiometricId(req.resolveToken());
            if (mapping.isEmpty()) {
                return EventOutcome.rejected(clientEventId, "No student is enrolled for the supplied token.");
            }
            student = mapping.get().getStudent();
        }

        // --- Step 3: resolve vehicle ---
        Optional<Transport> vehicleOpt = transportRepository.findById(req.getVehicleId());
        if (vehicleOpt.isEmpty()) {
            return EventOutcome.rejected(clientEventId, "Vehicle not found.");
        }
        Transport vehicle = vehicleOpt.get();

        // --- Step 4: resolve service date from capturedAt (school timezone) ---
        LocalDateTime capturedAt;
        LocalDate serviceDate;
        try {
            capturedAt = SchoolTimeZone.normalize(req.getCapturedAt());
            serviceDate = SchoolTimeZone.serviceDateOf(capturedAt);
        } catch (DateTimeParseException e) {
            return EventOutcome.rejected(clientEventId, "capturedAt is not a valid timestamp.");
        }

        // --- Step 5: resolve academic term/year for the service date ---
        Term term = Term.getTermByDate(serviceDate);
        if (term == null) {
            return EventOutcome.rejected(clientEventId,
                    "No academic term is configured for the service date " + serviceDate + ".");
        }
        Integer year = serviceDate.getYear();

        // --- Step 6: validate assignment (student assigned to THIS vehicle for term/year) ---
        Optional<AssignTransport> assignmentOpt =
                assignTransportRepository.findByStudentAndTermAndYear(student, term, year);
        if (assignmentOpt.isEmpty()) {
            return EventOutcome.rejected(clientEventId,
                    "Student has no transport assignment for the service date.");
        }
        AssignTransport assignment = assignmentOpt.get();
        if (assignment.getVehicle() == null
                || !assignment.getVehicle().getId().equals(vehicle.getId())) {
            return EventOutcome.rejected(clientEventId,
                    "Student is not assigned to the specified vehicle for the service date.");
        }

        // --- Step 7: leg eligibility (centralized, day-aware rule) ---
        // For ONE_WAY students the day is a single trip: the first phase to record a boarding
        // claims the day and locks out the other phase. To enforce that atomically against
        // concurrent devices and out-of-order offline sync, we take a PESSIMISTIC_WRITE lock on the
        // assignment row first (one row per student/term/year, always present here). All concurrent
        // scans for the same student then serialize through this section, so the phase read below
        // reflects any sibling event committed by the transaction that went first. TWO_WAY students
        // have no cross-phase constraint, so they skip the lock. The unique
        // (student, serviceDate, leg) constraint at Step 10 remains the backstop for same-leg races.
        if (assignment.getTransportType() == com.EduePoa.EP.Transport.TransportType.ONE_WAY) {
            assignTransportRepository.findByIdForUpdate(assignment.getId());
        }
        Set<BoardingLeg.Phase> boardedPhases = boardedPhasesForDay(student.getId(), serviceDate);
        Set<BoardingLeg> eligible = BoardingLegRules.eligibleLegs(assignment, boardedPhases);
        if (!eligible.contains(req.getLeg())) {
            BoardingLeg.Phase claimed = BoardingLegRules.claimedPhase(boardedPhases);
            BoardingLeg.Phase attempted = req.getLeg().phase();
            String reason;
            if (claimed != null && claimed != attempted) {
                reason = "Student already boarded in the " + phaseWord(claimed)
                        + " and cannot board in the " + phaseWord(attempted)
                        + " on the same day.";
            } else {
                reason = "Student is not expected on " + req.getLeg()
                        + " for the current transport assignment.";
            }
            return EventOutcome.rejected(clientEventId, reason);
        }

        // --- Step 8: client idempotency ---
        if (clientEventId != null) {
            Optional<TransportBoardingEvent> existingByClient =
                    boardingEventRepository.findByClientEventId(clientEventId);
            if (existingByClient.isPresent()) {
                TransportBoardingEvent e = existingByClient.get();
                return EventOutcome.duplicate(clientEventId, e.getId(), e.getStudent().getId());
            }
        }

        // --- Step 9: business duplicate (student + serviceDate + leg) ---
        Optional<TransportBoardingEvent> existingBusiness =
                boardingEventRepository.findByStudentAndServiceDateAndLeg(student, serviceDate, req.getLeg());
        if (existingBusiness.isPresent()) {
            TransportBoardingEvent e = existingBusiness.get();
            return EventOutcome.duplicate(clientEventId, e.getId(), student.getId());
        }

        // --- Step 10: persist (first valid write wins) ---
        TransportBoardingEvent event = TransportBoardingEvent.builder()
                .student(student)
                .vehicle(vehicle)
                .serviceDate(serviceDate)
                .leg(req.getLeg())
                .method(method)
                .status(BoardingStatus.ON_TIME)
                .capturedAt(capturedAt)
                .deviceId(trimToNull(req.getDeviceId()))
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .clientEventId(clientEventId)
                .recordedBy(currentUsername())
                .build();

        try {
            TransportBoardingEvent saved = boardingEventRepository.saveAndFlush(event);
            return EventOutcome.accepted(clientEventId, saved.getId(), student.getId());
        } catch (DataIntegrityViolationException dive) {
            // A concurrent request won the race for this (student, serviceDate, leg) — treat as a
            // duplicate, not a server error. Do not log the exception body (may reveal internals).
            // The current REQUIRES_NEW transaction is now rollback-only, so we cannot safely query
            // for the winning event id here; the caller resolves it in a fresh transaction and the
            // outcome is emitted with a null id (studentId is still returned).
            log.info("Concurrent duplicate boarding event for student {} date {} leg {} — resolved as DUPLICATE.",
                    student.getId(), serviceDate, req.getLeg());
            return EventOutcome.duplicate(clientEventId, null, student.getId());
        }
    }

    /**
     * Looks up the id of an existing business event in a fresh transaction. Used by the service to
     * enrich a concurrent-duplicate outcome whose id could not be resolved inside the poisoned
     * insert transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findExistingEventId(Long studentId, LocalDate serviceDate, BoardingLeg leg) {
        try {
            return boardingEventRepository
                    .findByStudentIdAndServiceDateBetweenOrderByServiceDateAscLegAsc(studentId, serviceDate, serviceDate)
                    .stream()
                    .filter(e -> e.getLeg() == leg)
                    .map(TransportBoardingEvent::getId)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The set of phases the student already has a boarding event in on the given service date.
     * Reads all of that day's events for the student (a small set — at most four rows) in the
     * active tenant context.
     */
    private Set<BoardingLeg.Phase> boardedPhasesForDay(Long studentId, LocalDate serviceDate) {
        Set<BoardingLeg.Phase> phases = EnumSet.noneOf(BoardingLeg.Phase.class);
        List<TransportBoardingEvent> dayEvents = boardingEventRepository
                .findByStudentIdAndServiceDateBetweenOrderByServiceDateAscLegAsc(
                        studentId, serviceDate, serviceDate);
        for (TransportBoardingEvent e : dayEvents) {
            if (e.getLeg() != null) {
                phases.add(e.getLeg().phase());
            }
        }
        return phases;
    }

    private static String phaseWord(BoardingLeg.Phase phase) {
        return phase == BoardingLeg.Phase.MORNING ? "morning" : "evening";
    }

    /**
     * The username of the authenticated operator recording this event, or {@code null} when there
     * is no authenticated principal (e.g. system/automated flows). Never throws.
     */
    private static String currentUsername() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
                return ud.getUsername();
            }
            String name = auth.getName();
            return "anonymousUser".equals(name) ? null : name;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean coordinatesValid(Double lat, Double lon) {
        if (lat != null && (lat < -90.0 || lat > 90.0)) {
            return false;
        }
        return lon == null || !(lon < -180.0) && !(lon > 180.0);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
