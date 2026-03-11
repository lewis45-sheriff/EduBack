package com.EduePoa.EP.academics.service.impl;


import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.AssignSubjectsToGradeRequest;
import com.EduePoa.EP.academics.dto.response.AssignmentResultDto;
import com.EduePoa.EP.academics.dto.response.SubjectResponseDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.ClassSubjectAssignment;
import com.EduePoa.EP.academics.repository.AcademicSubjectRepository;
import com.EduePoa.EP.academics.repository.ClassSubjectAssignmentRepository;
import com.EduePoa.EP.academics.service.ClassSubjectService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassSubjectServiceImpl implements ClassSubjectService {

    private final ClassSubjectAssignmentRepository assignmentRepository;
    private final AcademicSubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;


    @Override
    @Transactional
    public CustomResponse<AssignmentResultDto> assignSubjectsToGrade(AssignSubjectsToGradeRequest request) {
        CustomResponse<AssignmentResultDto> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(request.getGradeId());
            if (gradeOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Grade not found with id: " + request.getGradeId());
                return response;
            }

            Grade grade = gradeOpt.get();
            Year year = Year.of(request.getYear());

            List<AcademicSubject> allSubjects = subjectRepository.findAll();
            if (allSubjects.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No subjects found in the system. Please create subjects first.");
                return response;
            }

            List<String> assignedNames = new ArrayList<>();
            int newAssignments = 0;

            for (AcademicSubject subject : allSubjects) {
                boolean alreadyExists = assignmentRepository
                        .existsByGradeAndAcademicSubjectAndYear(grade, subject, year);
                if (!alreadyExists) {
                    ClassSubjectAssignment assignment = new ClassSubjectAssignment();
                    assignment.setGrade(grade);
                    assignment.setAcademicSubject(subject);
                    assignment.setYear(year);
                    assignmentRepository.save(assignment);
                    newAssignments++;
                }
                assignedNames.add(subject.getSubjectName());
            }

            AssignmentResultDto result = new AssignmentResultDto();
            result.setGradeId(grade.getId());
            result.setGradeName(grade.getName());
            result.setYear(request.getYear());
            result.setTotalSubjectsAssigned(assignedNames.size());
            result.setAssignedSubjectNames(assignedNames);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(newAssignments + " new subject(s) assigned to " + grade.getName() +
                    ". " + (assignedNames.size() - newAssignments) + " already existed.");
            response.setEntity(result);

        } catch (Exception e) {
            log.error("Error assigning subjects to grade {}: {}", request.getGradeId(), e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error assigning subjects: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<List<SubjectResponseDto>> getAssignedSubjects(Long gradeId, int year) {
        CustomResponse<List<SubjectResponseDto>> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            if (gradeOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Grade not found with id: " + gradeId);
                return response;
            }

            List<ClassSubjectAssignment> assignments = assignmentRepository
                    .findByGradeAndYear(gradeOpt.get(), Year.of(year));

            if (assignments.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No subjects assigned to this grade for year " + year);
                return response;
            }

            List<SubjectResponseDto> dtos = assignments.stream()
                    .map(a -> {
                        AcademicSubject s = a.getAcademicSubject();
                        SubjectResponseDto dto = new SubjectResponseDto();
                        dto.setId(s.getId());
                        dto.setSubjectName(s.getSubjectName());
                        dto.setSubjectCode(s.getSubjectCode());
                        dto.setLearningArea(s.getLearningArea());
                        dto.setIsCbcCore(s.getIsCbcCore());
                        return dto;
                    })
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Assigned subjects retrieved successfully");
            response.setEntity(dtos);

        } catch (Exception e) {
            log.error("Error fetching assigned subjects for grade {}: {}", gradeId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching assigned subjects: " + e.getMessage());
        }
        return response;
    }
}
