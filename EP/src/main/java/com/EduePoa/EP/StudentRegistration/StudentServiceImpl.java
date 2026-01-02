package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureResponseDTO;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Request.BulkUploadError;
import com.EduePoa.EP.StudentRegistration.Request.BulkUploadResponseDTO;
import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentResponseDTO;
import com.EduePoa.EP.StudentRegistration.Response.StudentsPerGradeDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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

    @Override
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

            List<StudentRequestDTO> studentDTOs = new ArrayList<>();

            // Parse file based on extension
            if (filename.endsWith(".csv")) {
                studentDTOs = parseCSV(file);
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                studentDTOs = parseExcel(file);
            } else {
                throw new RuntimeException("Unsupported file format. Please upload CSV or Excel file");
            }

            uploadResponse.setTotalRecords(studentDTOs.size());

            // Process each student
            for (int i = 0; i < studentDTOs.size(); i++) {
                StudentRequestDTO studentDTO = studentDTOs.get(i);
                int rowNumber = i + 2; // +2 because row 1 is header and arrays are 0-indexed

                try {
                    // Use the existing captureNewStudent method
                    CustomResponse<?> studentResponse = captureNewStudent(studentDTO);

                    if (studentResponse.getStatusCode() == HttpStatus.CREATED.value()) {
                        uploadResponse.setSuccessCount(uploadResponse.getSuccessCount() + 1);
                        uploadResponse.getSuccessfulStudents().add((StudentResponseDTO) studentResponse.getEntity());
                    } else {
                        uploadResponse.setFailureCount(uploadResponse.getFailureCount() + 1);
                        uploadResponse.getErrors().add(new BulkUploadError(
                                rowNumber,
                                studentDTO.getAdmissionNumber(),
                                studentResponse.getMessage()
                        ));
                    }
                } catch (Exception e) {
                    uploadResponse.setFailureCount(uploadResponse.getFailureCount() + 1);
                    uploadResponse.getErrors().add(new BulkUploadError(
                            rowNumber,
                            studentDTO.getAdmissionNumber(),
                            e.getMessage()
                    ));
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

    private List<StudentRequestDTO> parseCSV(MultipartFile file) throws Exception {
        List<StudentRequestDTO> students = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            for (CSVRecord record : csvParser) {
                StudentRequestDTO dto = new StudentRequestDTO();
                dto.setAdmissionNumber(record.get("admissionNumber"));
                dto.setFirstName(record.get("firstName"));
                dto.setLastName(record.get("lastName"));
                dto.setDateOfBirth(LocalDate.parse(record.get("dateOfBirth"))); // Expected format: yyyy-MM-dd
                dto.setAdmissionDate(record.get("admissionDate")); // Expected format: yyyy-MM-dd
                dto.setGrade(Long.parseLong(record.get("gradeId")));
                dto.setGender(record.get("gender"));

                // Optional fields
                if (record.isMapped("studentImage") && !record.get("studentImage").isEmpty()) {
                    dto.setStudentImage(record.get("studentImage"));
                }

                students.add(dto);
            }
        }

        return students;
    }

    private List<StudentRequestDTO> parseExcel(MultipartFile file) throws Exception {
        List<StudentRequestDTO> students = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Get header row to map column names
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnMap = new HashMap<>();

            for (Cell cell : headerRow) {
                columnMap.put(cell.getStringCellValue().toLowerCase().trim(), cell.getColumnIndex());
            }

            // Process data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                StudentRequestDTO dto = new StudentRequestDTO();
                dto.setAdmissionNumber(getCellValueAsString(row.getCell(columnMap.get("admissionnumber"))));
                dto.setFirstName(getCellValueAsString(row.getCell(columnMap.get("firstname"))));
                dto.setLastName(getCellValueAsString(row.getCell(columnMap.get("lastname"))));
                dto.setDateOfBirth(getCellValueAsDate(row.getCell(columnMap.get("dateofbirth"))));
                dto.setAdmissionDate(getCellValueAsString(row.getCell(columnMap.get("admissiondate"))));
                dto.setGrade(getCellValueAsLong(row.getCell(columnMap.get("gradeid"))));
                dto.setGender(getCellValueAsString(row.getCell(columnMap.get("gender"))));

                // Optional fields
                if (columnMap.containsKey("studentimage")) {
                    String imageValue = getCellValueAsString(row.getCell(columnMap.get("studentimage")));
                    if (imageValue != null && !imageValue.isEmpty()) {
                        dto.setStudentImage(imageValue);
                    }
                }

                students.add(dto);
            }
        }

        return students;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

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
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        } else if (cell.getCellType() == CellType.STRING) {
            return LocalDate.parse(cell.getStringCellValue().trim());
        }
        return null;
    }

    private Long getCellValueAsLong(Cell cell) {
        if (cell == null) return null;

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

    private ResponseEntity<Resource> generateCSVTemplate() throws Exception {
        StringBuilder csvContent = new StringBuilder();

        // Add headers
        csvContent.append("admissionNumber,firstName,lastName,dateOfBirth,admissionDate,gradeId,gender,studentImage\n");

        // Add sample data rows
        csvContent.append("STU001,John,Doe,2010-05-15,2024-01-10,1,Male,\n");
        csvContent.append("STU002,Jane,Smith,2011-08-22,2024-01-10,1,Female,\n");
        csvContent.append("STU003,Michael,Johnson,2010-12-03,2024-01-10,2,Male,\n");

        // Add instructions as comments (CSV comments start with #)
        csvContent.insert(0, "# Student Bulk Upload Template\n");
        csvContent.insert(0, "# Instructions:\n");
        csvContent.insert(0, "# 1. admissionNumber: Must be unique (e.g., STU001, STU002)\n");
        csvContent.insert(0, "# 2. firstName: Student's first name (required)\n");
        csvContent.insert(0, "# 3. lastName: Student's last name (required)\n");
        csvContent.insert(0, "# 4. dateOfBirth: Format YYYY-MM-DD (required)\n");
        csvContent.insert(0, "# 5. admissionDate: Format YYYY-MM-DD (required)\n");
        csvContent.insert(0, "# 6. gradeId: Numeric ID of the grade (required)\n");
        csvContent.insert(0, "# 7. gender: Male/Female (required)\n");
        csvContent.insert(0, "# 8. studentImage: Base64 encoded image or URL (optional)\n");
        csvContent.insert(0, "# \n");
        csvContent.insert(0, "# Note: Delete these instruction lines before uploading\n");
        csvContent.insert(0, "# Sample data is provided below. Replace with actual student data.\n");
        csvContent.insert(0, "#\n");

        ByteArrayResource resource = new ByteArrayResource(csvContent.toString().getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_bulk_upload_template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    private ResponseEntity<Resource> generateExcelTemplate() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Students");

        // Create styles
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle instructionStyle = workbook.createCellStyle();
        Font instructionFont = workbook.createFont();
        instructionFont.setItalic(true);
        instructionFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        instructionStyle.setFont(instructionFont);

        CellStyle sampleDataStyle = workbook.createCellStyle();
        sampleDataStyle.setBorderBottom(BorderStyle.THIN);
        sampleDataStyle.setBorderTop(BorderStyle.THIN);
        sampleDataStyle.setBorderRight(BorderStyle.THIN);
        sampleDataStyle.setBorderLeft(BorderStyle.THIN);

        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);

        // Create instructions sheet
        Sheet instructionsSheet = workbook.createSheet("Instructions");
        int instructionRow = 0;

        String[] instructions = {
                "STUDENT BULK UPLOAD TEMPLATE - INSTRUCTIONS",
                "",
                "Column Descriptions:",
                "1. admissionNumber - Unique identifier for the student (e.g., STU001, STU002) [REQUIRED]",
                "2. firstName - Student's first name [REQUIRED]",
                "3. lastName - Student's last name [REQUIRED]",
                "4. dateOfBirth - Student's date of birth in YYYY-MM-DD format [REQUIRED]",
                "5. admissionDate - Date of admission in YYYY-MM-DD format [REQUIRED]",
                "6. gradeId - Numeric ID of the grade/class [REQUIRED]",
                "7. gender - Student's gender (Male/Female) [REQUIRED]",
                "8. studentImage - Base64 encoded image string or image URL [OPTIONAL]",
                "",
                "Important Notes:",
                "- All fields marked as REQUIRED must be filled",
                "- Admission numbers must be unique across all students",
                "- Date format must be YYYY-MM-DD (e.g., 2010-05-15)",
                "- Grade ID must exist in your system",
                "- Delete or replace the sample data before uploading",
                "- Maximum file size: 10MB",
                "- Supported formats: .xlsx, .xls, .csv",
                "",
                "Sample Data:",
                "The 'Students' sheet contains sample data to help you understand the format.",
                "Replace this data with your actual student information.",
                "",
                "For any questions, please contact your system administrator."
        };

        for (String instruction : instructions) {
            Row row = instructionsSheet.createRow(instructionRow++);
            Cell cell = row.createCell(0);
            cell.setCellValue(instruction);
            if (instructionRow == 1) {
                CellStyle titleStyle = workbook.createCellStyle();
                Font titleFont = workbook.createFont();
                titleFont.setBold(true);
                titleFont.setFontHeightInPoints((short) 14);
                titleStyle.setFont(titleFont);
                cell.setCellStyle(titleStyle);
            } else {
                cell.setCellStyle(instructionStyle);
            }
        }
        instructionsSheet.setColumnWidth(0, 100 * 256);

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "admissionNumber", "firstName", "lastName", "dateOfBirth",
                "admissionDate", "gradeId", "gender", "studentImage"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);

            // Set column width
            if (i == 3 || i == 4) { // Date columns
                sheet.setColumnWidth(i, 15 * 256);
            } else if (i == 7) { // Image column
                sheet.setColumnWidth(i, 20 * 256);
            } else {
                sheet.setColumnWidth(i, 20 * 256);
            }
        }

        // Add sample data
        Object[][] sampleData = {
                {"STU001", "John", "Doe", LocalDate.of(2010, 5, 15), "2024-01-10", 1L, "Male", ""},
                {"STU002", "Jane", "Smith", LocalDate.of(2011, 8, 22), "2024-01-10", 1L, "Female", ""},
                {"STU003", "Michael", "Johnson", LocalDate.of(2010, 12, 3), "2024-01-10", 2L, "Male", ""}
        };

        for (int i = 0; i < sampleData.length; i++) {
            Row row = sheet.createRow(i + 1);
            Object[] rowData = sampleData[i];

            for (int j = 0; j < rowData.length; j++) {
                Cell cell = row.createCell(j);
                Object value = rowData[j];

                if (value instanceof String) {
                    cell.setCellValue((String) value);
                    cell.setCellStyle(sampleDataStyle);
                } else if (value instanceof Long) {
                    cell.setCellValue((Long) value);
                    cell.setCellStyle(sampleDataStyle);
                } else if (value instanceof LocalDate) {
                    cell.setCellValue((LocalDate) value);
                    cell.setCellStyle(dateStyle);
                }
            }
        }

        // Add data validation for gender column
        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(
                new String[]{"Male", "Female"}
        );
        CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, 6, 6);
        DataValidation dataValidation = validationHelper.createValidation(constraint, addressList);
        dataValidation.setShowErrorBox(true);
        dataValidation.createErrorBox("Invalid Gender", "Please select Male or Female");
        sheet.addValidationData(dataValidation);

        // Freeze header row
        sheet.createFreezePane(0, 1);

        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_bulk_upload_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(resource.contentLength())
                .body(resource);
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
