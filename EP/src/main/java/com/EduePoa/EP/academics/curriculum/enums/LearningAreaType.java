package com.EduePoa.EP.academics.curriculum.enums;

/**
 * Classification of a learning area within a curriculum version. Kept broad and
 * curriculum-version scoped so it can accommodate activity areas (Pre-Primary),
 * conventional learning areas, optional languages, and religious-education variants.
 */
public enum LearningAreaType {
    CORE,
    OPTIONAL,
    ACTIVITY_AREA,
    LANGUAGE,
    RELIGIOUS_EDUCATION,
    PATHWAY_SUBJECT
}
