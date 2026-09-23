package com.EduePoa.EP.academics.assessment;

import com.EduePoa.EP.academics.assessment.service.AssessmentCalculationService.ComponentScore;
import com.EduePoa.EP.academics.assessment.service.impl.AssessmentCalculationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for numeric normalization/aggregation. No Spring context, no DB. */
class AssessmentCalculationServiceImplTest {

    private final AssessmentCalculationServiceImpl service = new AssessmentCalculationServiceImpl();

    @Test
    @DisplayName("normalize: 42 out of 50 = 84.00%")
    void normalize_rawOverMax() {
        BigDecimal pct = service.normalize(new BigDecimal("42"), new BigDecimal("50"));
        assertThat(pct).isEqualByComparingTo("84.00");
    }

    @Test
    @DisplayName("normalize: returns null when inputs missing")
    void normalize_nullInputs() {
        assertThat(service.normalize(null, new BigDecimal("50"))).isNull();
        assertThat(service.normalize(new BigDecimal("10"), null)).isNull();
    }

    @Test
    @DisplayName("normalize: rejects max <= 0")
    void normalize_zeroMax() {
        assertThatThrownBy(() -> service.normalize(new BigDecimal("10"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("normalize: rejects raw > max")
    void normalize_rawExceedsMax() {
        assertThatThrownBy(() -> service.normalize(new BigDecimal("60"), new BigDecimal("50")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("weightedAverage: CAT 60% weight 30, End-Term 80% weight 70 => 74.00")
    void weightedAverage_catEndTerm() {
        BigDecimal result = service.weightedAverage(List.of(
                new ComponentScore(new BigDecimal("60"), new BigDecimal("30")),
                new ComponentScore(new BigDecimal("80"), new BigDecimal("70"))
        ));
        // (60*30 + 80*70) / (30+70) = (1800 + 5600) / 100 = 74.00
        assertThat(result).isEqualByComparingTo("74.00");
    }

    @Test
    @DisplayName("average: simple mean of percentages")
    void average_mean() {
        BigDecimal result = service.average(List.of(
                new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("100")));
        assertThat(result).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("average: empty list returns null")
    void average_empty() {
        assertThat(service.average(List.of())).isNull();
    }
}
