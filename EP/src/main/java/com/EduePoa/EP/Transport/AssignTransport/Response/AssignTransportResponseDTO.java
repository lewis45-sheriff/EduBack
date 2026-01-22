package com.EduePoa.EP.Transport.AssignTransport.Response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignTransportResponseDTO {

    private Long assignmentId;

    private Long studentId;
    private String studentName;

    private Long vehicleId;
    private String vehiclePlateNumber;

    private String pickupLocation;
    private String transportType;
}