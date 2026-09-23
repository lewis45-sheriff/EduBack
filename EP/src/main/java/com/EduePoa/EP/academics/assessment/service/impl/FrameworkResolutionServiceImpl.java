package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.repository.AssessmentFrameworkRepository;
import com.EduePoa.EP.academics.assessment.service.FrameworkResolutionService;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.EducationLevel;
import com.EduePoa.EP.academics.curriculum.entity.GradeLevelMapping;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.EduePoa.EP.academics.curriculum.repository.CurriculumVersionRepository;
import com.EduePoa.EP.academics.curriculum.repository.GradeLevelMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FrameworkResolutionServiceImpl implements FrameworkResolutionService {

    private final CurriculumVersionRepository curriculumVersionRepository;
    private final GradeLevelMappingRepository gradeLevelMappingRepository;
    private final AssessmentFrameworkRepository assessmentFrameworkRepository;

    @Override
    public Optional<AssessmentFramework> resolveForGrade(Grade grade) {
        if (grade == null) {
            return Optional.empty();
        }
        Optional<CurriculumVersion> activeVersion = findActiveVersion();
        if (activeVersion.isEmpty()) {
            return Optional.empty();
        }
        CurriculumVersion version = activeVersion.get();

        List<AssessmentFramework> frameworks = assessmentFrameworkRepository.findByCurriculumVersion(version)
                .stream()
                .filter(f -> f.getStatus() == CurriculumStatus.ACTIVE)
                .toList();
        if (frameworks.isEmpty()) {
            return Optional.empty();
        }

        Optional<GradeLevelMapping> mapping =
                gradeLevelMappingRepository.findByCurriculumVersionAndGradeId(version, grade.getId());

        // 1) Exact grade-code match.
        if (mapping.isPresent()) {
            String gradeCode = mapping.get().getGradeCode();
            Optional<AssessmentFramework> byGrade = frameworks.stream()
                    .filter(f -> gradeCode.equalsIgnoreCase(f.getGradeCode()))
                    .findFirst();
            if (byGrade.isPresent()) {
                return byGrade;
            }
            // 2) Education-level match.
            EducationLevel level = mapping.get().getEducationLevel();
            if (level != null) {
                Optional<AssessmentFramework> byLevel = frameworks.stream()
                        .filter(f -> f.getEducationLevel() == level.getBand())
                        .findFirst();
                if (byLevel.isPresent()) {
                    return byLevel;
                }
            }
        }

        // 3) Any framework that is neither grade- nor level-specific (a general default).
        Optional<AssessmentFramework> general = frameworks.stream()
                .filter(f -> f.getGradeCode() == null && f.getEducationLevel() == null)
                .findFirst();
        return general.isPresent() ? general : Optional.of(frameworks.get(0));
    }

    private Optional<CurriculumVersion> findActiveVersion() {
        List<CurriculumVersion> active = curriculumVersionRepository.findByStatus(CurriculumStatus.ACTIVE);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }
}
