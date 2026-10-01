package com.EduePoa.EP.Transport.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportArrearsSummaryDTO {
    private Long studentId;
    private String admissionNumber;
    private String fullName;

    @Builder.Default
    private List<TransportArrearsLineDTO> lines = new ArrayList<>();

    private Double totalExpected;
    private Double totalPaid;
    private Double totalOutstanding;
}
