package com.EduePoa.EP.TransactionReversal;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
import com.EduePoa.EP.PaymentTransfer.PaymentTransfer;
import com.EduePoa.EP.PaymentTransfer.PaymentTransferRepository;
import com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.TransactionReversal.Response.ReversalResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maker-checker reversal of a finance transaction, modelled on
 * {@link com.EduePoa.EP.PaymentTransfer.PaymentTransferServiceImpl}: the request is
 * stored as PENDING_APPROVAL with no financial effect, and a DIFFERENT user must
 * approve it before any balances change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionReversalServiceImpl implements TransactionReversalService {

    private final TransactionReversalRepository reversalRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceRepository financeRepository;
    private final PaymentTransferRepository paymentTransferRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.EduePoa.EP.FileStorage.FileStorageService fileStorageService;

    // ---- Create (maker): store only, no financial effect --------------------

    @Override
    @Audit(module = "TRANSACTION REVERSAL", action = "CREATE")
    public CustomResponse<?> createReversal(Long financeTransactionId, String reason) {
        CustomResponse<ReversalResponseDTO> response = new CustomResponse<>();
        try {
            if (financeTransactionId == null) {
                return bad(response, "financeTransactionId is required");
            }
            if (reason == null || reason.isBlank()) {
                return bad(response, "A reason is required to request a reversal");
            }

            FinanceTransaction txn = financeTransactionRepository.findById(financeTransactionId).orElse(null);
            if (txn == null) {
                return notFound(response, "Finance transaction not found with ID: " + financeTransactionId);
            }

            // Cannot request a reversal that is already pending or applied.
            boolean alreadyRequested = reversalRepository.existsByFinanceTransactionIdAndStatusIn(
                    financeTransactionId,
                    List.of(TransactionReversalStatus.PENDING_APPROVAL, TransactionReversalStatus.APPROVED));
            if (alreadyRequested) {
                return conflict(response, "A reversal for transaction " + financeTransactionId
                        + " is already pending or has been applied");
            }

            if (txn.getInvoiceId() == null) {
                return conflict(response, "Transaction " + financeTransactionId
                        + " is not linked to an invoice and cannot be deleted here");
            }

            // Deletion eligibility by origin: only manual entries (and payment-transfer
            // legs, which are undone by reversing the transfer) may be deleted. Money that
            // arrived via an M-Pesa callback/STK push is real and can only be transferred,
            // never deleted.
            if (!isDeletable(txn)) {
                return conflict(response, "Transaction " + financeTransactionId
                        + " originated from an M-Pesa payment and cannot be deleted. "
                        + "It can only be transferred to another student.");
            }

            boolean transferOriginated = isTransferOriginated(txn);
            Long paymentTransferId = transferOriginated ? parseTransferId(txn.getReference()) : null;

            User maker = getCurrentUser();

            TransactionReversal reversal = TransactionReversal.builder()
                    .financeTransactionId(txn.getId())
                    .studentId(txn.getStudentId())
                    .studentName(txn.getStudentName())
                    .invoiceId(txn.getInvoiceId())
                    .transactionType(txn.getTransactionType())
                    .category(txn.getCategory())
                    .amount(txn.getAmount())
                    .transactionReference(txn.getReference())
                    .term(txn.getTerm())
                    .academicYear(txn.getYear())
                    .transferOriginated(transferOriginated)
                    .paymentTransferId(paymentTransferId)
                    .reason(reason)
                    .status(TransactionReversalStatus.PENDING_APPROVAL)
                    .createdBy(maker)
                    .createdAt(LocalDateTime.now())
                    .build();

            TransactionReversal saved = reversalRepository.save(reversal);

            auditService.log("TRANSACTION_REVERSAL", "Requested reversal ID:", String.valueOf(saved.getId()),
                    "transaction:", String.valueOf(txn.getId()),
                    "amount:", String.valueOf(txn.getAmount()),
                    "transfer:", String.valueOf(transferOriginated));

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Reversal request created and pending approval. No balances changed.");
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to create reversal request: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Approve (checker): the only place balances are restored ------------

    @Override
    @Audit(module = "TRANSACTION REVERSAL", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approveReversal(Long id) {
        CustomResponse<ReversalResponseDTO> response = new CustomResponse<>();
        try {
            TransactionReversal reversal = reversalRepository.findById(id).orElse(null);
            if (reversal == null) {
                return notFound(response, "Reversal not found with ID: " + id);
            }
            if (reversal.getStatus() != TransactionReversalStatus.PENDING_APPROVAL) {
                return conflict(response, "Reversal " + id + " is not pending approval (current status: "
                        + reversal.getStatus() + ")");
            }

            User checker = getCurrentUser();

            // Maker must not be the checker.
            if (reversal.getCreatedBy() != null
                    && reversal.getCreatedBy().getId() != null
                    && reversal.getCreatedBy().getId().equals(checker.getId())) {
                return conflict(response,
                        "The maker of a reversal cannot approve it. A different approver is required.");
            }

            FinanceTransaction txn = financeTransactionRepository.findById(reversal.getFinanceTransactionId())
                    .orElseThrow(() -> new RuntimeException(
                            "Original transaction no longer exists: " + reversal.getFinanceTransactionId()));

            if (reversal.isTransferOriginated()) {
                reverseTransfer(reversal, txn);
            } else {
                reverseSimpleTransaction(txn);
                // Clean up any supporting document attached to the deleted transaction.
                if (txn.getAttachmentUrl() != null) {
                    fileStorageService.deleteByWebPath(txn.getAttachmentUrl());
                }
                financeTransactionRepository.delete(txn);
            }

            reversal.setStatus(TransactionReversalStatus.APPROVED);
            reversal.setApprovedBy(checker);
            reversal.setApprovedAt(LocalDateTime.now());
            reversal.setAppliedAt(LocalDateTime.now());
            TransactionReversal saved = reversalRepository.save(reversal);

            auditService.log("TRANSACTION_REVERSAL", "Approved reversal ID:", String.valueOf(saved.getId()),
                    "transaction:", String.valueOf(reversal.getFinanceTransactionId()),
                    "approvedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Reversal approved and applied. Invoice and balances restored.");
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            // Let the transaction roll back any partial mutations.
            throw new RuntimeException("Failed to approve reversal: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Reject (checker): no financial effect ------------------------------

    @Override
    @Audit(module = "TRANSACTION REVERSAL", action = "REJECT")
    public CustomResponse<?> rejectReversal(Long id, String reason) {
        CustomResponse<ReversalResponseDTO> response = new CustomResponse<>();
        try {
            TransactionReversal reversal = reversalRepository.findById(id).orElse(null);
            if (reversal == null) {
                return notFound(response, "Reversal not found with ID: " + id);
            }
            if (reversal.getStatus() != TransactionReversalStatus.PENDING_APPROVAL) {
                return conflict(response, "Reversal " + id + " is not pending approval (current status: "
                        + reversal.getStatus() + ")");
            }

            User checker = getCurrentUser();
            reversal.setStatus(TransactionReversalStatus.REJECTED);
            reversal.setApprovedBy(checker);
            reversal.setApprovedAt(LocalDateTime.now());
            reversal.setRejectionReason(reason);
            TransactionReversal saved = reversalRepository.save(reversal);

            auditService.log("TRANSACTION_REVERSAL", "Rejected reversal ID:", String.valueOf(saved.getId()),
                    "rejectedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Reversal rejected. No balances changed.");
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reject reversal: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Reads --------------------------------------------------------------

    @Override
    public CustomResponse<?> listReversals(TransactionReversalStatus status) {
        CustomResponse<List<ReversalResponseDTO>> response = new CustomResponse<>();
        try {
            List<TransactionReversal> reversals = (status == null)
                    ? reversalRepository.findAllByOrderByCreatedAtDesc()
                    : reversalRepository.findByStatusOrderByCreatedAtDesc(status);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(reversals.isEmpty() ? "No reversals found" : "Reversals retrieved successfully");
            response.setEntity(reversals.stream().map(this::toDto).toList());
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve reversals: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getReversal(Long id) {
        CustomResponse<ReversalResponseDTO> response = new CustomResponse<>();
        try {
            TransactionReversal reversal = reversalRepository.findById(id).orElse(null);
            if (reversal == null) {
                return notFound(response, "Reversal not found with ID: " + id);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Reversal retrieved successfully");
            response.setEntity(toDto(reversal));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve reversal: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Reversal mechanics -------------------------------------------------

    /**
     * Undoes a plain (non-transfer) transaction's effect on its invoice + finance.
     * INCOME originally reduced the balance / raised amountPaid, so reversing it
     * removes the payment; EXPENSE is the mirror.
     */
    private void reverseSimpleTransaction(FinanceTransaction txn) {
        StudentInvoices invoice = studentInvoicesRepository.findById(txn.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + txn.getInvoiceId()));
        BigDecimal amount = nz(txn.getAmount());
        if (txn.getTransactionType() == FinanceTransaction.TransactionType.INCOME) {
            applyToInvoice(invoice, amount.negate());
            adjustFinance(txn.getStudentId(), invoice, amount.negate());
        } else {
            applyToInvoice(invoice, amount);
            adjustFinance(txn.getStudentId(), invoice, amount);
        }
    }

    /**
     * Reverses a transfer-originated transaction by undoing BOTH legs of the
     * underlying {@link PaymentTransfer}: money is removed from the destination
     * student and returned to the source student, exactly mirroring what the
     * transfer approval did. Both audit transaction legs are deleted and the
     * transfer is marked REVERSED.
     */
    private void reverseTransfer(TransactionReversal reversal, FinanceTransaction targetTxn) {
        PaymentTransfer transfer = resolveTransfer(reversal, targetTxn);
        if (transfer == null) {
            throw new RuntimeException("Could not resolve the payment transfer behind transaction "
                    + targetTxn.getId() + "; cannot safely return the money to the source student");
        }
        if (transfer.getStatus() != PaymentTransferStatus.APPROVED) {
            throw new RuntimeException("Payment transfer " + transfer.getId()
                    + " is not in an APPROVED state and cannot be reversed (status: " + transfer.getStatus() + ")");
        }

        BigDecimal amount = nz(transfer.getAmount());

        // Undo destination side: remove the money that had arrived.
        StudentInvoices destInvoice = studentInvoicesRepository.findById(transfer.getDestInvoiceId())
                .orElseThrow(() -> new RuntimeException(
                        "Destination invoice not found with ID: " + transfer.getDestInvoiceId()));
        applyToInvoice(destInvoice, amount.negate());
        adjustFinance(destInvoice.getStudent().getId(), destInvoice, amount.negate());

        // Return the money to the source student.
        StudentInvoices sourceInvoice = studentInvoicesRepository.findById(transfer.getSourceInvoiceId())
                .orElseThrow(() -> new RuntimeException(
                        "Source invoice not found with ID: " + transfer.getSourceInvoiceId()));
        applyToInvoice(sourceInvoice, amount);
        adjustFinance(sourceInvoice.getStudent().getId(), sourceInvoice, amount);

        // Remove both audit transaction legs (TRF-OUT on source, TRF-IN on dest).
        deleteTransferLegs(transfer);

        // Mark the transfer as reversed so it no longer commits the source payment.
        transfer.setStatus(PaymentTransferStatus.REVERSED);
        paymentTransferRepository.save(transfer);
    }

    /** Deletes the paired PAYMENT_TRANSFER audit transactions for a transfer. */
    private void deleteTransferLegs(PaymentTransfer transfer) {
        String suffix = "-" + transfer.getSourcePaymentReference() + "-" + transfer.getId();
        for (FinanceTransaction ft : financeTransactionRepository.findAll()) {
            String ref = ft.getReference();
            if (ref != null && (ref.equals("TRF-OUT" + suffix) || ref.equals("TRF-IN" + suffix))) {
                financeTransactionRepository.delete(ft);
            }
        }
    }

    /** Locates the PaymentTransfer for a transfer-originated transaction. */
    private PaymentTransfer resolveTransfer(TransactionReversal reversal, FinanceTransaction txn) {
        Long transferId = reversal.getPaymentTransferId() != null
                ? reversal.getPaymentTransferId()
                : parseTransferId(txn.getReference());
        if (transferId == null) {
            return null;
        }
        return paymentTransferRepository.findById(transferId).orElse(null);
    }

    private boolean isTransferOriginated(FinanceTransaction txn) {
        if (txn.getSource() == FinanceTransaction.TransactionSource.PAYMENT_TRANSFER) {
            return true;
        }
        if ("PAYMENT_TRANSFER".equalsIgnoreCase(txn.getCategory())) {
            return true;
        }
        String ref = txn.getReference();
        return ref != null && (ref.startsWith("TRF-IN-") || ref.startsWith("TRF-OUT-"));
    }

    /**
     * A transaction may be deleted only when it was entered manually, or is a
     * payment-transfer leg (which is undone by reversing the transfer so the money
     * returns to the source student). M-Pesa gateway payments are never deletable.
     * <p>
     * Legacy rows created before the {@code source} field existed have a null source;
     * as a safety net those are treated as non-deletable when the payment method is
     * M-Pesa, so historical callback payments stay protected without a data migration.
     */
    private boolean isDeletable(FinanceTransaction txn) {
        FinanceTransaction.TransactionSource src = txn.getSource();
        if (src == FinanceTransaction.TransactionSource.MANUAL
                || src == FinanceTransaction.TransactionSource.PAYMENT_TRANSFER) {
            return true;
        }
        if (src == null) {
            // Unstamped legacy row: protect anything that looks like an M-Pesa payment.
            return txn.getPaymentMethod() != FinanceTransaction.PaymentMethod.MPESA
                    && !isTransferOriginated(txn);
        }
        // MPESA_CALLBACK, MPESA_STK, SYSTEM -> not deletable.
        return false;
    }

    /** Transfer references look like {@code TRF-IN-<sourceRef>-<transferId>}; extract the trailing id. */
    private Long parseTransferId(String reference) {
        if (reference == null) {
            return null;
        }
        int dash = reference.lastIndexOf('-');
        if (dash < 0 || dash == reference.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(reference.substring(dash + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Financial helpers (mirror PaymentTransferServiceImpl) ---------------

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

    private void adjustFinance(Long studentId, StudentInvoices invoice, BigDecimal delta) {
        Finance finance = financeRepository
                .findByStudentIdAndTermAndYear(studentId, invoice.getTerm(), invoice.getAcademicYear())
                .orElseThrow(() -> new RuntimeException("No finance record found for student " + studentId
                        + " for " + invoice.getTerm() + " " + invoice.getAcademicYear()));
        finance.setPaidAmount(nz(finance.getPaidAmount()).add(delta));
        finance.setBalance(nz(finance.getTotalFeeAmount()).subtract(finance.getPaidAmount()));
        finance.setLastUpdated(LocalDateTime.now());
        financeRepository.save(finance);
    }

    private ReversalResponseDTO toDto(TransactionReversal r) {
        return ReversalResponseDTO.builder()
                .id(r.getId())
                .financeTransactionId(r.getFinanceTransactionId())
                .studentId(r.getStudentId())
                .studentName(r.getStudentName())
                .invoiceId(r.getInvoiceId())
                .transactionType(r.getTransactionType())
                .category(r.getCategory())
                .amount(r.getAmount())
                .transactionReference(r.getTransactionReference())
                .term(r.getTerm())
                .academicYear(r.getAcademicYear())
                .transferOriginated(r.isTransferOriginated())
                .paymentTransferId(r.getPaymentTransferId())
                .reason(r.getReason())
                .status(r.getStatus())
                .createdByName(r.getCreatedBy() != null ? r.getCreatedBy().getUsername() : null)
                .createdAt(r.getCreatedAt())
                .approvedByName(r.getApprovedBy() != null ? r.getApprovedBy().getUsername() : null)
                .approvedAt(r.getApprovedAt())
                .appliedAt(r.getAppliedAt())
                .rejectionReason(r.getRejectionReason())
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

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private CustomResponse<ReversalResponseDTO> bad(CustomResponse<ReversalResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.BAD_REQUEST.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<ReversalResponseDTO> notFound(CustomResponse<ReversalResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<ReversalResponseDTO> conflict(CustomResponse<ReversalResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.CONFLICT.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
