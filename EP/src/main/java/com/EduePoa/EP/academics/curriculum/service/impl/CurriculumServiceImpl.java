package com.EduePoa.EP.academics.curriculum.service.impl;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.curriculum.dto.CurriculumDtos.*;
import com.EduePoa.EP.academics.curriculum.entity.*;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.EduePoa.EP.academics.curriculum.repository.*;
import com.EduePoa.EP.academics.curriculum.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurriculumServiceImpl implements CurriculumService {

    private final CurriculumVersionRepository versionRepository;
    private final GradeLevelMappingRepository gradeLevelMappingRepository;
    private final LearningAreaRepository learningAreaRepository;
    private final StrandRepository strandRepository;
    private final SubStrandRepository subStrandRepository;
    private final SpecificLearningOutcomeRepository learningOutcomeRepository;
    private final CoreCompetencyRepository competencyRepository;
    private final CbcValueRepository valueRepository;
    private final PertinentContemporaryIssueRepository pciRepository;

    // --- interface ---

    @Override
    public CustomResponse<List<CurriculumVersionDto>> getVersions() {
        return ok(() -> versionRepository.findAll().stream().map(this::toVersionDto).toList(),
                "Curriculum versions retrieved");
    }

    @Override
    public CustomResponse<List<GradeDto>> getGrades() {
        return withActiveVersion(v -> gradeLevelMappingRepository
                .findByCurriculumVersionOrderBySequenceAsc(v).stream().map(this::toGradeDto).toList(),
                "Grades retrieved");
    }

    @Override
    public CustomResponse<List<LearningAreaDto>> getAllLearningAreas() {
        return withActiveVersion(v -> learningAreaRepository
                .findByCurriculumVersionOrderBySequenceAsc(v).stream()
                .map(this::toLearningAreaDto).toList(), "Learning areas retrieved");
    }

    @Override
    public CustomResponse<List<LearningAreaDto>> getLearningAreasForGrade(Long gradeMappingId) {
        return ok(() -> {
            GradeLevelMapping mapping = gradeLevelMappingRepository.findById(gradeMappingId).orElse(null);
            if (mapping == null || mapping.getEducationLevel() == null) {
                return List.<LearningAreaDto>of();
            }
            return learningAreaRepository
                    .findByEducationLevelOrderBySequenceAsc(mapping.getEducationLevel())
                    .stream().map(this::toLearningAreaDto).toList();
        }, "Learning areas retrieved");
    }

    @Override
    public CustomResponse<LearningAreaDto> getLearningArea(Long learningAreaId) {
        CustomResponse<LearningAreaDto> r = new CustomResponse<>();
        Optional<LearningArea> la = learningAreaRepository.findById(learningAreaId);
        if (la.isEmpty()) {
            r.setStatusCode(HttpStatus.NOT_FOUND.value());
            r.setMessage("Learning area not found");
            return r;
        }
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage("Learning area retrieved");
        r.setEntity(toLearningAreaDto(la.get()));
        return r;
    }

    @Override
    public CustomResponse<List<StrandDto>> getStrands(Long learningAreaId) {
        return ok(() -> {
            LearningArea la = learningAreaRepository.findById(learningAreaId).orElse(null);
            if (la == null) return List.<StrandDto>of();
            return strandRepository.findByLearningAreaOrderBySequenceAsc(la).stream().map(this::toStrandDto).toList();
        }, "Strands retrieved");
    }

    @Override
    public CustomResponse<List<SubStrandDto>> getSubStrands(Long strandId) {
        return ok(() -> {
            Strand s = strandRepository.findById(strandId).orElse(null);
            if (s == null) return List.<SubStrandDto>of();
            return subStrandRepository.findByStrandOrderBySequenceAsc(s).stream().map(this::toSubStrandDto).toList();
        }, "Sub-strands retrieved");
    }

    @Override
    public CustomResponse<List<LearningOutcomeDto>> getLearningOutcomes(Long subStrandId) {
        return ok(() -> {
            SubStrand ss = subStrandRepository.findById(subStrandId).orElse(null);
            if (ss == null) return List.<LearningOutcomeDto>of();
            return learningOutcomeRepository.findBySubStrandOrderBySequenceAsc(ss).stream()
                    .map(this::toOutcomeDto).toList();
        }, "Learning outcomes retrieved");
    }

    @Override
    public CustomResponse<List<CompetencyDto>> getCompetencies() {
        return withActiveVersion(v -> competencyRepository.findByCurriculumVersionOrderBySequenceAsc(v)
                .stream().map(this::toCompetencyDto).toList(), "Core competencies retrieved");
    }

    @Override
    public CustomResponse<List<ValueDto>> getValues() {
        return withActiveVersion(v -> valueRepository.findByCurriculumVersionOrderBySequenceAsc(v)
                .stream().map(this::toValueDto).toList(), "Values retrieved");
    }

    @Override
    public CustomResponse<List<PciDto>> getPcis() {
        return withActiveVersion(v -> pciRepository.findByCurriculumVersionOrderBySequenceAsc(v)
                .stream().map(this::toPciDto).toList(), "PCIs retrieved");
    }

    // --- helpers ---

    private Optional<CurriculumVersion> activeVersion() {
        List<CurriculumVersion> active = versionRepository.findByStatus(CurriculumStatus.ACTIVE);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    private <T> CustomResponse<List<T>> ok(Supplier<List<T>> supplier, String message) {
        CustomResponse<List<T>> r = new CustomResponse<>();
        try {
            r.setStatusCode(HttpStatus.OK.value());
            r.setMessage(message);
            r.setEntity(supplier.get());
        } catch (Exception e) {
            log.error("Curriculum read failed: {}", e.getMessage(), e);
            r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            r.setMessage("Error: " + e.getMessage());
        }
        return r;
    }

    private <T> CustomResponse<List<T>> withActiveVersion(java.util.function.Function<CurriculumVersion, List<T>> fn,
                                                          String message) {
        CustomResponse<List<T>> r = new CustomResponse<>();
        Optional<CurriculumVersion> v = activeVersion();
        if (v.isEmpty()) {
            r.setStatusCode(HttpStatus.NOT_FOUND.value());
            r.setMessage("No active curriculum version configured");
            r.setEntity(List.of());
            return r;
        }
        return ok(() -> fn.apply(v.get()), message);
    }

    private CurriculumVersionDto toVersionDto(CurriculumVersion v) {
        CurriculumVersionDto d = new CurriculumVersionDto();
        d.setId(v.getId());
        d.setCode(v.getCode());
        d.setName(v.getName());
        d.setVersion(v.getVersion());
        d.setStatus(v.getStatus() == null ? null : v.getStatus().name());
        d.setSource(v.getSource() == null ? null : v.getSource().name());
        d.setDescription(v.getDescription());
        return d;
    }

    private GradeDto toGradeDto(GradeLevelMapping m) {
        GradeDto d = new GradeDto();
        d.setId(m.getId());
        d.setGradeId(m.getGrade() != null ? m.getGrade().getId() : null);
        d.setGradeCode(m.getGradeCode());
        d.setGradeName(m.getGradeName());
        d.setEducationLevel(m.getEducationLevel() != null ? m.getEducationLevel().getCode() : null);
        d.setSequence(m.getSequence());
        return d;
    }

    private LearningAreaDto toLearningAreaDto(LearningArea la) {
        LearningAreaDto d = new LearningAreaDto();
        d.setId(la.getId());
        d.setCatalogueKey(la.getCatalogueKey());
        d.setOfficialName(la.getOfficialName());
        d.setDisplayName(la.getDisplayName());
        d.setCode(la.getCode());
        d.setLearningAreaType(la.getLearningAreaType() == null ? null : la.getLearningAreaType().name());
        d.setIsCore(la.getIsCore());
        d.setIsOptional(la.getIsOptional());
        d.setStatus(la.getStatus() == null ? null : la.getStatus().name());
        d.setSequence(la.getSequence());
        d.setAcademicSubjectId(la.getAcademicSubjectId());
        return d;
    }

    private StrandDto toStrandDto(Strand s) {
        StrandDto d = new StrandDto();
        d.setId(s.getId());
        d.setCode(s.getCode());
        d.setName(s.getName());
        d.setDescription(s.getDescription());
        d.setSequence(s.getSequence());
        return d;
    }

    private SubStrandDto toSubStrandDto(SubStrand s) {
        SubStrandDto d = new SubStrandDto();
        d.setId(s.getId());
        d.setCode(s.getCode());
        d.setName(s.getName());
        d.setDescription(s.getDescription());
        d.setSuggestedLessons(s.getSuggestedLessons());
        d.setSequence(s.getSequence());
        return d;
    }

    private LearningOutcomeDto toOutcomeDto(SpecificLearningOutcome o) {
        LearningOutcomeDto d = new LearningOutcomeDto();
        d.setId(o.getId());
        d.setCode(o.getCode());
        d.setDescription(o.getDescription());
        d.setKnowledge(o.getKnowledge());
        d.setSkills(o.getSkills());
        d.setAttitudes(o.getAttitudes());
        d.setValues(o.getValues());
        d.setSequence(o.getSequence());
        return d;
    }

    private CompetencyDto toCompetencyDto(CoreCompetency c) {
        CompetencyDto d = new CompetencyDto();
        d.setId(c.getId());
        d.setCode(c.getCode());
        d.setName(c.getName());
        d.setDescription(c.getDescription());
        d.setSequence(c.getSequence());
        return d;
    }

    private ValueDto toValueDto(CbcValue v) {
        ValueDto d = new ValueDto();
        d.setId(v.getId());
        d.setCode(v.getCode());
        d.setName(v.getName());
        d.setDescription(v.getDescription());
        d.setSequence(v.getSequence());
        return d;
    }

    private PciDto toPciDto(PertinentContemporaryIssue p) {
        PciDto d = new PciDto();
        d.setId(p.getId());
        d.setCode(p.getCode());
        d.setName(p.getName());
        d.setCategory(p.getCategory());
        d.setDescription(p.getDescription());
        d.setSequence(p.getSequence());
        return d;
    }
}
