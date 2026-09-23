package com.EduePoa.EP.Grade.Stream.Requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeStreamDto {
    private Long id;
    private String name;
    private Long gradeId;
    private String gradeName;
    private Long classTeacherId;
    private String classTeacherName;
    private String classTeacherEmployeeNumber;
}
