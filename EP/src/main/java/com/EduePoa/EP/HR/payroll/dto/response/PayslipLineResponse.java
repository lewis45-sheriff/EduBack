package com.EduePoa.EP.HR.payroll.dto.response;

import com.EduePoa.EP.HR.payroll.enums.PayslipLineType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PayslipLineResponse {
    private PayslipLineType lineType;
    private String name;
    private BigDecimal amount;
}
