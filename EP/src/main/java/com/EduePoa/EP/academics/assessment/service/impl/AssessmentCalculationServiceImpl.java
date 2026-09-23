package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.academics.assessment.service.AssessmentCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Slf4j
public class AssessmentCalculationServiceImpl implements AssessmentCalculationService {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public BigDecimal normalize(BigDecimal rawScore, BigDecimal maximumScore) {
        if (rawScore == null || maximumScore == null) {
            return null;
        }
        if (maximumScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maximumScore must be greater than zero");
        }
        if (rawScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("rawScore cannot be negative");
        }
        if (rawScore.compareTo(maximumScore) > 0) {
            throw new IllegalArgumentException("rawScore (" + rawScore + ") cannot exceed maximumScore (" + maximumScore + ")");
        }
        return rawScore.multiply(HUNDRED).divide(maximumScore, SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal weightedAverage(List<ComponentScore> components) {
        if (components == null || components.isEmpty()) {
            return null;
        }
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (ComponentScore c : components) {
            if (c.percentage() == null || c.weight() == null) {
                continue;
            }
            weightedSum = weightedSum.add(c.percentage().multiply(c.weight()));
            weightTotal = weightTotal.add(c.weight());
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return weightedSum.divide(weightTotal, SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal average(List<BigDecimal> percentages) {
        if (percentages == null || percentages.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal p : percentages) {
            if (p != null) {
                sum = sum.add(p);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
    }
}
