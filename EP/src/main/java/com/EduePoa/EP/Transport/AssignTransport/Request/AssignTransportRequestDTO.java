package com.EduePoa.EP.Transport.AssignTransport.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Data;

@Data
public class AssignTransportRequestDTO {
    private Long studentId;
    private String pickupLocation;
    private Long vehicleId;
    private TransportType transportType;
    private Term term;
    private Integer year;
}
