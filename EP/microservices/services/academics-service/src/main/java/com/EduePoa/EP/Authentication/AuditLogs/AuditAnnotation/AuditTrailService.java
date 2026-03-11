package com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditTrailService {
    private final AuditService auditService;

    @AfterReturning("@annotation(audit)")
    public void save(JoinPoint ignored, Audit audit){
        String val = audit.value();
        this.auditService.log(val);
    }
}
