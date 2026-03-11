package com.EduePoa.EP.academics.service;



import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.CreateSubjectRequest;
import com.EduePoa.EP.academics.dto.response.SubjectResponseDto;

import java.util.List;

public interface AcademicSubjectService {

    CustomResponse<SubjectResponseDto> createSubject(CreateSubjectRequest request);
    CustomResponse<List<SubjectResponseDto>> getAllSubjects();
    CustomResponse<List<SubjectResponseDto>> getSubjectsByGrade(Long gradeId);
    CustomResponse<Void> deleteSubject(Long subjectId);
    CustomResponse<?>getSubjectsByStudent(Long studentId);
}
