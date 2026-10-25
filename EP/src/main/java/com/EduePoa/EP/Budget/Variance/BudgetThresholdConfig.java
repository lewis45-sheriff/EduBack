package com.EduePoa.EP.Budget.Variance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * System-wide default budget performance thresholds, overridable via
 * {@code budget.threshold.*} in application properties. These are the tenant-default
 * values; Phase 4 may layer per-tenant or per-budget overrides on top of these.
 *
 * <p>Boundary semantics for EXPENSE utilization (actual/approved*100):
 * <ul>
 *   <li>utilization &lt; {@code approachingPercent} → WITHIN (UNDER_BUDGET)</li>
 *   <li>{@code approachingPercent} &le; utilization &le; {@code overPercent} → APPROACHING (AT_BUDGET)</li>
 *   <li>utilization &gt; {@code overPercent} → OVER (OVER_BUDGET)</li>
 * </ul>
 * With the defaults (80 / 100): exactly 100% is APPROACHING (fully utilized, not yet over);
 * strictly above 100% is OVER. A non-zero actual against a zero approved amount is always OVER
 * (unbudgeted) regardless of thresholds.
 */
@Configuration
@ConfigurationProperties(prefix = "budget.threshold")
@Getter
@Setter
public class BudgetThresholdConfig {

    /** Lower bound of the APPROACHING band, as a percentage (default 80). */
    private BigDecimal approachingPercent = new BigDecimal("80");

    /** Upper bound of the APPROACHING band, as a percentage (default 100). Above this is OVER. */
    private BigDecimal overPercent = new BigDecimal("100");
}
