package com.EduePoa.EP.Transport.AssignTransport.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class AssignTransportResponseDTO {

    private Long assignmentId;

    private Long studentId;
    private String studentName;

    private Long vehicleId;
    private String vehiclePlateNumber;

    private String pickupLocation;
    private TransportType transportType;
    private Term term;
    private Integer year;
    private String admissionNumber;
    private LocalDate assignedDate;

}