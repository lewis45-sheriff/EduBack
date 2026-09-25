package com.EduePoa.EP.FeeStructure.FeeComponentConfig;

import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class FeeComponentConfig extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Integer id;


    private String name;

    private String feeStatus;

    @CreatedDate
    @JsonIgnore
    private LocalDateTime createdAt;

    @LastModifiedDate
    @JsonIgnore
    private LocalDateTime updatedAt;
    private BigDecimal amount;
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdOn;
    @ManyToOne
    @JsonBackReference
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id")
    private FeeStructure feeStructure;

    private String term;

    /**
     * Whether this fee-structure line item may be assigned to individual students
     * as an optional fee. Defaults to {@code false} so existing line items remain
     * mandatory and backward compatible.
     */
    @Column(nullable = false)
    private boolean optional = false;

    /**
     * Whether an authorized parent may self-assign this optional line item to their
     * own child. Only meaningful when {@link #optional} is {@code true}. Defaults to
     * {@code false}; a value of {@code true} does not itself grant authorization.
     */
    @Column(nullable = false)
    private boolean parentAssignable = false;

}
