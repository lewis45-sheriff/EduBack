package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Teacher-portal request to save (upsert) marks for an assignment's learners against a specific
 * term, academic year and exam type. The teacher identity is NEVER taken from this body — it is
 * resolved from the authenticated security context on the server.
 * <p>
 * A {@code null} rawScore means "not entered" and is skipped (never written as 0). The assignment
 * is identified by the path variable, not by this body.
 */
@Data
public class SaveScoresRequest {

    /** Term id (1, 2 or 3). Required. Must be consistent with the assignment's term when scoped. */
    private Long termId;

    /** Academic year. When null the server defaults to the current year. */
    private Integer year;

    /** Exam type (e.g. Opener/Midterm/Endterm). Required; drives the unique score combination. */
    private Long examTypeId;

    private List<ScoreEntry> scores = new ArrayList<>();

    @Data
    public static class ScoreEntry {
        private Long studentId;
        /** Raw mark; null = not entered (left blank, never persisted as 0). */
        private BigDecimal rawScore;
    }
}
