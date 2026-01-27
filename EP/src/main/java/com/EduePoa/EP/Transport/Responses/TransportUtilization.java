package com.EduePoa.EP.Transport.Responses;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportUtilization {
    private Integer vehicleId;
    private String vehicleNumber;
    private String route;
    private Integer capacity;
    private Integer assignedStudents;
    private Double utilizationPercentage;
    private Double expectedRevenue;
    private Double collectedRevenue;
    private Double pendingRevenue;
}