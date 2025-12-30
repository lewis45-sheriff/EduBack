package com.EduePoa.EP.Authentication.Enum;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Month;

@Getter
public enum Term {
    TERM_1(
            LocalDate.of(2025, Month.JANUARY, 1),
            LocalDate.of(2025, Month.APRIL, 27)
    ),
    TERM_2(
            LocalDate.of(2025, Month.APRIL, 28),
            LocalDate.of(2025, Month.AUGUST, 31)
    ),
    TERM_3(
            LocalDate.of(2025, Month.SEPTEMBER, 1),
            LocalDate.of(2025, Month.DECEMBER, 31)
    );

    private final LocalDate startDate;
    private final LocalDate endDate;

    Term(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }


    public boolean isDateInTerm(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }


    public static Term getCurrentTerm() {
        LocalDate today = LocalDate.now();
        for (Term term : Term.values()) {
            if (term.isDateInTerm(today)) {
                return term;
            }
        }
        return null; // No active term
    }

    public static Term getTermByDate(LocalDate date) {
        for (Term term : Term.values()) {
            if (term.isDateInTerm(date)) {
                return term;
            }
        }
        return null;
    }
}