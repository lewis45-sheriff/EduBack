package com.EduePoa.EP.StudentRegistration.Response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NemisStatusResponseDTO {
    private Long studentId;
    private String upi;
    private String registrationIdentifierType;
    private String registrationIdentifierValue;
    /** READY if data exists and queueSync is false; QUEUED if queueSync is true; NOT_REGISTERED if no NEMIS record. */
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastSyncAttemptAt;
    private String lastSyncError;
    private LocalDateTime verifiedAt;
}
