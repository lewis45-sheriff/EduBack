package com.EduePoa.EP.academics.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * A summarized learner entry for the report-card search/list screen.
 * Mirrors the frontend {@code ReportCardListItem} contract.
 */
@Data
@Builder
public class ReportCardListItemDto {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private String gradeName;
    private String streamName;
    /** Human-readable readiness label, e.g. "Ready" or "Pending". */
    private String status;
    /** True when assessment results exist for this learner in the term/year. */
    private boolean ready;
}
