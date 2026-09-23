package com.EduePoa.EP.academics.curriculum.dto;

import lombok.Data;

/**
 * Lightweight response DTOs for the curriculum read APIs. Grouped in one file to keep the many
 * small read shapes together; JPA entities are never exposed directly from controllers.
 */
public class CurriculumDtos {

    private CurriculumDtos() {}

    @Data
    public static class CurriculumVersionDto {
        private Long id;
        private String code;
        private String name;
        private String version;
        private String status;
        private String source;
        private String description;
    }

    @Data
    public static class EducationLevelDto {
        private Long id;
        private String code;
        private String name;
        private String band;
        private Integer sequence;
    }

    @Data
    public static class GradeDto {
        private Long id;
        private Long gradeId;
        private String gradeCode;
        private String gradeName;
        private String educationLevel;
        private Integer sequence;
    }

    @Data
    public static class LearningAreaDto {
        private Long id;
        private String catalogueKey;
        private String officialName;
        private String displayName;
        private String code;
        private String learningAreaType;
        private Boolean isCore;
        private Boolean isOptional;
        private String status;
        private Integer sequence;
        /**
         * Bridge to the operational AcademicSubject id. Use this when calling marks-entry,
         * teacher-assignment or results endpoints that still key on the subject id.
         */
        private Long academicSubjectId;
    }

    @Data
    public static class StrandDto {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Integer sequence;
    }

    @Data
    public static class SubStrandDto {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Integer suggestedLessons;
        private Integer sequence;
    }

    @Data
    public static class LearningOutcomeDto {
        private Long id;
        private String code;
        private String description;
        private String knowledge;
        private String skills;
        private String attitudes;
        private String values;
        private Integer sequence;
    }

    @Data
    public static class CompetencyDto {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Integer sequence;
    }

    @Data
    public static class ValueDto {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Integer sequence;
    }

    @Data
    public static class PciDto {
        private Long id;
        private String code;
        private String name;
        private String category;
        private String description;
        private Integer sequence;
    }
}
