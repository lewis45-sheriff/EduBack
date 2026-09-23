package com.EduePoa.EP.academics.service;



import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.CreateSubjectRequest;
import com.EduePoa.EP.academics.dto.response.SubjectResponseDto;

import java.util.List;

/**
 * @deprecated Superseded by the CBC learning-area catalogue
 * ({@code com.EduePoa.EP.academics.curriculum.service.CurriculumService}). Subjects are now derived
 * from active learning areas and bridged to {@code AcademicSubject}. Retained for backward
 * compatibility; do not build new features on this service.
 */
@Deprecated
public interface AcademicSubjectService {

    CustomResponse<SubjectResponseDto> createSubject(CreateSubjectRequest request);
    CustomResponse<List<SubjectResponseDto>> getAllSubjects();
    CustomResponse<List<SubjectResponseDto>> getSubjectsByGrade(Long gradeId);
    CustomResponse<Void> deleteSubject(Long subjectId);
    CustomResponse<?>getSubjectsByStudent(Long studentId);
}
