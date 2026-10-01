package com.EduePoa.EP.Transport.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One outstanding-balance line for a student's transport, scoped to a single
 * (term, year, vehicle, transportType) — the same granularity the payment engine uses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportArrearsLineDTO {
    private Term term;
    private Integer year;
    private TransportType transportType;

    private Long vehicleId;
    private String vehicleNumber;
    private String route;

    private Double expectedFee;
    private Double totalPaid;
    private Double outstanding;

    /** "COMPLETED" when outstanding is ~0, otherwise "PARTIAL"/"UNPAID". */
    private String status;
}
