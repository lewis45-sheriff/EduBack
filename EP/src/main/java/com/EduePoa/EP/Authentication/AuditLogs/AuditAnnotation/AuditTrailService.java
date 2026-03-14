package com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation;

import com.EduePoa.EP.Authentication.AuditLogs.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * AOP Aspect that automatically saves an audit log entry after any method
 * annotated with @Audit completes (successfully or with an exception).
 *
 * Place @Audit on service methods:
 *
 *   @Audit(module = "SUPPLIER INVOICE", action = "APPROVE")
 *   public CustomResponse<?> approveInvoice(...) { ... }
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {

    private final AuditRepository auditRepository;

    // ---------------------------------------------------------------
    // Fires AFTER a method returns successfully
    // ---------------------------------------------------------------
    @AfterReturning(pointcut = "@annotation(auditAnnotation)", returning = "returnValue")
    public void afterSuccess(JoinPoint joinPoint, com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit auditAnnotation, Object returnValue) {
        saveAuditLog(joinPoint, auditAnnotation, null);
    }

    // ---------------------------------------------------------------
    // Fires AFTER a method throws an exception
    // ---------------------------------------------------------------
    @AfterThrowing(pointcut = "@annotation(auditAnnotation)", throwing = "ex")
    public void afterFailure(JoinPoint joinPoint, com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit auditAnnotation, Throwable ex) {
        saveAuditLog(joinPoint, auditAnnotation, ex);
    }

    // ---------------------------------------------------------------
    // Core logic — runs async so it never blocks the main request
    // ---------------------------------------------------------------
    @Async
    protected void saveAuditLog(JoinPoint joinPoint,
                                com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit auditAnnotation,
                                Throwable exception) {
        try {
            // --- Resolve user -------------------------------------------------
            String userEmail = "SYSTEM";
            try {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof UserDetails ud) {
                    userEmail = ud.getUsername();
                } else if (principal instanceof String s) {
                    userEmail = s;
                }
            } catch (Exception ignored) {}

            // --- Resolve request context (IP, device) -------------------------
            String ipAddress = "N/A";
            String device = "N/A";
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpServletRequest request = attrs.getRequest();
                ipAddress = getClientIp(request);
                String ua = request.getHeader("User-Agent");
                device = ua != null ? ua : "N/A";
            } catch (Exception ignored) {}

            // --- Build module / action / activity description -----------------
            String module = !auditAnnotation.module().isEmpty()
                    ? auditAnnotation.module()
                    : auditAnnotation.value();

            String action = !auditAnnotation.action().isEmpty()
                    ? auditAnnotation.action()
                    : auditAnnotation.value();

            String methodName = joinPoint.getSignature().getName();

            String argsDescription = Arrays.stream(joinPoint.getArgs())
                    .map(arg -> arg != null ? arg.toString() : "null")
                    .collect(Collectors.joining(", "));

            String activity;
            if (exception != null) {
                activity = String.format("[FAILED] %s | method=%s | args=[%s] | error=%s",
                        action, methodName, argsDescription, exception.getMessage());
            } else {
                activity = String.format("%s | method=%s | args=[%s]",
                        action, methodName, argsDescription);
            }

            // --- Persist the log entry ----------------------------------------
            Audit entry = new Audit();
            entry.setTimestamp(new Timestamp(System.currentTimeMillis()));
            entry.setModule(module);
            entry.setActivity(activity);
            entry.setUserEmail(userEmail);
            entry.setIpAddress(ipAddress);
            entry.setDevice(device);

            auditRepository.save(entry);

        } catch (Exception e) {
            // Audit logging must never break the main flow
            log.error("Audit logging failed: {}", e.getMessage());
        }
    }

    /**
     * Extracts the real client IP, honouring common reverse-proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim(); // first entry is the original client
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return request.getRemoteAddr();
    }
}
