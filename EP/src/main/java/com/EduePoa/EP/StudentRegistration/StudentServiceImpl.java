package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureGroupedResponseDTO;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureResponseDTO;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.*;
import com.EduePoa.EP.StudentRegistration.Request.*;
import com.EduePoa.EP.Parents.Parent;
import com.EduePoa.EP.Parents.ParentRepository;
import com.EduePoa.EP.Parents.Request.ParentInfoDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentResponseDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentsPerGradeDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements StudentService {
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final AuditService auditService;
    private final ParentRepository parentRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final StudentNemisRepository studentNemisRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Audit(module = "STUDENT MANAGEMENT", action = "CREATE")
    @Transactional
    public CustomResponse<?> captureNewStudent(CreateStudentRequestDTO request) {
        CustomResponse<StudentResponseDTO> response = new CustomResponse<>();
        try {
            if (request == null || request.getStudent() == null) {
                throw new RuntimeException("Student data cannot be null");
            }
            StudentInfoDTO dto = request.getStudent();

            // Required field validations
            if (dto.getAdmissionNumber() == null || dto.getAdmissionNumber().trim().isEmpty())
                throw new RuntimeException("Admission number is required");
            if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty())
                throw new RuntimeException("First name is required");
            if (dto.getLastName() == null || dto.getLastName().trim().isEmpty())
                throw new RuntimeException("Last name is required");
            if (dto.getDateOfBirth() == null)
                throw new RuntimeException("Date of birth is required");
            if (dto.getAdmissionDate() == null)
                throw new RuntimeException("Admission date is required");
            if (dto.getGradeId() == null)
                throw new RuntimeException("Grade ID is required");

            // Duplicate admission number check
            if (studentRepository.existsByAdmissionNumber(dto.getAdmissionNumber().trim()))
                throw new RuntimeException("Student with admission number " + dto.getAdmissionNumber() + " already exists");

            Grade grade = gradeRepository.findById(dto.getGradeId())
                    .orElseThrow(() -> new RuntimeException("Grade not found with ID: " + dto.getGradeId()));

            FeeStructure feeStructure = feeStructureRepository.findByGrade(grade)
                    .orElseThrow(() -> new RuntimeException("Fee structure not found for grade: " + grade.getName()));

            Student student = new Student();
            student.setAdmissionNumber(dto.getAdmissionNumber().trim());
            student.setFirstName(dto.getFirstName().trim());
            student.setMiddleName(dto.getMiddleName());
            student.setLastName(dto.getLastName().trim());
            student.setDateOfBirth(dto.getDateOfBirth());
            student.setAdmissionDate(dto.getAdmissionDate());
            student.setGrade(grade);
            student.setGradeName(grade.getName());
            student.setStreamName(dto.getStreamName());
            student.setGender(dto.getGender());
            student.setBoardingStatus(dto.getBoardingStatus());
            student.setRouteName(dto.getRouteName());
            student.setResidentialAddress(dto.getResidentialAddress());
            student.setMedicalNotes(dto.getMedicalNotes());
            student.setSpecialNeedsFlag(Boolean.TRUE.equals(dto.getSpecialNeedsFlag()));
            student.setSpecialNeedsNotes(dto.getSpecialNeedsNotes());
            student.setPreviousSchoolName(dto.getPreviousSchoolName());
            student.setPreviousSchoolNemisCode(dto.getPreviousSchoolNemisCode());
            student.setBirthCertificateNumber(dto.getBirthCertificateNumber());
            student.setNationality(dto.getNationality());
            student.setFeeStructure(feeStructure);
            student.setStatus(Status.ACTIVE);
            student.setIs_lockedFlag('N');
            student.setOnLastGrade('N');
            student.setAcademicYear(Year.of(LocalDate.now().getYear()));
            if (dto.getStudentImage() != null && !dto.getStudentImage().trim().isEmpty())
                student.setStudentImage(dto.getStudentImage());

            Student savedStudent = studentRepository.save(student);

            if (request.getGuardians() != null) {
                for (GuardianDTO guardianDTO : request.getGuardians()) {
                    Parent parent;

                    if (guardianDTO.getParentId() != null) {
                        // Link existing parent
                        parent = parentRepository.findById(guardianDTO.getParentId())
                                .orElseThrow(() -> new RuntimeException(
                                        "Parent not found with ID: " + guardianDTO.getParentId()));
                    } else if (guardianDTO.getParent() != null) {
                        // Create new parent
                        ParentInfoDTO pDto = guardianDTO.getParent();

                        // Check for email duplicate
                        if (pDto.getEmail() != null && parentRepository.existsByEmail(pDto.getEmail()))
                            throw new RuntimeException("A parent with email " + pDto.getEmail() + " already exists");

                        parent = new Parent();
                        parent.setFirstName(pDto.getFirstName());
                        parent.setLastName(pDto.getLastName());
                        parent.setOtherNames(pDto.getOtherNames());
                        parent.setPhoneNumber(pDto.getPhoneNumber());
                        parent.setAlternatePhoneNumber(pDto.getAlternatePhoneNumber());
                        parent.setEmail(pDto.getEmail());
                        parent.setNationalIdOrPassport(pDto.getNationalIdOrPassport());
                        parent.setOccupation(pDto.getOccupation());
                        parent.setAddress(pDto.getAddress());
                        boolean hasEmail = pDto.getEmail() != null && !pDto.getEmail().trim().isEmpty();
                        parent.setPortalAccessEnabled(pDto.isPortalAccessEnabled() || hasEmail);
                        parent.setReceiveSms(pDto.isReceiveSms());
                        parent.setReceiveEmail(pDto.isReceiveEmail());

                        // Create a linked User account if portal access is enabled or email is provided
                        if (pDto.getEmail() != null && !pDto.getEmail().trim().isEmpty()) {
                            // Check if user with that email already exists
                            if (!userRepository.existsByEmail(pDto.getEmail())) {
                                Role parentRole = roleRepository.findByName("ROLE_PARENT")
                                        .orElseThrow(() -> new RuntimeException(
                                                "PARENT role not found. Please create it in the database."));

                                User user = new User();
                                user.setFirstName(pDto.getFirstName());
                                user.setLastName(pDto.getLastName());
                                user.setEmail(pDto.getEmail());
                                user.setPhoneNumber(pDto.getPhoneNumber());
                                user.setPassword(passwordEncoder.encode("1234"));
                                user.setForcePasswordReset(true);
                                user.setRole(parentRole);
                                user.setStatus(Status.ACTIVE);
                                user.setEnabledFlag('Y');
                                user.setDeletedFlag('N');
                                user.setIs_lockedFlag('N');

                                User savedUser = userRepository.save(user);
                                parent.setUser(savedUser);
                            } else {
                                // Link existing user account
                                userRepository.findByEmail(pDto.getEmail())
                                        .ifPresent(parent::setUser);
                            }
                        }

                        parent = parentRepository.save(parent);
                    } else {
                        throw new RuntimeException("Guardian entry must have either parentId or a parent object");
                    }

                    // Create the guardian link
                    StudentGuardian guardian = getStudentGuardian(guardianDTO, savedStudent, parent);
                    studentGuardianRepository.save(guardian);
                }
            }


            if (request.getNemis() != null) {
                NemisDTO nemisDTO = request.getNemis();
                StudentNemis nemis = new StudentNemis();
                nemis.setStudent(savedStudent);
                nemis.setUpi(nemisDTO.getUpi());
                nemis.setRegistrationIdentifierType(nemisDTO.getRegistrationIdentifierType());
                nemis.setRegistrationIdentifierValue(nemisDTO.getRegistrationIdentifierValue());
                nemis.setQueueSync(nemisDTO.isQueueSync());
                studentNemisRepository.save(nemis);
            }

            StudentResponseDTO responseDTO = getStudentResponseDTO(savedStudent);
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setEntity(responseDTO);
            response.setMessage("Student created successfully");

            log.info("Student created successfully with admission number: {}", savedStudent.getAdmissionNumber());
            auditService.log("STUDENT_MANAGEMENT", "Created student:", savedStudent.getFirstName(),
                    savedStudent.getLastName(), "with admission number:", savedStudent.getAdmissionNumber());

        } catch (DataIntegrityViolationException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Data integrity violation while creating student: {}", e.getMessage());
            response.setStatusCode(HttpStatus.CONFLICT.value());
            response.setEntity(null);
            response.setMessage("Student with this admission number already exists");
        } catch (RuntimeException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Runtime exception while creating student: {}", e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Unexpected error while creating student: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("An unexpected error occurred while creating student");
        }
        return response;
    }

    @NotNull
    private static StudentGuardian getStudentGuardian(GuardianDTO guardianDTO, Student savedStudent, Parent parent) {
        StudentGuardian guardian = new StudentGuardian();
        guardian.setStudent(savedStudent);
        guardian.setParent(parent);
        guardian.setRelationship(guardianDTO.getRelationship());
        guardian.setPrimaryContact(guardianDTO.isPrimaryContact());
        guardian.setFeePayer(guardianDTO.isFeePayer());
        guardian.setFeeResponsibilityPercent(guardianDTO.getFeeResponsibilityPercent());
        guardian.setPickupAuthorized(guardianDTO.isPickupAuthorized());
        return guardian;
    }

    @Override
    @Audit(module = "STUDENT MANAGEMENT", action = "GET_ALL")
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
            auditService.log("STUDENT_MANAGEMENT", "Retrieved", String.valueOf(studentDTOs.size()), "students");

        } catch (Exception e) {
            log.error("Error retrieving all students: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Failed to retrieve students");
        }
        return response;
    }

    @Override
    @Audit(module = "STUDENT MANAGEMENT", action = "GET_BY_ID")
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
            auditService.log("STUDENT_MANAGEMENT", "Retrieved student with ID:", String.valueOf(studentId));

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

    @Override
    public CustomResponse<?> getFeeStructurePerStudent(Long studentId) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            FeeStructure feeStructure = getActiveFeeStructureForStudent(studentId);
            FeeStructureResponseDTO feeStructureResponse = convertToResponseDTO(feeStructure);

            response.setEntity(feeStructureResponse);
            response.setMessage("Fee structure retrieved successfully for student");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (IllegalArgumentException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
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

    @Override
    @Audit(module = "STUDENT MANAGEMENT", action = "BULK_UPLOAD")
    public CustomResponse<?> bulkUploads(MultipartFile file) {
        CustomResponse<BulkUploadResponseDTO> response = new CustomResponse<>();
        BulkUploadResponseDTO uploadResponse = new BulkUploadResponseDTO();

        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("File cannot be empty");
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                throw new RuntimeException("Invalid file name");
            }

            List<CreateStudentRequestDTO> records = new ArrayList<>();

            // Parse file based on extension
            if (filename.endsWith(".csv")) {
                records = parseBulkCSV(file);
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                records = parseBulkExcel(file);
            } else {
                throw new RuntimeException("Unsupported file format. Please upload CSV or Excel file");
            }

            uploadResponse.setTotalRecords(records.size());

            // Process each student (with parent) using the existing captureNewStudent flow
            for (int i = 0; i < records.size(); i++) {
                CreateStudentRequestDTO record = records.get(i);
                int rowNumber = i + 2; // +2 because row 1 is header and arrays are 0-indexed
                String admissionNumber = record.getStudent() != null ? record.getStudent().getAdmissionNumber() : "ROW_" + rowNumber;

                try {
                    // Reuse captureNewStudent which handles student + guardian + parent creation
                    CustomResponse<?> studentResponse = captureNewStudent(record);

                    if (studentResponse.getStatusCode() == HttpStatus.CREATED.value()) {
                        uploadResponse.setSuccessCount(uploadResponse.getSuccessCount() + 1);
                        uploadResponse.getSuccessfulStudents().add((StudentResponseDTO) studentResponse.getEntity());
                    } else {
                        uploadResponse.setFailureCount(uploadResponse.getFailureCount() + 1);
                        uploadResponse.getErrors().add(new BulkUploadError(
                                rowNumber, admissionNumber, studentResponse.getMessage()));
                    }
                } catch (Exception e) {
                    uploadResponse.setFailureCount(uploadResponse.getFailureCount() + 1);
                    uploadResponse.getErrors().add(new BulkUploadError(
                            rowNumber, admissionNumber, e.getMessage()));
                    log.error("Error processing student at row {}: {}", rowNumber, e.getMessage());
                }
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(uploadResponse);
            response.setMessage(String.format("Bulk upload completed. Success: %d, Failed: %d",
                    uploadResponse.getSuccessCount(), uploadResponse.getFailureCount()));

            log.info("Bulk upload completed. Total: {}, Success: {}, Failed: {}",
                    uploadResponse.getTotalRecords(),
                    uploadResponse.getSuccessCount(),
                    uploadResponse.getFailureCount());
            auditService.log("STUDENT_MANAGEMENT", "Bulk upload completed. Success:",
                    String.valueOf(uploadResponse.getSuccessCount()), "Failed:",
                    String.valueOf(uploadResponse.getFailureCount()));

        } catch (RuntimeException e) {
            log.error("Runtime exception during bulk upload: {}", e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during bulk upload: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("An unexpected error occurred during bulk upload");
        }
        return response;
    }

    private List<CreateStudentRequestDTO> parseBulkCSV(MultipartFile file) throws Exception {
        List<CreateStudentRequestDTO> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            for (CSVRecord record : csvParser) {
                CreateStudentRequestDTO dto = buildFromCsvRecord(record);
                // Skip completely empty rows
                if (dto.getStudent().getAdmissionNumber() == null ||
                        dto.getStudent().getAdmissionNumber().isBlank()) continue;
                records.add(dto);
            }
        }
        return records;
    }

    private CreateStudentRequestDTO buildFromCsvRecord(CSVRecord record) {
        StudentInfoDTO student = new StudentInfoDTO();
        student.setAdmissionNumber(safeGet(record, "admissionNumber"));
        student.setFirstName(safeGet(record, "firstName"));
        student.setMiddleName(safeGet(record, "middleName"));
        student.setLastName(safeGet(record, "lastName"));
        student.setGender(safeGet(record, "gender"));
        student.setStreamName(safeGet(record, "streamName"));
        student.setNationality(safeGet(record, "nationality"));
        student.setBirthCertificateNumber(safeGet(record, "birthCertificateNumber"));
        String dobStr = safeGet(record, "dateOfBirth");
        if (dobStr != null && !dobStr.isBlank()) student.setDateOfBirth(LocalDate.parse(dobStr));
        String admDateStr = safeGet(record, "admissionDate");
        if (admDateStr != null && !admDateStr.isBlank()) student.setAdmissionDate(LocalDate.parse(admDateStr));
        String gradeIdStr = safeGet(record, "gradeId");
        if (gradeIdStr != null && !gradeIdStr.isBlank()) student.setGradeId(Long.parseLong(gradeIdStr.trim()));

        String parentFirstName = safeGet(record, "parentFirstName");
        List<GuardianDTO> guardians = new ArrayList<>();
        if (parentFirstName != null && !parentFirstName.isBlank()) {
            ParentInfoDTO parent = new ParentInfoDTO();
            parent.setFirstName(parentFirstName);
            parent.setLastName(safeGet(record, "parentLastName"));
            parent.setPhoneNumber(safeGet(record, "parentPhone"));
            parent.setEmail(safeGet(record, "parentEmail"));
            parent.setNationalIdOrPassport(safeGet(record, "parentNationalId"));
            parent.setOccupation(safeGet(record, "parentOccupation"));

            String relStr = safeGet(record, "relationship");
            GuardianRelationship rel = parseRelationship(relStr);

            GuardianDTO guardian = new GuardianDTO();
            guardian.setParent(parent);
            guardian.setRelationship(rel);
            guardian.setPrimaryContact(true);
            guardian.setFeePayer(true);
            guardians.add(guardian);
        }

        CreateStudentRequestDTO dto = new CreateStudentRequestDTO();
        dto.setStudent(student);
        dto.setGuardians(guardians);
        return dto;
    }

    private String safeGet(CSVRecord record, String key) {
        try {
            String val = record.get(key);
            return (val == null || val.isBlank()) ? null : val.trim();
        } catch (IllegalArgumentException e) {
            return null; // column not present in file
        }
    }

    private List<CreateStudentRequestDTO> parseBulkExcel(MultipartFile file) throws Exception {
        List<CreateStudentRequestDTO> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            // Use first sheet that is named "Students" or just the first sheet
            Sheet sheet = null;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                if ("Students".equalsIgnoreCase(workbook.getSheetName(s))) {
                    sheet = workbook.getSheetAt(s);
                    break;
                }
            }
            if (sheet == null) sheet = workbook.getSheetAt(0);

            // Build header -> column index map (case-insensitive, space removed)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new RuntimeException("Excel file has no header row");
            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String key = cell.getStringCellValue().toLowerCase().replace(" ", "").trim();
                colMap.put(key, cell.getColumnIndex());
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Skip fully blank rows
                String admNumber = getCellValueAsString(row.getCell(colMap.getOrDefault("admissionnumber", -1)));
                if (admNumber == null || admNumber.isBlank()) continue;

                CreateStudentRequestDTO dto = buildFromExcelRow(row, colMap);
                records.add(dto);
            }
        }
        return records;
    }

    private CreateStudentRequestDTO buildFromExcelRow(Row row, Map<String, Integer> colMap) {
        StudentInfoDTO student = new StudentInfoDTO();
        student.setAdmissionNumber(excelStr(row, colMap, "admissionnumber"));
        student.setFirstName(excelStr(row, colMap, "firstname"));
        student.setMiddleName(excelStr(row, colMap, "middlename"));
        student.setLastName(excelStr(row, colMap, "lastname"));
        student.setGender(excelStr(row, colMap, "gender"));
        student.setStreamName(excelStr(row, colMap, "streamname"));
        student.setNationality(excelStr(row, colMap, "nationality"));
        student.setBirthCertificateNumber(excelStr(row, colMap, "birthcertificatenumber"));
        student.setDateOfBirth(getCellValueAsDate(row.getCell(colMap.getOrDefault("dateofbirth", -1))));
        LocalDate admDate = getCellValueAsDate(row.getCell(colMap.getOrDefault("admissiondate", -1)));
        student.setAdmissionDate(admDate);
        student.setGradeId(getCellValueAsLong(row.getCell(colMap.getOrDefault("gradeid", -1))));

        String img = excelStr(row, colMap, "studentimage");
        if (img != null && !img.isBlank()) student.setStudentImage(img);

        String parentFirstName = excelStr(row, colMap, "parentfirstname");
        List<GuardianDTO> guardians = new ArrayList<>();
        if (parentFirstName != null && !parentFirstName.isBlank()) {
            ParentInfoDTO parent = new ParentInfoDTO();
            parent.setFirstName(parentFirstName);
            parent.setLastName(excelStr(row, colMap, "parentlastname"));
            parent.setPhoneNumber(excelStr(row, colMap, "parentphone"));
            parent.setEmail(excelStr(row, colMap, "parentemail"));
            parent.setNationalIdOrPassport(excelStr(row, colMap, "parentnationalid"));
            parent.setOccupation(excelStr(row, colMap, "parentoccupation"));
            parent.setReceiveSms(true);

            String relStr = excelStr(row, colMap, "relationship");
            GuardianRelationship rel = parseRelationship(relStr);

            GuardianDTO guardian = new GuardianDTO();
            guardian.setParent(parent);
            guardian.setRelationship(rel);
            guardian.setPrimaryContact(true);
            guardian.setFeePayer(true);
            guardians.add(guardian);
        }

        CreateStudentRequestDTO dto = new CreateStudentRequestDTO();
        dto.setStudent(student);
        dto.setGuardians(guardians);
        return dto;
    }

    private String excelStr(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        if (idx == null || idx < 0) return null;
        return getCellValueAsString(row.getCell(idx));
    }

    private GuardianRelationship parseRelationship(String value) {
        if (value == null || value.isBlank()) return GuardianRelationship.GUARDIAN;
        try {
            return GuardianRelationship.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return GuardianRelationship.GUARDIAN;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null)
            return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        } else if (cell.getCellType() == CellType.STRING) {
            return LocalDate.parse(cell.getStringCellValue().trim());
        }
        return null;
    }

    private Long getCellValueAsLong(Cell cell) {
        if (cell == null)
            return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            return Long.parseLong(cell.getStringCellValue().trim());
        }
        return null;
    }

    @Override
    public ResponseEntity<Resource> generateBulkUploadTemplate(String fileType) {
        try {
            if ("csv".equalsIgnoreCase(fileType)) {
                return generateCSVTemplate();
            } else if ("excel".equalsIgnoreCase(fileType) || "xlsx".equalsIgnoreCase(fileType)) {
                return generateExcelTemplate();
            } else {
                throw new RuntimeException("Unsupported file type. Use 'csv' or 'excel'");
            }
        } catch (Exception e) {
            log.error("Error generating template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate template: " + e.getMessage());
        }
    }

    @Override
    public CustomResponse<?> getPerGrade(Long id) {
        CustomResponse<List<StudentResponseDTO>> response = new CustomResponse<>();
        try {
            List<Student> students = studentRepository.findByGradeId(id);

            if (students.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                response.setMessage("No students found for this grade");
                return response;
            }

            List<StudentResponseDTO> studentDTOs = students.stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(studentDTOs);
            response.setMessage("Students retrieved successfully");

            log.info("Retrieved {} students for grade ID {}", studentDTOs.size(), id);
            auditService.log("STUDENT_MANAGEMENT", "Retrieved", String.valueOf(studentDTOs.size()),
                    "students for grade ID:", String.valueOf(id));

        } catch (Exception e) {
            log.error("Error retrieving students for grade ID {}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Failed to retrieve students per grade");
        }
        return response;
    }

    @Override
    public CustomResponse<?> getFeeStucturePerStudent(Long studentId) {
        CustomResponse<FeeStructureGroupedResponseDTO> response = new CustomResponse<>();
        try {
            FeeStructure feeStructure = getActiveFeeStructureForStudent(studentId);
            FeeStructureGroupedResponseDTO feeStructureResponse = convertToGroupedResponseDTO(feeStructure);

            response.setEntity(feeStructureResponse);
            response.setMessage("Fee structure retrieved successfully for student");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error retrieving fee structure for student: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getStudentGuardians(Long studentId) {
        CustomResponse<List<com.EduePoa.EP.StudentRegistration.Response.StudentGuardianResponseDTO>> response = new CustomResponse<>();
        try {
            studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            List<StudentGuardian> guardians = studentGuardianRepository.findByStudentId(studentId);

            List<com.EduePoa.EP.StudentRegistration.Response.StudentGuardianResponseDTO> result = guardians.stream()
                    .map(g -> {
                        Parent parent = g.getParent();
                        String fullName = buildFullName(parent.getFirstName(), parent.getOtherNames(), parent.getLastName());
                        return com.EduePoa.EP.StudentRegistration.Response.StudentGuardianResponseDTO.builder()
                                .id(g.getId())
                                .studentId(studentId)
                                .parentId(parent.getId())
                                .fullName(fullName)
                                .relationship(g.getRelationship() != null ? g.getRelationship().name() : null)
                                .phoneNumber(parent.getPhoneNumber())
                                .email(parent.getEmail())
                                .nationalIdOrPassport(parent.getNationalIdOrPassport())
                                .isPrimaryContact(g.isPrimaryContact())
                                .isFeePayer(g.isFeePayer())
                                .pickupAuthorized(g.isPickupAuthorized())
                                .feeResponsibilityPercent(g.getFeeResponsibilityPercent())
                                .build();
                    })
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Student guardians fetched successfully");
            response.setEntity(result);

        } catch (RuntimeException e) {
            log.error("Error fetching student guardians for studentId {}: {}", studentId, e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        } catch (Exception e) {
            log.error("Unexpected error fetching student guardians: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching student guardians");
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getNemisStatus(Long studentId) {
        CustomResponse<com.EduePoa.EP.StudentRegistration.Response.NemisStatusResponseDTO> response = new CustomResponse<>();
        try {
            studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            StudentNemis nemis = studentNemisRepository.findByStudentId(studentId)
                    .orElse(null);

            com.EduePoa.EP.StudentRegistration.Response.NemisStatusResponseDTO dto;
            if (nemis == null) {
                dto = com.EduePoa.EP.StudentRegistration.Response.NemisStatusResponseDTO.builder()
                        .studentId(studentId)
                        .syncStatus("NOT_REGISTERED")
                        .build();
            } else {
                String syncStatus = nemis.isQueueSync() ? "QUEUED" : "READY";
                dto = com.EduePoa.EP.StudentRegistration.Response.NemisStatusResponseDTO.builder()
                        .studentId(studentId)
                        .upi(nemis.getUpi())
                        .registrationIdentifierType(nemis.getRegistrationIdentifierType())
                        .registrationIdentifierValue(nemis.getRegistrationIdentifierValue())
                        .syncStatus(syncStatus)
                        .lastSyncedAt(null)
                        .lastSyncAttemptAt(null)
                        .lastSyncError(null)
                        .verifiedAt(null)
                        .build();
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("NEMIS status fetched successfully");
            response.setEntity(dto);

        } catch (RuntimeException e) {
            log.error("Error fetching NEMIS status for studentId {}: {}", studentId, e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        } catch (Exception e) {
            log.error("Unexpected error fetching NEMIS status: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching NEMIS status");
            response.setEntity(null);
        }
        return response;
    }

    private String buildFullName(String firstName, String otherNames, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null) sb.append(firstName.trim());
        if (otherNames != null && !otherNames.trim().isEmpty()) sb.append(" ").append(otherNames.trim());
        if (lastName != null) sb.append(" ").append(lastName.trim());
        return sb.toString().trim();
    }

    private ResponseEntity<Resource> generateCSVTemplate() throws Exception {
        StringBuilder csv = new StringBuilder();

        csv.append("admissionNumber,firstName,middleName,lastName,dateOfBirth,admissionDate,gradeId,");
        csv.append("gender,streamName,nationality,birthCertificateNumber,");
        csv.append("parentFirstName,parentLastName,parentPhone,parentEmail,parentNationalId,parentOccupation,relationship\n");

        csv.append("STU001,John,,Doe,2010-05-15,2024-01-10,1,Male,Form 1A,Kenyan,,");
        csv.append("Mary,Doe,0712345678,mary.doe@email.com,12345678,Teacher,MOTHER\n");

        csv.append("STU002,Jane,,Smith,2011-08-22,2024-01-10,1,Female,Form 1A,Kenyan,,");
        csv.append("Peter,Smith,0723456789,peter.smith@email.com,98765432,Engineer,FATHER\n");

        csv.append("STU003,Michael,,Johnson,2010-12-03,2024-01-10,2,Male,Form 2B,Kenyan,,");
        csv.append("Susan,Johnson,0734567890,,45678901,Nurse,MOTHER\n");

        ByteArrayResource resource = new ByteArrayResource(csv.toString().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_parent_bulk_upload_template.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    private ResponseEntity<Resource> generateExcelTemplate() throws Exception {
        Workbook workbook = new XSSFWorkbook();

        CellStyle studentHeaderStyle = workbook.createCellStyle();
        Font studentHeaderFont = workbook.createFont();
        studentHeaderFont.setBold(true);
        studentHeaderFont.setFontHeightInPoints((short) 11);
        studentHeaderFont.setColor(IndexedColors.WHITE.getIndex());
        studentHeaderStyle.setFont(studentHeaderFont);
        studentHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        studentHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        studentHeaderStyle.setBorderBottom(BorderStyle.THIN);
        studentHeaderStyle.setBorderTop(BorderStyle.THIN);
        studentHeaderStyle.setBorderRight(BorderStyle.THIN);
        studentHeaderStyle.setBorderLeft(BorderStyle.THIN);
        studentHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        studentHeaderStyle.setWrapText(true);

        CellStyle parentHeaderStyle = workbook.createCellStyle();
        Font parentHeaderFont = workbook.createFont();
        parentHeaderFont.setBold(true);
        parentHeaderFont.setFontHeightInPoints((short) 11);
        parentHeaderFont.setColor(IndexedColors.WHITE.getIndex());
        parentHeaderStyle.setFont(parentHeaderFont);
        parentHeaderStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        parentHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        parentHeaderStyle.setBorderBottom(BorderStyle.THIN);
        parentHeaderStyle.setBorderTop(BorderStyle.THIN);
        parentHeaderStyle.setBorderRight(BorderStyle.THIN);
        parentHeaderStyle.setBorderLeft(BorderStyle.THIN);
        parentHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        parentHeaderStyle.setWrapText(true);

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);

        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);

        Sheet sheet = workbook.createSheet("Students");

        String[] headers = {
                // Student columns
                "admissionNumber", "firstName", "middleName", "lastName",
                "dateOfBirth", "admissionDate", "gradeId", "gender",
                "streamName", "nationality", "birthCertificateNumber",
                // Parent / Guardian columns
                "parentFirstName", "parentLastName", "parentPhone",
                "parentEmail", "parentNationalId", "parentOccupation", "relationship"
        };

        int parentColStart = 11;
        int genderColIdx  = 7;
        int relColIdx     = 17;

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(i < parentColStart ? studentHeaderStyle : parentHeaderStyle);
            sheet.setColumnWidth(i, 22 * 256);
        }
        sheet.getRow(0).setHeight((short) 600);

        Object[][] sampleData = {
            {"STU001","John","","Doe",LocalDate.of(2010,5,15),LocalDate.of(2024,1,10),1L,"Male","Form 1A","Kenyan","",
             "Mary","Doe","0712345678","mary.doe@email.com","12345678","Teacher","MOTHER"},
            {"STU002","Jane","","Smith",LocalDate.of(2011,8,22),LocalDate.of(2024,1,10),1L,"Female","Form 1A","Kenyan","",
             "Peter","Smith","0723456789","peter.smith@email.com","98765432","Engineer","FATHER"},
            {"STU003","Michael","","Johnson",LocalDate.of(2010,12,3),LocalDate.of(2024,1,10),2L,"Male","Form 2B","Kenyan","",
             "Susan","Johnson","0734567890","","45678901","Nurse","MOTHER"}
        };

        for (int i = 0; i < sampleData.length; i++) {
            Row row = sheet.createRow(i + 1);
            Object[] cols = sampleData[i];
            for (int j = 0; j < cols.length; j++) {
                Cell cell = row.createCell(j);
                Object val = cols[j];
                if (val instanceof LocalDate) {
                    cell.setCellValue((LocalDate) val);
                    cell.setCellStyle(dateStyle);
                } else if (val instanceof Long) {
                    cell.setCellValue((Long) val);
                    cell.setCellStyle(dataStyle);
                } else {
                    cell.setCellValue(val == null ? "" : val.toString());
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        DataValidationConstraint genderConstraint = dvHelper.createExplicitListConstraint(new String[]{"Male","Female"});
        CellRangeAddressList genderRange = new CellRangeAddressList(1, 1000, genderColIdx, genderColIdx);
        DataValidation genderValidation = dvHelper.createValidation(genderConstraint, genderRange);
        genderValidation.setShowErrorBox(true);
        genderValidation.createErrorBox("Invalid Gender", "Please select Male or Female");
        sheet.addValidationData(genderValidation);

        DataValidationConstraint relConstraint = dvHelper.createExplicitListConstraint(
                new String[]{"MOTHER","FATHER","GUARDIAN","SIBLING","OTHER"});
        CellRangeAddressList relRange = new CellRangeAddressList(1, 1000, relColIdx, relColIdx);
        DataValidation relValidation = dvHelper.createValidation(relConstraint, relRange);
        relValidation.setShowErrorBox(true);
        relValidation.createErrorBox("Invalid Relationship", "Select: MOTHER, FATHER, GUARDIAN, SIBLING, or OTHER");
        sheet.addValidationData(relValidation);

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));

        Sheet instrSheet = workbook.createSheet("Instructions");
        CellStyle instrStyle = workbook.createCellStyle();
        Font instrFont = workbook.createFont();
        instrFont.setItalic(true);
        instrStyle.setFont(instrFont);

        String[] instructions = {
            "STUDENT + PARENT BULK UPLOAD TEMPLATE",
            "",
            "═══ STUDENT COLUMNS (Blue headers) ═══",
            "admissionNumber     - Unique student ID e.g. STU001 [REQUIRED]",
            "firstName           - Student first name [REQUIRED]",
            "middleName          - Middle name [OPTIONAL]",
            "lastName            - Student last name [REQUIRED]",
            "dateOfBirth         - Format YYYY-MM-DD e.g. 2010-05-15 [REQUIRED]",
            "admissionDate       - Format YYYY-MM-DD e.g. 2024-01-10 [REQUIRED]",
            "gradeId             - Numeric grade/class ID from the system [REQUIRED]",
            "gender              - Male or Female (dropdown) [REQUIRED]",
            "streamName          - Stream/section e.g. Form 1A [OPTIONAL]",
            "nationality         - e.g. Kenyan [OPTIONAL]",
            "birthCertificateNumber - Birth certificate no. [OPTIONAL]",
            "",
            "═══ PARENT / GUARDIAN COLUMNS (Green headers) ═══",
            "parentFirstName     - Parent/guardian first name [OPTIONAL]",
            "parentLastName      - Parent/guardian last name [OPTIONAL]",
            "parentPhone         - Phone number e.g. 0712345678 [OPTIONAL]",
            "parentEmail         - Email address (used for portal login) [OPTIONAL]",
            "parentNationalId    - National ID or Passport number [OPTIONAL]",
            "parentOccupation    - Occupation [OPTIONAL]",
            "relationship        - MOTHER / FATHER / GUARDIAN / SIBLING / OTHER (dropdown) [OPTIONAL]",
            "",
            "NOTES:",
            "- Rows with a blank admissionNumber are skipped.",
            "- If parentEmail is provided, a portal account is created (default password: 1234).",
            "- Replace sample rows with your actual data before uploading.",
            "- Maximum file size: 10 MB. Supported formats: .xlsx, .xls, .csv",
        };

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        for (int i = 0; i < instructions.length; i++) {
            Row row = instrSheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(instructions[i]);
            cell.setCellStyle(i == 0 ? titleStyle : instrStyle);
        }
        instrSheet.setColumnWidth(0, 100 * 256);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=student_parent_bulk_upload_template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    private FeeStructureResponseDTO convertToResponseDTO(FeeStructure feeStructure) {
        FeeStructureResponseDTO dto = new FeeStructureResponseDTO();
        dto.setId(feeStructure.getId());
        dto.setGrade(feeStructure.getGrade() != null ? feeStructure.getGrade().getName() : "Unknown");
        dto.setCreatedOn(feeStructure.getDatePosted());
        dto.setUpdatedOn(feeStructure.getDatePosted());

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

    private FeeStructureGroupedResponseDTO convertToGroupedResponseDTO(FeeStructure feeStructure) {
        FeeStructureGroupedResponseDTO dto = new FeeStructureGroupedResponseDTO();
        dto.setId(feeStructure.getId());
        dto.setGrade(feeStructure.getGrade() != null ? feeStructure.getGrade().getName() : "Unknown");
        dto.setCreatedOn(feeStructure.getDatePosted());
        dto.setUpdatedOn(feeStructure.getDatePosted());
        dto.setTerms(getGroupedTerms(feeStructure));
        return dto;
    }

    private List<FeeStructureGroupedResponseDTO.TermDTO> getGroupedTerms(FeeStructure feeStructure) {
        Map<String, List<FeeStructureGroupedResponseDTO.FeeItemDTO>> groupedByTerm = new LinkedHashMap<>();

        if (feeStructure.getTermComponents() != null) {
            for (FeeComponentConfig config : feeStructure.getTermComponents()) {
                String term = config.getTerm() != null ? config.getTerm() : "Unknown";
                groupedByTerm.computeIfAbsent(term, ignored -> new ArrayList<>())
                        .add(new FeeStructureGroupedResponseDTO.FeeItemDTO(
                                Long.valueOf(config.getId()),
                                config.getName() != null ? config.getName() : "Unknown",
                                config.getAmount() != null ? config.getAmount().doubleValue() : 0.0));
            }
        }

        return groupedByTerm.entrySet().stream()
                .map(entry -> new FeeStructureGroupedResponseDTO.TermDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private FeeStructure getActiveFeeStructureForStudent(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID cannot be null");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

        if (student.getFeeStructure() == null) {
            throw new RuntimeException("No fee structure assigned to student with ID: " + studentId);
        }

        FeeStructure feeStructure = student.getFeeStructure();
        if (feeStructure.getIsDeleted() == 'Y' || feeStructure.getDeleted() == 'Y') {
            throw new RuntimeException("Fee structure assigned to student is no longer active");
        }

        return feeStructure;
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
        // dto.setAcademicYear(student.getAcademicYear());
        dto.setStudentImage(student.getStudentImage());
        // dto.setIsLocked(student.getIs_lockedFlag() == 'Y');
        // dto.setOnLastGrade(student.getOnLastGrade() == 'Y');

        // Add related entity information
        if (student.getGrade() != null) {
            dto.setGradeName(student.getGrade().getName());
        }

        // if (student.getParent() != null) {
        // dto.setParentId(student.getParent().getId());
        // dto.setParentName(student.getParent().getFirstName() + " " +
        // student.getParent().getLastName());
        // }

        // if (student.getFeeStructure() != null) {
        // dto.setFeeStructureId(student.getFeeStructure().getId());
        // }

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
        // responseDTO.setAcademicYear(savedStudent.getAcademicYear());
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
    // private String generateAdmissionNumber() {
    // String prefix = "STD";
    // String year = String.valueOf(LocalDate.now().getYear());
    //
    // // Get the last admission number for this year
    // String lastAdmissionNumber =
    // studentRepository.findLastAdmissionNumberForYear(year);
    //
    // int nextNumber = 1;
    // if (lastAdmissionNumber != null && lastAdmissionNumber.contains(year)) {
    // String numberPart =
    // lastAdmissionNumber.substring(lastAdmissionNumber.lastIndexOf(year) + 4);
    // nextNumber = Integer.parseInt(numberPart) + 1;
    // }
    //
    // return String.format("%s%s%04d", prefix, year, nextNumber);
    // }

}
