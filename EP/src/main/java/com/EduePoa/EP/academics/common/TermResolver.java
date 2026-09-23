package com.EduePoa.EP.academics.common;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single source of truth for mapping a numeric term id (1/2/3) to the {@link Term} enum.
 * <p>
 * This centralizes the {@code resolveTermById} switch that was previously duplicated across
 * ExamService, CbcCalculatorServiceImpl and UploadMarksServiceImpl. It also provides the ordinal
 * sequence for a term. When the project later migrates to a persisted AcademicTerm model, only
 * this component needs to change — callers already depend on the abstraction rather than a local
 * switch statement.
 */
@Component
public class TermResolver {

    /** Map 1&rarr;TERM_1, 2&rarr;TERM_2, 3&rarr;TERM_3; empty for anything else. */
    public Optional<Term> resolveById(Long termId) {
        if (termId == null) {
            return Optional.empty();
        }
        return switch (termId.intValue()) {
            case 1 -> Optional.of(Term.TERM_1);
            case 2 -> Optional.of(Term.TERM_2);
            case 3 -> Optional.of(Term.TERM_3);
            default -> Optional.empty();
        };
    }

    /** 1-based sequence for a term (TERM_1 &rarr; 1). */
    public int sequenceOf(Term term) {
        return term == null ? 0 : term.ordinal() + 1;
    }

    /** Numeric id for a term (inverse of {@link #resolveById}). */
    public Long idOf(Term term) {
        return term == null ? null : (long) (term.ordinal() + 1);
    }
}
