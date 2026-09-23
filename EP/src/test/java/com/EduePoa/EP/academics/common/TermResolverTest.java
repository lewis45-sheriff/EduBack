package com.EduePoa.EP.academics.common;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermResolverTest {

    private final TermResolver resolver = new TermResolver();

    @Test
    @DisplayName("resolveById maps 1/2/3 to the term enum and rejects others")
    void resolveById() {
        assertThat(resolver.resolveById(1L)).contains(Term.TERM_1);
        assertThat(resolver.resolveById(2L)).contains(Term.TERM_2);
        assertThat(resolver.resolveById(3L)).contains(Term.TERM_3);
        assertThat(resolver.resolveById(4L)).isEmpty();
        assertThat(resolver.resolveById(null)).isEmpty();
    }

    @Test
    @DisplayName("sequenceOf / idOf are consistent inverses of resolveById")
    void sequenceAndId() {
        assertThat(resolver.sequenceOf(Term.TERM_1)).isEqualTo(1);
        assertThat(resolver.sequenceOf(Term.TERM_3)).isEqualTo(3);
        assertThat(resolver.idOf(Term.TERM_2)).isEqualTo(2L);
        assertThat(resolver.resolveById(resolver.idOf(Term.TERM_3))).contains(Term.TERM_3);
    }
}
