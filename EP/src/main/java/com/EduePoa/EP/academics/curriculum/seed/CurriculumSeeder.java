package com.EduePoa.EP.academics.curriculum.seed;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import com.EduePoa.EP.academics.assessment.entity.*;
import com.EduePoa.EP.academics.assessment.enums.AssessmentMethod;
import com.EduePoa.EP.academics.assessment.enums.BroadPerformanceLevel;
import com.EduePoa.EP.academics.assessment.repository.*;
import com.EduePoa.EP.academics.assessment.service.PerformanceLevelService;
import com.EduePoa.EP.academics.curriculum.entity.*;
import com.EduePoa.EP.academics.curriculum.enums.*;
import com.EduePoa.EP.academics.curriculum.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Idempotent, per-tenant curriculum seeder. Runs after {@code CreateAdmin} (@Order 10) so the
 * default tenant exists. Reads externalized reference data from {@code resources/curriculum/*.json}
 * and seeds the active curriculum version, education levels, grade mappings, learning areas, a
 * sample curriculum tree, competencies, values, PCIs and assessment frameworks (incl. KJSEA bands).
 * <p>
 * Idempotency: every insert is guarded by an existence check keyed on the natural business keys, so
 * running the application many times never creates duplicates. {@link #seedForTenant(String)} is
 * reusable for newly provisioned tenants.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(30)
public class CurriculumSeeder implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    private final CurriculumVersionRepository versionRepository;
    private final EducationLevelRepository educationLevelRepository;
    private final GradeLevelMappingRepository gradeLevelMappingRepository;
    private final LearningAreaRepository learningAreaRepository;
    private final StrandRepository strandRepository;
    private final SubStrandRepository subStrandRepository;
    private final SpecificLearningOutcomeRepository outcomeRepository;
    private final InquiryQuestionRepository inquiryQuestionRepository;
    private final CoreCompetencyRepository competencyRepository;
    private final CbcValueRepository valueRepository;
    private final PertinentContemporaryIssueRepository pciRepository;

    private final AssessmentFrameworkRepository frameworkRepository;
    private final PerformanceLevelRepository performanceLevelRepository;
    private final AssessmentComponentRepository componentRepository;
    private final AssessmentTypeRepository assessmentTypeRepository;
    private final PerformanceLevelService performanceLevelService;

    private final com.EduePoa.EP.academics.repository.AcademicSubjectRepository academicSubjectRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                seedForTenant(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("Curriculum seeding failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    /** Seed (idempotently) for a single tenant. Safe to call for a newly provisioned tenant. */
    public void seedForTenant(String tenantIdentifier) throws Exception {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            CurriculumSeedData data = load("curriculum/cbc-2024.json", CurriculumSeedData.class);
            CurriculumSeedData.FrameworksFile frameworks =
                    load("curriculum/assessment-frameworks.json", CurriculumSeedData.FrameworksFile.class);

            CurriculumVersion version = seedVersion(data.getVersion());
            Map<String, EducationLevel> levels = seedLevels(version, data.getEducationLevels());
            seedGrades(version, levels, data.getGrades());
            Map<String, LearningArea> areas = seedLearningAreas(version, levels, data.getLearningAreas());
            seedTree(version, areas, data.getSampleCurriculumTree());
            seedCompetencies(version, data.getCoreCompetencies());
            seedValues(version, data.getValues());
            seedPcis(version, data.getPcis());
            seedFrameworks(version, frameworks);
            seedAssessmentTypes(frameworks);
            bridgeLearningAreasToSubjects(version);

            log.info("Curriculum seeding complete for tenant {}", tenantIdentifier);
        } finally {
            TenantContext.clear();
        }
    }

    private <T> T load(String path, Class<T> type) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    private CurriculumVersion seedVersion(CurriculumSeedData.VersionSeed v) {
        return versionRepository.findByCode(v.getCode()).orElseGet(() -> {
            CurriculumVersion cv = new CurriculumVersion();
            cv.setCode(v.getCode());
            cv.setName(v.getName());
            cv.setVersion(v.getVersion());
            cv.setStatus(CurriculumStatus.valueOf(v.getStatus()));
            cv.setSource(CurriculumSource.valueOf(v.getSource()));
            cv.setDescription(v.getDescription());
            return versionRepository.save(cv);
        });
    }

    private Map<String, EducationLevel> seedLevels(CurriculumVersion version,
                                                   List<CurriculumSeedData.LevelSeed> seeds) {
        Map<String, EducationLevel> byCode = new HashMap<>();
        if (seeds == null) return byCode;
        for (var s : seeds) {
            EducationLevel level = educationLevelRepository
                    .findByCurriculumVersionAndCode(version, s.getCode())
                    .orElseGet(() -> {
                        EducationLevel e = new EducationLevel();
                        e.setCurriculumVersion(version);
                        e.setCode(s.getCode());
                        e.setBand(CbcEducationLevel.valueOf(s.getBand()));
                        e.setName(s.getName());
                        e.setSequence(s.getSequence());
                        return educationLevelRepository.save(e);
                    });
            byCode.put(s.getCode(), level);
        }
        return byCode;
    }

    private void seedGrades(CurriculumVersion version, Map<String, EducationLevel> levels,
                            List<CurriculumSeedData.GradeSeed> seeds) {
        if (seeds == null) return;
        for (var s : seeds) {
            if (gradeLevelMappingRepository.existsByCurriculumVersionAndGradeCode(version, s.getGradeCode())) {
                continue;
            }
            EducationLevel level = levels.get(s.getEducationLevel());
            if (level == null) continue;
            GradeLevelMapping m = new GradeLevelMapping();
            m.setCurriculumVersion(version);
            m.setEducationLevel(level);
            m.setGradeCode(s.getGradeCode());
            m.setGradeName(s.getGradeName());
            m.setSequence(s.getSequence());
            gradeLevelMappingRepository.save(m);
        }
    }

    private Map<String, LearningArea> seedLearningAreas(CurriculumVersion version,
                                                        Map<String, EducationLevel> levels,
                                                        List<CurriculumSeedData.LearningAreaSeed> seeds) {
        Map<String, LearningArea> byKey = new HashMap<>();
        if (seeds == null) return byKey;
        for (var s : seeds) {
            LearningArea la = learningAreaRepository
                    .findByCurriculumVersionAndCatalogueKey(version, s.getCatalogueKey())
                    .orElseGet(() -> {
                        LearningArea a = new LearningArea();
                        a.setCurriculumVersion(version);
                        a.setEducationLevel(levels.get(s.getEducationLevel()));
                        a.setCatalogueKey(s.getCatalogueKey());
                        a.setOfficialName(s.getOfficialName());
                        a.setDisplayName(s.getOfficialName());
                        a.setLearningAreaType(LearningAreaType.valueOf(s.getType()));
                        a.setIsCore(Boolean.TRUE.equals(s.getCore()));
                        a.setIsOptional(Boolean.TRUE.equals(s.getOptional()));
                        a.setSource(CurriculumSource.OFFICIAL_KICD);
                        a.setStatus(CurriculumStatus.ACTIVE);
                        a.setSequence(s.getSequence());
                        return learningAreaRepository.save(a);
                    });
            byKey.put(s.getCatalogueKey(), la);
        }
        return byKey;
    }

    private void seedTree(CurriculumVersion version, Map<String, LearningArea> areas,
                          CurriculumSeedData.TreeSeed tree) {
        if (tree == null || tree.getStrands() == null) return;
        LearningArea la = areas.get(tree.getLearningAreaKey());
        if (la == null) return;
        for (var strandSeed : tree.getStrands()) {
            Strand strand = strandRepository.findByLearningAreaAndCode(la, strandSeed.getCode())
                    .orElseGet(() -> {
                        Strand st = new Strand();
                        st.setCurriculumVersion(version);
                        st.setLearningArea(la);
                        st.setCode(strandSeed.getCode());
                        st.setName(strandSeed.getName());
                        st.setSequence(strandSeed.getSequence());
                        st.setStatus(CurriculumStatus.ACTIVE);
                        return strandRepository.save(st);
                    });
            if (strandSeed.getSubStrands() == null) continue;
            for (var ssSeed : strandSeed.getSubStrands()) {
                SubStrand subStrand = subStrandRepository.findByStrandAndCode(strand, ssSeed.getCode())
                        .orElseGet(() -> {
                            SubStrand ss = new SubStrand();
                            ss.setCurriculumVersion(version);
                            ss.setStrand(strand);
                            ss.setCode(ssSeed.getCode());
                            ss.setName(ssSeed.getName());
                            ss.setSequence(ssSeed.getSequence());
                            ss.setStatus(CurriculumStatus.ACTIVE);
                            return subStrandRepository.save(ss);
                        });
                if (ssSeed.getLearningOutcomes() == null) continue;
                for (var oSeed : ssSeed.getLearningOutcomes()) {
                    SpecificLearningOutcome outcome = outcomeRepository
                            .findBySubStrandAndCode(subStrand, oSeed.getCode())
                            .orElseGet(() -> {
                                SpecificLearningOutcome o = new SpecificLearningOutcome();
                                o.setCurriculumVersion(version);
                                o.setSubStrand(subStrand);
                                o.setCode(oSeed.getCode());
                                o.setDescription(oSeed.getDescription());
                                o.setSequence(oSeed.getSequence());
                                o.setStatus(CurriculumStatus.ACTIVE);
                                return outcomeRepository.save(o);
                            });
                    seedInquiryQuestions(version, outcome, oSeed.getInquiryQuestions());
                }
            }
        }
    }

    private void seedInquiryQuestions(CurriculumVersion version, SpecificLearningOutcome outcome,
                                      List<String> questions) {
        if (questions == null) return;
        int seq = 1;
        for (String q : questions) {
            if (!inquiryQuestionRepository.existsByLearningOutcomeAndQuestion(outcome, q)) {
                InquiryQuestion iq = new InquiryQuestion();
                iq.setCurriculumVersion(version);
                iq.setLearningOutcome(outcome);
                iq.setQuestion(q);
                iq.setSequence(seq);
                inquiryQuestionRepository.save(iq);
            }
            seq++;
        }
    }

    private void seedCompetencies(CurriculumVersion version, List<CurriculumSeedData.NamedSeed> seeds) {
        if (seeds == null) return;
        for (var s : seeds) {
            if (competencyRepository.existsByCurriculumVersionAndCode(version, s.getCode())) continue;
            CoreCompetency c = new CoreCompetency();
            c.setCurriculumVersion(version);
            c.setCode(s.getCode());
            c.setName(s.getName());
            c.setDescription(s.getDescription());
            c.setSequence(s.getSequence());
            c.setStatus(CurriculumStatus.ACTIVE);
            competencyRepository.save(c);
        }
    }

    private void seedValues(CurriculumVersion version, List<CurriculumSeedData.NamedSeed> seeds) {
        if (seeds == null) return;
        for (var s : seeds) {
            if (valueRepository.existsByCurriculumVersionAndCode(version, s.getCode())) continue;
            CbcValue v = new CbcValue();
            v.setCurriculumVersion(version);
            v.setCode(s.getCode());
            v.setName(s.getName());
            v.setDescription(s.getDescription());
            v.setSequence(s.getSequence());
            v.setStatus(CurriculumStatus.ACTIVE);
            valueRepository.save(v);
        }
    }

    private void seedPcis(CurriculumVersion version, List<CurriculumSeedData.PciSeed> seeds) {
        if (seeds == null) return;
        for (var s : seeds) {
            if (pciRepository.existsByCurriculumVersionAndCode(version, s.getCode())) continue;
            PertinentContemporaryIssue p = new PertinentContemporaryIssue();
            p.setCurriculumVersion(version);
            p.setCode(s.getCode());
            p.setName(s.getName());
            p.setCategory(s.getCategory());
            p.setDescription(s.getDescription());
            p.setSequence(s.getSequence());
            p.setStatus(CurriculumStatus.ACTIVE);
            pciRepository.save(p);
        }
    }

    private void seedFrameworks(CurriculumVersion version, CurriculumSeedData.FrameworksFile file) {
        if (file == null || file.getFrameworks() == null) return;
        for (var fSeed : file.getFrameworks()) {
            AssessmentFramework framework = frameworkRepository
                    .findByCurriculumVersionAndCode(version, fSeed.getCode())
                    .orElseGet(() -> {
                        AssessmentFramework f = new AssessmentFramework();
                        f.setCurriculumVersion(version);
                        f.setCode(fSeed.getCode());
                        f.setName(fSeed.getName());
                        f.setDescription(fSeed.getDescription());
                        if (fSeed.getEducationLevel() != null) {
                            f.setEducationLevel(CbcEducationLevel.valueOf(fSeed.getEducationLevel()));
                        }
                        f.setGradeCode(fSeed.getGradeCode());
                        f.setAggregateAcrossLearningAreas(
                                !Boolean.FALSE.equals(fSeed.getAggregateAcrossLearningAreas()));
                        f.setStatus(CurriculumStatus.ACTIVE);
                        return frameworkRepository.save(f);
                    });

            seedPerformanceLevels(framework, fSeed.getPerformanceLevels());
            seedComponents(framework, fSeed.getComponents());
        }
    }

    private void seedPerformanceLevels(AssessmentFramework framework,
                                       List<CurriculumSeedData.PerformanceLevelSeed> seeds) {
        if (seeds == null || seeds.isEmpty()) return;

        // Build in-memory list first so we can validate bands before persisting.
        List<PerformanceLevel> toValidate = new ArrayList<>();
        for (var s : seeds) {
            PerformanceLevel pl = new PerformanceLevel();
            pl.setAssessmentFramework(framework);
            pl.setCode(s.getCode());
            pl.setBroadLevel(BroadPerformanceLevel.valueOf(s.getBroadLevel()));
            pl.setLabel(s.getLabel());
            pl.setAbbreviation(s.getAbbreviation());
            pl.setMinScore(s.getMinScore());
            pl.setMaxScore(s.getMaxScore());
            pl.setPoints(s.getPoints());
            pl.setSequence(s.getSequence());
            toValidate.add(pl);
        }
        // Fail fast on misconfigured (overlapping/out-of-range) bands.
        performanceLevelService.validateBands(toValidate);

        for (PerformanceLevel pl : toValidate) {
            if (!performanceLevelRepository.existsByAssessmentFrameworkAndCode(framework, pl.getCode())) {
                performanceLevelRepository.save(pl);
            }
        }
    }

    private void seedComponents(AssessmentFramework framework,
                                List<CurriculumSeedData.ComponentSeed> seeds) {
        if (seeds == null) return;
        for (var s : seeds) {
            if (componentRepository.existsByAssessmentFrameworkAndCode(framework, s.getCode())) continue;
            AssessmentComponent c = new AssessmentComponent();
            c.setAssessmentFramework(framework);
            c.setCode(s.getCode());
            c.setName(s.getName());
            c.setWeight(s.getWeight());
            c.setSequence(s.getSequence());
            c.setActive(true);
            componentRepository.save(c);
        }
    }

    private void seedAssessmentTypes(CurriculumSeedData.FrameworksFile file) {
        if (file == null || file.getAssessmentTypes() == null) return;
        for (var s : file.getAssessmentTypes()) {
            if (assessmentTypeRepository.existsByCode(s.getCode())) continue;
            AssessmentType t = new AssessmentType();
            t.setCode(s.getCode());
            t.setName(s.getName());
            t.setDefaultMethod(AssessmentMethod.valueOf(s.getDefaultMethod()));
            t.setActive(true);
            assessmentTypeRepository.save(t);
        }
    }

    /**
     * Bridges every active {@link LearningArea} of the version to an {@link AcademicSubject} so the
     * operational pipeline (StudentsScore, CbcGradeResult, ClassSubjectAssignment,
     * TeacherSubjectAssignment) has concrete subject rows to reference, and sets
     * {@link LearningArea#getAcademicSubjectId()} to the linked subject id.
     * <p>
     * Idempotent: reuses an existing AcademicSubject matched by name (or an already-set bridge id)
     * instead of creating duplicates.
     */
    private void bridgeLearningAreasToSubjects(CurriculumVersion version) {
        List<LearningArea> areas = learningAreaRepository.findByCurriculumVersionOrderBySequenceAsc(version);
        for (LearningArea la : areas) {
            if (la.getStatus() != CurriculumStatus.ACTIVE) {
                continue;
            }

            // If already bridged and the subject still exists, leave it alone.
            if (la.getAcademicSubjectId() != null
                    && academicSubjectRepository.existsById(la.getAcademicSubjectId())) {
                continue;
            }

            String name = la.getOfficialName();
            com.EduePoa.EP.academics.entity.AcademicSubject subject = academicSubjectRepository
                    .findBySubjectName(name)
                    .orElseGet(() -> {
                        var s = new com.EduePoa.EP.academics.entity.AcademicSubject();
                        s.setSubjectName(name);
                        s.setSubjectCode(la.getCode() != null ? la.getCode() : la.getCatalogueKey());
                        s.setLearningArea(la.getCatalogueKey());
                        s.setIsCbcCore(Boolean.TRUE.equals(la.getIsCore()));
                        return academicSubjectRepository.save(s);
                    });

            if (la.getAcademicSubjectId() == null
                    || !la.getAcademicSubjectId().equals(subject.getId())) {
                la.setAcademicSubjectId(subject.getId());
                learningAreaRepository.save(la);
            }
        }
    }
}
