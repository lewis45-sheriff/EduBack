package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.StudentInvoices.Responses.StudentInvoiceResponseDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor

public class StudentInvoicesServiceImpl implements StudentInvoicesService{
    private final StudentRepository studentRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceRepository financeRepository;

    public CustomResponse<?> create(Long studentId, String term) {
        CustomResponse<StudentInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            // Get current term
            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                throw new RuntimeException("No active term found for current date");
            }

            // Validate that the requested term matches current term
            Term requestedTerm = Term.valueOf(term.toUpperCase());
            if (requestedTerm != currentTerm) {
                throw new RuntimeException(
                        "Invoices can only be created for the current term (" + currentTerm.name() +
                                "). Requested term: " + requestedTerm.name()
                );
            }

            // Fetch the student
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Get the student's grade
            Grade studentGrade = student.getGrade();
            if (studentGrade == null) {
                throw new RuntimeException("Student has no assigned grade");
            }

            // Find the approved fee structure for the student's grade and current year
            int currentYear = Year.now().getValue();
            FeeStructure feeStructure = feeStructureRepository.findByGradeAndYear(
                            studentGrade,
                            currentYear
                    )
                    .orElseThrow(() -> new RuntimeException(
                            "No approved fee structure found for grade: " + studentGrade.getName() +
                                    " and year: " + currentYear
                    ));

