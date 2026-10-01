package com.EduePoa.EP.Transport.TransportTermPrice;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Transport.Transport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * Per-term price for a vehicle's route. One row per (vehicle, term, year) holding
 * both the one-way and two-way amounts for that term.
 *
 * Mirrors the FeeStructure -> FeeComponentConfig per-term pattern used elsewhere:
 * a parent ({@link Transport}) with many term-scoped child rows.
 */
@Entity
@Table(
        name = "transport_term_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transport_term_year",
                columnNames = {"vehicle_id", "term", "year"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class TransportTermPrice extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    @JsonIgnore
    private Transport vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "term", nullable = false)
    private Term term;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "one_way_amount")
    private Double oneWayAmount;

    @Column(name = "two_way_amount")
    private Double twoWayAmount;
}
