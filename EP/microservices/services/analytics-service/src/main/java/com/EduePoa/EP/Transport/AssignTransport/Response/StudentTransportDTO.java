package com.EduePoa.EP.Transport.AssignTransport.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentTransportDTO {

    private Long studentId;
    private String admissionNumber;
    private String fullName;
    private String pickupLocation;
    private String transportType;
    private String vehicleName;
}