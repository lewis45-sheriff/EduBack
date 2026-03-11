package com.EduePoa.EP.Scoring;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Scoring.Requests.CreateExamTypeRequest;
import com.EduePoa.EP.Scoring.Requests.CreateExamTypeGradingRequest;
import com.EduePoa.EP.Scoring.Requests.StudentBulkRequest;
import com.EduePoa.EP.Scoring.Requests.UpdateExamTypeRequest;
import com.EduePoa.EP.Scoring.Response.ExamTypeGradingResponseDto;
import com.EduePoa.EP.Scoring.Response.ExamTypeResponseDto;
import com.EduePoa.EP.Scoring.Response.StudentsScoreResponseDto;
import com.EduePoa.EP.Scoring.Response.SubjectMarksResponse;
import com.EduePoa.EP.Scoring.Response.SubjectResponse;
import com.EduePoa.EP.Scoring.Response.SubjectStudentResponse;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.repository.AcademicSubjectRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ExamService {

    @Autowired
    private ExamTypeRepository examTypeRepository;

    @Autowired
    private ExamTypeGradingRepository examTypeGradingRepository;

    @Autowired
    private StudentsScoreRepository studentsScoreRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicSubjectRepository academicSubjectRepository;

    @Transactional
    public CustomResponse<ExamTypeResponseDto> createExamType(CreateExamTypeRequest request) {
        CustomResponse<ExamTypeResponseDto> response = new CustomResponse<>();

        try {
            // Check if the exam type already exists
            if (examTypeRepository.existsByName(request.getName())) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value()); // HTTP 400 Bad Request
                response.setMessage("Exam type already exists");
                return response;
            }

            // Create new ExamType
            ExamType examType = new ExamType();
            examType.setName(request.getName());
            examType.setMinScore(request.getMinScore());
            examType.setMaxScore(request.getMaxScore());
            examType = examTypeRepository.save(examType);

            // Prepare response DTO
            ExamTypeResponseDto dto = new ExamTypeResponseDto();
            dto.setId(examType.getId());
            dto.setName(examType.getName());
            dto.setMaxScore(examType.getMaxScore());
            dto.setMinScore(examType.getMinScore());

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Exam type created successfully");
            response.setEntity(dto);

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error creating exam type: " + e.getMessage());
        }

        return response;
    }

    public CustomResponse<List<ExamTypeResponseDto>> getAllExamTypes() {
        CustomResponse<List<ExamTypeResponseDto>> response = new CustomResponse<>();

        try {
            List<ExamType> examTypes = examTypeRepository.findAll();
            if (examTypes.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value()); // HTTP 404 Not Found
                response.setMessage("No exam types found");
                response.setEntity(null);
                return response;
            }

            // Map ExamType entities to DTOs
            List<ExamTypeResponseDto> dtoList = examTypes.stream().map(examType -> {
                ExamTypeResponseDto dto = new ExamTypeResponseDto();
                dto.setId(examType.getId());
                dto.setName(examType.getName());
                dto.setMinScore(examType.getMinScore());
                dto.setMaxScore(examType.getMaxScore());
                return dto;
            }).toList();

            response.setStatusCode(HttpStatus.OK.value()); // HTTP 200 OK
            response.setMessage("Exam types fetched successfully");
            response.setEntity(dtoList);

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP 500 Internal Server Error
            response.setMessage("Error fetching exam types: " + e.getMessage());
        }

        return response;
    }

    @Transactional
    public CustomResponse<ExamTypeResponseDto> updateExamType(UpdateExamTypeRequest request) {
        CustomResponse<ExamTypeResponseDto> response = new CustomResponse<>();

        try {
            Optional<ExamType> examTypeOpt = examTypeRepository.findById(request.getId());

            if (examTypeOpt.isPresent()) {
                ExamType examType = examTypeOpt.get();
                examType.setName(request.getName());
                examType.setMaxScore(request.getMaxScore());
                examType.setMinScore(request.getMinScore());
                examType = examTypeRepository.save(examType);

                // Prepare response DTO
                ExamTypeResponseDto dto = new ExamTypeResponseDto();
                dto.setId(examType.getId());
                dto.setName(examType.getName());
                dto.setMinScore(examType.getMinScore());
                dto.setMaxScore(examType.getMaxScore());

                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("Exam type updated successfully");
                response.setEntity(dto);

            } else {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Exam type not found");
            }

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating exam type: " + e.getMessage());
        }

        return response;
    }

    public CustomResponse<Void> deleteExamType(Long id) {
        CustomResponse<Void> response = new CustomResponse<>();

        try {
            Optional<ExamType> examTypeOpt = examTypeRepository.findById(id);

            if (examTypeOpt.isPresent()) {
                examTypeRepository.deleteById(id);
                response.setStatusCode(HttpStatus.NO_CONTENT.value()); // HTTP 204 No Content
                response.setMessage("Exam type deleted successfully");
            } else {
                response.setStatusCode(HttpStatus.NOT_FOUND.value()); // HTTP 404 Not Found
                response.setMessage("Exam type not found");
            }

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP 500 Internal Server Error
            response.setMessage("Error deleting exam type: " + e.getMessage());
        }

        return response;
    }

    public CustomResponse<?> insertStudentScores(StudentBulkRequest request) {
        CustomResponse<List<StudentsScore>> response = new CustomResponse<>();
        List<StudentsScore> savedScores = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        try {
            // Get common data for all scores
            Optional<Grade> gradeOpt = gradeRepository.findById(request.getGrade());
            Optional<AcademicSubject> subjectOpt = academicSubjectRepository.findById(request.getSubject());
            Year year = Year.of(request.getYear());

            // Validate common entities
            if (gradeOpt.isEmpty() || subjectOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Invalid grade or subject ID");
                return response;
            }

            Grade grade = gradeOpt.get();
            AcademicSubject subject = subjectOpt.get();

            // Process each mark entry in the payload
            for (StudentBulkRequest.StudentMark mark : request.getMarks()) {
                try {
                    Optional<Student> studentOpt = studentRepository.findById(mark.getStudentId());
                    Optional<Term> termOpt = resolveTermById(mark.getTermId());
                    Optional<ExamType> examTypeOpt = examTypeRepository.findById(mark.getExamTypeId());

                    // Skip invalid entries but continue processing others
                    if (studentOpt.isEmpty() || termOpt.isEmpty() || examTypeOpt.isEmpty()) {
                        errorMessages.add("Invalid IDs for student " + mark.getStudentId());
                        continue;
                    }

                    Student student = studentOpt.get();
                    Term term = termOpt.get();
                    ExamType examType = examTypeOpt.get();
                    Double examScore = mark.getMark() != null ? mark.getMark() : mark.getMarks(); // Handle both field
                                                                                                  // names

                    if (examScore == null) {
                        errorMessages.add("Missing score for student " + student.getId());
                        continue;
                    }

                    // Check for duplicate entry
                    boolean exists = studentsScoreRepository
                            .existsByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
                                    student, grade, term, subject, examType, year);

                    if (exists) {
                        errorMessages.add("Exam score already exists for student " + student.getId());
                        continue;
                    }

                    // Validate exam score range
                    if (examScore < examType.getMinScore() || examScore > examType.getMaxScore()) {
                        errorMessages.add("Results for student " + student.getId() +
                                " must be between " + examType.getMinScore() +
                                " and " + examType.getMaxScore());
                        continue;
                    }

                    // Create and save StudentsScore object
                    StudentsScore studentsScore = new StudentsScore();
                    studentsScore.setStudent(student);
                    studentsScore.setGrade(grade);
                    studentsScore.setTerm(term);
                    studentsScore.setAcademicSubject(subject);
                    studentsScore.setExamScore(examScore);
                    studentsScore.setExamType(examType);
                    studentsScore.setYear(year);

                    // For subject teacher, you might need to determine this differently
                    // since it's not in the bulk payload. Maybe set based on current user?
                    // Commented out for now
                    // studentsScore.setSubjectTeacher(subjectTeacherOpt.get());

                    StudentsScore saved = studentsScoreRepository.save(studentsScore);
                    savedScores.add(saved);

                } catch (Exception e) {
                    errorMessages.add("Error processing student " + mark.getStudentId() + ": " + e.getMessage());
                }
            }

            // Determine response status
            if (savedScores.isEmpty() && !errorMessages.isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Failed to insert any scores: " + String.join("; ", errorMessages));
            } else if (!errorMessages.isEmpty()) {
                response.setStatusCode(HttpStatus.PARTIAL_CONTENT.value());
                response.setMessage("Some scores inserted with " + errorMessages.size() + " errors: " +
                        String.join("; ", errorMessages));
                response.setEntity(savedScores);
            } else {
                response.setStatusCode(HttpStatus.CREATED.value());
                response.setMessage("All scores inserted successfully");
                response.setEntity(savedScores);
            }

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error processing scores: " + e.getMessage());
        }

        return response;
    }

    public CustomResponse<?> getStudentScorePerTermGradeSubject(Long gradeId, Long termId, Long subjectId) {
        CustomResponse<List<StudentsScoreResponseDto>> response = new CustomResponse<>();
        try {
            // Retrieve subject, grade, and term using their respective IDs
            AcademicSubject subject = academicSubjectRepository.findById(subjectId)
                    .orElseThrow(() -> new RuntimeException("Subject not found"));

            Grade grade = gradeRepository.findById(gradeId)
                    .orElseThrow(() -> new RuntimeException("Grade not found"));

            Term term = resolveTermById(termId)
                    .orElseThrow(() -> new RuntimeException("Term not found with id: " + termId));

            // Fetch the list of student scores based on subject, grade, and term
            List<StudentsScore> studentsScores = studentsScoreRepository.findByAcademicSubjectAndTermAndGrade(subject,
                    term,
                    grade);

            if (!studentsScores.isEmpty()) {
                // Create a list to hold the response DTOs
                List<StudentsScoreResponseDto> responseDtos = new ArrayList<>();

                // Iterate through the student scores list and map each entry to the response
                // DTO
                for (StudentsScore studentsScore : studentsScores) {
                    StudentsScoreResponseDto responseDto = getStudentsScoreResponseDto(studentsScore);

                    responseDtos.add(responseDto);
                }

                response.setMessage("Scores retrieved successfully");
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(responseDtos);

            } else {
                response.setMessage("No scores found for the specified grade, term, and subject");
                response.setStatusCode(HttpStatus.OK.value());
            }
        } catch (RuntimeException ex) {
            response.setMessage(ex.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            log.error("Fetching scores for grade {}, term {} and subject {} failed.", gradeId, termId, subjectId, ex);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching scores ");
            log.error(e.getMessage());
        }
        return response;
    }

    @NotNull
    private static StudentsScoreResponseDto getStudentsScoreResponseDto(StudentsScore studentsScore) {
        StudentsScoreResponseDto responseDto = new StudentsScoreResponseDto();
        responseDto.setId(studentsScore.getId());
        responseDto.setStudentName(
                studentsScore.getStudent() + " " + studentsScore.getStudent().getLastName());
        responseDto.setGradeName(studentsScore.getGrade().getName());
        responseDto.setTermName(studentsScore.getTerm().name());
        responseDto.setSubjectTeacherName(studentsScore.getSubjectTeacher().getFirstName() + " "
                + studentsScore.getSubjectTeacher().getLastName());
        responseDto.setExamScore(studentsScore.getExamScore());
        responseDto.setExamType(studentsScore.getExamType().getName());
        responseDto.setYear(studentsScore.getYear());
        responseDto.setResultApproved(studentsScore.getResultApproved() == 'Y');
        responseDto.setResultApprovedBy(
                studentsScore.getResultApprovedBy() != null
                        ? studentsScore.getResultApprovedBy().getFirstName() + " "
                                + studentsScore.getResultApprovedBy().getLastName()
                        : "Not Approved");
        return responseDto;
    }

    @Transactional
    public CustomResponse<?> createExamTypeGradingCriteria(List<CreateExamTypeGradingRequest> requestList) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            // ExamType examType = examTypeRepository.findById(request.getExamTypeId())
            // .orElseThrow(() -> new RuntimeException("The exam type is not found."));

            // Iterate over the list of grading requests
            for (CreateExamTypeGradingRequest request : requestList) {
                ExamTypeGrading examTypeGrading = new ExamTypeGrading();
                examTypeGrading.setStart(request.getMin());
                examTypeGrading.setEnd(request.getMax());
                examTypeGrading.setPoint(request.getPoint());
                examTypeGrading.setRemarks(request.getGrading());

                // Save each grading to the repository
                examTypeGradingRepository.save(examTypeGrading);
            }

            response.setMessage("Exam grading criteria created successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(null);
        } catch (Exception e) {
            response.setMessage("Error occurred while creating grading criteria.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    public CustomResponse<?> getExamTypeGradingCriteria() {
        CustomResponse<List<ExamTypeGradingResponseDto>> response = new CustomResponse<>();
        try {
            List<ExamTypeGrading> examTypeGradings = examTypeGradingRepository.findAll();
            List<ExamTypeGradingResponseDto> responseDtos = getExamTypeGradingResponseDtos(examTypeGradings);

            response.setMessage("Exam grading criteria retrieved successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(responseDtos);
        } catch (Exception e) {
            response.setMessage("Error occurred while creating grading criteria.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    public CustomResponse<?> updateSubjectDetails(Long id, SubjectStudentResponse updateRequest) {
        CustomResponse<StudentsScore> response = new CustomResponse<>();
        try {
            Optional<StudentsScore> optionalStudentsScore = studentsScoreRepository.findById(id);
            if (optionalStudentsScore.isPresent()) {
                StudentsScore studentsScore = optionalStudentsScore.get();

                // Update fields if they are provided in the request
                if (updateRequest.getMarks() != null) {
                    studentsScore.setExamScore(updateRequest.getMarks());
                }
                // if (updateRequest.getSubjectName() != null) {
                // studentsScore.setSubjectName(updateRequest.getSubjectName());
                // }

                // Save the updated entity
                studentsScoreRepository.save(studentsScore);

                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("Subject details updated successfully");
                response.setEntity(studentsScore);
            } else {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student score not found");
            }
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An error occurred: " + e.getMessage());
        }
        return response;
    }

    public CustomResponse<SubjectMarksResponse> getSubjectMarks(Long Id) {
        CustomResponse<SubjectMarksResponse> response = new CustomResponse<>();

        try {
            Optional<StudentsScore> optionalStudentsScore = studentsScoreRepository.findById(Id);

            if (optionalStudentsScore.isPresent()) {
                SubjectMarksResponse subjectMarksResponse = getSubjectMarksResponse(optionalStudentsScore);

                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("Subject marks fetched successfully.");
                response.setEntity(subjectMarksResponse);
            } else {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student score not found.");
                response.setEntity(null);
            }
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error occurred while fetching subject marks.");
            response.setEntity(null);
        }

        return response;

    }

    @NotNull
    private static SubjectMarksResponse getSubjectMarksResponse(Optional<StudentsScore> optionalStudentsScore) {
        StudentsScore studentsScore = optionalStudentsScore.get();

        // Mapping entity to DTO
        SubjectMarksResponse subjectMarksResponse = new SubjectMarksResponse();
        subjectMarksResponse.setFirstName(studentsScore.getStudent().getFirstName());
        subjectMarksResponse.setLastName(studentsScore.getStudent().getLastName());
        subjectMarksResponse.setSubjectName(studentsScore.getAcademicSubject().getSubjectName());
        subjectMarksResponse.setMarks(studentsScore.getExamScore());
        subjectMarksResponse.setGradeName(studentsScore.getGrade().getName());
        return subjectMarksResponse;
    }

    public CustomResponse<List<SubjectResponse>> getSubjectsByTerm() {
        CustomResponse<List<SubjectResponse>> res = new CustomResponse<>();
        try {
            List<SubjectResponse> subjectResponses = studentsScoreRepository.findAll().stream()
                    .map(score -> {
                        SubjectResponse response = new SubjectResponse();
                        response.setId(score.getId());
                        response.setDescription(score.getGrade().getName() + " " + score.getYear() + " "
                                + score.getTerm().name() + " - " + score.getAcademicSubject().getSubjectName());
                        return response;
                    })
                    .toList();

            res.setStatusCode(HttpStatus.OK.value());
            res.setMessage("Subjects retrieved successfully");
            res.setEntity(subjectResponses);

        } catch (Exception e) {
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("Failed to retrieve subjects: " + e.getMessage());
            res.setEntity(null);
        }

        return res;
    }

    @NotNull
    private static List<ExamTypeGradingResponseDto> getExamTypeGradingResponseDtos(
            List<ExamTypeGrading> examTypeGradings) {
        List<ExamTypeGradingResponseDto> responseDtos = new ArrayList<>();

        for (ExamTypeGrading examTypeGrading : examTypeGradings) {
            ExamTypeGradingResponseDto responseDto = new ExamTypeGradingResponseDto();
            responseDto.setId(examTypeGrading.getId());
            responseDto.setMin(examTypeGrading.getStart());
            responseDto.setMax(examTypeGrading.getEnd());
            responseDto.setPoint(examTypeGrading.getPoint());
            responseDto.setGrading(examTypeGrading.getRemarks());

            responseDtos.add(responseDto);
        }
        return responseDtos;
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
