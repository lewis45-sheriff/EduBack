package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.repository.PerformanceLevelRepository;
import com.EduePoa.EP.academics.assessment.service.PerformanceLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceLevelServiceImpl implements PerformanceLevelService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PerformanceLevelRepository performanceLevelRepository;

    @Override
    public Optional<PerformanceLevel> resolve(AssessmentFramework framework, BigDecimal percentage) {
        if (framework == null || percentage == null) {
            return Optional.empty();
        }
        List<PerformanceLevel> levels = performanceLevelRepository
                .findByAssessmentFrameworkOrderBySequenceAsc(framework);
        return levels.stream().filter(l -> l.matches(percentage)).findFirst();
    }

    @Override
    public void validateBands(List<PerformanceLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("A framework must define at least one performance level");
        }
        // Per-band sanity: min <= max and both within [0,100].
        for (PerformanceLevel l : levels) {
            if (l.getMinScore() == null || l.getMaxScore() == null) {
                throw new IllegalArgumentException("Performance level " + l.getCode() + " must set minScore and maxScore");
            }
            if (l.getMinScore().compareTo(l.getMaxScore()) > 0) {
                throw new IllegalArgumentException("Performance level " + l.getCode()
                        + " has minScore > maxScore (" + l.getMinScore() + " > " + l.getMaxScore() + ")");
            }
            if (l.getMinScore().compareTo(ZERO) < 0 || l.getMaxScore().compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("Performance level " + l.getCode()
                        + " bounds must be within [0,100]");
            }
        }
        // Overlap check: sort by minScore, ensure each band starts strictly after the previous ends.
        List<PerformanceLevel> sorted = levels.stream()
                .sorted(Comparator.comparing(PerformanceLevel::getMinScore))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            PerformanceLevel prev = sorted.get(i - 1);
            PerformanceLevel curr = sorted.get(i);
            if (curr.getMinScore().compareTo(prev.getMaxScore()) <= 0) {
                throw new IllegalArgumentException("Overlapping performance bands: "
                        + prev.getCode() + " [" + prev.getMinScore() + "," + prev.getMaxScore() + "] and "
                        + curr.getCode() + " [" + curr.getMinScore() + "," + curr.getMaxScore() + "]");
            }
        }
    }
}
