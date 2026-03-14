package com.EduePoa.EP.Procurement.SupplierPayments;


import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Authentication.Enum.PaymentStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.Ledger.LedgerService;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoice;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoiceRepository;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboardingRepository;
import com.EduePoa.EP.Procurement.SupplierPayments.Requests.PaymentRequestDTO;
import com.EduePoa.EP.Procurement.SupplierPayments.Responses.PaymentResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SupplierPaymentServiceImpl implements SupplierPaymentService {

    private final PaymentRepository paymentRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierOnboardingRepository supplierOnboardingRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    @Override
    @Audit(module = "SUPPLIER PAYMENT", action = "RECORD")
    @Transactional
    public CustomResponse<?> recordPayment(PaymentRequestDTO request) {
        CustomResponse<PaymentResponseDTO> response = new CustomResponse<>();
        try {
            SupplierInvoice invoice = supplierInvoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            //  Allow payment on APPROVED or PARTIALLY_PAID invoices
            if (invoice.getStatus() != InvoiceStatus.APPROVED
                    && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
                throw new RuntimeException(
                        "Only APPROVED or PARTIALLY_PAID invoices can be paid. Current status: " + invoice.getStatus());
            }

            //  Calculate current outstanding balance
            BigDecimal invoiceTotal = invoice.getTotalAmount();
            BigDecimal currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;

            //  Also sum any existing PENDING_APPROVAL payments (not yet approved/applied)
            BigDecimal pendingPaymentsTotal = paymentRepository
                    .sumAmountByInvoiceIdAndStatus(invoice.getId(), PaymentStatus.PENDING_APPROVAL);
            if (pendingPaymentsTotal == null) pendingPaymentsTotal = BigDecimal.ZERO;

            //  Effective outstanding = total - already paid - already pending
            BigDecimal effectiveOutstanding = invoiceTotal.subtract(currentPaid).subtract(pendingPaymentsTotal);

            //  Validate payment does not exceed effective outstanding balance
            if (request.getAmount().compareTo(effectiveOutstanding) > 0) {
                throw new RuntimeException(String.format(
                        "Payment amount (%.2f) exceeds available outstanding balance (%.2f). " +
                        "Invoice total: %.2f, Already paid: %.2f, Pending approval: %.2f",
                        request.getAmount(), effectiveOutstanding, invoiceTotal, currentPaid, pendingPaymentsTotal));
            }

            SupplierOnboarding supplier = invoice.getSupplier();

            //  Serialize the payment request to JSON for approval workflow
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            String paymentDataJson = objectMapper.writeValueAsString(request);

            //  Create payment record with PENDING_APPROVAL status (no financial effects yet)
            User currentUser = getCurrentUser();

            Payment payment = Payment.builder()
                    .supplierInvoice(invoice)
                    .supplier(supplier)
                    .paymentDate(request.getPaymentDate())
                    .amount(request.getAmount())
                    .paymentMethod(request.getPaymentMethod())
                    .referenceNumber(request.getReferenceNumber())
                    .remarks(request.getRemarks())
                    .status(PaymentStatus.PENDING_APPROVAL)
                    .pendingPaymentData(paymentDataJson)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();

            Payment savedPayment = paymentRepository.save(payment);
//            auditService.logAction("POST", "SUPPLIER PAYMENT", savedPayment, "Payment initiated for invoice: " + invoice.getInvoiceNumber() + ", amount: " + request.getAmount());

            response.setMessage("Payment initiated successfully and pending approval");
            response.setEntity(toResponseDTO(savedPayment));
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (RuntimeException e) {
            response.setMessage("Payment recording failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Payment recording failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER PAYMENT", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approvePayment(Long paymentId) {
        CustomResponse<PaymentResponseDTO> response = new CustomResponse<>();
        try {
            // Fetch payment and validate
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
                throw new RuntimeException(
                        "Only PENDING_APPROVAL payments can be approved. Current status: " + payment.getStatus());
            }

            // Get linked invoice
            SupplierInvoice invoice = payment.getSupplierInvoice();

            //  Re-validate outstanding balance at approval time (it may have changed)
            BigDecimal invoiceTotal = invoice.getTotalAmount();
            BigDecimal newPaidAmount = getNewPaidAmount(invoice, invoiceTotal, payment);
            BigDecimal newOutstanding = invoiceTotal.subtract(newPaidAmount);
            invoice.setPaidAmount(newPaidAmount);
            invoice.setOutstandingBalance(newOutstanding);

            //  Set invoice status based on remaining balance
            if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }
            supplierInvoiceRepository.save(invoice);

            //  Update supplier balance
            SupplierOnboarding supplier = payment.getSupplier();
            BigDecimal supplierBalance = supplier.getCurrentBalance() != null
                    ? supplier.getCurrentBalance()
                    : BigDecimal.ZERO;
            supplier.setCurrentBalance(supplierBalance.subtract(payment.getAmount()));
            supplierOnboardingRepository.save(supplier);

            //  Mark payment as APPROVED
            User approver = getCurrentUser();
            payment.setStatus(PaymentStatus.APPROVED);
            payment.setApprovedBy(approver);
            payment.setApprovedAt(LocalDateTime.now());
            payment.setPendingPaymentData(null); // Clear the JSON data after approval
            paymentRepository.save(payment);

            // Record DEBIT ledger entry for money going out
            try {
                ledgerService.recordDebit(
                        payment.getAmount(),
                        "Supplier payment to " + supplier.getBusinessName() + " (Invoice: " + invoice.getInvoiceNumber() + ")",
                        payment.getId(),
                        payment.getReferenceNumber(),
                        payment.getPaymentDate(),
                        approver
                );
            } catch (Exception ledgerEx) {
                // Log but don't fail the approval
                // Ledger entry can be manually reconciled
            }

            String statusMsg = invoice.getStatus() == InvoiceStatus.PAID
                    ? "Payment approved. Invoice is now fully PAID."
                    : String.format("Payment approved. Invoice is PARTIALLY_PAID. Outstanding balance: %.2f",
                            newOutstanding);

            response.setMessage(statusMsg);
            response.setEntity(toResponseDTO(payment));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("APPROVE", "SUPPLIER PAYMENT", payment, "Payment approved: #" + payment.getId() + ", amount: " + payment.getAmount() + " for supplier: " + supplier.getBusinessName());

        } catch (RuntimeException e) {
            response.setMessage("Payment approval failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Payment approval failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @NotNull
    private static BigDecimal getNewPaidAmount(SupplierInvoice invoice, BigDecimal invoiceTotal, Payment payment) {
        BigDecimal currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal currentOutstanding = invoiceTotal.subtract(currentPaid);

        if (payment.getAmount().compareTo(currentOutstanding) > 0) {
            throw new RuntimeException(String.format(
                    "Payment amount (%.2f) now exceeds outstanding balance (%.2f). Another payment may have been approved since this was initiated.",
                    payment.getAmount(), currentOutstanding));
        }

        //  Apply financial effects — update invoice balance
        BigDecimal newPaidAmount = currentPaid.add(payment.getAmount());
        return newPaidAmount;
    }

    @Override
    @Audit(module = "SUPPLIER PAYMENT", action = "REJECT")
    @Transactional
    public CustomResponse<?> rejectPayment(Long paymentId, String reason) {
        CustomResponse<PaymentResponseDTO> response = new CustomResponse<>();
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
                throw new RuntimeException(
                        "Only PENDING_APPROVAL payments can be rejected. Current status: " + payment.getStatus());
            }

            //  Mark payment as REJECTED — no financial effects applied
            User rejector = getCurrentUser();
            payment.setStatus(PaymentStatus.REJECTED);
            payment.setApprovedBy(rejector); // tracks who rejected
            payment.setApprovedAt(LocalDateTime.now());
            payment.setRemarks(reason != null ? reason : payment.getRemarks());
            paymentRepository.save(payment);

            response.setMessage("Payment rejected successfully");
            response.setEntity(toResponseDTO(payment));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("REJECT", "SUPPLIER PAYMENT", payment, "Payment rejected: #" + payment.getId() + (reason != null ? ", reason: " + reason : ""));

        } catch (RuntimeException e) {
            response.setMessage("Payment rejection failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Payment rejection failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER PAYMENT", action = "EDIT")
    @Transactional
    public CustomResponse<?> editPayment(Long paymentId, PaymentRequestDTO request) {
        CustomResponse<PaymentResponseDTO> response = new CustomResponse<>();
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            //  Only allow editing PENDING_APPROVAL or REJECTED payments
            if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL
                    && payment.getStatus() != PaymentStatus.REJECTED) {
                throw new RuntimeException(
                        "Only PENDING_APPROVAL or REJECTED payments can be edited. Current status: " + payment.getStatus());
            }

            //  Validate the new amount against invoice outstanding balance
            SupplierInvoice invoice = payment.getSupplierInvoice();
            BigDecimal invoiceTotal = invoice.getTotalAmount();
            BigDecimal currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal currentOutstanding = invoiceTotal.subtract(currentPaid);

            if (request.getAmount().compareTo(currentOutstanding) > 0) {
                throw new RuntimeException(String.format(
                        "Payment amount (%.2f) exceeds outstanding balance (%.2f). Invoice total: %.2f, Already paid: %.2f",
                        request.getAmount(), currentOutstanding, invoiceTotal, currentPaid));
            }

            //  Serialize updated request to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            String paymentDataJson = objectMapper.writeValueAsString(request);

            //  Update payment fields
            payment.setAmount(request.getAmount());
            payment.setPaymentDate(request.getPaymentDate());
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setReferenceNumber(request.getReferenceNumber());
            payment.setRemarks(request.getRemarks());
            payment.setPendingPaymentData(paymentDataJson);

            //  Reset status to PENDING_APPROVAL
            payment.setStatus(PaymentStatus.PENDING_APPROVAL);
            payment.setApprovedBy(null);
            payment.setApprovedAt(null);

            paymentRepository.save(payment);

            response.setMessage("Payment updated successfully and reset to PENDING_APPROVAL");
            response.setEntity(toResponseDTO(payment));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("PUT", "SUPPLIER PAYMENT", payment, "Payment edited: #" + payment.getId() + ", new amount: " + request.getAmount());

        } catch (RuntimeException e) {
            response.setMessage("Payment edit failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Payment edit failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<PaymentResponseDTO> getPaymentById(Long id) {
        CustomResponse<PaymentResponseDTO> response = new CustomResponse<>();
        try {
            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            response.setMessage("Payment retrieved successfully");
            response.setEntity(toResponseDTO(payment));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("GET", "SUPPLIER PAYMENT", payment, "Payment retrieved: #" + payment.getId());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving payment: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponseDTO> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable)
                .map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponseDTO> getPaymentsBySupplier(Long supplierId, Pageable pageable) {
        return supplierOnboardingRepository.findById(supplierId)
                .or(() -> supplierOnboardingRepository.findByUser_Id(supplierId))
                .map(SupplierOnboarding::getId)
                .map(actualId -> paymentRepository.findBySupplierId(actualId, pageable))
                .orElse(Page.empty())
                .map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponseDTO> getPaymentsByInvoice(Long invoiceId, Pageable pageable) {
        return paymentRepository.findBySupplierInvoiceId(invoiceId, pageable)
                .map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getSupplierBalance(Long supplierId) {
        CustomResponse<BigDecimal> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierOnboardingRepository.findById(supplierId)
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(supplierId)
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId)));

            BigDecimal balance = supplier.getCurrentBalance() != null
                    ? supplier.getCurrentBalance()
                    : BigDecimal.ZERO;

            response.setMessage("Supplier balance retrieved successfully");
            response.setEntity(balance);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving supplier balance: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private PaymentResponseDTO toResponseDTO(Payment payment) {
        SupplierInvoice invoice = payment.getSupplierInvoice();
        return PaymentResponseDTO.builder()
                .id(payment.getId())
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .supplierId(payment.getSupplier().getId())
                .supplierName(payment.getSupplier().getBusinessName())
                .paymentDate(payment.getPaymentDate())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .referenceNumber(payment.getReferenceNumber())
                .remarks(payment.getRemarks())
                .invoiceTotalAmount(invoice.getTotalAmount())
                .totalPaidAmount(invoice.getPaidAmount())
                .outstandingBalance(invoice.getOutstandingBalance())
                .invoiceStatus(invoice.getStatus().name())
                .paymentStatus(payment.getStatus().name())
                .createdBy(payment.getCreatedBy() != null ? payment.getCreatedBy().getEmail() : null)
                .createdAt(payment.getCreatedAt())
                .approvedBy(payment.getApprovedBy() != null ? payment.getApprovedBy().getEmail() : null)
                .approvedAt(payment.getApprovedAt())
                .build();
    }
}
