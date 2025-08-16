package com.EduePoa.EP.Authentication.AuditLogs;

import com.EduePoa.EP.Utils.CustomResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository auditRepository;
    public CustomResponse<?> getAuditClass(Date date) {
        CustomResponse<List<Audit>> response = new CustomResponse<>();
        try {
            List<Audit> auditTrails = auditRepository.findByDate(date);

            response.setEntity(auditTrails);
            response.setMessage("Audit trail records retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage("Error retrieving audit trail: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            // Optionally log the error
            // log.error("Error in getAuditClass: {}", e.getMessage(), e);
        }
        return response;
    }

    public void log(String module, String ...val) {
        Audit auditTrail = new Audit();
        String userEmail, device = "", ipAddress = "";
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            ipAddress = request.getRemoteAddr();
            device = request.getHeader("User-Agent");

            userEmail = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();


        } catch (NullPointerException | ClassCastException ignored1) {
            userEmail = "SYSTEM";
        }

        auditTrail.setModule(module);
        auditTrail.setDevice(device);
        auditTrail.setIpAddress(ipAddress);
        auditTrail.setUserEmail(userEmail);
        auditTrail.setActivity(Arrays.stream(val).map(str-> Objects.isNull(str) ? "" : str).collect(Collectors.joining(" ")));
        auditRepository.save(auditTrail);
    }
}
