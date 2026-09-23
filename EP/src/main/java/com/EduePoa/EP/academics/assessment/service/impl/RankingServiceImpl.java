package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.assessment.entity.RankingPolicy;
import com.EduePoa.EP.academics.assessment.entity.StudentTermSummary;
import com.EduePoa.EP.academics.assessment.enums.RankingScope;
import com.EduePoa.EP.academics.assessment.repository.RankingPolicyRepository;
import com.EduePoa.EP.academics.assessment.repository.StudentTermSummaryRepository;
import com.EduePoa.EP.academics.assessment.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingServiceImpl implements RankingService {

    private final RankingPolicyRepository rankingPolicyRepository;
    private final StudentTermSummaryRepository summaryRepository;

    @Override
    public boolean isRankingEnabled(Student student, Term term, Year year) {
        return resolvePolicy(student, term, year).isPresent();
    }

    @Override
    public String resolveScopeLabel(Student student, Term term, Year year) {
        return resolvePolicy(student, term, year)
                .map(p -> p.getScope() == null ? null : p.getScope().name())
                .orElse(null);
    }

    @Override
    public Optional<Integer> resolvePosition(Student student, Term term, Year year) {
        Optional<RankingPolicy> policyOpt = resolvePolicy(student, term, year);
        if (policyOpt.isEmpty()) {
            return Optional.empty();
        }
        RankingPolicy policy = policyOpt.get();

        // Choose the cohort based on scope.
        List<StudentTermSummary> cohort;
        if (policy.getScope() == RankingScope.WITHIN_STREAM && student.getGradeStream() != null) {
            cohort = summaryRepository.findByGradeStreamIdAndTermAndYear(
                    student.getGradeStream().getId(), term, year);
        } else if (student.getGrade() != null) {
            cohort = summaryRepository.findByGradeIdAndTermAndYear(student.getGrade().getId(), term, year);
        } else {
            return Optional.empty();
        }

        // Only rank learners that actually have an aggregate percentage.
        List<StudentTermSummary> ranked = cohort.stream()
                .filter(s -> s.getOverallPercentage() != null)
                .sorted(Comparator.comparing(StudentTermSummary::getOverallPercentage).reversed())
                .toList();
        if (ranked.isEmpty()) {
            return Optional.empty();
        }

        // Standard competition ranking (ties share a position).
        int position = 0;
        int seen = 0;
        BigDecimal previous = null;
        for (StudentTermSummary s : ranked) {
            seen++;
            if (previous == null || s.getOverallPercentage().compareTo(previous) != 0) {
                position = seen;
                previous = s.getOverallPercentage();
            }
            if (s.getStudent() != null && s.getStudent().getId().equals(student.getId())) {
                return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    private Optional<RankingPolicy> resolvePolicy(Student student, Term term, Year year) {
        List<RankingPolicy> enabled = rankingPolicyRepository.findByEnabledTrue();
        if (enabled.isEmpty() || student == null) {
            return Optional.empty();
        }
        Long gradeId = student.getGrade() != null ? student.getGrade().getId() : null;
        Long streamId = student.getGradeStream() != null ? student.getGradeStream().getId() : null;

        // Prefer the most specific matching policy: stream, then grade, then tenant-wide.
        return enabled.stream()
                .filter(p -> p.getScope() != RankingScope.NONE)
                .filter(p -> matchesScope(p, gradeId, streamId, term, year))
                .max(Comparator.comparingInt(this::specificity));
    }

    private boolean matchesScope(RankingPolicy p, Long gradeId, Long streamId, Term term, Year year) {
        if (p.getGradeStreamId() != null && !p.getGradeStreamId().equals(streamId)) return false;
        if (p.getGradeId() != null && !p.getGradeId().equals(gradeId)) return false;
        if (p.getTerm() != null && p.getTerm() != term) return false;
        if (p.getYear() != null && !p.getYear().equals(year)) return false;
        return true;
    }

    private int specificity(RankingPolicy p) {
        int s = 0;
        if (p.getGradeStreamId() != null) s += 4;
        if (p.getGradeId() != null) s += 2;
        if (p.getTerm() != null) s += 1;
        return s;
    }
}
