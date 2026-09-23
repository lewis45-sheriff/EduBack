package com.EduePoa.EP.Grade.Requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeDto {
    private Long id;
    private String name;
    private Long classTeacherId;
    private String classTeacherName;
    private String classTeacherEmployeeNumber;
}
