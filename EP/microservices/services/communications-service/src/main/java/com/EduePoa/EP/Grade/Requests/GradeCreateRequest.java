package com.EduePoa.EP.Grade.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GradeCreateRequest {

    @NotBlank(message = "Invalid grade name: Grade name is empty")
    private String grade;

    @NotNull(message = "Start value is required")
    private Integer startRange;

    @NotNull(message = "End value is required")
    private Integer endRange;
}
