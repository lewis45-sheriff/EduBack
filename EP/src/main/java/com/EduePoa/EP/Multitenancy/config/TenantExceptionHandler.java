package com.EduePoa.EP.Multitenancy.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@ControllerAdvice
public class TenantExceptionHandler {

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<Map<String, Object>> handleTenantContextMissing(
            TenantContextMissingException ex, HttpServletRequest request) {
        log.error("Tenant context missing: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TENANT_CONTEXT_MISSING",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TenantMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTenantMismatch(
            TenantMismatchException ex, HttpServletRequest request) {
        log.warn("SECURITY_WARN tenant_mismatch | context_tenant={} | path={} | action={}",
                ex.getTenantId(), request.getRequestURI(), request.getMethod());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "TENANT_MISMATCH",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TenantInactiveException.class)
    public ResponseEntity<Map<String, Object>> handleTenantInactive(
            TenantInactiveException ex, HttpServletRequest request) {
        log.warn("Tenant inactive: tenantId={}", ex.getTenantId());
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "TENANT_INACTIVE",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotFound(
            TenantNotFoundException ex, HttpServletRequest request) {
        log.warn("Tenant not found: tenantId={}", ex.getTenantId());
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "TENANT_NOT_FOUND",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<Map<String, Object>> handleCrossTenantAccess(
            CrossTenantAccessException ex, HttpServletRequest request) {
        log.warn("SECURITY_WARN cross_tenant_access | context_tenant={} | path={} | action={}",
                ex.getTenantId(), request.getRequestURI(), request.getMethod());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "CROSS_TENANT_ACCESS",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TenantConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTenantConflict(
            TenantConflictException ex, HttpServletRequest request) {
        log.warn("Tenant conflict: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "TENANT_CONFLICT",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TenantConfigurationMissingException.class)
    public ResponseEntity<Map<String, Object>> handleTenantConfigMissing(
            TenantConfigurationMissingException ex, HttpServletRequest request) {
        log.error("Tenant configuration missing: tenantId={} | message={}",
                ex.getTenantId(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TENANT_CONFIG_MISSING",
                ex.getMessage(),
                ex.getTenantId(),
                request.getRequestURI()
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message, String tenantId, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        body.put("tenantId", tenantId);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
