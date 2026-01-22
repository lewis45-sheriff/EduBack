package com.EduePoa.EP.Transport.AssignTransport.Request;

import lombok.Data;

@Data
public class AssignTransportRequestDTO {
    private Long studentId;
    private String pickupLocation;
    private Long vehicleId;
    private String transportType;
}