            // Check if invoice already exists for this student, term, and year
            Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, currentTerm, Year.of(currentYear));

            if (existingInvoice.isPresent()) {
                throw new RuntimeException(
                        "Invoice already exists for student " + student.getFirstName() +
                                " for term " + currentTerm.name() + " of year " + currentYear
                );
            }

            // Calculate total amount from fee components for the specified term
            List<FeeComponentConfig> termComponents = feeStructure.getTermComponents()
                    .stream()
                    .filter(config -> config.getTerm().equalsIgnoreCase(term))
                    .toList();

            if (termComponents.isEmpty()) {
                throw new RuntimeException(
                        "No fee components found for term: " + term +
                                " in fee structure: " + feeStructure.getName()
                );
            }

            BigDecimal currentTermAmount = termComponents.stream()
                    .map(FeeComponentConfig::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Get previous term balance (can be positive for arrears or negative for overpayment)
            BalanceCarryForward carryForward = getPreviousTermBalance(studentId, currentTerm, Year.of(currentYear));
            BigDecimal previousBalance = carryForward.balance();

            // Calculate total amount including previous balance
            // If previousBalance is negative (overpayment), it reduces the total
            BigDecimal totalAmount = currentTermAmount.add(previousBalance);

            // Create the invoice
            StudentInvoices invoice = StudentInvoices.builder()
                    .student(student)
                    .feeStructure(feeStructure)
                    .term(currentTerm)
                    .academicYear(Year.of(currentYear))
                    .totalAmount(totalAmount)
                    .amountPaid(BigDecimal.ZERO)
                    .balance(totalAmount)
                    .status('P') // Pending
                    .invoiceDate(LocalDate.now())
                    .dueDate(calculateDueDate(term))
                    .isDeleted('N')
                    .build();

            // Save the invoice
            StudentInvoices savedInvoice = studentInvoicesRepository.save(invoice);

            // Update or Create Finance record
            Finance finance = financeRepository.findByStudentIdAndTermAndYear(
                            studentId, currentTerm, Year.of(currentYear))
                    .orElse(new Finance());

            finance.setStudentId(studentId);
            finance.setTotalFeeAmount(totalAmount);
            finance.setPaidAmount(BigDecimal.ZERO);
            finance.setBalance(totalAmount);
            finance.setTerm(currentTerm);
            finance.setYear(Year.of(currentYear));
            finance.setLastUpdated(LocalDateTime.now());

            // Save the finance record
            financeRepository.save(finance);

            StudentInvoiceResponseDTO dto = StudentInvoiceResponseDTO.builder()
                    .invoiceId(savedInvoice.getId())
                    .studentName(
                            savedInvoice.getStudent().getFirstName() + " " +
                                    savedInvoice.getStudent().getLastName()
                    )
                    .admissionNumber(savedInvoice.getStudent().getAdmissionNumber())
                    .grade(savedInvoice.getFeeStructure().getGrade().getName())
                    .term(savedInvoice.getTerm())
                    .academicYear(savedInvoice.getAcademicYear())
                    .totalAmount(savedInvoice.getTotalAmount())
                    .amountPaid(savedInvoice.getAmountPaid())
                    .balance(savedInvoice.getBalance())
                    .status(savedInvoice.getStatus())
                    .invoiceDate(savedInvoice.getInvoiceDate())
                    .dueDate(savedInvoice.getDueDate())
                    .build();

            response.setEntity(dto);

            // Build descriptive message
            String message = "Invoice created successfully for " + student.getFirstName() +
                    " - Term: " + currentTerm.name() + ", Current Term Fees: " + currentTermAmount;

            if (previousBalance.compareTo(BigDecimal.ZERO) > 0) {
                message += ", Arrears Carried Forward: " + previousBalance;
            } else if (previousBalance.compareTo(BigDecimal.ZERO) < 0) {
                message += ", Credit Carried Forward: " + previousBalance.abs();
            }

            message += ", Total Amount: " + totalAmount;

            response.setMessage(message);
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    private BalanceCarryForward getPreviousTermBalance(Long studentId, Term currentTerm, Year academicYear) {
        // Get all previous terms for this academic year
        List<Term> previousTerms = getPreviousTerms(currentTerm);

        BigDecimal totalPreviousBalance = BigDecimal.ZERO;

        for (Term previousTerm : previousTerms) {
            Optional<Finance> previousFinance = financeRepository
                    .findByStudentIdAndTermAndYear(studentId, previousTerm, academicYear);

            if (previousFinance.isPresent()) {
                BigDecimal balance = previousFinance.get().getBalance();
                if (balance != null) {
                    // Include both positive (arrears) and negative (overpayment) balances
                    totalPreviousBalance = totalPreviousBalance.add(balance);
                }
            }
        }

        return new BalanceCarryForward(totalPreviousBalance);
    }


    private List<Term> getPreviousTerms(Term currentTerm) {
        List<Term> allTerms = Arrays.asList(Term.values());
        int currentIndex = allTerms.indexOf(currentTerm);

        if (currentIndex <= 0) {
            return Collections.emptyList();
        }

        return allTerms.subList(0, currentIndex);
    }


    // Helper class to carry balance information
        private record BalanceCarryForward(BigDecimal balance) {

    }
    @Override
    public CustomResponse<?> invoiceAll(String term) {
        CustomResponse<InvoiceSummary> response = new CustomResponse<>();
        try {
            // Validate term
            Term termEnum;
            try {
                termEnum = Term.valueOf(term.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid term: " + term + ". Valid terms are: " +
                        Arrays.toString(Term.values()));
            }

            // Get current academic year
            int currentYear = Year.now().getValue();

            // Fetch all active students
            List<Student> allStudents = studentRepository.findAllByIsDeleted(false);

            if (allStudents.isEmpty()) {
                response.setMessage("No active students found in the system");
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setEntity(null);
                return response;
            }

            // Track results
            List<InvoiceResult> successfulInvoices = new ArrayList<>();
            List<InvoiceResult> failedInvoices = new ArrayList<>();
            int skippedCount = 0;

            // Process each student by calling the create method
            for (Student student : allStudents) {
                try {
                    // Check if student has a grade (early validation)
                    if (student.getGrade() == null) {
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "No grade assigned",
                                null
                        ));
                        continue;
                    }

                    // Check if invoice already exists to avoid unnecessary create calls
                    Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                            .findByStudentAndTermAndAcademicYear(student, termEnum, Year.of(currentYear));

                    if (existingInvoice.isPresent()) {
                        skippedCount++;
                        continue;
                    }

                    // Call the create method for this student
                    CustomResponse<?> createResponse = this.create(student.getId(), term);

                    // Check the result
                    if (createResponse.getStatusCode() == HttpStatus.CREATED.value()) {
                        // Success
                        StudentInvoiceResponseDTO createdInvoice = (StudentInvoiceResponseDTO) createResponse.getEntity();
                        successfulInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "Success",
                                createdInvoice.getInvoiceId()
                        ));
                    } else {
                        // Create method returned an error
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                createResponse.getMessage(),
                                null
                        ));
                    }

                } catch (Exception e) {
                    failedInvoices.add(new InvoiceResult(
                            student.getId(),
                            student.getFirstName(),
                            "Error: " + e.getMessage(),
                            null
                    ));
                }
            }

            // Prepare summary response
            InvoiceSummary summary = new InvoiceSummary(
                    allStudents.size(),
                    successfulInvoices.size(),
                    failedInvoices.size(),
                    skippedCount,
                    term,
                    currentYear,
                    successfulInvoices,
                    failedInvoices
            );

            response.setEntity(summary);
            response.setMessage(String.format(
                    "Bulk invoicing completed: %d successful, %d failed, %d skipped out of %d students",
                    successfulInvoices.size(),
                    failedInvoices.size(),
                    skippedCount,
                    allStudents.size()
            ));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllInvoices() {
        CustomResponse<List<StudentInvoiceResponseDTO>> response = new CustomResponse<>();

        try {
            List<StudentInvoices> invoices = studentInvoicesRepository.findAll();

            if (invoices.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No invoices found");
                response.setEntity(Collections.emptyList());
                return response;
            }

            List<StudentInvoiceResponseDTO> invoiceDTOs = invoices.stream()
                    .map(invoice -> StudentInvoiceResponseDTO.builder()
                            .invoiceId(invoice.getId())
                            .studentName(
                                    invoice.getStudent().getFirstName() + " " + invoice.getStudent().getLastName()
                            )
                            .admissionNumber(invoice.getStudent().getAdmissionNumber())
                            .grade(invoice.getStudent().getGrade().getName())
                            .term(invoice.getTerm())
                            .academicYear(invoice.getAcademicYear())
                            .totalAmount(invoice.getTotalAmount())
                            .amountPaid(invoice.getAmountPaid())
                            .balance(invoice.getBalance())
                            .status(invoice.getStatus())
                            .invoiceDate(invoice.getInvoiceDate())
                            .dueDate(invoice.getDueDate())
                            .build()
                    )
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Invoices retrieved successfully");
            response.setEntity(invoiceDTOs);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve invoices: " + e.getMessage());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    public CustomResponse<?> getAllInvoices(Long id) {
        CustomResponse<Object> response = new CustomResponse<>();

        try {
            List<StudentInvoices> invoices =
                    studentInvoicesRepository.findAllByStudent_IdAndIsDeleted(id, 'N');

            if (invoices.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(Collections.emptyList());
                response.setMessage("No invoices found for the selected student");
                return response;
            }

            List<StudentInvoiceResponseDTO> invoiceDTOs = invoices.stream()
                    .map(this::mapToStudentInvoiceResponseDTO)
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(invoiceDTOs);
            response.setMessage("Student invoices retrieved successfully");

        } catch (RuntimeException e) {

            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Failed to retrieve student invoices");
        }

        return response;
    }



    private StudentInvoiceResponseDTO mapToStudentInvoiceResponseDTO(StudentInvoices invoice) {

        return StudentInvoiceResponseDTO.builder()
                .invoiceId(invoice.getId())
                .studentName(
                        invoice.getStudent().getFirstName() + " " +
                                invoice.getStudent().getLastName()
                )
                .admissionNumber(invoice.getStudent().getAdmissionNumber())
                .grade(invoice.getStudent().getGrade().getName())
                .term(invoice.getTerm())
                .academicYear(invoice.getAcademicYear())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .balance(invoice.getBalance())
                .status(invoice.getStatus())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .build();
    }



    // Inner classes for response structure
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvoiceResult {
        private Long studentId;
        private String studentName;
        private String status;
        private Long invoiceId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvoiceSummary {
        private int totalStudents;
        private int successfulInvoices;
        private int failedInvoices;
        private int skippedInvoices;
        private String term;
        private int academicYear;
        private List<InvoiceResult> successful;
        private List<InvoiceResult> failed;
    }

    // Helper method to calculate due date based on term
    private LocalDate calculateDueDate(String term) {
        LocalDate now = LocalDate.now();
        return switch (term.toUpperCase()) {
            case "TERM1", "TERM_1" -> now.plusMonths(1); // Due in 1 month
            case "TERM2", "TERM_2" -> now.plusMonths(1);
            case "TERM3", "TERM_3" -> now.plusMonths(1);
            default -> now.plusDays(30);
        };
    }
}
