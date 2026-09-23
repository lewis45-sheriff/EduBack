package com.EduePoa.EP.Grade.Stream.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignClassTeacherRequest {
    @NotNull(message = "staffId is required")
    private Long staffId;
}
