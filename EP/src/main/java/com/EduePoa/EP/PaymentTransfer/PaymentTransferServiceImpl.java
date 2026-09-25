package com.EduePoa.EP.PaymentTransfer;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
import com.EduePoa.EP.PaymentTransfer.Request.CreatePaymentTransferRequest;
import com.EduePoa.EP.PaymentTransfer.Response.PaymentTransferResponseDTO;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentTransferServiceImpl implements PaymentTransferService {

    private final PaymentTransferRepository paymentTransferRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final StudentRepository studentRepository;
    private final FinanceRepository financeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ---- Create (maker): store only, no financial effect --------------------

    @Override
    @Audit(module = "PAYMENT TRANSFER", action = "CREATE")
    public CustomResponse<?> createTransfer(CreatePaymentTransferRequest request) {
        CustomResponse<PaymentTransferResponseDTO> response = new CustomResponse<>();
        try {
            if (request == null || request.getSourcePaymentReference() == null
                    || request.getSourcePaymentReference().isBlank()) {
                return bad(response, "sourcePaymentReference is required");
            }
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return bad(response, "amount must be greater than zero");
            }
            if (request.getDestStudentId() == null || request.getDestInvoiceId() == null) {
                return bad(response, "destStudentId and destInvoiceId are required");
            }

            // Resolve the source payment by its unique reference.
            FinanceTransaction sourcePayment = financeTransactionRepository
                    .findByReferenceAndTransactionType(
                            request.getSourcePaymentReference(),
                            FinanceTransaction.TransactionType.INCOME)
                    .orElse(null);
            if (sourcePayment == null) {
                return notFound(response, "No payment found with reference: "
                        + request.getSourcePaymentReference());
            }

            Long sourceStudentId = sourcePayment.getStudentId();
            Long sourceInvoiceId = sourcePayment.getInvoiceId();
            if (sourceInvoiceId == null) {
                return conflict(response, "The source payment is not linked to an invoice and cannot be transferred");
            }

            // Destination must differ from source and be a distinct invoice.
            if (request.getDestStudentId().equals(sourceStudentId)
                    && request.getDestInvoiceId().equals(sourceInvoiceId)) {
                return bad(response, "Source and destination are the same; nothing to transfer");
            }

            // How much of this payment can still be moved.
            BigDecimal committed = nz(paymentTransferRepository
                    .sumCommittedForReference(request.getSourcePaymentReference()));
            BigDecimal transferable = nz(sourcePayment.getAmount()).subtract(committed);
            if (request.getAmount().compareTo(transferable) > 0) {
                return conflict(response, "Amount " + request.getAmount()
                        + " exceeds the transferable amount " + transferable
                        + " for reference " + request.getSourcePaymentReference());
            }

            // Validate destination student + invoice ownership.
            Student destStudent = studentRepository.findById(request.getDestStudentId()).orElse(null);
            if (destStudent == null) {
                return notFound(response, "Destination student not found with ID: " + request.getDestStudentId());
            }
            StudentInvoices destInvoice = studentInvoicesRepository.findById(request.getDestInvoiceId()).orElse(null);
            if (destInvoice == null) {
                return notFound(response, "Destination invoice not found with ID: " + request.getDestInvoiceId());
            }
            if (!destInvoice.getStudent().getId().equals(request.getDestStudentId())) {
                return conflict(response, "Destination invoice does not belong to the destination student");
            }

            String sourceStudentName = studentRepository.findById(sourceStudentId)
                    .map(s -> s.getFirstName() + " " + s.getLastName())
                    .orElse(sourcePayment.getStudentName());

            User maker = getCurrentUser();

            PaymentTransfer transfer = PaymentTransfer.builder()
                    .sourcePaymentReference(request.getSourcePaymentReference())
                    .sourceTransactionId(sourcePayment.getId())
                    .originalPaymentAmount(nz(sourcePayment.getAmount()))
                    .amount(request.getAmount())
                    .sourceStudentId(sourceStudentId)
                    .sourceStudentName(sourceStudentName)
                    .sourceInvoiceId(sourceInvoiceId)
                    .destStudentId(request.getDestStudentId())
                    .destStudentName(destStudent.getFirstName() + " " + destStudent.getLastName())
                    .destInvoiceId(request.getDestInvoiceId())
                    .term(sourcePayment.getTerm())
                    .academicYear(sourcePayment.getYear())
                    .paymentMethod(sourcePayment.getPaymentMethod())
                    .reason(request.getReason())
                    .status(PaymentTransferStatus.PENDING_APPROVAL)
                    .createdBy(maker)
                    .createdAt(LocalDateTime.now())
                    .build();

            PaymentTransfer saved = paymentTransferRepository.save(transfer);

            auditService.log("PAYMENT_TRANSFER", "Requested transfer ID:", String.valueOf(saved.getId()),
                    "reference:", saved.getSourcePaymentReference(),
                    "amount:", String.valueOf(saved.getAmount()),
                    "from student:", String.valueOf(sourceStudentId),
                    "to student:", String.valueOf(request.getDestStudentId()));

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Payment transfer request created and pending approval. No balances changed.");
            response.setEntity(toDto(saved, transferable.subtract(request.getAmount())));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to create payment transfer: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Approve (checker): the only place money moves ----------------------

    @Override
    @Audit(module = "PAYMENT TRANSFER", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approveTransfer(Long id) {
        CustomResponse<PaymentTransferResponseDTO> response = new CustomResponse<>();
        try {
            PaymentTransfer transfer = paymentTransferRepository.findById(id).orElse(null);
            if (transfer == null) {
                return notFound(response, "Payment transfer not found with ID: " + id);
            }
            if (transfer.getStatus() != PaymentTransferStatus.PENDING_APPROVAL) {
                return conflict(response, "Transfer " + id + " is not pending approval (current status: "
                        + transfer.getStatus() + ")");
            }

            User checker = getCurrentUser();

            // Maker must not be the checker.
            if (transfer.getCreatedBy() != null
                    && transfer.getCreatedBy().getId() != null
                    && transfer.getCreatedBy().getId().equals(checker.getId())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("The maker of a transfer cannot approve it. A different approver is required.");
                response.setEntity(null);
                return response;
            }

            // Re-validate availability (excluding this transfer) in case other
            // transfers for the same reference were approved meanwhile.
            BigDecimal committedByOthers = nz(paymentTransferRepository
                    .sumCommittedForReferenceExcluding(transfer.getSourcePaymentReference(), transfer.getId()));
            BigDecimal transferable = nz(transfer.getOriginalPaymentAmount()).subtract(committedByOthers);
            if (transfer.getAmount().compareTo(transferable) > 0) {
                return conflict(response, "Amount " + transfer.getAmount()
                        + " can no longer be transferred; only " + transferable
                        + " remains for reference " + transfer.getSourcePaymentReference());
            }

            BigDecimal amount = transfer.getAmount();

            // 1. Remove the amount from the SOURCE invoice + finance.
            StudentInvoices sourceInvoice = studentInvoicesRepository.findById(transfer.getSourceInvoiceId())
                    .orElseThrow(() -> new RuntimeException(
                            "Source invoice not found with ID: " + transfer.getSourceInvoiceId()));
            applyToInvoice(sourceInvoice, amount.negate());
            adjustFinance(transfer.getSourceStudentId(), transfer.getTerm(),
                    transfer.getAcademicYear(), amount.negate());

            // 2. Add the amount to the DESTINATION invoice + finance.
            StudentInvoices destInvoice = studentInvoicesRepository.findById(transfer.getDestInvoiceId())
                    .orElseThrow(() -> new RuntimeException(
                            "Destination invoice not found with ID: " + transfer.getDestInvoiceId()));
            applyToInvoice(destInvoice, amount);
            adjustFinance(destInvoice.getStudent().getId(), destInvoice.getTerm(),
                    destInvoice.getAcademicYear(), amount);

            // 3. Write two audit transactions tied to the original reference.
            //    Describe the counterparty by name + admission number, not raw id.
            String sourceLabel = studentLabel(sourceInvoice.getStudent());
            String destLabel = studentLabel(destInvoice.getStudent());
            writeTransferTransaction(sourceInvoice, transfer,
                    FinanceTransaction.TransactionType.EXPENSE, "TRF-OUT-",
                    "Transferred out to " + destLabel);
            writeTransferTransaction(destInvoice, transfer,
                    FinanceTransaction.TransactionType.INCOME, "TRF-IN-",
                    "Transferred in from " + sourceLabel);

            // 4. Mark the transfer applied.
            transfer.setStatus(PaymentTransferStatus.APPROVED);
            transfer.setApprovedBy(checker);
            transfer.setApprovedAt(LocalDateTime.now());
            transfer.setAppliedAt(LocalDateTime.now());
            PaymentTransfer saved = paymentTransferRepository.save(transfer);

            auditService.log("PAYMENT_TRANSFER", "Approved transfer ID:", String.valueOf(saved.getId()),
                    "reference:", saved.getSourcePaymentReference(),
                    "amount:", String.valueOf(saved.getAmount()),
                    "approvedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payment transfer approved and applied.");
            response.setEntity(toDto(saved, null));

        } catch (RuntimeException e) {
            // Let the transaction roll back the partial mutations.
            throw new RuntimeException("Failed to approve payment transfer: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Reject (checker): no financial effect ------------------------------

    @Override
    @Audit(module = "PAYMENT TRANSFER", action = "REJECT")
    public CustomResponse<?> rejectTransfer(Long id, String reason) {
        CustomResponse<PaymentTransferResponseDTO> response = new CustomResponse<>();
        try {
            PaymentTransfer transfer = paymentTransferRepository.findById(id).orElse(null);
            if (transfer == null) {
                return notFound(response, "Payment transfer not found with ID: " + id);
            }
            if (transfer.getStatus() != PaymentTransferStatus.PENDING_APPROVAL) {
                return conflict(response, "Transfer " + id + " is not pending approval (current status: "
                        + transfer.getStatus() + ")");
            }

            User checker = getCurrentUser();
            transfer.setStatus(PaymentTransferStatus.REJECTED);
            transfer.setApprovedBy(checker);
            transfer.setApprovedAt(LocalDateTime.now());
            transfer.setRejectionReason(reason);
            PaymentTransfer saved = paymentTransferRepository.save(transfer);

            auditService.log("PAYMENT_TRANSFER", "Rejected transfer ID:", String.valueOf(saved.getId()),
                    "reference:", saved.getSourcePaymentReference(),
                    "rejectedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payment transfer rejected. No balances changed.");
            response.setEntity(toDto(saved, null));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reject payment transfer: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Reads --------------------------------------------------------------

    @Override
    public CustomResponse<?> listTransfers(PaymentTransferStatus status) {
        CustomResponse<List<PaymentTransferResponseDTO>> response = new CustomResponse<>();
        try {
            List<PaymentTransfer> transfers = (status == null)
                    ? paymentTransferRepository.findAllByOrderByCreatedAtDesc()
                    : paymentTransferRepository.findByStatusOrderByCreatedAtDesc(status);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(transfers.isEmpty() ? "No payment transfers found"
                    : "Payment transfers retrieved successfully");
            response.setEntity(transfers.stream().map(t -> toDto(t, null)).toList());
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve payment transfers: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getTransfer(Long id) {
        CustomResponse<PaymentTransferResponseDTO> response = new CustomResponse<>();
        try {
            PaymentTransfer transfer = paymentTransferRepository.findById(id).orElse(null);
            if (transfer == null) {
                return notFound(response, "Payment transfer not found with ID: " + id);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payment transfer retrieved successfully");
            response.setEntity(toDto(transfer, null));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve payment transfer: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Financial helpers --------------------------------------------------

    /**
     * Applies a signed delta to an invoice's paid amount and recomputes balance
     * and status. Positive delta = money in (payment applied), negative = money
     * out (payment removed).
     */
    private void applyToInvoice(StudentInvoices invoice, BigDecimal delta) {
        BigDecimal newPaid = nz(invoice.getAmountPaid()).add(delta);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Invoice " + invoice.getId()
                    + " does not have enough paid amount to remove " + delta.abs());
        }
        invoice.setAmountPaid(newPaid);
        invoice.setBalance(nz(invoice.getTotalAmount()).subtract(newPaid));
        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus('C'); // Cleared
        } else {
            invoice.setStatus('P'); // Pending / partially paid
        }
        studentInvoicesRepository.save(invoice);
    }

    /** Applies a signed delta to the student's finance rollup for the term/year. */
    private void adjustFinance(Long studentId, com.EduePoa.EP.Authentication.Enum.Term term,
                               java.time.Year year, BigDecimal delta) {
        Finance finance = financeRepository.findByStudentIdAndTermAndYear(studentId, term, year)
                .orElseThrow(() -> new RuntimeException(
                        "No finance record found for student " + studentId + " for " + term + " " + year));
        finance.setPaidAmount(nz(finance.getPaidAmount()).add(delta));
        finance.setBalance(nz(finance.getTotalFeeAmount()).subtract(finance.getPaidAmount()));
        finance.setLastUpdated(LocalDateTime.now());
        financeRepository.save(finance);
    }

    /** Records an audit FinanceTransaction for one side of the transfer. */
    private void writeTransferTransaction(StudentInvoices invoice, PaymentTransfer transfer,
                                          FinanceTransaction.TransactionType type,
                                          String refPrefix, String description) {
        Student student = invoice.getStudent();
        FinanceTransaction tx = new FinanceTransaction();
        tx.setStudentId(student.getId());
        tx.setStudentName(student.getFirstName() + " " + student.getLastName());
        tx.setAdmissionNumber(student.getAdmissionNumber());
        tx.setTransactionType(type);
        tx.setCategory("PAYMENT_TRANSFER");
        tx.setSource(FinanceTransaction.TransactionSource.PAYMENT_TRANSFER);
        tx.setAmount(transfer.getAmount());
        tx.setTransactionDate(LocalDate.now());
        tx.setDescription(description + " (transfer #" + transfer.getId() + ")");
        tx.setPaymentMethod(transfer.getPaymentMethod() != null
                ? transfer.getPaymentMethod() : FinanceTransaction.PaymentMethod.OTHER);
        tx.setReference(refPrefix + transfer.getSourcePaymentReference() + "-" + transfer.getId());
        tx.setInvoiceId(invoice.getId());
        tx.setTerm(invoice.getTerm());
        tx.setYear(invoice.getAcademicYear());
        financeTransactionRepository.save(tx);
    }

    // ---- Misc helpers -------------------------------------------------------

    private PaymentTransferResponseDTO toDto(PaymentTransfer t, BigDecimal remainingTransferableAfter) {
        return PaymentTransferResponseDTO.builder()
                .id(t.getId())
                .status(t.getStatus())
                .sourcePaymentReference(t.getSourcePaymentReference())
                .sourceTransactionId(t.getSourceTransactionId())
                .originalPaymentAmount(t.getOriginalPaymentAmount())
                .amount(t.getAmount())
                .remainingTransferableAfter(remainingTransferableAfter)
                .sourceStudentId(t.getSourceStudentId())
                .sourceStudentName(t.getSourceStudentName())
                .sourceInvoiceId(t.getSourceInvoiceId())
                .destStudentId(t.getDestStudentId())
                .destStudentName(t.getDestStudentName())
                .destInvoiceId(t.getDestInvoiceId())
                .term(t.getTerm())
                .academicYear(t.getAcademicYear())
                .paymentMethod(t.getPaymentMethod())
                .reason(t.getReason())
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null)
                .createdAt(t.getCreatedAt())
                .approvedByName(t.getApprovedBy() != null ? t.getApprovedBy().getUsername() : null)
                .approvedAt(t.getApprovedAt())
                .appliedAt(t.getAppliedAt())
                .rejectionReason(t.getRejectionReason())
                .build();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    /** "First Last (ADM123)" for use in human-readable transaction descriptions. */
    private String studentLabel(Student student) {
        String name = (student.getFirstName() + " " + student.getLastName()).trim();
        String adm = student.getAdmissionNumber();
        return adm != null && !adm.isBlank() ? name + " (" + adm + ")" : name;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private CustomResponse<PaymentTransferResponseDTO> bad(
            CustomResponse<PaymentTransferResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.BAD_REQUEST.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<PaymentTransferResponseDTO> notFound(
            CustomResponse<PaymentTransferResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<PaymentTransferResponseDTO> conflict(
            CustomResponse<PaymentTransferResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.CONFLICT.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
