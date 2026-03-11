package com.EduePoa.EP.Procurement.Inventory;


import com.EduePoa.EP.Authentication.Enum.RequisitionStatus;
import com.EduePoa.EP.Authentication.User.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "stock_requisitions", indexes = {
        @Index(name = "idx_requisition_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String requisitionNumber;
    @Column(length = 500)
    private String purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequisitionStatus status = RequisitionStatus.PENDING;
    @OneToMany(mappedBy = "stockRequisition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockRequisitionItem> items;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    private LocalDateTime approvedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;
    private LocalDateTime rejectedAt;
    @Column(length = 500)
    private String rejectionReason;
}
