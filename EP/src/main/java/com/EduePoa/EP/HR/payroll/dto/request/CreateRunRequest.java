package com.EduePoa.EP.HR.payroll.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRunRequest {

    @NotNull(message = "month is required")
    @Min(value = 1, message = "month must be 1-12")
    @Max(value = 12, message = "month must be 1-12")
    private Integer month;

    @NotNull(message = "year is required")
    @Min(value = 2000, message = "year is out of range")
    @Max(value = 2100, message = "year is out of range")
    private Integer year;
}
