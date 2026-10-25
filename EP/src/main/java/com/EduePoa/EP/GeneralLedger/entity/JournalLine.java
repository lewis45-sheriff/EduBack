package com.EduePoa.EP.GeneralLedger.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A single line of a {@link JournalEntry}. Exactly one of {@link #debit} /
 * {@link #credit} is positive; the other is zero. Lines are append-only: once a
 * journal is posted its lines are never updated or deleted. Corrections are made
 * by posting a new counter-journal.
 */
@Entity
@Table(name = "gl_journal_line",
        indexes = {
                @Index(name = "idx_gl_line_journal", columnList = "journal_id"),
                @Index(name = "idx_gl_line_account", columnList = "tenant_id, account_id")
        })
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "journal")
public class JournalLine extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false)
    private JournalEntry journal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private GlAccount account;

    @Column(name = "debit", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "narration", length = 300)
    private String narration;

    @Column(name = "line_order", nullable = false)
    @Builder.Default
    private int lineOrder = 0;
}
