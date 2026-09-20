package com.EduePoa.EP.Grade.Stream.Requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GradeStreamCreateRequest {

    @NotNull(message = "Grade id is required")
    private Long gradeId;

    @NotEmpty(message = "At least one stream name is required")
    private List<String> streamNames;
}
