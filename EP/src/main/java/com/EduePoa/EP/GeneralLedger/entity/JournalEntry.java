package com.EduePoa.EP.GeneralLedger.entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A balanced double-entry journal header for a single tenant.
 * <p>
 * Idempotency: a non-reversal journal is unique per {@code (tenant_id,
 * source_type, source_id)}, so a given money event can be posted at most once
 * even under concurrent retries. Reversal journals set {@code source_id = null}
 * and instead carry {@link #reversalOfJournalId}, which is unique per tenant so a
 * journal can be reversed at most once. {@code entry_number} is unique per tenant
 * and generated under a tenant-scoped sequence.
 * <p>
 * Posted journals are append-only. A correction is a new counter-journal; the
 * original transitions to {@link JournalStatus#REVERSED} but its lines are never
 * mutated or deleted.
 */
@Entity
@Table(name = "gl_journal_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gl_journal_entry_number", columnNames = {"tenant_id", "entry_number"}),
                @UniqueConstraint(name = "uk_gl_journal_source", columnNames = {"tenant_id", "source_type", "source_id"}),
                @UniqueConstraint(name = "uk_gl_journal_reversal_of", columnNames = {"tenant_id", "reversal_of_journal_id"})
        },
        indexes = {
                @Index(name = "idx_gl_journal_date", columnList = "tenant_id, accounting_date"),
                @Index(name = "idx_gl_journal_source", columnList = "tenant_id, source_type, source_id"),
                @Index(name = "idx_gl_journal_status", columnList = "tenant_id, status")
        })
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "lines")
public class JournalEntry extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_number", nullable = false, length = 40)
    private String entryNumber;

    @Column(name = "accounting_date", nullable = false)
    private LocalDate accountingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private GlSourceType sourceType;

    /**
     * Identifier of the originating event in its own subsystem (e.g. the
     * FinanceTransaction id). Null for reversal counter-journals, which are keyed
     * by {@link #reversalOfJournalId} instead.
     */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JournalStatus status = JournalStatus.POSTED;

    /** Set on a counter-journal: the id of the original journal it reverses. */
    @Column(name = "reversal_of_journal_id")
    private Long reversalOfJournalId;

    /** Set on an original journal once it has been reversed: the counter-journal id. */
    @Column(name = "reversed_by_journal_id")
    private Long reversedByJournalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /** Human label for a system actor when no authenticated user is present (e.g. callbacks). */
    @Column(name = "created_by_label", length = 100)
    private String createdByLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = JournalStatus.POSTED;
        }
    }

    /** Adds a line and maintains the bidirectional relationship + line order. */
    public void addLine(JournalLine line) {
        line.setJournal(this);
        line.setLineOrder(lines.size());
        lines.add(line);
    }
}
