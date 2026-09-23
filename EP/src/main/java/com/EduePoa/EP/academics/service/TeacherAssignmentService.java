package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.AssignTeacherToSubjectRequest;
import com.EduePoa.EP.academics.dto.request.BulkAssignTeacherRequest;
import com.EduePoa.EP.academics.dto.response.BulkAssignResultDto;
import com.EduePoa.EP.academics.dto.response.TeacherSubjectAssignmentDto;

import java.util.List;

/**
 * Assigns teachers to learning areas (subjects) for a grade/stream/year and exposes lookups used by
 * the assessment layer to authorize marks entry/approval. A teacher may hold many assignments across
 * different subjects, grades and streams.
 */
public interface TeacherAssignmentService {

    CustomResponse<TeacherSubjectAssignmentDto> assignTeacher(AssignTeacherToSubjectRequest request);

    /** Assign one teacher to many subject/class combinations in a single call (partial success). */
    CustomResponse<BulkAssignResultDto> bulkAssign(BulkAssignTeacherRequest request);

    CustomResponse<String> unassignTeacher(Long assignmentId);

    CustomResponse<List<TeacherSubjectAssignmentDto>> getByTeacher(Long teacherId, int year);

    CustomResponse<List<TeacherSubjectAssignmentDto>> getByGrade(Long gradeId, int year);

    CustomResponse<List<TeacherSubjectAssignmentDto>> getBySubject(Long subjectId, int year);
}
