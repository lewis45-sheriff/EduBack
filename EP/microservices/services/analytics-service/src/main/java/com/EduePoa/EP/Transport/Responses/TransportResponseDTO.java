package com.EduePoa.EP.Transport.Responses;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportResponseDTO {
    private Long id;
    private String vehicleNumber;

    private String vehicleType;

    private Integer capacity;

    private String driverName;

    private String driverContact;

    private String route;

    private Double routePriceOneWay;

    private Double routePriceTwoWay;

    private String status;
}
