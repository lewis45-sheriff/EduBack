package com.EduePoa.EP.academics.assessment.enums;

/**
 * Approval/publication lifecycle for assessment scores and computed results.
 * Mirrors the existing approval concept (StudentsScore.resultApproved) while allowing the richer
 * DRAFT &rarr; SUBMITTED &rarr; APPROVED &rarr; PUBLISHED workflow the CBC module needs. AMENDED marks a
 * previously published result that went through the amendment workflow.
 */
public enum ResultStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    PUBLISHED,
    AMENDED
}
