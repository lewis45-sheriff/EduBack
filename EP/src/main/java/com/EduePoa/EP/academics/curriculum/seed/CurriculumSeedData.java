package com.EduePoa.EP.academics.curriculum.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Jackson binding for the externalized curriculum reference data
 * ({@code resources/curriculum/cbc-2024.json} and {@code assessment-frameworks.json}).
 * Curriculum content lives in editable JSON, not in Java, so a curriculum update is a data change.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurriculumSeedData {

    private VersionSeed version;
    private List<LevelSeed> educationLevels;
    private List<GradeSeed> grades;
    private List<LearningAreaSeed> learningAreas;
    private TreeSeed sampleCurriculumTree;
    private List<NamedSeed> coreCompetencies;
    private List<NamedSeed> values;
    private List<PciSeed> pcis;

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VersionSeed {
        private String code;
        private String name;
        private String version;
        private String status;
        private String source;
        private String description;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LevelSeed {
        private String code;
        private String band;
        private String name;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GradeSeed {
        private String gradeCode;
        private String gradeName;
        private String educationLevel;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LearningAreaSeed {
        private String catalogueKey;
        private String officialName;
        private String educationLevel;
        private String type;
        private Boolean core;
        private Boolean optional;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TreeSeed {
        private String learningAreaKey;
        private List<StrandSeed> strands;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StrandSeed {
        private String code;
        private String name;
        private Integer sequence;
        private List<SubStrandSeed> subStrands;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubStrandSeed {
        private String code;
        private String name;
        private Integer sequence;
        private List<OutcomeSeed> learningOutcomes;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutcomeSeed {
        private String code;
        private String description;
        private Integer sequence;
        private List<String> inquiryQuestions;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamedSeed {
        private String code;
        private String name;
        private String description;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PciSeed {
        private String code;
        private String name;
        private String category;
        private String description;
        private Integer sequence;
    }

    // ----- assessment-frameworks.json -----

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrameworksFile {
        private List<FrameworkSeed> frameworks;
        private List<TypeSeed> assessmentTypes;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrameworkSeed {
        private String code;
        private String name;
        private String description;
        private String educationLevel;
        private String gradeCode;
        private Boolean aggregateAcrossLearningAreas;
        private List<PerformanceLevelSeed> performanceLevels;
        private List<ComponentSeed> components;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PerformanceLevelSeed {
        private String code;
        private String broadLevel;
        private String label;
        private String abbreviation;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private Integer points;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentSeed {
        private String code;
        private String name;
        private BigDecimal weight;
        private Integer sequence;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypeSeed {
        private String code;
        private String name;
        private String defaultMethod;
    }
}
