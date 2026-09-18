package com.EduePoa.EP.StudentRegistration.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress event streamed over WebSocket while a bulk student upload runs.
 * One of these is published to {@code /topic/bulk-upload/{jobId}} as rows are
 * processed, plus a final event when the job finishes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadProgressDTO {

    /** Lifecycle status of the job. */
    public enum Status { STARTED, IN_PROGRESS, COMPLETED, FAILED }

    private String jobId;
    private Status status;

    private int totalRecords;
    private int processedRecords;
    private int successCount;
    private int failureCount;

    /** 0–100, convenience for the frontend progress bar. */
    private int percentComplete;

    /** The admission number of the row just processed (null for STARTED/COMPLETED). */
    private String currentAdmissionNumber;

    /** Errors accumulated so far. */
    private List<BulkUploadError> errors = new ArrayList<>();

    /** Human-readable message (e.g. failure reason for the whole job). */
    private String message;
}
