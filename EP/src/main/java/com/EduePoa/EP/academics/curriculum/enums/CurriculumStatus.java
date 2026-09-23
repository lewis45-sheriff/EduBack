package com.EduePoa.EP.academics.curriculum.enums;

/**
 * Lifecycle status of curriculum reference/configuration data.
 * Soft-delete/versioning is preferred over physical deletion so historical
 * learner records keep referencing the definitions they were created under.
 */
public enum CurriculumStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    ARCHIVED
}
