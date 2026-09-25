package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeMode;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.StudentInvoices.Responses.InvoiceReversalResponseDTO;
import com.EduePoa.EP.StudentInvoices.Responses.StudentInvoiceResponseDTO;
import com.EduePoa.EP.StudentRegistration.OptionalFees.StudentOptionalFee;
import com.EduePoa.EP.StudentRegistration.OptionalFees.StudentOptionalFeeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor

public class StudentInvoicesServiceImpl implements StudentInvoicesService {
    private final StudentRepository studentRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceRepository financeRepository;
    private final StudentOptionalFeeRepository studentOptionalFeeRepository;
    private final InvoiceReversalRepository invoiceReversalRepository;
    private final AuditService auditService;

    @Audit(module = "STUDENT INVOICE", action = "CREATE")
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
                                "). Requested term: " + requestedTerm.name());
            }

            // Fetch the student
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Get the student's grade
            Grade studentGrade = student.getGrade();
            if (studentGrade == null) {
                throw new RuntimeException("Student has no assigned grade");
            }

            // Find the fee structure for the student's grade, fee mode (from boarding
            // status) and current year. Day scholars use the DAY structure; boarding
            // and weekly-boarding students use the BOARDING structure.
            int currentYear = Year.now().getValue();
            FeeMode feeMode = FeeMode.fromBoardingStatus(student.getBoardingStatus());
            FeeStructure feeStructure = feeStructureRepository.findByGradeAndModeAndYear(
                    studentGrade,
                    feeMode,
                    currentYear)
                    .orElseThrow(() -> new RuntimeException(buildMissingFeeStructureMessage(
                            feeMode, student.getBoardingStatus(), studentGrade.getName(), currentYear)));

            // Check if invoice already exists for this student, term, and year
            Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, currentTerm, Year.of(currentYear));

            if (existingInvoice.isPresent()) {
                throw new RuntimeException(
                        "Invoice already exists for student " + student.getFirstName() +
                                " for term " + currentTerm.name() + " of year " + currentYear);
            }

            // Calculate total amount from fee components for the specified term
            List<FeeComponentConfig> termComponents = feeStructure.getTermComponents()
                    .stream()
                    .filter(config -> config.getTerm().equalsIgnoreCase(term))
                    .toList();

            if (termComponents.isEmpty()) {
                throw new RuntimeException(
                        "No fee components found for term: " + term +
                                " in fee structure: " + feeStructure.getName());
            }

            // Mandatory charge excludes line items flagged optional: those are only
            // billed when explicitly assigned to a student (added via optionalFees
            // below), so they must not also be counted in the mandatory total.
            BigDecimal mandatoryFeesAmount = termComponents.stream()
                    .filter(config -> !config.isOptional())
                    .map(FeeComponentConfig::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Load active optional fee assignments for this student/term/year and add
            // their snapshot amounts to the current-term charge. Optional fees are part
            // of the current term charge and are applied BEFORE the carried-forward
            // balance.
            List<StudentOptionalFee> optionalFees = studentOptionalFeeRepository
                    .findByStudent_IdAndTermAndAcademicYearAndIsDeleted(
                            studentId, currentTerm, Year.of(currentYear), 'N');

            BigDecimal optionalFeesAmount = optionalFees.stream()
                    .map(StudentOptionalFee::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // current term charge = mandatory + optional
            BigDecimal currentTermAmount = mandatoryFeesAmount.add(optionalFeesAmount);

            // Get previous term balance (can be positive for arrears or negative for
            // overpayment)
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

            // Mark the optional-fee assignments as invoiced so they are not
            // double-counted on a later invoice run and cannot be silently removed
            // in a way that changes this issued invoice's total.
            if (!optionalFees.isEmpty()) {
                optionalFees.forEach(fee -> fee.setIsInvoiced('Y'));
                studentOptionalFeeRepository.saveAll(optionalFees);
            }

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
                                    savedInvoice.getStudent().getLastName())
                    .admissionNumber(savedInvoice.getStudent().getAdmissionNumber())
                    .grade(savedInvoice.getFeeStructure().getGrade().getName())
                    .feeMode(savedInvoice.getFeeStructure().getMode())
                    .term(savedInvoice.getTerm())
                    .academicYear(savedInvoice.getAcademicYear())
                    .mandatoryFeesAmount(mandatoryFeesAmount)
                    .optionalFeesAmount(optionalFeesAmount)
                    .carriedForwardAmount(previousBalance)
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
                    " - Term: " + currentTerm.name() + ", Mandatory Fees: " + mandatoryFeesAmount +
                    ", Optional Fees: " + optionalFeesAmount + ", Current Term Fees: " + currentTermAmount;

            if (previousBalance.compareTo(BigDecimal.ZERO) > 0) {
                message += ", Arrears Carried Forward: " + previousBalance;
            } else if (previousBalance.compareTo(BigDecimal.ZERO) < 0) {
                message += ", Credit Carried Forward: " + previousBalance.abs();
            }

            message += ", Total Amount: " + totalAmount;

            response.setMessage(message);
            response.setStatusCode(HttpStatus.CREATED.value());
            auditService.log("INVOICE", "Created invoice for student:", student.getFirstName(), "ID:",
                    String.valueOf(savedInvoice.getId()), "term:", term, "amount:", String.valueOf(totalAmount));

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
    @Audit(module = "STUDENT INVOICE", action = "BULK_INVOICE")
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

            // Process each student by calling the create method with bypass
            for (Student student : allStudents) {
                try {
                    // Check if student has a grade (early validation)
                    if (student.getGrade() == null) {
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "No grade assigned",
                                null));
                        continue;
                    }

                    // Check if invoice already exists to avoid unnecessary create calls
                    Optional<StudentInvoices> existingInvoice = studentInvoicesRepository
                            .findByStudentAndTermAndAcademicYear(student, termEnum, Year.of(currentYear));

                    if (existingInvoice.isPresent()) {
                        skippedCount++;
                        continue;
                    }

                    // Call the create method with bypass=true to allow any term
                    CustomResponse<?> createResponse = this.create(student.getId(), term);

                    // Check the result
                    if (createResponse.getStatusCode() == HttpStatus.CREATED.value()) {
                        // Success
                        StudentInvoiceResponseDTO createdInvoice = (StudentInvoiceResponseDTO) createResponse
                                .getEntity();
                        successfulInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                "Success",
                                createdInvoice.getInvoiceId()));
                    } else {
                        // Create method returned an error
                        failedInvoices.add(new InvoiceResult(
                                student.getId(),
                                student.getFirstName(),
                                createResponse.getMessage(),
                                null));
                    }

                } catch (Exception e) {
                    failedInvoices.add(new InvoiceResult(
                            student.getId(),
                            student.getFirstName(),
                            "Error: " + e.getMessage(),
                            null));
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
                    failedInvoices);

            response.setEntity(summary);
            response.setMessage(String.format(
                    "Bulk invoicing completed: %d successful, %d failed, %d skipped out of %d students",
                    successfulInvoices.size(),
                    failedInvoices.size(),
                    skippedCount,
                    allStudents.size()));
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("INVOICE", "Bulk invoicing completed for term:", term, "successful:",
                    String.valueOf(successfulInvoices.size()), "failed:", String.valueOf(failedInvoices.size()));

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
            // Exclude reversed/deleted invoices from the list.
            List<StudentInvoices> invoices = studentInvoicesRepository.findByIsDeleted('N');

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
                                    invoice.getStudent().getFirstName() + " " + invoice.getStudent().getLastName())
                            .admissionNumber(invoice.getStudent().getAdmissionNumber())
                            .grade(invoice.getStudent().getGrade().getName())
                            .feeMode(resolveFeeMode(invoice))
                            .term(invoice.getTerm())
                            .academicYear(invoice.getAcademicYear())
                            .totalAmount(invoice.getTotalAmount())
                            .amountPaid(invoice.getAmountPaid())
                            .balance(invoice.getBalance())
                            .status(invoice.getStatus())
                            .invoiceDate(invoice.getInvoiceDate())
                            .dueDate(invoice.getDueDate())
                            .build())
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
            List<StudentInvoices> invoices = studentInvoicesRepository.findAllByStudent_IdAndIsDeleted(id, 'N');

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

    @Override
    public CustomResponse<?> getCurrentTermInvoices() {
        CustomResponse<List<StudentInvoiceResponseDTO>> response = new CustomResponse<>();

        try {
            // Get current term
            Term currentTerm = Term.getCurrentTerm();

            if (currentTerm == null) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No active term at the moment");
                response.setEntity(Collections.emptyList());
                return response;
            }

            // Fetch invoices for current term (excluding deleted ones)
            List<StudentInvoices> invoices = studentInvoicesRepository
                    .findByTermAndIsDeleted(currentTerm, 'N');

            if (invoices.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No invoices found for current term: " + currentTerm.name());
                response.setEntity(Collections.emptyList());
                return response;
            }

            // Map to DTOs
            List<StudentInvoiceResponseDTO> invoiceDTOs = invoices.stream()
                    .map(invoice -> StudentInvoiceResponseDTO.builder()
                            .invoiceId(invoice.getId())
                            .studentName(
                                    invoice.getStudent().getFirstName() + " " +
                                            invoice.getStudent().getLastName())
                            .admissionNumber(invoice.getStudent().getAdmissionNumber())
                            .grade(invoice.getStudent().getGrade().getName())
                            .feeMode(resolveFeeMode(invoice))
                            .term(invoice.getTerm())
                            .academicYear(invoice.getAcademicYear())
                            .totalAmount(invoice.getTotalAmount())
                            .amountPaid(invoice.getAmountPaid())
                            .balance(invoice.getBalance())
                            .status(invoice.getStatus())
                            .invoiceDate(invoice.getInvoiceDate())
                            .dueDate(invoice.getDueDate())
                            .build())
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Invoices for " + currentTerm.name() + " retrieved successfully");
            response.setEntity(invoiceDTOs);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve current term invoices: " + e.getMessage());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    public CustomResponse<?> getInvoicesByTerm(Term term) {
        CustomResponse<List<StudentInvoiceResponseDTO>> response = new CustomResponse<>();

        try {
            if (term == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Term cannot be null");
                response.setEntity(null);
                return response;
            }

            List<StudentInvoices> invoices = studentInvoicesRepository
                    .findByTermAndIsDeleted(term, 'N');

            if (invoices.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No invoices found for term: " + term.name());
                response.setEntity(Collections.emptyList());
                return response;
            }

            List<StudentInvoiceResponseDTO> invoiceDTOs = invoices.stream()
                    .map(invoice -> StudentInvoiceResponseDTO.builder()
                            .invoiceId(invoice.getId())
                            .studentName(
                                    invoice.getStudent().getFirstName() + " " +
                                            invoice.getStudent().getLastName())
                            .admissionNumber(invoice.getStudent().getAdmissionNumber())
                            .grade(invoice.getStudent().getGrade().getName())
                            .feeMode(resolveFeeMode(invoice))
                            .term(invoice.getTerm())
                            .academicYear(invoice.getAcademicYear())
                            .totalAmount(invoice.getTotalAmount())
                            .amountPaid(invoice.getAmountPaid())
                            .balance(invoice.getBalance())
                            .status(invoice.getStatus())
                            .invoiceDate(invoice.getInvoiceDate())
                            .dueDate(invoice.getDueDate())
                            .build())
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Invoices for " + term.name() + " retrieved successfully");
            response.setEntity(invoiceDTOs);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve invoices: " + e.getMessage());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    @Audit(module = "STUDENT INVOICE", action = "REVERSE")
    public CustomResponse<?> reverseInvoice(Long invoiceId) {
        CustomResponse<StudentInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            if (invoiceId == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("invoiceId is required");
                response.setEntity(null);
                return response;
            }

            StudentInvoices invoice = studentInvoicesRepository.findById(invoiceId).orElse(null);
            if (invoice == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Invoice not found with ID: " + invoiceId);
                response.setEntity(null);
                return response;
            }

            return doReverse(invoice, response, "SINGLE");

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reverse invoice: " + e.getMessage());
            response.setEntity(null);
            return response;
        }
    }

    @Override
    @Audit(module = "STUDENT INVOICE", action = "REVERSE")
    public CustomResponse<?> reverseInvoice(Long studentId, Term term, Integer academicYear) {
        CustomResponse<StudentInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            if (studentId == null || term == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("studentId and term are required");
                response.setEntity(null);
                return response;
            }
            int year = academicYear != null ? academicYear : Year.now().getValue();

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found with ID: " + studentId);
                response.setEntity(null);
                return response;
            }

            StudentInvoices invoice = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, term, Year.of(year))
                    .orElse(null);
            if (invoice == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No invoice found for student " + studentId + " for "
                        + term.name() + " " + year);
                response.setEntity(null);
                return response;
            }

            return doReverse(invoice, response, "STUDENT");

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reverse invoice: " + e.getMessage());
            response.setEntity(null);
            return response;
        }
    }

    @Override
    @Audit(module = "STUDENT INVOICE", action = "BULK_REVERSE")
    public CustomResponse<?> reverseAll(Term term, Integer academicYear) {
        CustomResponse<ReversalSummary> response = new CustomResponse<>();
        try {
            if (term == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("term is required");
                response.setEntity(null);
                return response;
            }
            int year = academicYear != null ? academicYear : Year.now().getValue();

            List<StudentInvoices> invoices = studentInvoicesRepository
                    .findByTermAndAcademicYearAndIsDeleted(term, Year.of(year), 'N');

            ReversalSummary summary = reverseBatch(invoices, term.name(), year, "SCHOOL_WIDE");

            response.setEntity(summary);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(String.format(
                    "School-wide reversal completed for %s %d: %d reversed, %d failed, %d skipped out of %d invoices",
                    term.name(), year,
                    summary.getReversedInvoices(), summary.getFailedInvoices(),
                    summary.getSkippedInvoices(), summary.getTotalInvoices()));
            auditService.log("INVOICE", "School-wide reversal for term:", term.name(),
                    "year:", String.valueOf(year), "reversed:", String.valueOf(summary.getReversedInvoices()),
                    "failed:", String.valueOf(summary.getFailedInvoices()));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reverse invoices: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "STUDENT INVOICE", action = "BULK_REVERSE")
    public CustomResponse<?> reverseByGrade(Long gradeId, Term term, Integer academicYear) {
        CustomResponse<ReversalSummary> response = new CustomResponse<>();
        try {
            if (gradeId == null || term == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("gradeId and term are required");
                response.setEntity(null);
                return response;
            }
            int year = academicYear != null ? academicYear : Year.now().getValue();

            List<StudentInvoices> invoices = studentInvoicesRepository
                    .findByStudent_Grade_IdAndTermAndAcademicYearAndIsDeleted(gradeId, term, Year.of(year), 'N');

            ReversalSummary summary = reverseBatch(invoices, term.name(), year, "GRADE");

            response.setEntity(summary);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(String.format(
                    "Grade reversal completed for grade %d, %s %d: %d reversed, %d failed, %d skipped out of %d invoices",
                    gradeId, term.name(), year,
                    summary.getReversedInvoices(), summary.getFailedInvoices(),
                    summary.getSkippedInvoices(), summary.getTotalInvoices()));
            auditService.log("INVOICE", "Grade reversal for grade:", String.valueOf(gradeId),
                    "term:", term.name(), "year:", String.valueOf(year),
                    "reversed:", String.valueOf(summary.getReversedInvoices()),
                    "failed:", String.valueOf(summary.getFailedInvoices()));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reverse invoices: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getReversals() {
        CustomResponse<List<InvoiceReversalResponseDTO>> response = new CustomResponse<>();
        try {
            List<InvoiceReversalResponseDTO> reversals = invoiceReversalRepository
                    .findAllByOrderByReversedAtDesc()
                    .stream()
                    .map(this::mapToInvoiceReversalResponseDTO)
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(reversals.isEmpty()
                    ? "No reversed invoices found"
                    : "Reversed invoices retrieved successfully");
            response.setEntity(reversals);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve reversed invoices: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    private InvoiceReversalResponseDTO mapToInvoiceReversalResponseDTO(InvoiceReversal r) {
        return InvoiceReversalResponseDTO.builder()
                .id(r.getId())
                .originalInvoiceId(r.getOriginalInvoiceId())
                .studentId(r.getStudentId())
                .studentName(r.getStudentName())
                .admissionNumber(r.getAdmissionNumber())
                .grade(r.getGrade())
                .term(r.getTerm())
                .academicYear(r.getAcademicYear())
                .totalAmount(r.getTotalAmount())
                .amountPaid(r.getAmountPaid())
                .balance(r.getBalance())
                .invoiceDate(r.getInvoiceDate())
                .dueDate(r.getDueDate())
                .scope(r.getScope())
                .releasedOptionalFees(r.getReleasedOptionalFees())
                .reversedBy(r.getReversedBy())
                .reversedAt(r.getReversedAt())
                .build();
    }

    /**
     * Resolves the email/username of the currently authenticated user for
     * reversal attribution. Returns "SYSTEM" when no user can be determined
     * (e.g. background execution), mirroring the audit service's fallback.
     */
    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return "SYSTEM";
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetails ud) {
                return ud.getUsername();
            }
            return auth.getName() != null ? auth.getName() : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    /**
     * Reverses a batch of invoices one at a time, isolating each in its own
     * try/catch so a single failure (e.g. an invoice with payments) never aborts
     * the rest. Invoices that {@code doReverse} rejects with a non-2xx status are
     * recorded as skipped/failed; the accumulated outcome is returned as a summary.
     */
    private ReversalSummary reverseBatch(List<StudentInvoices> invoices, String term, int year, String scope) {
        List<ReversalResult> reversed = new ArrayList<>();
        List<ReversalResult> failed = new ArrayList<>();
        int skipped = 0;

        for (StudentInvoices invoice : invoices) {
            Long invoiceId = invoice.getId();
            String studentName = invoice.getStudent().getFirstName() + " "
                    + invoice.getStudent().getLastName();
            try {
                CustomResponse<StudentInvoiceResponseDTO> perInvoice = new CustomResponse<>();
                doReverse(invoice, perInvoice, scope);

                if (perInvoice.getStatusCode() == HttpStatus.OK.value()) {
                    reversed.add(new ReversalResult(invoice.getStudent().getId(), studentName,
                            "Reversed", invoiceId));
                } else if (perInvoice.getStatusCode() == HttpStatus.CONFLICT.value()) {
                    // Blocked (already reversed or has payments) — report as skipped.
                    skipped++;
                    failed.add(new ReversalResult(invoice.getStudent().getId(), studentName,
                            perInvoice.getMessage(), invoiceId));
                } else {
                    failed.add(new ReversalResult(invoice.getStudent().getId(), studentName,
                            perInvoice.getMessage(), invoiceId));
                }
            } catch (Exception e) {
                failed.add(new ReversalResult(invoice.getStudent().getId(), studentName,
                        "Error: " + e.getMessage(), invoiceId));
            }
        }

        return new ReversalSummary(
                invoices.size(),
                reversed.size(),
                failed.size(),
                skipped,
                term,
                year,
                reversed,
                failed);
    }

    /**
     * Reverses (voids) an invoice: soft-deletes it, removes its contribution from
     * the Finance rollup for the same term/year, and resets the optional-fee
     * assignments it consumed ({@code isInvoiced='N'}) so they can be re-invoiced.
     * <p>
     * Reversal is blocked when a payment has already been recorded against the
     * invoice ({@code amountPaid > 0}) to avoid orphaning received money.
     */
    private CustomResponse<StudentInvoiceResponseDTO> doReverse(
            StudentInvoices invoice, CustomResponse<StudentInvoiceResponseDTO> response, String scope) {

        if (invoice.getIsDeleted() == 'Y') {
            response.setStatusCode(HttpStatus.CONFLICT.value());
            response.setMessage("Invoice " + invoice.getId() + " is already reversed");
            response.setEntity(null);
            return response;
        }

        if (invoice.getAmountPaid() != null
                && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            response.setStatusCode(HttpStatus.CONFLICT.value());
            response.setMessage("Invoice " + invoice.getId() + " has payments of "
                    + invoice.getAmountPaid() + " recorded and cannot be reversed. "
                    + "Reverse or refund the payment first.");
            response.setEntity(null);
            return response;
        }

        Long studentId = invoice.getStudent().getId();
        Term term = invoice.getTerm();
        Year year = invoice.getAcademicYear();

        // 1. Reset optional-fee assignments that were consumed by this invoice so
        //    they are no longer marked invoiced (they remain active assignments).
        List<StudentOptionalFee> optionalFees = studentOptionalFeeRepository
                .findByStudent_IdAndTermAndAcademicYearAndIsDeleted(studentId, term, year, 'N');
        List<StudentOptionalFee> toReset = optionalFees.stream()
                .filter(f -> f.getIsInvoiced() == 'Y')
                .toList();
        if (!toReset.isEmpty()) {
            toReset.forEach(f -> f.setIsInvoiced('N'));
            studentOptionalFeeRepository.saveAll(toReset);
        }

        // 2. Remove this invoice's contribution from the Finance rollup. Since the
        //    invoice upsert sets the finance totals to the invoice values, reversal
        //    zeroes them for this term/year (no payments exist here).
        financeRepository.findByStudentIdAndTermAndYear(studentId, term, year).ifPresent(finance -> {
            finance.setTotalFeeAmount(BigDecimal.ZERO);
            finance.setBalance(BigDecimal.ZERO.subtract(
                    finance.getPaidAmount() != null ? finance.getPaidAmount() : BigDecimal.ZERO));
            finance.setLastUpdated(LocalDateTime.now());
            financeRepository.save(finance);
        });

        // 3. Capture a response snapshot before deletion, then physically remove the
        //    invoice. A hard delete frees the unique (student, term, year) key so the
        //    term can be re-invoiced. This is safe here because reversal is blocked
        //    when any payment exists, so no received money is orphaned.
        Long removedId = invoice.getId();
        StudentInvoiceResponseDTO snapshot = mapToStudentInvoiceResponseDTO(invoice);

        // Persist a durable record of this reversal before the invoice row is gone,
        // so the operation can be listed as reversal history on the frontend.
        InvoiceReversal reversalRecord = InvoiceReversal.builder()
                .originalInvoiceId(removedId)
                .studentId(studentId)
                .studentName(invoice.getStudent().getFirstName() + " "
                        + invoice.getStudent().getLastName())
                .admissionNumber(invoice.getStudent().getAdmissionNumber())
                .grade(invoice.getStudent().getGrade() != null
                        ? invoice.getStudent().getGrade().getName() : null)
                .term(term)
                .academicYear(year)
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .balance(invoice.getBalance())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .scope(scope != null ? scope : "SINGLE")
                .releasedOptionalFees(toReset.size())
                .reversedBy(getCurrentUsername())
                .reversedAt(LocalDateTime.now())
                .build();
        invoiceReversalRepository.save(reversalRecord);

        studentInvoicesRepository.delete(invoice);

        auditService.log("INVOICE", "Reversed invoice ID:", String.valueOf(removedId),
                "student:", String.valueOf(studentId), "term:", term.name(),
                "year:", String.valueOf(year), "resetOptionalFees:", String.valueOf(toReset.size()));

        response.setStatusCode(HttpStatus.OK.value());
        response.setMessage("Invoice " + removedId + " reversed. "
                + toReset.size() + " optional fee assignment(s) released; the term can be re-invoiced.");
        response.setEntity(snapshot);
        return response;
    }

    private StudentInvoiceResponseDTO mapToStudentInvoiceResponseDTO(StudentInvoices invoice) {

        return StudentInvoiceResponseDTO.builder()
                .invoiceId(invoice.getId())
                .studentName(
                        invoice.getStudent().getFirstName() + " " +
                                invoice.getStudent().getLastName())
                .admissionNumber(invoice.getStudent().getAdmissionNumber())
                .grade(invoice.getStudent().getGrade().getName())
                .feeMode(resolveFeeMode(invoice))
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

    /**
     * Resolves the billing mode for an already-issued invoice. Prefers the fee
     * structure the invoice was generated against; falls back to deriving it from
     * the student's current boarding status.
     */
    private FeeMode resolveFeeMode(StudentInvoices invoice) {
        if (invoice.getFeeStructure() != null && invoice.getFeeStructure().getMode() != null) {
            return invoice.getFeeStructure().getMode();
        }
        return FeeMode.fromBoardingStatus(invoice.getStudent().getBoardingStatus());
    }

    /**
     * Builds an explicit, actionable message when the required fee structure is
     * missing, identifying the student type, fee mode, grade and academic year.
     */
    private String buildMissingFeeStructureMessage(FeeMode feeMode,
                                                   com.EduePoa.EP.StudentRegistration.BoardingStatus boardingStatus,
                                                   String gradeName, int year) {
        String studentType = feeMode == FeeMode.DAY ? "day" : "boarding";
        String statusLabel = boardingStatus != null ? boardingStatus.name() : "unspecified";
        return "No " + feeMode.name() + " fee structure configured for " + gradeName
                + " for academic year " + year + ". Configure a " + feeMode.name()
                + " fee structure before invoicing " + studentType + " students (student boarding status: "
                + statusLabel + ").";
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReversalResult {
        private Long studentId;
        private String studentName;
        private String status;
        private Long invoiceId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReversalSummary {
        private int totalInvoices;
        private int reversedInvoices;
        private int failedInvoices;
        private int skippedInvoices;
        private String term;
        private int academicYear;
        private List<ReversalResult> reversed;
        private List<ReversalResult> failed;
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
