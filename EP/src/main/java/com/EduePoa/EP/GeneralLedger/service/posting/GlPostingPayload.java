package com.EduePoa.EP.GeneralLedger.service.posting;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Compact, non-sensitive snapshot of a finalized money event, stored on the
 * outbox row and used to build the journal at processing time. It carries only
 * the amounts, the accounting date and a reference — never raw payment-gateway
 * callback bodies or personal data.
 * <p>
 * {@code amounts} is a map keyed by a rule-specific label (e.g. {@code "amount"},
 * or payroll totals like {@code "totalGross"}); the posting rule interprets it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GlPostingPayload(
        LocalDate accountingDate,
        String referenceNumber,
        String description,
        Map<String, BigDecimal> amounts
) {
    public BigDecimal amount(String key) {
        BigDecimal v = amounts != null ? amounts.get(key) : null;
        return v != null ? v : BigDecimal.ZERO;
    }
}
