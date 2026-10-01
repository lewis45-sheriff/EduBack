package com.EduePoa.EP.Transport.TransportTransactions.Approval;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Transport.TransportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_transport_transactions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingTransportTransaction extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- What is being paid ------------------------------------------------

    @Column(nullable = false)
    private Long studentId;
    private String studentName;
    private String admissionNumber;

    @Column(nullable = false)
    private Long vehicleId;
    private String vehicleNumber;
    private String route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportType transportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Double amount;

    private String paymentMethod;

    // ---- Snapshot at submit time (display only; recomputed on approval) -----

    private Double expectedFee;
    private Double totalPaidBeforeSnapshot;
    private Double arrearsBeforeSnapshot;

    // ---- Maker's justification --------------------------------------------

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PendingTransportTransactionStatus status = PendingTransportTransactionStatus.PENDING_APPROVAL;

    // ---- Maker / checker ---------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    /** Id of the {@code TransportTransactions} row created when this request was approved. */
    private Long postedTransactionId;

    @Column(length = 500)
    private String rejectionReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PendingTransportTransactionStatus.PENDING_APPROVAL;
        }
    }
}
