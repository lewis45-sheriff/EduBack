package com.EduePoa.EP.Budget.Variance;

/**
 * Whether a budget line's actuals can be reliably attributed from the General Ledger.
 * <p>
 * This is a data-quality flag, surfaced verbatim in every variance/dashboard/report
 * response so the UI can distinguish a genuine zero from "we cannot attribute this".
 *
 * <ul>
 *   <li>{@link #MAPPED} — the category maps to a valid, tenant-owned GL account of a
 *       compatible type; account-level actuals are reliable.</li>
 *   <li>{@link #UNMAPPED} — the category has no {@code mappingKey}; actuals are reported
 *       as zero and the line is flagged (not presented as "nothing was spent").</li>
 *   <li>{@link #INVALID_MAPPING} — a {@code mappingKey} is set but does not resolve to a
 *       valid account for this tenant, or resolves to an incompatible account type;
 *       actuals are reported as zero and flagged for administrator attention.</li>
 * </ul>
 */
public enum MappingStatus {
    MAPPED,
    UNMAPPED,
    INVALID_MAPPING
}
