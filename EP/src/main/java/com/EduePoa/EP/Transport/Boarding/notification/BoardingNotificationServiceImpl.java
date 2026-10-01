package com.EduePoa.EP.Transport.Boarding.notification;

import com.EduePoa.EP.Authentication.Email.EmailService;
import com.EduePoa.EP.Communications.SMS.SmsGatewayService;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Parents.Parent;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentGuardian;
import com.EduePoa.EP.StudentRegistration.StudentGuardianRepository;
import com.EduePoa.EP.Transport.Boarding.BoardingLeg;
import com.EduePoa.EP.Transport.Boarding.TransportBoardingEvent;
import com.EduePoa.EP.Transport.Boarding.TransportBoardingEventRepository;
import com.EduePoa.EP.Transport.Transport;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Async, best-effort implementation of {@link BoardingNotificationService}.
 *
 * <p>Reuses the existing {@link SmsGatewayService} and {@link EmailService}; does not introduce a
 * new SMS/email sender. Guardian resolution reuses {@link StudentGuardianRepository}. Because the
 * work runs on an {@code @Async} worker thread (a fresh thread with an empty {@link TenantContext}),
 * the tenant id is re-established and the Hibernate tenant filter is enabled on this thread —
 * mirroring the pattern used by {@code CurriculumSeeder} and {@code StudentServiceImpl}'s bulk
 * upload — so every lookup is tenant-scoped and no other tenant's rows can leak.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardingNotificationServiceImpl implements BoardingNotificationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TransportBoardingEventRepository boardingEventRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final SmsGatewayService smsGatewayService;
    private final EmailService emailService;
    private final EntityManager entityManager;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyForEvent(Long boardingEventId, String tenantId) {
        if (boardingEventId == null || tenantId == null || tenantId.isBlank()) {
            return;
        }

        boolean contextEstablished = false;
        try {
            // Re-establish tenant context + Hibernate filter on this worker thread.
            TenantContext.setCurrentTenant(tenantId);
            enableTenantFilter(tenantId);
            contextEstablished = true;

            TransportBoardingEvent event = boardingEventRepository.findById(boardingEventId).orElse(null);
            if (event == null) {
                log.warn("Boarding notification skipped: event {} not found for tenant {}",
                        boardingEventId, tenantId);
                return;
            }

            Student student = event.getStudent();
            if (student == null) {
                return;
            }

            List<StudentGuardian> guardians = studentGuardianRepository.findByStudentId(student.getId());
            if (guardians.isEmpty()) {
                log.info("Boarding notification: no guardians on record for student {}", student.getId());
                return;
            }

            String studentName = fullName(student);
            String smsText = buildSmsText(event, studentName);
            String emailSubject = buildEmailSubject(event, studentName);
            String emailBody = buildEmailBody(event, studentName);

            for (StudentGuardian guardian : guardians) {
                Parent parent = guardian.getParent();
                if (parent == null) {
                    continue;
                }
                notifyParent(parent, student.getId(), smsText, emailSubject, emailBody);
            }

        } catch (RuntimeException e) {
            // Best-effort: never propagate. Do not log message bodies (may contain names/PII beyond
            // what is necessary); the identifiers below are sufficient for troubleshooting.
            log.error("Boarding notification failed for event {} (tenant {}): {}",
                    boardingEventId, tenantId, e.getMessage());
        } finally {
            if (contextEstablished) {
                disableTenantFilter();
            }
            TenantContext.clear();
        }
    }

    private void notifyParent(Parent parent, Long studentId, String smsText,
                              String emailSubject, String emailBody) {
        // SMS
        try {
            String phone = parent.getPhoneNumber();
            if (parent.isReceiveSms() && phone != null && !phone.isBlank()) {
                smsGatewayService.sendSms(phone, smsText);
            }
        } catch (RuntimeException e) {
            log.warn("Boarding SMS to parent {} for student {} failed: {}",
                    parent.getId(), studentId, e.getMessage());
        }

        // Email (EmailService.sendEmail is itself @Async and swallows its own errors, but guard anyway)
        try {
            String email = parent.getEmail();
            if (parent.isReceiveEmail() && email != null && !email.isBlank()) {
                emailService.sendEmail(email, emailSubject, emailBody);
            }
        } catch (RuntimeException e) {
            log.warn("Boarding email to parent {} for student {} failed: {}",
                    parent.getId(), studentId, e.getMessage());
        }
    }

    // --- message building -------------------------------------------------------------------

    /** Whether the leg represents the student getting ON the vehicle (vs alighting). */
    private static boolean isBoarding(BoardingLeg leg) {
        return leg == BoardingLeg.MORNING_PICKUP || leg == BoardingLeg.EVENING_PICKUP;
    }

    private static String action(BoardingLeg leg) {
        return isBoarding(leg) ? "boarded" : "alighted from";
    }

    private String buildSmsText(TransportBoardingEvent event, String studentName) {
        String time = event.getCapturedAt() != null ? event.getCapturedAt().format(TIME_FMT) : "";
        String vehicle = vehicleLabel(event.getVehicle());
        // e.g. "Alice Doe has boarded the school vehicle KDA 123A at 06:42 on 2026-09-29."
        return String.format("%s has %s the school vehicle %s at %s on %s.",
                studentName, action(event.getLeg()), vehicle, time, event.getServiceDate());
    }

    private String buildEmailSubject(TransportBoardingEvent event, String studentName) {
        return "Transport update: " + studentName + " "
                + (isBoarding(event.getLeg()) ? "boarded" : "alighted");
    }

    private String buildEmailBody(TransportBoardingEvent event, String studentName) {
        String time = event.getCapturedAt() != null ? event.getCapturedAt().format(TIME_FMT) : "";
        String vehicle = vehicleLabel(event.getVehicle());
        String legDescription = legDescription(event.getLeg());
        return "<p>Dear Parent/Guardian,</p>"
                + "<p>This is to notify you that <strong>" + escape(studentName) + "</strong> has <strong>"
                + action(event.getLeg()) + "</strong> the school vehicle <strong>" + escape(vehicle)
                + "</strong> at <strong>" + time + "</strong> on <strong>" + event.getServiceDate()
                + "</strong> (" + legDescription + ").</p>"
                + "<p>Regards,<br/>School Transport</p>";
    }

    private static String legDescription(BoardingLeg leg) {
        return switch (leg) {
            case MORNING_PICKUP -> "morning pickup — travelling to school";
            case MORNING_DROPOFF -> "arrived at school";
            case EVENING_PICKUP -> "evening pickup — travelling home";
            case EVENING_DROPOFF -> "arrived at the drop-off stop";
        };
    }

    private static String vehicleLabel(Transport vehicle) {
        if (vehicle == null) {
            return "the school vehicle";
        }
        String number = vehicle.getVehicleNumber();
        return (number != null && !number.isBlank()) ? number : "the school vehicle";
    }

    private static String fullName(Student s) {
        String first = s.getFirstName() != null ? s.getFirstName() : "";
        String last = s.getLastName() != null ? s.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Your child" : name;
    }

    /** Minimal HTML escaping for values interpolated into the email body. */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // --- tenant filter (mirrors CurriculumSeeder) -------------------------------------------

    private void enableTenantFilter(String tenantId) {
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (RuntimeException ignored) {
            // Filter may not be enabled; safe to ignore.
        }
    }
}
