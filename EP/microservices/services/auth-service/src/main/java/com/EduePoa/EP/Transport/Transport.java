package com.EduePoa.EP.Transport;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transport {
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
    @Column(name = "route_price_one_way")
    private Double routePriceOneWay;
    @Column(name = "route_price_two_way")
    private Double routePriceTwoWay;
    @Column
    private String status;
}
