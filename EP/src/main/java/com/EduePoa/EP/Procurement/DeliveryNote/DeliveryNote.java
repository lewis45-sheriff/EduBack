package com.EduePoa.EP.Procurement.DeliveryNote;


import com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "delivery_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// REMOVE @EntityListeners(AuditingEntityListener.class) ← DELETE THIS
public class DeliveryNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", referencedColumnName = "id", nullable = false)
    private PurchaseOrder purchaseOrder;

    private LocalDate deliveryDate;

    @Column()
    private String deliveredBy;

    @Column()
    private String receivedBy;

    @OneToMany(mappedBy = "deliveryNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryNoteItem> items;

    @Lob
    private String deliveryDocument;

    @Enumerated(EnumType.STRING)
    @Column()
    private DeliveryNoteStatus status = DeliveryNoteStatus.DRAFT;

    @Column(length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column()
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    private LocalDateTime rejectedAt;

    // REMOVE @LastModifiedBy ← deleted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    // REMOVE @LastModifiedDate ← deleted
    private LocalDateTime updatedAt;
}
