package com.EduePoa.EP.OrgUnit.Entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * A node in a tenant's configurable organizational hierarchy (school → divisions →
 * departments → sections → units), used to allocate budgets by organizational unit and to
 * roll performance up the tree (design §5).
 *
 * <p>Descendant queries use a materialized {@link #path}: a root-rooted, id-based string such as
 * {@code "/1/"} for the root and {@code "/1/4/9/"} for a grandchild. A unit's whole subtree is
 * every row whose path starts with that unit's path, enabling efficient roll-ups and cycle
 * detection at arbitrary depth. {@link #depth} is the number of ancestors (root = 0).
 *
 * <p>{@link #type} is a free-form, tenant-configurable label (suggested constants in
 * {@link OrgUnitType}); administrators can introduce new types without code changes. {@link #code}
 * is normalized (trimmed, upper-cased) and unique per tenant so it is a stable reporting/integration
 * reference even if the unit is renamed or moved.
 */
@Entity
@Table(name = "org_units",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_org_unit_code", columnNames = {"tenant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_org_unit_parent", columnList = "tenant_id, parent_id"),
                @Index(name = "idx_org_unit_path", columnList = "tenant_id, path"),
                @Index(name = "idx_org_unit_status", columnList = "tenant_id, status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class OrgUnit extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /** Normalized (trimmed + upper-cased), unique per tenant. Stable across rename/move. */
    @Column(nullable = false, length = 60)
    private String code;

    /** Free-form configurable type label (see {@link OrgUnitType} for suggested defaults). */
    @Column(nullable = false, length = 40)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    /** Materialized root-rooted id path, e.g. "/1/4/9/". Maintained on create/move. */
    @Column(nullable = false, length = 1000)
    private String path;

    /** Number of ancestors; root = 0. */
    @Column(nullable = false)
    @Builder.Default
    private int depth = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrgUnitStatus status = OrgUnitStatus.ACTIVE;

    /** Optional unit head; departmental views may be scoped to this user's subtree. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_user_id")
    private User headUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
