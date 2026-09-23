package com.EduePoa.EP.academics.assessment;

import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.enums.BroadPerformanceLevel;
import com.EduePoa.EP.academics.assessment.repository.PerformanceLevelRepository;
import com.EduePoa.EP.academics.assessment.service.impl.PerformanceLevelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Tests the configurable performance-level resolution and band validation. The KJSEA bands are
 * treated as data (built here as in-memory rows) — proving the engine reads configuration, not
 * hard-coded thresholds.
 */
@ExtendWith(MockitoExtension.class)
class PerformanceLevelServiceImplTest {

    @Mock
    private PerformanceLevelRepository performanceLevelRepository;

    @InjectMocks
    private PerformanceLevelServiceImpl service;

    private AssessmentFramework kjsea;
    private List<PerformanceLevel> kjseaLevels;

    @BeforeEach
    void setUp() {
        kjsea = new AssessmentFramework();
        kjsea.setId(1L);
        kjsea.setCode("KJSEA");
        kjseaLevels = buildKjseaLevels();
        lenient().when(performanceLevelRepository.findByAssessmentFrameworkOrderBySequenceAsc(kjsea))
                .thenReturn(kjseaLevels);
    }

    @Test
    @DisplayName("KJSEA: representative scores map to EE1..BE2 with correct points")
    void kjsea_bandResolution() {
        assertLevel(95, "EE1", 8);
        assertLevel(80, "EE2", 7);
        assertLevel(65, "ME1", 6);
        assertLevel(50, "ME2", 5);
        assertLevel(35, "AE1", 4);
        assertLevel(25, "AE2", 3);
        assertLevel(15, "BE1", 2);
        assertLevel(5,  "BE2", 1);
    }

    @Test
    @DisplayName("KJSEA: boundary values are inclusive (90 -> EE1, 89 -> EE2)")
    void kjsea_boundaries() {
        assertLevel(90, "EE1", 8);
        assertLevel(89, "EE2", 7);
        assertLevel(0,  "BE2", 1);
        assertLevel(100, "EE1", 8);
    }

    @Test
    @DisplayName("validateBands: accepts a well-formed KJSEA configuration")
    void validateBands_valid() {
        service.validateBands(kjseaLevels);
    }

    @Test
    @DisplayName("validateBands: rejects overlapping bands")
    void validateBands_overlap() {
        List<PerformanceLevel> bad = new ArrayList<>();
        bad.add(level("A", 75, 100, 4));
        bad.add(level("B", 70, 80, 3)); // overlaps A
        assertThatThrownBy(() -> service.validateBands(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
    }

    @Test
    @DisplayName("validateBands: rejects min > max")
    void validateBands_minGtMax() {
        List<PerformanceLevel> bad = List.of(level("A", 80, 50, 4));
        assertThatThrownBy(() -> service.validateBands(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateBands: rejects out-of-range bounds")
    void validateBands_outOfRange() {
        List<PerformanceLevel> bad = List.of(level("A", 0, 120, 4));
        assertThatThrownBy(() -> service.validateBands(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertLevel(int score, String expectedCode, int expectedPoints) {
        var resolved = service.resolve(kjsea, BigDecimal.valueOf(score));
        assertThat(resolved).as("score %d", score).isPresent();
        assertThat(resolved.get().getCode()).isEqualTo(expectedCode);
        assertThat(resolved.get().getPoints()).isEqualTo(expectedPoints);
    }

    private List<PerformanceLevel> buildKjseaLevels() {
        List<PerformanceLevel> list = new ArrayList<>();
        list.add(level("EE1", 90, 100, 8, BroadPerformanceLevel.EXCEEDING_EXPECTATION));
        list.add(level("EE2", 75, 89, 7, BroadPerformanceLevel.EXCEEDING_EXPECTATION));
        list.add(level("ME1", 58, 74, 6, BroadPerformanceLevel.MEETING_EXPECTATION));
        list.add(level("ME2", 41, 57, 5, BroadPerformanceLevel.MEETING_EXPECTATION));
        list.add(level("AE1", 31, 40, 4, BroadPerformanceLevel.APPROACHING_EXPECTATION));
        list.add(level("AE2", 21, 30, 3, BroadPerformanceLevel.APPROACHING_EXPECTATION));
        list.add(level("BE1", 11, 20, 2, BroadPerformanceLevel.BELOW_EXPECTATION));
        list.add(level("BE2", 0, 10, 1, BroadPerformanceLevel.BELOW_EXPECTATION));
        return list;
    }

    private PerformanceLevel level(String code, int min, int max, int points) {
        return level(code, min, max, points, BroadPerformanceLevel.MEETING_EXPECTATION);
    }

    private PerformanceLevel level(String code, int min, int max, int points, BroadPerformanceLevel broad) {
        PerformanceLevel pl = new PerformanceLevel();
        pl.setCode(code);
        pl.setBroadLevel(broad);
        pl.setLabel(code);
        pl.setAbbreviation(code);
        pl.setMinScore(BigDecimal.valueOf(min));
        pl.setMaxScore(BigDecimal.valueOf(max));
        pl.setPoints(points);
        return pl;
    }
}
