package com.EduePoa.EP.academics.service.impl;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import com.EduePoa.EP.academics.service.CbcCalculatorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbcCalculatorServiceImpl implements CbcCalculatorService {

    private final CbcGradeResultRepository cbcResultRepository;
    private final StudentsScoreRepository studentsScoreRepository;
    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;

    @Override
    @Transactional
    public CustomResponse<List<CbcResultDto>> computeForGrade(Long gradeId, Long termId, int year) {
        CustomResponse<List<CbcResultDto>> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            Optional<Term> termOpt = resolveTermById(termId);

            if (gradeOpt.isEmpty() || termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Grade or Term not found");
                return response;
            }

            Grade grade = gradeOpt.get();
            Term term = termOpt.get();
            Year acaYear = Year.of(year);

            // Fetch all raw scores for this grade + term + year
            // Note: Currently StudentsScore uses the old Subject entity, not
            // AcademicSubject.
            // Wait, this requires mapping. If StudentsScore points to Subject instead of
            // AcademicSubject,
            // we have a mismatch!

            // For the sake of this implementation, we will fetch raw scores and map them by
            // name
            // or adapt the StudentsScore entity later. Assuming StudentsScore works with
            // the
            // existing Subject entity, we'll calculate averages based on subject names or
            // IDs.
            // Since AcademicSubject is new, let's compute and store it.
            // Let's assume StudentsScore's subject name matches AcademicSubject name
            // temporarily.

            // NOTE: This implementation will need adjustment to link the old Subject with
            // AcademicSubject,
            // but we'll fulfill the logic of CBC calculation here.

            List<StudentsScore> allScores = studentsScoreRepository.findByTermAndGrade(term, grade);
            // Further filter by year if needed (in memory for now)
            allScores = allScores.stream().filter(s -> s.getYear() != null && s.getYear().equals(acaYear))
                    .toList();

            if (allScores.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No scores found for this grade/term/year to compute");
                return response;
            }

            // Group by Student -> AcademicSubject -> List of Exam Scores
            Map<Student, Map<AcademicSubject, List<StudentsScore>>> grouped = allScores.stream()
                    .filter(s -> s.getAcademicSubject() != null)
                    .collect(Collectors.groupingBy(
                            StudentsScore::getStudent,
                            Collectors.groupingBy(StudentsScore::getAcademicSubject)));

            List<CbcResultDto> resultsDtos = new ArrayList<>();
            int computedCount = 0;

            for (Map.Entry<Student, Map<AcademicSubject, List<StudentsScore>>> studentEntry : grouped.entrySet()) {
                Student student = studentEntry.getKey();

                for (Map.Entry<AcademicSubject, List<StudentsScore>> subjectEntry : studentEntry.getValue()
                        .entrySet()) {
                    AcademicSubject academicSubject = subjectEntry.getKey();
                    String subjectName = academicSubject.getSubjectName();
                    List<StudentsScore> scores = subjectEntry.getValue();

                    // Calculate average score across all exam types
                    double sum = 0;
                    for (StudentsScore sc : scores) {
                        sum += (sc.getExamScore() != null ? sc.getExamScore() : 0);
                    }
                    double average = scores.isEmpty() ? 0 : sum / scores.size();

                    int level = computeLevel(average);
                    String label = computeLabel(level);

                    // Skip or create CbcGradeResult entity
                    Optional<CbcGradeResult> existingResult = cbcResultRepository
                            .findByStudentAndAcademicSubjectAndTermAndYear(
                                    student, academicSubject, term, acaYear);

                    CbcGradeResult gradeResult;
                    if (existingResult.isPresent()) {
                        gradeResult = existingResult.get();
                    } else {
                        gradeResult = new CbcGradeResult();
                        gradeResult.setStudent(student);
                        gradeResult.setAcademicSubject(academicSubject);
                        gradeResult.setTerm(term);
                        gradeResult.setYear(acaYear);
                    }
                    gradeResult.setAverageScore(average);
                    gradeResult.setCbcLevel(level);
                    gradeResult.setCbcLabel(label);

                    cbcResultRepository.save(gradeResult);

                    computedCount++;

                    CbcResultDto dto = new CbcResultDto();
                    dto.setStudentId(student.getId());
                    dto.setStudentName(student.getFirstName() + " " + student.getLastName());
                    dto.setSubjectName(subjectName);
                    dto.setTermName(term.name());
                    dto.setYear(year);
                    dto.setAverageScore(average);
                    dto.setCbcLevel(level);
                    dto.setCbcLabel(label);
                    resultsDtos.add(dto);
                }
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Computed CBC results for " + computedCount + " student-subject combinations.");
            response.setEntity(resultsDtos);

        } catch (Exception e) {
            log.error("Error computing CBC grades for grade {}: {}", gradeId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error computing CBC grades: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<List<CbcResultDto>> getResultsForStudent(Long studentId, Long termId) {
        CustomResponse<List<CbcResultDto>> response = new CustomResponse<>();
        try {
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            Optional<Term> termOpt = resolveTermById(termId);

            if (studentOpt.isEmpty() || termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student or Term not found");
                return response;
            }

            List<CbcGradeResult> results = cbcResultRepository.findByStudentAndTerm(studentOpt.get(), termOpt.get());

            if (results.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No CBC results found for this student and term");
                return response;
            }

            List<CbcResultDto> dtos = results.stream().map(r -> {
                CbcResultDto dto = new CbcResultDto();
                dto.setId(r.getId());
                dto.setStudentId(r.getStudent().getId());
                dto.setStudentName(r.getStudent().getFirstName() + " " + r.getStudent().getLastName());
                dto.setSubjectId(r.getAcademicSubject().getId());
                dto.setSubjectName(r.getAcademicSubject().getSubjectName());
                dto.setTermName(r.getTerm().name());
                dto.setYear(r.getYear().getValue());
                dto.setAverageScore(r.getAverageScore());
                dto.setCbcLevel(r.getCbcLevel());
                dto.setCbcLabel(r.getCbcLabel());
                return dto;
            }).collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Results retrieved successfully");
            response.setEntity(dtos);

        } catch (Exception e) {
            log.error("Error fetching CBC results for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching CBC results: " + e.getMessage());
        }
        return response;
    }

    private int computeLevel(double average) {
        if (average >= 80)
            return 4;
        if (average >= 50)
            return 3;
        if (average >= 30)
            return 2;
        return 1;
    }

    private String computeLabel(int level) {
        return switch (level) {
            case 4 -> "Exceeding Expectations";
            case 3 -> "Meeting Expectations";
            case 2 -> "Approaching Expectations";
            case 1 -> "Below Expectations";
            default -> "Unknown";
        };
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
