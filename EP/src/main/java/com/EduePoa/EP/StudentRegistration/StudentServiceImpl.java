package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureResponseDTO;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentResponseDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentsPerGradeDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements  StudentService{
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final FeeStructureRepository feeStructureRepository;


    @Override

    public CustomResponse<?> captureNewStudent(StudentRequestDTO studentRequestDTO) {
        CustomResponse<StudentResponseDTO> response = new CustomResponse<>();
        try {
            // Validate input
            if (studentRequestDTO == null) {
                throw new RuntimeException("Student data cannot be null");
            }

            // Validate required fields
            if (studentRequestDTO.getAdmissionNumber() == null || studentRequestDTO.getAdmissionNumber().trim().isEmpty()) {
                throw new RuntimeException("Admission number is required");
            }
            if (studentRequestDTO.getFirstName() == null || studentRequestDTO.getFirstName().trim().isEmpty()) {
                throw new RuntimeException("First name is required");
            }
            if (studentRequestDTO.getLastName() == null || studentRequestDTO.getLastName().trim().isEmpty()) {
                throw new RuntimeException("Last name is required");
            }
            if (studentRequestDTO.getDateOfBirth() == null) {
                throw new RuntimeException("Date of birth is required");
            }
            if (studentRequestDTO.getAdmissionDate() == null) {
                throw new RuntimeException("Admission date is required");
            }
            if (studentRequestDTO.getGrade() == null) {
                throw new RuntimeException("Grade is required");
            }

            // Check if admission number already exists
            if (studentRepository.existsByAdmissionNumber(studentRequestDTO.getAdmissionNumber().trim())) {
                throw new RuntimeException("Student with admission number " + studentRequestDTO.getAdmissionNumber() + " already exists");
            }

            // Fetch Grade entity
            Grade grade = gradeRepository.findById(studentRequestDTO.getGrade())
                    .orElseThrow(() -> new RuntimeException("Grade not found with ID: " + studentRequestDTO.getGrade()));

            // Get FeeStructure by grade (assuming you have a method to get fee structure by grade)
            FeeStructure feeStructure = feeStructureRepository.findByGrade(grade)
                    .orElseThrow(() -> new RuntimeException("Fee structure not found for grade: " + grade.getName()));

            // Create new Student entity
            Student student = new Student();
            student.setAdmissionNumber(studentRequestDTO.getAdmissionNumber().trim());
            student.setFirstName(studentRequestDTO.getFirstName().trim());
            student.setLastName(studentRequestDTO.getLastName().trim());
            student.setDateOfBirth(studentRequestDTO.getDateOfBirth());
            student.setAdmissionDate(LocalDate.parse(studentRequestDTO.getAdmissionDate()));
            student.setGradeName(grade.getName());
            student.setGrade(grade);
            student.setGender(studentRequestDTO.getGender());
            student.setFeeStructure(feeStructure);
            student.setStatus(Status.ACTIVE);
            student.setIs_lockedFlag('N');
            student.setOnLastGrade('N');
            student.setAcademicYear(Year.of(LocalDate.now().getYear())); // Current academic year

            // Handle student image if provided
            if (studentRequestDTO.getStudentImage() != null && !studentRequestDTO.getStudentImage().trim().isEmpty()) {
                student.setStudentImage(studentRequestDTO.getStudentImage());
            }

            // Handle parent assignment if parent ID is provided
//            if (studentRequestDTO.getParentId() != null) {
//                User parent = userRepository.findById(studentRequestDTO.getParentId())
//                        .orElseThrow(() -> new RuntimeException("Parent not found with ID: " + studentRequestDTO.getParentId()));
//                student.setParent(parent);
//            }

            // Save the student
            Student savedStudent = studentRepository.save(student);

            // Create response DTO
            StudentResponseDTO responseDTO = getStudentResponseDTO(savedStudent);

            // Set success response
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setEntity(responseDTO);
            response.setMessage("Student created successfully");

            // Log the successful creation
            log.info("Student created successfully with admission number: {}", savedStudent.getAdmissionNumber());

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while creating student: {}", e.getMessage());
            response.setStatusCode(HttpStatus.CONFLICT.value());
            response.setEntity(null);
            response.setMessage("Student with this admission number already exists");
        } catch (RuntimeException e) {
            log.error("Runtime exception while creating student: {}", e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while creating student: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("An unexpected error occurred while creating student");
        }
        return response;
    }
    @Override
    public CustomResponse<?> getAllStudents() {
        CustomResponse<List<StudentResponseDTO>> response = new CustomResponse<>();
        try {
            List<Student> students = studentRepository.findAll();

            List<StudentResponseDTO> studentDTOs = students.stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(studentDTOs);
            response.setMessage("Students retrieved successfully");

            log.info("Retrieved {} students", studentDTOs.size());

        } catch (Exception e) {
            log.error("Error retrieving all students: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Failed to retrieve students");
        }
        return response;
    }
    @Override
    public CustomResponse<StudentResponseDTO> getStudentById(Long studentId) {
        CustomResponse<StudentResponseDTO> response = new CustomResponse<>();
        try {
            if (studentId == null) {
                throw new RuntimeException("Student ID cannot be null");
            }

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            StudentResponseDTO studentDTO = convertToResponseDTO(student);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(studentDTO);
            response.setMessage("Student retrieved successfully");

            log.info("Retrieved student with ID: {}", studentId);

        } catch (RuntimeException e) {
            log.error("Runtime error retrieving student by ID {}: {}", studentId, e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving student by ID {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Failed to retrieve student");
        }
        return response;
    }

    // Simple implementation - returns only the fee structure
    @Override
    public CustomResponse<?> getFeeStructurePerStudent(Long studentId) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            // Validate student ID
            if (studentId == null) {
                response.setEntity(null);
                response.setMessage("Student ID cannot be null");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Find the student by ID
            Optional<Student> studentOptional = studentRepository.findById(studentId);
            if (studentOptional.isEmpty()) {
                response.setEntity(null);
                response.setMessage("Student not found with ID: " + studentId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            Student student = studentOptional.get();

            // Check if student has an assigned fee structure
            if (student.getFeeStructure() == null) {
                response.setEntity(null);
                response.setMessage("No fee structure assigned to student with ID: " + studentId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            FeeStructure feeStructure = student.getFeeStructure();

            // Check if fee structure is active (not deleted)
            if (feeStructure.getIsDeleted() == 'Y' || feeStructure.getDeleted() == 'Y') {
                response.setEntity(null);
                response.setMessage("Fee structure assigned to student is no longer active");
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            // Convert fee structure to response DTO using your existing method
            FeeStructureResponseDTO feeStructureResponse = convertToResponseDTO(feeStructure);

            response.setEntity(feeStructureResponse);
            response.setMessage("Fee structure retrieved successfully for student");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setEntity(null);
            response.setMessage("Error retrieving fee structure for student: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> totalNumberStudents() {
        CustomResponse<Long> response = new CustomResponse<>();
        try {

            Long studentCount = studentRepository.count();
            response.setEntity(studentCount);
            response.setMessage("Students Retrived Successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> studentsPerGrade() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {

            List<StudentsPerGradeDTO> data = studentRepository.countStudentsPerGrade();

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(data);
            response.setMessage("Students per grade retrieved successfully");

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }


    private FeeStructureResponseDTO convertToResponseDTO(FeeStructure feeStructure) {
        FeeStructureResponseDTO dto = new FeeStructureResponseDTO();
        dto.setId(feeStructure.getId());
        dto.setGrade(feeStructure.getGrade() != null ? feeStructure.getGrade().getName() : "Unknown");
        dto.setCreatedOn(feeStructure.getDatePosted());
        dto.setUpdatedOn(feeStructure.getDatePosted());

        // Convert fee components
        List<FeeStructureResponseDTO.FeeItemDTO> feeItems = getFeeItemDTOS(feeStructure);
        dto.setFeeItems(feeItems);

        return dto;
    }

    private static List<FeeStructureResponseDTO.FeeItemDTO> getFeeItemDTOS(FeeStructure feeStructure) {
        List<FeeStructureResponseDTO.FeeItemDTO> feeItems = new ArrayList<>();
        if (feeStructure.getTermComponents() != null) {
            for (FeeComponentConfig config : feeStructure.getTermComponents()) {
                FeeStructureResponseDTO.FeeItemDTO item = new FeeStructureResponseDTO.FeeItemDTO();
                item.setId(Long.valueOf(config.getId()));
                item.setTerm(config.getTerm());

                item.setName(config.getName() != null ? config.getName() : "Unknown");
                item.setAmount(config.getAmount() != null ? config.getAmount().doubleValue() : 0.0);
                feeItems.add(item);
            }
        }
        return feeItems;
    }

    private StudentResponseDTO convertToResponseDTO(Student student) {
        StudentResponseDTO dto = new StudentResponseDTO();
        dto.setId(student.getId());
        dto.setAdmissionNumber(student.getAdmissionNumber());
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setDateOfBirth(student.getDateOfBirth());
        dto.setAdmissionDate(String.valueOf(student.getAdmissionDate()));
        dto.setGradeName(student.getGradeName());
        dto.setStatus(String.valueOf(student.getStatus()));
//        dto.setAcademicYear(student.getAcademicYear());
        dto.setStudentImage(student.getStudentImage());
//        dto.setIsLocked(student.getIs_lockedFlag() == 'Y');
//        dto.setOnLastGrade(student.getOnLastGrade() == 'Y');

        // Add related entity information
        if (student.getGrade() != null) {
            dto.setGradeName(student.getGrade().getName());
        }

//        if (student.getParent() != null) {
//            dto.setParentId(student.getParent().getId());
//            dto.setParentName(student.getParent().getFirstName() + " " + student.getParent().getLastName());
//        }

//        if (student.getFeeStructure() != null) {
//            dto.setFeeStructureId(student.getFeeStructure().getId());
//        }

        return dto;
    }

    private static StudentResponseDTO getStudentResponseDTO(Student savedStudent) {
        StudentResponseDTO responseDTO = new StudentResponseDTO();
        responseDTO.setId(savedStudent.getId());
        responseDTO.setAdmissionNumber(savedStudent.getAdmissionNumber());
        responseDTO.setFirstName(savedStudent.getFirstName());
        responseDTO.setLastName(savedStudent.getLastName());
        responseDTO.setDateOfBirth(savedStudent.getDateOfBirth());
        responseDTO.setAdmissionDate(String.valueOf(savedStudent.getAdmissionDate()));
        responseDTO.setGradeName(savedStudent.getGradeName());
        responseDTO.setStatus(String.valueOf(savedStudent.getStatus()));
//            responseDTO.setAcademicYear(savedStudent.getAcademicYear());
        responseDTO.setStudentImage(savedStudent.getStudentImage());
        return responseDTO;
    }

    // Helper method to validate age based on date of birth
    private void validateStudentAge(LocalDate dateOfBirth) {
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new RuntimeException("Date of birth cannot be in the future");
        }

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 3 || age > 25) {
            throw new RuntimeException("Student age must be between 3 and 25 years");
        }
    }

    // Helper method to generate admission number if not provided
//    private String generateAdmissionNumber() {
//        String prefix = "STD";
//        String year = String.valueOf(LocalDate.now().getYear());
//
//        // Get the last admission number for this year
//        String lastAdmissionNumber = studentRepository.findLastAdmissionNumberForYear(year);
//
//        int nextNumber = 1;
//        if (lastAdmissionNumber != null && lastAdmissionNumber.contains(year)) {
//            String numberPart = lastAdmissionNumber.substring(lastAdmissionNumber.lastIndexOf(year) + 4);
//            nextNumber = Integer.parseInt(numberPart) + 1;
//        }
//
//        return String.format("%s%s%04d", prefix, year, nextNumber);
//    }


}
