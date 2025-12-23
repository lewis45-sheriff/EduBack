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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
            // Fetch the student
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Get the student's grade
            Grade studentGrade = student.getGrade();
            if (studentGrade == null) {
                throw new RuntimeException("Student has no assigned grade");
            }

            // Find the approved fee structure for the student's grade and current year
            Year currentYear = Year.now();
            FeeStructure feeStructure = feeStructureRepository.findByGradeAndYear(
                            studentGrade,
                            currentYear.getValue()
                    )
                    .orElseThrow(() -> new RuntimeException(
                            "No approved fee structure found for grade: " + studentGrade.getName() +
                                    " and year: " + currentYear
                    ));

            // Check if invoice already exists for this student, term, and year
            Term termEnum = Term.valueOf(term.toUpperCase());
            Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, termEnum, currentYear);

            if (existingInvoice.isPresent()) {
                throw new RuntimeException(
                        "Invoice already exists for student " + student.getFirstName() +
                                " for term " + term + " of year " + currentYear
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

            // Get previous term balance from Finance table
            BigDecimal previousBalance = getPreviousTermBalance(studentId, termEnum, currentYear);

            // Calculate total amount including previous balance
            BigDecimal totalAmount = currentTermAmount.add(previousBalance);

            // Create the invoice
            StudentInvoices invoice = StudentInvoices.builder()
                    .student(student)
                    .feeStructure(feeStructure)
                    .term(termEnum)
                    .academicYear(currentYear)
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
                            studentId, termEnum, currentYear)
                    .orElse(new Finance());

            finance.setStudentId(studentId);
            finance.setTotalFeeAmount(totalAmount);
            finance.setPaidAmount(BigDecimal.ZERO);
            finance.setBalance(totalAmount);
            finance.setTerm(String.valueOf(termEnum));
            finance.setYear(currentYear);
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

            String message = "Invoice created successfully for " + student.getFirstName() +
                    " - Term: " + term + ", Current Term Fees: " + currentTermAmount;

            if (previousBalance.compareTo(BigDecimal.ZERO) > 0) {
                message += ", Previous Balance: " + previousBalance;
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


    private BigDecimal getPreviousTermBalance(Long studentId, Term currentTerm, Year academicYear) {
        // Get all previous terms for this academic year
        List<Term> previousTerms = getPreviousTerms(currentTerm);

        BigDecimal totalPreviousBalance = BigDecimal.ZERO;

        for (Term previousTerm : previousTerms) {
            Optional<Finance> previousFinance = financeRepository
                    .findByStudentIdAndTermAndYear(studentId, previousTerm, academicYear);

            if (previousFinance.isPresent()) {
                BigDecimal balance = previousFinance.get().getBalance();
                if (balance != null && balance.compareTo(BigDecimal.ZERO) > 0) {
                    totalPreviousBalance = totalPreviousBalance.add(balance);
                }
            }
        }

        return totalPreviousBalance;
    }


    private List<Term> getPreviousTerms(Term currentTerm) {
        List<Term> allTerms = Arrays.asList(Term.values());
        int currentIndex = allTerms.indexOf(currentTerm);

        if (currentIndex <= 0) {
            return Collections.emptyList();
        }

        return allTerms.subList(0, currentIndex);
    }

    @Override
    public CustomResponse<?> invoiceAll(String term) {
        CustomResponse<InvoiceSummary> response = new CustomResponse<>();
        try {
            //  Validate term
            Term termEnum;
            try {
                termEnum = Term.valueOf(term.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid term: " + term + ". Valid terms are: " +
                        Arrays.toString(Term.values()));
            }

            //  Get current academic year
            Year currentYear = Year.now();

            // Fetch all active students
            List<Student> allStudents = studentRepository.findAllByIsDeleted(false);

            if (allStudents.isEmpty()) {
                response.setMessage("No active students found in the system");
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setEntity(null);
                return response;
            }
            //  Track results
            List<InvoiceResult> successfulInvoices = new ArrayList<>();
            List<InvoiceResult> failedInvoices = new ArrayList<>();
            int skippedCount = 0;

            // 5. Process each student
            for (Student student : allStudents) {
                try {
                    // Check if student has a grade
                    if (student.getGrade() == null) {
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "No grade assigned",
                                null
                        ));
                        continue;
                    }

                    // Check if invoice already exists
                    Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                            .findByStudentAndTermAndAcademicYear(student, termEnum, currentYear);

                    if (existingInvoice.isPresent()) {
                        skippedCount++;
                        continue; // Skip if already invoiced
                    }

                    // Find approved fee structure for student's grade
                    Optional<FeeStructure> feeStructureOpt = feeStructureRepository
                            .findByGradeAndYear(
                                    student.getGrade(),
                                    currentYear.getValue()
                            );

                    if (feeStructureOpt.isEmpty()) {
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "No approved fee structure for grade: " + student.getGrade().getName(),
                                null
                        ));
                        continue;
                    }

                    FeeStructure feeStructure = feeStructureOpt.get();

                    // Get fee components for the term
                    List<FeeComponentConfig> termComponents = feeStructure.getTermComponents()
                            .stream()
                            .filter(config -> config.getTerm().equalsIgnoreCase(term))
                            .toList();

                    if (termComponents.isEmpty()) {
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "No fee components found for term: " + term,
                                null
                        ));
                        continue;
                    }

                    // Calculate total amount
                    BigDecimal totalAmount = termComponents.stream()
                            .map(FeeComponentConfig::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Create invoice
                    StudentInvoices invoice = StudentInvoices.builder()
                            .student(student)
                            .feeStructure(feeStructure)
                            .term(termEnum)
                            .academicYear(currentYear)
                            .totalAmount(totalAmount)
                            .amountPaid(BigDecimal.ZERO)
                            .balance(totalAmount)
                            .status('P')
                            .invoiceDate(LocalDate.now())
                            .dueDate(calculateDueDate(term))
                            .isDeleted('N')
                            .build();

                    // Save invoice
                    StudentInvoices savedInvoice = studentInvoicesRepository.save(invoice);

                    successfulInvoices.add(new InvoiceResult(
                            student.getId(),
                            student.getFirstName(),
                            "Success",
                            savedInvoice.getId()
                    ));

                } catch (Exception e) {
                    failedInvoices.add(new InvoiceResult(
                            student.getId(),
                            student.getFirstName(),
                            "Error: " + e.getMessage(),
                            null
                    ));
                }
            }

            // 6. Prepare summary response
            InvoiceSummary summary = new InvoiceSummary(
                    allStudents.size(),
                    successfulInvoices.size(),
                    failedInvoices.size(),
                    skippedCount,
                    term,
                    currentYear.toString(),
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
        private String academicYear;
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
