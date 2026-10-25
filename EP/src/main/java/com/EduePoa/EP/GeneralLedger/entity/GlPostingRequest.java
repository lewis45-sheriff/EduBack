package com.EduePoa.EP.GeneralLedger.entity;

import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
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
 * Transactional-outbox record for a GL posting.
 * <p>
 * Written {@link GlPostingStatus#PENDING} in the SAME database transaction as the
 * finalized money event, so a committed business event always leaves a durable,
 * recoverable instruction to post — even if the posting itself later fails. A
 * processor (invoked inline, by a scheduled sweeper, and by a manual retry
 * endpoint) turns it into a journal and marks it {@link GlPostingStatus#POSTED},
 * or records {@link GlPostingStatus#FAILED} with {@link #lastError} and increments
 * {@link #attempts}. The unique {@code (tenant_id, source_type, source_id)}
 * constraint makes enqueueing idempotent under concurrent callbacks/retries.
 * <p>
 * {@link #payload} holds only a compact, non-sensitive JSON of the amounts and
 * references needed to post — never raw payment-gateway callback bodies.
 */
@Entity
@Table(name = "gl_posting_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gl_posting_source", columnNames = {"tenant_id", "source_type", "source_id"})
        },
        indexes = {
                @Index(name = "idx_gl_posting_status", columnList = "tenant_id, status"),
                @Index(name = "idx_gl_posting_status_global", columnList = "status")
        })
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlPostingRequest extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private GlSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GlPostingStatus status = GlPostingStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** Id of the journal produced on success; null until POSTED. */
    @Column(name = "journal_id")
    private Long journalId;

    /** Compact, non-sensitive JSON of amounts/refs needed to build the journal. */
    @Lob
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = GlPostingStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
