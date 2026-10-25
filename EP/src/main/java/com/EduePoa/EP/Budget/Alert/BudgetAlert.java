package com.EduePoa.EP.Budget.Alert;

import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A persisted, deduplicated budget performance alert (design §14 Phase 4). An alert is raised when
 * a budget line crosses into an APPROACHING (AT_BUDGET) or OVER (OVER_BUDGET) state. Deduplication
 * key is {@code (tenant, budget, line, alertStatus)} so repeatedly evaluating the dashboard never
 * creates duplicate alerts for the same unchanged condition; a changed utilization updates the
 * existing row's amounts and {@code lastEvaluatedAt} instead of inserting a new one.
 *
 * <p>Alerts are a read model surfaced in the dashboard/reports, not an SMS/email delivery channel.
 * There is no general in-app notification store in the platform, so budget alerts are self-contained
 * and auditable here rather than fabricating integration with an unrelated messaging subsystem.
 */
@Entity
@Table(name = "budget_alerts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_budget_alert_dedup",
                        columnNames = {"tenant_id", "budget_id", "budget_line_id", "alert_status"})
        },
        indexes = {
                @Index(name = "idx_budget_alert_budget", columnList = "tenant_id, budget_id"),
                @Index(name = "idx_budget_alert_active", columnList = "tenant_id, active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class BudgetAlert extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "budget_line_id", nullable = false)
    private Long budgetLineId;

    @Column(name = "category_name", length = 150)
    private String categoryName;

    @Column(name = "org_unit_name", length = 200)
    private String orgUnitName;

    /** The performance state that triggered the alert (AT_BUDGET or OVER_BUDGET). */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 20)
    private PerformanceStatus alertStatus;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "utilization_percent", precision = 9, scale = 2)
    private BigDecimal utilizationPercent;

    /** False once the condition clears (utilization no longer in the alert band). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "first_raised_at", nullable = false)
    private LocalDateTime firstRaisedAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private LocalDateTime lastEvaluatedAt;

    @Version
    private Long version;
}
