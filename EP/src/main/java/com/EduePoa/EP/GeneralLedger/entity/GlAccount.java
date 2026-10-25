package com.EduePoa.EP.GeneralLedger.entity;

import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * A chart-of-accounts entry for a single tenant.
 * <p>
 * Account {@code code} and non-null {@code systemKey} are unique within a tenant
 * (enforced by database constraints that include {@code tenant_id}). System
 * accounts (those with a {@code systemKey}) back the automatic posting rules and
 * are protected from deletion and from changes that would invalidate posting
 * (see {@code GlAccountServiceImpl}). The declared {@link #normalBalance} is
 * validated against the {@link #accountType}'s conventional side on create.
 */
@Entity
@Table(name = "gl_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gl_account_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uk_gl_account_system_key", columnNames = {"tenant_id", "system_key"})
        },
        indexes = {
                @Index(name = "idx_gl_account_tenant_type", columnList = "tenant_id, account_type"),
                @Index(name = "idx_gl_account_tenant_active", columnList = "tenant_id, active")
        })
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlAccount extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private GlAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private GlNormalBalance normalBalance;

    /**
     * Stable machine key used by posting rules and seeding (e.g. {@code CASH_BANK}).
     * Null for administrator-created accounts; unique per tenant when present.
     */
    @Column(name = "system_key", length = 50)
    private String systemKey;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** True for seeded accounts that back posting rules; protected from deletion. */
    @Column(name = "system_account", nullable = false)
    @Builder.Default
    private boolean systemAccount = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
