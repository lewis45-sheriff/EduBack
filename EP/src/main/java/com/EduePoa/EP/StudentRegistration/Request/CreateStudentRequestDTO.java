package com.EduePoa.EP.StudentRegistration.Request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateStudentRequestDTO {
    private StudentInfoDTO student;
    private List<GuardianDTO> guardians = new ArrayList<>();
    private NemisDTO nemis;
}
