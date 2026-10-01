package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.Boarding.Requests.BoardingEventRequest;
import com.EduePoa.EP.Transport.Boarding.Requests.StudentTransportBiometricEnrollRequest;
import com.EduePoa.EP.Transport.Boarding.Requests.TransportBoardingScanRequest;
import com.EduePoa.EP.Transport.Boarding.Responses.BoardingEventOutcomeResponse;
import com.EduePoa.EP.Transport.Boarding.Responses.StudentTransportBiometricResponse;
import com.EduePoa.EP.Transport.Boarding.Responses.TransportBoardingHistoryResponse;
import com.EduePoa.EP.Transport.Boarding.Responses.TransportBoardingManifestResponse;
import com.EduePoa.EP.Transport.Boarding.Responses.TransportBoardingScanResponse;
import com.EduePoa.EP.Transport.Boarding.Responses.TransportStudentRosterResponse;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Transport.Boarding.internal.BoardingEventProcessor;
import com.EduePoa.EP.Transport.Boarding.internal.EventOutcome;
import com.EduePoa.EP.Transport.Boarding.internal.OutcomeType;
import com.EduePoa.EP.Transport.Boarding.notification.BoardingNotificationService;
import com.EduePoa.EP.Transport.Transport;
import com.EduePoa.EP.Transport.TransportRepository;
import com.EduePoa.EP.Transport.TransportType;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransportBoardingServiceImpl implements TransportBoardingService {

    /** Guard against unbounded history queries. */
    private static final long MAX_HISTORY_DAYS = 366;

    private final BoardingEventProcessor eventProcessor;
    private final BoardingNotificationService boardingNotificationService;
    private final StudentRepository studentRepository;
    private final TransportRepository transportRepository;
    private final AssignTransportRepository assignTransportRepository;
    private final TransportBoardingEventRepository boardingEventRepository;
    private final StudentTransportBiometricRepository biometricRepository;
    private final AuditService auditService;

    @Override
    public Set<BoardingLeg> eligibleLegs(AssignTransport assignment) {
        return BoardingLegRules.eligibleLegs(assignment);
    }

    @Override
    @Audit(module = "TRANSPORT", action = "BOARDING_MARK")
    public CustomResponse<?> recordBatch(TransportBoardingScanRequest request) {
        CustomResponse<TransportBoardingScanResponse> response = new CustomResponse<>();
        try {
            if (request == null || request.getEvents() == null || request.getEvents().isEmpty()) {
                response.setEntity(null);
                response.setMessage("events must not be empty");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            TransportBoardingScanResponse result = TransportBoardingScanResponse.builder().build();

            // Captured on the request thread: the async notifier cannot read the ThreadLocal
            // TenantContext, so we pass the tenant explicitly.
            final String tenantId = TenantContext.getCurrentTenant();
            List<Long> acceptedEventIds = new ArrayList<>();

            for (BoardingEventRequest event : request.getEvents()) {
                EventOutcome outcome;
                try {
                    // Each event runs in its own REQUIRES_NEW transaction; a failure here cannot roll
                    // back siblings already committed in this loop.
                    outcome = eventProcessor.process(event);
                } catch (RuntimeException ex) {
                    // Defensive: never let an unexpected error abort the batch. Do not leak internals.
                    log.error("Unexpected error processing boarding event: {}", ex.getMessage(), ex);
                    outcome = EventOutcome.rejected(
                            event != null ? trimToNull(event.getClientEventId()) : null,
                            "Event could not be processed.");
                }
                bucket(result, outcome);

                // Notify parents ONLY for events actually recorded now. Duplicates (already recorded
                // earlier) and rejections never trigger a notification.
                if (outcome.type() == OutcomeType.ACCEPTED && outcome.eventId() != null) {
                    acceptedEventIds.add(outcome.eventId());
                }
            }

            // Fire-and-forget after the batch is processed (each accepted event is already committed
            // in its own transaction). Best-effort: notification failures never affect the response.
            for (Long acceptedEventId : acceptedEventIds) {
                try {
                    boardingNotificationService.notifyForEvent(acceptedEventId, tenantId);
                } catch (RuntimeException ex) {
                    log.warn("Failed to dispatch boarding notification for event {}: {}",
                            acceptedEventId, ex.getMessage());
                }
            }

            result.setAcceptedCount(result.getAccepted().size());
            result.setDuplicateCount(result.getDuplicates().size());
            result.setRejectedCount(result.getRejected().size());

            response.setEntity(result);
            response.setMessage(String.format(
                    "Processed %d event(s): %d accepted, %d duplicate, %d rejected",
                    request.getEvents().size(),
                    result.getAcceptedCount(), result.getDuplicateCount(), result.getRejectedCount()));
            response.setStatusCode(HttpStatus.OK.value());

            auditService.log("TRANSPORT", "Recorded transport boarding batch:",
                    String.valueOf(result.getAcceptedCount()), "accepted,",
                    String.valueOf(result.getDuplicateCount()), "duplicate,",
                    String.valueOf(result.getRejectedCount()), "rejected");

        } catch (RuntimeException e) {
            log.error("Error recording boarding batch: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to record boarding events.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private void bucket(TransportBoardingScanResponse result, EventOutcome outcome) {
        BoardingEventOutcomeResponse dto = BoardingEventOutcomeResponse.builder()
                .clientEventId(outcome.clientEventId())
                .eventId(outcome.eventId())
                .studentId(outcome.studentId())
                .build();
        switch (outcome.type()) {
            case ACCEPTED -> {
                dto.setStatus("RECORDED");
                result.getAccepted().add(dto);
            }
            case DUPLICATE -> {
                dto.setStatus("DUPLICATE");
                result.getDuplicates().add(dto);
            }
            case REJECTED -> {
                dto.setStatus("REJECTED");
                dto.setReason(outcome.reason());
                result.getRejected().add(dto);
            }
        }
    }

    @Override
    public CustomResponse<?> getVehicleManifest(Long vehicleId, LocalDate date, BoardingLeg leg) {
        CustomResponse<List<TransportBoardingManifestResponse>> response = new CustomResponse<>();
        try {
            // All filters optional. Date defaults to today (school timezone) so the term/year and
            // "actual" roster are always well-defined.
            LocalDate serviceDate = date != null ? date : SchoolTimeZone.today();

            Term term = Term.getTermByDate(serviceDate);
            if (term == null) {
                response.setEntity(null);
                response.setMessage("No academic term is configured for " + serviceDate);
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            Integer year = serviceDate.getYear();

            // Resolve the vehicle set from the optional vehicleId filter.
            List<Transport> vehicles;
            if (vehicleId != null) {
                Optional<Transport> vehicleOpt = transportRepository.findById(vehicleId);
                if (vehicleOpt.isEmpty()) {
                    response.setEntity(null);
                    response.setMessage("Vehicle not found");
                    response.setStatusCode(HttpStatus.NOT_FOUND.value());
                    return response;
                }
                vehicles = List.of(vehicleOpt.get());
            } else {
                vehicles = transportRepository.findAll();
            }

            // Resolve the leg set from the optional leg filter.
            List<BoardingLeg> legs = leg != null
                    ? List.of(leg)
                    : List.of(BoardingLeg.values());

            // One manifest per (vehicle x leg). Skip empty vehicle/leg combinations (no expected and
            // no boarded students) to keep the "all data" response focused on meaningful rosters.
            List<TransportBoardingManifestResponse> manifests = new ArrayList<>();
            for (Transport vehicle : vehicles) {
                List<AssignTransport> assignments =
                        assignTransportRepository.findByVehicleAndTermAndYear(vehicle, term, year);
                // Load the whole day's events for these students once (all legs), so both the
                // day-aware "expected" filter and each student's applicableLegs can be computed
                // without an extra query per leg or per student.
                Map<Long, Set<BoardingLeg.Phase>> phasesByStudent =
                        boardedPhasesByStudent(assignments, serviceDate);
                for (BoardingLeg l : legs) {
                    TransportBoardingManifestResponse manifest =
                            buildManifest(vehicle, serviceDate, l, assignments, phasesByStudent);
                    boolean singleTarget = vehicleId != null && leg != null;
                    if (singleTarget || manifest.getExpectedCount() > 0 || manifest.getBoardedCount() > 0) {
                        manifests.add(manifest);
                    }
                }
            }

            response.setEntity(manifests);
            response.setMessage("Transport boarding manifest retrieved successfully ("
                    + manifests.size() + " roster(s))");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            log.error("Error building boarding manifest: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to retrieve boarding manifest.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    /**
     * Builds a single expected-vs-actual manifest for one vehicle/date/leg. {@code assignments} is
     * the vehicle's assignments for the relevant term/year (passed in so the caller can load them
     * once per vehicle rather than once per leg).
     */
    private TransportBoardingManifestResponse buildManifest(
            Transport vehicle, LocalDate serviceDate, BoardingLeg leg, List<AssignTransport> assignments,
            Map<Long, Set<BoardingLeg.Phase>> phasesByStudent) {

        // Expected roster: assignments filtered by the day-aware eligibleLegs rule — the same rule
        // the scan endpoint enforces. A ONE_WAY student who has already claimed the other phase
        // that day is NOT expected on this leg (and so is not reported missing on it).
        Map<Long, AssignTransport> expected = new LinkedHashMap<>();
        for (AssignTransport a : assignments) {
            if (a.getStudent() == null) {
                continue;
            }
            Set<BoardingLeg.Phase> phases =
                    phasesByStudent.getOrDefault(a.getStudent().getId(), java.util.Set.of());
            if (BoardingLegRules.eligibleLegs(a, phases).contains(leg)) {
                expected.put(a.getStudent().getId(), a);
            }
        }

        // Actual roster.
        List<TransportBoardingEvent> events =
                boardingEventRepository.findByVehicleAndServiceDateAndLeg(vehicle, serviceDate, leg);

        List<TransportBoardingManifestResponse.BoardedStudent> boarded = new ArrayList<>();
        Set<Long> boardedIds = new java.util.HashSet<>();
        for (TransportBoardingEvent e : events) {
            Student s = e.getStudent();
            if (s == null) {
                continue;
            }
            boardedIds.add(s.getId());
            AssignTransport a = expected.get(s.getId());
            TransportType type = a != null ? a.getTransportType() : null;
            Set<BoardingLeg.Phase> phases =
                    phasesByStudent.getOrDefault(s.getId(), java.util.Set.of());
            boarded.add(TransportBoardingManifestResponse.BoardedStudent.builder()
                    .studentId(s.getId())
                    .admissionNumber(s.getAdmissionNumber())
                    .fullName(fullName(s))
                    .transportType(type)
                    .applicableLegs(BoardingLegRules.applicableLegs(a, phases))
                    .method(e.getMethod())
                    .capturedAt(e.getCapturedAt())
                    .status(e.getStatus())
                    .build());
        }

        // Missing = expected - actual (keyed by studentId).
        List<TransportBoardingManifestResponse.MissingStudent> missing = new ArrayList<>();
        for (Map.Entry<Long, AssignTransport> entry : expected.entrySet()) {
            if (boardedIds.contains(entry.getKey())) {
                continue;
            }
            AssignTransport a = entry.getValue();
            Student s = a.getStudent();
            Set<BoardingLeg.Phase> phases =
                    phasesByStudent.getOrDefault(s.getId(), java.util.Set.of());
            missing.add(TransportBoardingManifestResponse.MissingStudent.builder()
                    .studentId(s.getId())
                    .admissionNumber(s.getAdmissionNumber())
                    .fullName(fullName(s))
                    .transportType(a.getTransportType())
                    .applicableLegs(BoardingLegRules.applicableLegs(a, phases))
                    .build());
        }

        return TransportBoardingManifestResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleNumber(vehicle.getVehicleNumber())
                .route(vehicle.getRoute())
                .date(serviceDate)
                .leg(leg)
                .expectedCount(expected.size())
                .boardedCount(boarded.size())
                .missingCount(missing.size())
                .boarded(boarded)
                .missing(missing)
                .build();
    }

    @Override
    public CustomResponse<?> getStudentHistory(Long studentId, LocalDate from, LocalDate to) {
        CustomResponse<TransportBoardingHistoryResponse> response = new CustomResponse<>();
        try {
            if (studentId == null || from == null || to == null) {
                response.setEntity(null);
                response.setMessage("studentId, from and to are required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            if (from.isAfter(to)) {
                response.setEntity(null);
                response.setMessage("'from' must be on or before 'to'");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_HISTORY_DAYS) {
                response.setEntity(null);
                response.setMessage("Date range too large; maximum is " + MAX_HISTORY_DAYS + " days");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                response.setEntity(null);
                response.setMessage("Student not found");
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }
            Student student = studentOpt.get();

            // transportType is best-effort context: use the assignment for the term of 'to'.
            TransportType transportType = resolveTransportType(student, to);

            List<TransportBoardingEvent> events = boardingEventRepository
                    .findByStudentIdAndServiceDateBetweenOrderByServiceDateAscLegAsc(studentId, from, to);

            // Group by service date preserving ascending order (repository already orders).
            Map<LocalDate, List<TransportBoardingHistoryResponse.Event>> grouped = new LinkedHashMap<>();
            for (TransportBoardingEvent e : events) {
                grouped.computeIfAbsent(e.getServiceDate(), d -> new ArrayList<>())
                        .add(TransportBoardingHistoryResponse.Event.builder()
                                .leg(e.getLeg())
                                .capturedAt(e.getCapturedAt())
                                .method(e.getMethod())
                                .status(e.getStatus())
                                .latitude(e.getLatitude())
                                .longitude(e.getLongitude())
                                .recordedBy(e.getRecordedBy())
                                .build());
            }

            List<TransportBoardingHistoryResponse.Day> days = new ArrayList<>();
            for (Map.Entry<LocalDate, List<TransportBoardingHistoryResponse.Event>> entry : grouped.entrySet()) {
                days.add(TransportBoardingHistoryResponse.Day.builder()
                        .serviceDate(entry.getKey())
                        .events(entry.getValue())
                        .build());
            }

            TransportBoardingHistoryResponse history = TransportBoardingHistoryResponse.builder()
                    .studentId(studentId)
                    .transportType(transportType)
                    .from(from)
                    .to(to)
                    .days(days)
                    .build();

            response.setEntity(history);
            response.setMessage("Transport boarding history retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            log.error("Error building boarding history: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to retrieve boarding history.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "TRANSPORT", action = "BIOMETRIC_ENROLL")
    public CustomResponse<?> enrollIdentifier(StudentTransportBiometricEnrollRequest request) {
        CustomResponse<StudentTransportBiometricResponse> response = new CustomResponse<>();
        try {
            if (request == null || request.getStudentId() == null) {
                response.setEntity(null);
                response.setMessage("studentId is required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            if (request.getMethod() == null) {
                response.setEntity(null);
                response.setMessage("method is required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            if (request.getMethod() == CaptureMethod.MANUAL) {
                response.setEntity(null);
                response.setMessage("MANUAL is not a valid enrollment method");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            String token = request.resolveToken();
            if (token == null) {
                response.setEntity(null);
                response.setMessage("An identification token is required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<Student> studentOpt = studentRepository.findById(request.getStudentId());
            if (studentOpt.isEmpty()) {
                response.setEntity(null);
                response.setMessage("Student not found");
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }
            Student student = studentOpt.get();

            // If the token already exists, it must belong to the same student — never transfer it.
            Optional<StudentTransportBiometric> byToken = biometricRepository.findByBiometricId(token);
            StudentTransportBiometric mapping;
            if (byToken.isPresent()) {
                mapping = byToken.get();
                if (mapping.getStudent() == null
                        || !mapping.getStudent().getId().equals(student.getId())) {
                    response.setEntity(null);
                    response.setMessage("Token is already enrolled to a different student");
                    response.setStatusCode(HttpStatus.CONFLICT.value());
                    return response;
                }
                // Same student: refresh method/vendor.
                mapping.setMethod(request.getMethod());
                mapping.setDeviceVendor(trimToNull(request.getDeviceVendor()));
            } else {
                // No such token. If the student already has a mapping for this method, replace it
                // (single active token per method); otherwise create a new mapping.
                Optional<StudentTransportBiometric> byStudentMethod =
                        biometricRepository.findByStudentAndMethod(student, request.getMethod());
                if (byStudentMethod.isPresent()) {
                    mapping = byStudentMethod.get();
                    mapping.setBiometricId(token);
                    mapping.setDeviceVendor(trimToNull(request.getDeviceVendor()));
                } else {
                    mapping = StudentTransportBiometric.builder()
                            .student(student)
                            .biometricId(token)
                            .method(request.getMethod())
                            .deviceVendor(trimToNull(request.getDeviceVendor()))
                            .build();
                }
            }

            StudentTransportBiometric saved = biometricRepository.save(mapping);

            StudentTransportBiometricResponse dto = StudentTransportBiometricResponse.builder()
                    .id(saved.getId())
                    .studentId(student.getId())
                    .biometricId(mask(saved.getBiometricId()))
                    .method(saved.getMethod())
                    .enrolledAt(saved.getEnrolledAt())
                    .build();

            response.setEntity(dto);
            response.setMessage("Transport identification token enrolled successfully");
            response.setStatusCode(HttpStatus.CREATED.value());

            // Never log the raw token.
            auditService.log("TRANSPORT", "Enrolled transport", String.valueOf(saved.getMethod()),
                    "token for student ID:", String.valueOf(student.getId()));

        } catch (RuntimeException e) {
            log.error("Error enrolling transport identifier: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to enroll transport identifier.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getTransportStudents(Long vehicleId, LocalDate date) {
        CustomResponse<List<TransportStudentRosterResponse>> response = new CustomResponse<>();
        try {
            // Resolve the vehicle filter (if any) up front so a bad id is a clean 404.
            Transport vehicle = null;
            if (vehicleId != null) {
                Optional<Transport> vehicleOpt = transportRepository.findById(vehicleId);
                if (vehicleOpt.isEmpty()) {
                    response.setEntity(null);
                    response.setMessage("Vehicle not found");
                    response.setStatusCode(HttpStatus.NOT_FOUND.value());
                    return response;
                }
                vehicle = vehicleOpt.get();
            }

            // When a date is supplied, scope assignments to that date's term/year.
            Term term = null;
            Integer year = null;
            if (date != null) {
                term = Term.getTermByDate(date);
                if (term == null) {
                    response.setEntity(null);
                    response.setMessage("No academic term is configured for " + date);
                    response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return response;
                }
                year = date.getYear();
            }

            // Select assignments per the combination of optional filters.
            List<AssignTransport> assignments;
            if (vehicle != null && term != null) {
                assignments = assignTransportRepository.findByVehicleAndTermAndYear(vehicle, term, year);
            } else if (vehicle != null) {
                assignments = assignTransportRepository.findByVehicle(vehicle);
            } else if (term != null) {
                // Date only: all assignments for that term/year, across vehicles.
                final Term filterTerm = term;
                final Integer filterYear = year;
                assignments = assignTransportRepository.findAll().stream()
                        .filter(a -> a.getTerm() == filterTerm && filterYear.equals(a.getYear()))
                        .toList();
            } else {
                // No filters: everything.
                assignments = assignTransportRepository.findAll();
            }

            // When a date is supplied, batch-load that day's events for the roster's students.
            Map<Long, List<TransportBoardingEvent>> eventsByStudent = new java.util.HashMap<>();
            if (date != null && !assignments.isEmpty()) {
                List<Long> studentIds = assignments.stream()
                        .map(AssignTransport::getStudent)
                        .filter(java.util.Objects::nonNull)
                        .map(Student::getId)
                        .distinct()
                        .toList();
                if (!studentIds.isEmpty()) {
                    for (TransportBoardingEvent e :
                            boardingEventRepository.findByStudentIdInAndServiceDate(studentIds, date)) {
                        if (e.getStudent() != null) {
                            eventsByStudent
                                    .computeIfAbsent(e.getStudent().getId(), k -> new ArrayList<>())
                                    .add(e);
                        }
                    }
                }
            }

            final boolean includeEvents = date != null;
            List<TransportStudentRosterResponse> rows = new ArrayList<>();
            for (AssignTransport a : assignments) {
                Student s = a.getStudent();
                if (s == null) {
                    continue;
                }
                Transport v = a.getVehicle();

                List<TransportStudentRosterResponse.RosterEvent> events = null;
                if (includeEvents) {
                    events = new ArrayList<>();
                    for (TransportBoardingEvent e :
                            eventsByStudent.getOrDefault(s.getId(), List.of())) {
                        events.add(TransportStudentRosterResponse.RosterEvent.builder()
                                .eventId(e.getId())
                                .leg(e.getLeg())
                                .capturedAt(e.getCapturedAt())
                                .method(e.getMethod())
                                .status(e.getStatus())
                                .build());
                    }
                }

                rows.add(TransportStudentRosterResponse.builder()
                        .assignmentId(a.getId())
                        .studentId(s.getId())
                        .admissionNumber(s.getAdmissionNumber())
                        .fullName(fullName(s))
                        .vehicleId(v != null ? v.getId() : null)
                        .vehicleNumber(v != null ? v.getVehicleNumber() : null)
                        .route(v != null ? v.getRoute() : null)
                        .pickupLocation(a.getPickupLocation())
                        .transportType(a.getTransportType())
                        .applicableLegs(BoardingLegRules.applicableLegs(
                                a, phasesOf(eventsByStudent.getOrDefault(s.getId(), List.of()))))
                        .term(a.getTerm())
                        .year(a.getYear())
                        .assignmentDate(a.getAssignmentDate())
                        .events(events)
                        .build());
            }

            response.setEntity(rows);
            response.setMessage("Transport students retrieved successfully (" + rows.size() + " record(s))");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            log.error("Error retrieving transport students: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to retrieve transport students.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    /**
     * Resolves the student's transport type for the term containing {@code date}; best-effort context
     * only (returns null if no assignment / term).
     */
    private TransportType resolveTransportType(Student student, LocalDate date) {
        Term term = Term.getTermByDate(date);
        if (term == null) {
            return null;
        }
        return assignTransportRepository
                .findByStudentAndTermAndYear(student, term, date.getYear())
                .map(AssignTransport::getTransportType)
                .orElse(null);
    }

    private static String fullName(Student s) {
        String first = s.getFirstName() != null ? s.getFirstName() : "";
        String last = s.getLastName() != null ? s.getLastName() : "";
        return (first + " " + last).trim();
    }

    /**
     * The set of day phases represented by a student's boarding events. Empty when the list is
     * empty (no phase claimed) — which makes {@link BoardingLegRules#applicableLegs} return all
     * four legs for a ONE_WAY student, matching the "no day claimed yet" case.
     */
    private static Set<BoardingLeg.Phase> phasesOf(List<TransportBoardingEvent> events) {
        Set<BoardingLeg.Phase> phases = EnumSet.noneOf(BoardingLeg.Phase.class);
        for (TransportBoardingEvent e : events) {
            if (e.getLeg() != null) {
                phases.add(e.getLeg().phase());
            }
        }
        return phases;
    }

    /**
     * Builds a {@code studentId -> boarded phases} map for the given assignments on one service
     * date in a single batch query (no N+1). Reused across every leg's manifest for the vehicle.
     */
    private Map<Long, Set<BoardingLeg.Phase>> boardedPhasesByStudent(
            List<AssignTransport> assignments, LocalDate serviceDate) {
        Map<Long, Set<BoardingLeg.Phase>> phasesByStudent = new HashMap<>();
        List<Long> studentIds = assignments.stream()
                .map(AssignTransport::getStudent)
                .filter(java.util.Objects::nonNull)
                .map(Student::getId)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return phasesByStudent;
        }
        for (TransportBoardingEvent e :
                boardingEventRepository.findByStudentIdInAndServiceDate(studentIds, serviceDate)) {
            if (e.getStudent() != null && e.getLeg() != null) {
                phasesByStudent
                        .computeIfAbsent(e.getStudent().getId(), k -> EnumSet.noneOf(BoardingLeg.Phase.class))
                        .add(e.getLeg().phase());
            }
        }
        return phasesByStudent;
    }

    /**
     * Masks a sensitive token as {@code AF93****92}: keep up to the first 4 and last 2 characters.
     */
    private static String mask(String token) {
        if (token == null) {
            return null;
        }
        int len = token.length();
        if (len <= 6) {
            return "****";
        }
        String prefix = token.substring(0, 4);
        String suffix = token.substring(len - 2);
        return prefix + "****" + suffix;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
