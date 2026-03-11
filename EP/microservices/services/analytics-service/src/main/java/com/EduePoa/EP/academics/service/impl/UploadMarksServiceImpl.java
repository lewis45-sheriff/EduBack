package com.EduePoa.EP.academics.service.impl;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Scoring.ExamType;
import com.EduePoa.EP.Scoring.ExamTypeRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.UploadMarksRequest;
import com.EduePoa.EP.academics.dto.response.UploadMarksResponseDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.repository.AcademicSubjectRepository;
import com.EduePoa.EP.academics.service.UploadMarksService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadMarksServiceImpl implements UploadMarksService {

    private final StudentsScoreRepository studentsScoreRepository;
    private final GradeRepository gradeRepository;
    private final AcademicSubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final ExamTypeRepository examTypeRepository;

    @Override
    @Transactional
    public CustomResponse<UploadMarksResponseDto> uploadMarks(List<UploadMarksRequest> requests) {
        CustomResponse<UploadMarksResponseDto> response = new CustomResponse<>();
        UploadMarksResponseDto responseDto = new UploadMarksResponseDto(0, 0, new ArrayList<>());

        try {
            int savedCount = 0;
            int skippedCount = 0;
            List<String> errors = responseDto.getErrors();

            for (UploadMarksRequest request : requests) {
                Optional<Grade> gradeOpt = gradeRepository.findById(request.getGradeId());
                Optional<AcademicSubject> subjectOpt = subjectRepository.findById(request.getSubjectId());
                Optional<Term> termOpt = resolveTermById(request.getTermId());

                if (gradeOpt.isEmpty()) {
                    errors.add("Grade not found with ID: " + request.getGradeId());
                    continue;
                }
                if (subjectOpt.isEmpty()) {
                    errors.add("Subject not found with ID: " + request.getSubjectId());
                    continue;
                }
                if (termOpt.isEmpty()) {
                    errors.add("Term not found with ID: " + request.getTermId());
                    continue;
                }

                Grade grade = gradeOpt.get();
                AcademicSubject subject = subjectOpt.get();
                Term term = termOpt.get();
                Year year = Year.of(request.getYear());

                for (UploadMarksRequest.StudentMarkEntry markEntry : request.getMarks()) {
                    Optional<Student> studentOpt = studentRepository.findById(markEntry.getStudentId());
                    if (studentOpt.isEmpty()) {
                        errors.add("Student not found: " + markEntry.getStudentId());
                        continue;
                    }

                    Optional<ExamType> examOpt = examTypeRepository.findById(markEntry.getExamTypeId());
                    if (examOpt.isEmpty()) {
                        errors.add("ExamType not found: " + markEntry.getExamTypeId());
                        continue;
                    }

                    Student student = studentOpt.get();
                    ExamType examType = examOpt.get();

                    // Check if exists
                    boolean exists = studentsScoreRepository
                            .existsByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
                                    student, grade, term, subject, examType, year);
                    if (exists) {
                        skippedCount++;
                        continue;
                    }

                    StudentsScore score = new StudentsScore();
                    score.setStudent(student);
                    score.setGrade(grade);
                    score.setTerm(term);
                    score.setAcademicSubject(subject);
                    score.setExamType(examType);
                    score.setYear(year);
                    score.setExamScore(markEntry.getScore());
                    score.setResultApproved('N');

                    studentsScoreRepository.save(score);
                    savedCount++;
                }
            }

            responseDto.setSavedCount(savedCount);
            responseDto.setSkippedCount(skippedCount);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Marks uploaded successfully");
            response.setEntity(responseDto);

        } catch (Exception e) {
            log.error("Error uploading marks: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error uploading marks: " + e.getMessage());
            response.setEntity(responseDto);
        }

        return response;
    }

    private Optional<Term> resolveTermById(Long termId) {
        if (termId == null) {
            return Optional.empty();
        }
        return switch (termId.intValue()) {
            case 1 -> Optional.of(Term.TERM_1);
            case 2 -> Optional.of(Term.TERM_2);
            case 3 -> Optional.of(Term.TERM_3);
            default -> Optional.empty();
        };
    }
}
