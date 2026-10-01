package com.EduePoa.EP.Transport.AssignTransport.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
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
    private TransportType transportType;
    private Term term;
    private Integer year;
    private String vehicleName;
}