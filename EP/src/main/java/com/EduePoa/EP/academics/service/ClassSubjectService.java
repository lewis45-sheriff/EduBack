package com.EduePoa.EP.academics.service;



import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.AssignSubjectsToGradeRequest;
import com.EduePoa.EP.academics.dto.response.AssignmentResultDto;
import com.EduePoa.EP.academics.dto.response.SubjectResponseDto;

import java.util.List;

public interface ClassSubjectService {

    CustomResponse<AssignmentResultDto> assignSubjectsToGrade(AssignSubjectsToGradeRequest request);

    CustomResponse<List<SubjectResponseDto>> getAssignedSubjects(Long gradeId, int year);
}
