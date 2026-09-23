package com.EduePoa.EP.Grade.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignClassTeacherRequest {
    @NotNull(message = "staffId is required")
    private Long staffId;
}
