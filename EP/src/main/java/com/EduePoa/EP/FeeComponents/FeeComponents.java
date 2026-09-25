package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_components")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class FeeComponents extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String type;
    private String category;
    private Status status ;
    @Column()
    private boolean deleted = false;
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdOn;
    @ManyToOne
    @JsonBackReference
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id")
    private FeeStructure feeStructure;

    private String term;
    private BigDecimal amount;

    /**
     * Whether an authorized parent may self-assign this optional component to their
     * own child. Only meaningful when the component is optional (see
     * {@link #isOptional()}). Defaults to {@code false}; a value of {@code true}
     * does not itself grant authorization.
     */
    @Column(nullable = false)
    private boolean parentAssignable = false;

    /**
     * Whether this component is an optional fee, derived from {@link #type}. A
     * component is optional when its type equals {@code "OPTIONAL"}
     * (case-insensitive); any other type (e.g. {@code "MANDATORY"}) is mandatory.
     * Not persisted — {@code type} is the single source of truth.
     */
    @Transient
    public boolean isOptional() {
        return type != null && "OPTIONAL".equalsIgnoreCase(type.trim());
    }

}
