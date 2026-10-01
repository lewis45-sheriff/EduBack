package com.EduePoa.EP.Transport.TransportTransactions.Approval;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.Transport;
import com.EduePoa.EP.Transport.TransportRepository;
import com.EduePoa.EP.Transport.TransportType;
import com.EduePoa.EP.Transport.TransportTermPrice.TransportTermPrice;
import com.EduePoa.EP.Transport.TransportTermPrice.TransportTermPriceRepository;
import com.EduePoa.EP.Transport.TransportTransactions.Approval.Request.SubmitTransportPaymentRequest;
import com.EduePoa.EP.Transport.TransportTransactions.Approval.Response.PendingTransportTransactionResponseDTO;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactions;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactionsRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Maker-checker workflow for transport fee payments, modelled on
 * {@link com.EduePoa.EP.TransactionReversal.TransactionReversalServiceImpl}. A payment is submitted
 * as {@code PENDING_APPROVAL} with NO effect on the transport ledger; a DIFFERENT user must approve
 * it before the real {@link TransportTransactions} row is written and the balance changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingTransportTransactionServiceImpl implements PendingTransportTransactionService {

    private final PendingTransportTransactionRepository pendingRepository;
    private final TransportTransactionsRepository transportTransactionsRepository;
    private final TransportTermPriceRepository transportTermPriceRepository;
    private final TransportRepository transportRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ---- Submit (maker): store only, no ledger effect ----------------------

    @Override
    @Audit(module = "TRANSPORT_TRANSACTION", action = "SUBMIT")
    public CustomResponse<?> submit(Long studentId, SubmitTransportPaymentRequest request) {
        CustomResponse<PendingTransportTransactionResponseDTO> response = new CustomResponse<>();
        try {
            if (request == null) {
                return bad(response, "Request body is required");
            }
            if (studentId == null) {
                return bad(response, "studentId is required");
            }
            if (request.getVehicleId() == null) {
                return bad(response, "vehicleId is required");
            }
            if (request.getTerm() == null || request.getYear() == null) {
                return bad(response, "Term and year are required to process a transport payment");
            }
            if (request.getTransportType() == null) {
                return bad(response, "transportType is required");
            }
            if (request.getAmount() == null || request.getAmount() <= 0) {
                return bad(response, "Payment amount must be greater than zero");
            }

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                return notFound(response, "Student not found");
            }
            Transport vehicle = transportRepository.findById(request.getVehicleId()).orElse(null);
            if (vehicle == null) {
                return notFound(response, "Transport not found");
            }

            // A student may only have one open request per vehicle/term/year/type at a time.
            boolean alreadyPending = pendingRepository
                    .existsByStudentIdAndVehicleIdAndTermAndYearAndTransportTypeAndStatus(
                            studentId, vehicle.getId(), request.getTerm(), request.getYear(),
                            request.getTransportType(), PendingTransportTransactionStatus.PENDING_APPROVAL);
            if (alreadyPending) {
                return conflict(response, "A transport payment for this student, vehicle, term and year "
                        + "is already pending approval.");
            }

            // Validate against the live balance (same rules the direct posting path enforces).
            Computation c = compute(vehicle, request.getTransportType(), request.getTerm(),
                    request.getYear(), request.getAmount(), studentId);
            if (c.error != null) {
                return bad(response, c.error);
            }

            User maker = getCurrentUser();

            PendingTransportTransaction pending = PendingTransportTransaction.builder()
                    .studentId(student.getId())
                    .studentName(fullName(student))
                    .admissionNumber(student.getAdmissionNumber())
                    .vehicleId(vehicle.getId())
                    .vehicleNumber(vehicle.getVehicleNumber())
                    .route(vehicle.getRoute())
                    .transportType(request.getTransportType())
                    .term(request.getTerm())
                    .year(request.getYear())
                    .amount(request.getAmount())
                    .paymentMethod(request.getPaymentMethod())
                    .expectedFee(c.expectedFee)
                    .totalPaidBeforeSnapshot(c.totalPaidBefore)
                    .arrearsBeforeSnapshot(c.arrearsBefore)
                    .reason(request.getReason())
                    .status(PendingTransportTransactionStatus.PENDING_APPROVAL)
                    .createdBy(maker)
                    .createdAt(LocalDateTime.now())
                    .build();

            PendingTransportTransaction saved = pendingRepository.save(pending);

            auditService.log("TRANSPORT_TRANSACTION", "Submitted payment request ID:", String.valueOf(saved.getId()),
                    "student:", String.valueOf(studentId), "amount:", String.valueOf(request.getAmount()));

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Transport payment submitted and pending approval. No balance changed.");
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            log.error("Error submitting transport payment: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to submit transport payment: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Approve (checker): the only place a ledger row is written ----------

    @Override
    @Audit(module = "TRANSPORT_TRANSACTION", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approve(Long id) {
        CustomResponse<PendingTransportTransactionResponseDTO> response = new CustomResponse<>();
        try {
            PendingTransportTransaction pending = pendingRepository.findById(id).orElse(null);
            if (pending == null) {
                return notFound(response, "Pending transport payment not found with ID: " + id);
            }
            if (pending.getStatus() != PendingTransportTransactionStatus.PENDING_APPROVAL) {
                return conflict(response, "Payment " + id + " is not pending approval (current status: "
                        + pending.getStatus() + ")");
            }

            User checker = getCurrentUser();

            // Maker must not be the checker.
            if (pending.getCreatedBy() != null
                    && pending.getCreatedBy().getId() != null
                    && pending.getCreatedBy().getId().equals(checker.getId())) {
                return conflict(response,
                        "The maker of a payment cannot approve it. A different approver is required.");
            }

            Student student = studentRepository.findById(pending.getStudentId()).orElse(null);
            if (student == null) {
                return notFound(response, "Student no longer exists: " + pending.getStudentId());
            }
            Transport vehicle = transportRepository.findById(pending.getVehicleId()).orElse(null);
            if (vehicle == null) {
                return notFound(response, "Vehicle no longer exists: " + pending.getVehicleId());
            }

            // Re-validate against the CURRENT balance at approval time — the ledger may have moved
            // since submission (e.g. another payment was approved first), so a stale amount must not
            // be posted blindly.
            Computation c = compute(vehicle, pending.getTransportType(), pending.getTerm(),
                    pending.getYear(), pending.getAmount(), pending.getStudentId());
            if (c.error != null) {
                return conflict(response, "Cannot approve: " + c.error);
            }

            TransportTransactions transaction = new TransportTransactions();
            transaction.setAmount(pending.getAmount());
            transaction.setPaymentMethod(pending.getPaymentMethod());
            transaction.setTerm(pending.getTerm());
            transaction.setYear(pending.getYear());
            transaction.setTransportType(pending.getTransportType());
            transaction.setStudent(student);
            transaction.setTransport(vehicle);
            transaction.setExpectedFee(c.expectedFee);
            transaction.setTotalPaidBeforeThis(c.totalPaidBefore);
            transaction.setTotalPaidAfterThis(c.totalPaidAfter);
            transaction.setArrearsAfterThis(c.arrearsAfter);
            transaction.setStatus(c.paymentStatus);

            TransportTransactions posted = transportTransactionsRepository.save(transaction);

            pending.setStatus(PendingTransportTransactionStatus.APPROVED);
            pending.setApprovedBy(checker);
            pending.setApprovedAt(LocalDateTime.now());
            pending.setPostedTransactionId(posted.getId());
            PendingTransportTransaction saved = pendingRepository.save(pending);

            auditService.log("TRANSPORT_TRANSACTION", "Approved payment request ID:", String.valueOf(saved.getId()),
                    "postedTransaction:", String.valueOf(posted.getId()),
                    "approvedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(c.paymentStatus.equals("COMPLETED")
                    ? "Payment approved and posted. Transport fee fully paid."
                    : String.format("Payment approved and posted. Remaining balance: %.2f", c.arrearsAfter));
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            // Let the transaction roll back any partial mutations.
            throw new RuntimeException("Failed to approve transport payment: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Reject (checker): no ledger effect --------------------------------

    @Override
    @Audit(module = "TRANSPORT_TRANSACTION", action = "REJECT")
    public CustomResponse<?> reject(Long id, String reason) {
        CustomResponse<PendingTransportTransactionResponseDTO> response = new CustomResponse<>();
        try {
            PendingTransportTransaction pending = pendingRepository.findById(id).orElse(null);
            if (pending == null) {
                return notFound(response, "Pending transport payment not found with ID: " + id);
            }
            if (pending.getStatus() != PendingTransportTransactionStatus.PENDING_APPROVAL) {
                return conflict(response, "Payment " + id + " is not pending approval (current status: "
                        + pending.getStatus() + ")");
            }

            User checker = getCurrentUser();
            pending.setStatus(PendingTransportTransactionStatus.REJECTED);
            pending.setApprovedBy(checker);
            pending.setApprovedAt(LocalDateTime.now());
            pending.setRejectionReason(reason);
            PendingTransportTransaction saved = pendingRepository.save(pending);

            auditService.log("TRANSPORT_TRANSACTION", "Rejected payment request ID:", String.valueOf(saved.getId()),
                    "rejectedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Transport payment rejected. No balance changed.");
            response.setEntity(toDto(saved));

        } catch (RuntimeException e) {
            log.error("Error rejecting transport payment: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to reject transport payment: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Reads -------------------------------------------------------------

    @Override
    public CustomResponse<?> list(PendingTransportTransactionStatus status) {
        CustomResponse<List<PendingTransportTransactionResponseDTO>> response = new CustomResponse<>();
        try {
            List<PendingTransportTransaction> rows = (status == null)
                    ? pendingRepository.findAllByOrderByCreatedAtDesc()
                    : pendingRepository.findByStatusOrderByCreatedAtDesc(status);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(rows.isEmpty() ? "No transport payment requests found"
                    : "Transport payment requests retrieved successfully");
            response.setEntity(rows.stream().map(this::toDto).toList());
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve transport payment requests: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> get(Long id) {
        CustomResponse<PendingTransportTransactionResponseDTO> response = new CustomResponse<>();
        try {
            PendingTransportTransaction pending = pendingRepository.findById(id).orElse(null);
            if (pending == null) {
                return notFound(response, "Pending transport payment not found with ID: " + id);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Transport payment request retrieved successfully");
            response.setEntity(toDto(pending));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve transport payment request: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- Shared fee/arrears computation + validation ------------------------

    /**
     * Computes the fee/arrears figures for a prospective payment and validates it against the live
     * balance. Mirrors the rules in {@code TransportServiceImpl.createTransportTransaction}: fee
     * must be configured, the balance must not already be fully paid, and the payment must not
     * exceed the outstanding balance. On any violation {@link Computation#error} is set.
     */
    private Computation compute(Transport vehicle, TransportType transportType, Term term,
                                Integer year, Double amount, Long studentId) {
        Computation c = new Computation();

        double expectedFee = orZero(getExpectedFee(vehicle, transportType, term, year));
        if (expectedFee <= 0.0) {
            c.error = "Transport fee not configured for " + transportType + " in " + term + " " + year;
            return c;
        }

        Double latestArrears = transportTransactionsRepository.getLatestArrears(
                studentId, vehicle.getId(), term, year, transportType);

        double totalPaidBefore;
        double arrearsBefore;
        if (latestArrears != null) {
            arrearsBefore = latestArrears;
            totalPaidBefore = expectedFee - latestArrears;
        } else {
            totalPaidBefore = 0.0;
            arrearsBefore = expectedFee;
        }

        if (arrearsBefore <= 0.0) {
            c.error = "Transport fee is already fully paid.";
            return c;
        }
        if (amount > arrearsBefore) {
            c.error = String.format(
                    "Payment amount (%.2f) exceeds remaining balance (%.2f). Maximum allowed payment is %.2f",
                    amount, arrearsBefore, arrearsBefore);
            return c;
        }

        double totalPaidAfter = totalPaidBefore + amount;
        double arrearsAfter = expectedFee - totalPaidAfter;
        String paymentStatus;
        if (Math.abs(arrearsAfter) < 0.01) {
            paymentStatus = "COMPLETED";
            arrearsAfter = 0.0;
        } else {
            paymentStatus = "PARTIAL";
        }

        c.expectedFee = round(expectedFee);
        c.totalPaidBefore = round(totalPaidBefore);
        c.arrearsBefore = round(arrearsBefore);
        c.totalPaidAfter = round(totalPaidAfter);
        c.arrearsAfter = round(arrearsAfter);
        c.paymentStatus = paymentStatus;
        return c;
    }

    /** Holds computed fee/arrears figures, or an error message when the payment is invalid. */
    private static final class Computation {
        String error;
        Double expectedFee;
        Double totalPaidBefore;
        Double arrearsBefore;
        Double totalPaidAfter;
        Double arrearsAfter;
        String paymentStatus;
    }

    private Double getExpectedFee(Transport transport, TransportType transportType, Term term, Integer year) {
        if (transportType == null || term == null || year == null) {
            return 0.0;
        }
        TransportTermPrice price = transportTermPriceRepository
                .findByVehicleAndTermAndYear(transport, term, year)
                .orElse(null);
        if (price == null) {
            return 0.0;
        }
        Double amount = transportType == TransportType.ONE_WAY
                ? price.getOneWayAmount()
                : price.getTwoWayAmount();
        return amount != null ? amount : 0.0;
    }

    // ---- Helpers -----------------------------------------------------------

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user in context");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private PendingTransportTransactionResponseDTO toDto(PendingTransportTransaction p) {
        return PendingTransportTransactionResponseDTO.builder()
                .id(p.getId())
                .studentId(p.getStudentId())
                .studentName(p.getStudentName())
                .admissionNumber(p.getAdmissionNumber())
                .vehicleId(p.getVehicleId())
                .vehicleNumber(p.getVehicleNumber())
                .route(p.getRoute())
                .transportType(p.getTransportType())
                .term(p.getTerm())
                .year(p.getYear())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .expectedFee(p.getExpectedFee())
                .totalPaidBeforeSnapshot(p.getTotalPaidBeforeSnapshot())
                .arrearsBeforeSnapshot(p.getArrearsBeforeSnapshot())
                .reason(p.getReason())
                .status(p.getStatus())
                .createdByName(userName(p.getCreatedBy()))
                .createdAt(p.getCreatedAt())
                .approvedByName(userName(p.getApprovedBy()))
                .approvedAt(p.getApprovedAt())
                .postedTransactionId(p.getPostedTransactionId())
                .rejectionReason(p.getRejectionReason())
                .build();
    }

    private static String userName(User u) {
        if (u == null) {
            return null;
        }
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        String full = (first + " " + last).trim();
        return !full.isEmpty() ? full : u.getUsername();
    }

    private static String fullName(Student s) {
        String first = s.getFirstName() != null ? s.getFirstName() : "";
        String last = s.getLastName() != null ? s.getLastName() : "";
        return (first + " " + last).trim();
    }

    private static double orZero(Double v) {
        return v != null ? v : 0.0;
    }

    private static Double round(Double v) {
        if (v == null) {
            return null;
        }
        return Math.round(v * 100.0) / 100.0;
    }

    private CustomResponse<PendingTransportTransactionResponseDTO> bad(
            CustomResponse<PendingTransportTransactionResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.BAD_REQUEST.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<PendingTransportTransactionResponseDTO> notFound(
            CustomResponse<PendingTransportTransactionResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<PendingTransportTransactionResponseDTO> conflict(
            CustomResponse<PendingTransportTransactionResponseDTO> r, String msg) {
        r.setStatusCode(HttpStatus.CONFLICT.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
