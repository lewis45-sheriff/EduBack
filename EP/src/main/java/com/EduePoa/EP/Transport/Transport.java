package com.EduePoa.EP.Transport;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Transport.TransportTermPrice.TransportTermPrice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Transport extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "vehicle_number", nullable = false, unique = true)
    private String vehicleNumber;
    @Column(name = "vehicle_type")
    private String vehicleType;
    @Column()
    private Integer capacity;
    @Column(name = "driver_name")
    private String driverName;
    @Column(name = "driver_contact")
    private String driverContact;
    @Column
    private String route;

    /**
     * @deprecated Pricing is now per-term via {@link #termPrices}. These flat fields are
     * retained only for backward compatibility with existing data and are no longer used
     * for fee resolution. They may be removed once all tenants are migrated.
     */
    @Deprecated
    @Column(name = "route_price_one_way")
    private Double routePriceOneWay;

    /**
     * @deprecated See {@link #routePriceOneWay}.
     */
    @Deprecated
    @Column(name = "route_price_two_way")
    private Double routePriceTwoWay;

    @Column
    private String status;

    /**
     * Per-term prices for this vehicle's route. One row per (term, year) holding the
     * one-way and two-way amounts for that term.
     */
    @Builder.Default
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransportTermPrice> termPrices = new ArrayList<>();
}
