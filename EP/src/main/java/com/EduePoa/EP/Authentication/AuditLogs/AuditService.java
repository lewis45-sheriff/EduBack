package com.EduePoa.EP.Authentication.AuditLogs;

import com.EduePoa.EP.Utils.CustomResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository auditRepository;


    public CustomResponse<?> getAllAuditLogs(int page, int size) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Audit> auditPage = auditRepository.findAllByOrderByTimestampDesc(pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("logs", auditPage.getContent());
            result.put("currentPage", auditPage.getNumber());
            result.put("totalItems", auditPage.getTotalElements());
            result.put("totalPages", auditPage.getTotalPages());

            response.setEntity(result);
            response.setMessage("Audit logs retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error retrieving all audit logs: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error retrieving audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    public CustomResponse<?> getAuditClass(Date date) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            List<Audit> auditTrails = auditRepository.findByDate(date);

            response.setEntity(auditTrails);
            response.setMessage(
                    "Audit logs for " + date + " retrieved successfully (" + auditTrails.size() + " records)");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error retrieving audit logs by date: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error retrieving audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    public CustomResponse<?> getAuditLogsByDateRange(Date startDate, Date endDate) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            if (startDate.after(endDate)) {
                response.setEntity(null);
                response.setMessage("Start date must be before or equal to end date");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            List<Audit> auditTrails = auditRepository.findByDateRange(startDate, endDate);

            response.setEntity(auditTrails);
            response.setMessage("Audit logs retrieved successfully (" + auditTrails.size() + " records)");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error retrieving audit logs by date range: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error retrieving audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    public CustomResponse<?> getAuditLogsByModule(String module) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            List<Audit> auditTrails = auditRepository.findByModuleIgnoreCaseOrderByTimestampDesc(module);

            response.setEntity(auditTrails);
            response.setMessage("Audit logs for module '" + module + "' retrieved successfully (" + auditTrails.size()
                    + " records)");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error retrieving audit logs by module: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error retrieving audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    public CustomResponse<?> getAuditLogsByUser(String email) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            List<Audit> auditTrails = auditRepository.findByUserEmailIgnoreCaseOrderByTimestampDesc(email);

            response.setEntity(auditTrails);
            response.setMessage(
                    "Audit logs for user '" + email + "' retrieved successfully (" + auditTrails.size() + " records)");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error retrieving audit logs by user: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error retrieving audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    public CustomResponse<?> searchAuditLogs(String keyword) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                response.setEntity(null);
                response.setMessage("Search keyword cannot be empty");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            List<Audit> auditTrails = auditRepository.searchByActivity(keyword.trim());

            response.setEntity(auditTrails);
            response.setMessage("Search results for '" + keyword + "' (" + auditTrails.size() + " records)");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            log.error("Error searching audit logs: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Error searching audit logs: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    public void log(String module, String... val) {
        Audit auditTrail = new Audit();
        String userEmail, device = "", ipAddress = "";
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            ipAddress = request.getRemoteAddr();
            device = request.getHeader("User-Agent");

            userEmail = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .getUsername();

        } catch (NullPointerException | ClassCastException ignored1) {
            userEmail = "SYSTEM";
        }

        auditTrail.setModule(module);
        auditTrail.setDevice(device);
        auditTrail.setIpAddress(ipAddress);
        auditTrail.setUserEmail(userEmail);
        auditTrail.setActivity(
                Arrays.stream(val).map(str -> Objects.isNull(str) ? "" : str).collect(Collectors.joining(" ")));
        auditRepository.save(auditTrail);
    }
}
