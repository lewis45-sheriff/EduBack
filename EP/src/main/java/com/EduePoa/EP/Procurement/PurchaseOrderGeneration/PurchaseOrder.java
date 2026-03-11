package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;


import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
//@EntityListeners(AuditingEntityListener.class)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String poNumber;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierOnboarding supplier;

    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status;

    private BigDecimal totalAmount;

    @CreatedDate
    @Column( )
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @ManyToOne
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    private LocalDateTime rejectedAt;

    @Column(length = 500)
    private String rejectionReason;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer rejectionCount = 0;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    private LocalDateTime updatedAt;

}
