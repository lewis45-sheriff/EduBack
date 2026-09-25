package com.EduePoa.EP.PaymentTransfer;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the student-to-student payment transfer maker-checker flow.
 * Verifies that creating a transfer stores intent only (no balance changes),
 * that the amount is derived from and validated against the referenced payment
 * (full and partial), that approval is the only point money moves, that a maker
 * cannot approve their own request, and that reject moves no money.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTransferServiceImplTest {

    @Mock private PaymentTransferRepository paymentTransferRepository;
    @Mock private FinanceTransactionRepository financeTransactionRepository;
    @Mock private StudentInvoicesRepository studentInvoicesRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private FinanceRepository financeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private PaymentTransferServiceImpl service;

    private static final String MAKER_EMAIL = "maker@school.test";
    private static final String CHECKER_EMAIL = "checker@school.test";
    private static final String REFERENCE = "SGR7XYZ12K";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ---------------------------------------------------------

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private User user(long id, String email) {
        return User.builder().id(id).email(email).username(email).build();
    }

    private Student student(long id, String first, String last) {
        Student s = new Student();
        s.setId(id);
        s.setFirstName(first);
        s.setLastName(last);
        s.setAdmissionNumber("ADM-" + id);
        return s;
    }

    private FinanceTransaction payment(long id, long studentId, long invoiceId, String amount) {
        FinanceTransaction t = new FinanceTransaction();
        t.setId(id);
        t.setStudentId(studentId);
        t.setStudentName("Src Student");
        t.setInvoiceId(invoiceId);
        t.setAmount(new BigDecimal(amount));
        t.setTransactionType(FinanceTransaction.TransactionType.INCOME);
        t.setPaymentMethod(FinanceTransaction.PaymentMethod.MPESA);
        t.setTerm(Term.TERM_1);
        t.setYear(Year.of(2026));
        return t;
    }

    private StudentInvoices invoice(long id, Student owner, String total, String paid) {
        StudentInvoices inv = StudentInvoices.builder()
                .id(id)
                .student(owner)
                .term(Term.TERM_1)
                .academicYear(Year.of(2026))
                .totalAmount(new BigDecimal(total))
                .amountPaid(new BigDecimal(paid))
                .balance(new BigDecimal(total).subtract(new BigDecimal(paid)))
                .status('P')
                .isDeleted('N')
                .build();
        return inv;
    }

    private Finance finance(long studentId, String total, String paid) {
        Finance f = new Finance();
        f.setStudentId(studentId);
        f.setTotalFeeAmount(new BigDecimal(total));
        f.setPaidAmount(new BigDecimal(paid));
        f.setBalance(new BigDecimal(total).subtract(new BigDecimal(paid)));
        f.setTerm(Term.TERM_1);
        f.setYear(Year.of(2026));
        return f;
    }

    private CreatePaymentTransferRequest request(String amount, long destStudentId, long destInvoiceId) {
        return CreatePaymentTransferRequest.builder()
                .sourcePaymentReference(REFERENCE)
                .amount(new BigDecimal(amount))
                .destStudentId(destStudentId)
                .destInvoiceId(destInvoiceId)
                .reason("test")
                .build();
    }

    // ---- create ----------------------------------------------------------

    @Test
    @DisplayName("Create derives source from the reference, stores PENDING, and touches no balances")
    void create_storesPendingOnly() {
        authenticateAs(MAKER_EMAIL);
        when(userRepository.findByEmail(MAKER_EMAIL)).thenReturn(Optional.of(user(1L, MAKER_EMAIL)));
        when(financeTransactionRepository.findByReferenceAndTransactionType(
                REFERENCE, FinanceTransaction.TransactionType.INCOME))
                .thenReturn(Optional.of(payment(900L, 10L, 210L, "5000")));
        when(paymentTransferRepository.sumCommittedForReference(REFERENCE)).thenReturn(BigDecimal.ZERO);
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L, "Ann", "Doe")));
        when(studentRepository.findById(22L)).thenReturn(Optional.of(student(22L, "Jon", "Kay")));
        when(studentInvoicesRepository.findById(305L))
                .thenReturn(Optional.of(invoice(305L, student(22L, "Jon", "Kay"), "8000", "0")));
        when(paymentTransferRepository.save(any(PaymentTransfer.class)))
                .thenAnswer(inv -> { PaymentTransfer t = inv.getArgument(0); t.setId(45L); return t; });

        CustomResponse<?> res = service.createTransfer(request("2000", 22L, 305L));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        PaymentTransferResponseDTO dto = (PaymentTransferResponseDTO) res.getEntity();
        assertThat(dto.getStatus()).isEqualTo(PaymentTransferStatus.PENDING_APPROVAL);
        assertThat(dto.getSourceStudentId()).isEqualTo(10L);
        assertThat(dto.getSourceInvoiceId()).isEqualTo(210L);
        assertThat(dto.getOriginalPaymentAmount()).isEqualByComparingTo("5000");
        assertThat(dto.getAmount()).isEqualByComparingTo("2000");
        assertThat(dto.getRemainingTransferableAfter()).isEqualByComparingTo("3000");
        assertThat(dto.getTerm()).isEqualTo(Term.TERM_1);
        assertThat(dto.getAppliedAt()).isNull();

        // No financial effect at create time.
        verify(financeRepository, never()).save(any());
        verify(financeTransactionRepository, never()).save(any());
        verify(studentInvoicesRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create fails when the payment reference is unknown")
    void create_unknownReference_notFound() {
        authenticateAs(MAKER_EMAIL);
        when(financeTransactionRepository.findByReferenceAndTransactionType(
                REFERENCE, FinanceTransaction.TransactionType.INCOME))
                .thenReturn(Optional.empty());

        CustomResponse<?> res = service.createTransfer(request("2000", 22L, 305L));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        verify(paymentTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create rejects an amount exceeding the transferable balance for the reference")
    void create_overAmount_conflict() {
        authenticateAs(MAKER_EMAIL);
        when(financeTransactionRepository.findByReferenceAndTransactionType(
                REFERENCE, FinanceTransaction.TransactionType.INCOME))
                .thenReturn(Optional.of(payment(900L, 10L, 210L, "5000")));
        // 4000 already committed by other transfers -> only 1000 transferable.
        when(paymentTransferRepository.sumCommittedForReference(REFERENCE)).thenReturn(new BigDecimal("4000"));

        CustomResponse<?> res = service.createTransfer(request("2000", 22L, 305L));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(res.getMessage()).contains("exceeds the transferable amount");
        verify(paymentTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create rejects a non-positive amount")
    void create_zeroAmount_badRequest() {
        authenticateAs(MAKER_EMAIL);
        CustomResponse<?> res = service.createTransfer(request("0", 22L, 305L));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verifyNoInteractions(financeTransactionRepository);
    }

    // ---- approve ---------------------------------------------------------

    private PaymentTransfer pendingTransfer(long makerId) {
        return PaymentTransfer.builder()
                .id(45L)
                .sourcePaymentReference(REFERENCE)
                .sourceTransactionId(900L)
                .originalPaymentAmount(new BigDecimal("5000"))
                .amount(new BigDecimal("2000"))
                .sourceStudentId(10L)
                .sourceInvoiceId(210L)
                .destStudentId(22L)
                .destInvoiceId(305L)
                .term(Term.TERM_1)
                .academicYear(Year.of(2026))
                .paymentMethod(FinanceTransaction.PaymentMethod.MPESA)
                .status(PaymentTransferStatus.PENDING_APPROVAL)
                .createdBy(user(makerId, MAKER_EMAIL))
                .build();
    }

    @Test
    @DisplayName("Approve moves the amount off the source and onto the destination and marks applied")
    void approve_movesMoney() {
        authenticateAs(CHECKER_EMAIL);
        when(userRepository.findByEmail(CHECKER_EMAIL)).thenReturn(Optional.of(user(2L, CHECKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));
        when(paymentTransferRepository.sumCommittedForReferenceExcluding(REFERENCE, 45L))
                .thenReturn(BigDecimal.ZERO);

        Student src = student(10L, "Ann", "Doe");
        Student dst = student(22L, "Jon", "Kay");
        StudentInvoices sourceInvoice = invoice(210L, src, "10000", "5000");
        StudentInvoices destInvoice = invoice(305L, dst, "8000", "1000");
        when(studentInvoicesRepository.findById(210L)).thenReturn(Optional.of(sourceInvoice));
        when(studentInvoicesRepository.findById(305L)).thenReturn(Optional.of(destInvoice));
        when(financeRepository.findByStudentIdAndTermAndYear(10L, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(finance(10L, "10000", "5000")));
        when(financeRepository.findByStudentIdAndTermAndYear(22L, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(finance(22L, "8000", "1000")));
        when(paymentTransferRepository.save(any(PaymentTransfer.class))).thenAnswer(i -> i.getArgument(0));

        CustomResponse<?> res = service.approveTransfer(45L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        // Source lost 2000, destination gained 2000.
        assertThat(sourceInvoice.getAmountPaid()).isEqualByComparingTo("3000");
        assertThat(sourceInvoice.getBalance()).isEqualByComparingTo("7000");
        assertThat(destInvoice.getAmountPaid()).isEqualByComparingTo("3000");
        assertThat(destInvoice.getBalance()).isEqualByComparingTo("5000");

        // Two audit transactions written (out + in).
        verify(financeTransactionRepository, times(2)).save(any(FinanceTransaction.class));

        PaymentTransferResponseDTO dto = (PaymentTransferResponseDTO) res.getEntity();
        assertThat(dto.getStatus()).isEqualTo(PaymentTransferStatus.APPROVED);
        assertThat(dto.getAppliedAt()).isNotNull();
    }

    @Test
    @DisplayName("Approve fully clears the destination invoice when the transfer covers the balance")
    void approve_clearsDestination() {
        authenticateAs(CHECKER_EMAIL);
        when(userRepository.findByEmail(CHECKER_EMAIL)).thenReturn(Optional.of(user(2L, CHECKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));
        when(paymentTransferRepository.sumCommittedForReferenceExcluding(REFERENCE, 45L))
                .thenReturn(BigDecimal.ZERO);

        Student src = student(10L, "Ann", "Doe");
        Student dst = student(22L, "Jon", "Kay");
        StudentInvoices sourceInvoice = invoice(210L, src, "10000", "5000");
        StudentInvoices destInvoice = invoice(305L, dst, "2000", "0"); // 2000 owed, transfer is 2000
        when(studentInvoicesRepository.findById(210L)).thenReturn(Optional.of(sourceInvoice));
        when(studentInvoicesRepository.findById(305L)).thenReturn(Optional.of(destInvoice));
        when(financeRepository.findByStudentIdAndTermAndYear(10L, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(finance(10L, "10000", "5000")));
        when(financeRepository.findByStudentIdAndTermAndYear(22L, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(finance(22L, "2000", "0")));
        when(paymentTransferRepository.save(any(PaymentTransfer.class))).thenAnswer(i -> i.getArgument(0));

        service.approveTransfer(45L);

        assertThat(destInvoice.getAmountPaid()).isEqualByComparingTo("2000");
        assertThat(destInvoice.getBalance()).isEqualByComparingTo("0");
        assertThat(destInvoice.getStatus()).isEqualTo('C');
    }

    @Test
    @DisplayName("The maker cannot approve their own transfer")
    void approve_makerIsChecker_conflict() {
        authenticateAs(MAKER_EMAIL);
        // Checker resolves to the same user id (1L) that created the transfer.
        when(userRepository.findByEmail(MAKER_EMAIL)).thenReturn(Optional.of(user(1L, MAKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));

        CustomResponse<?> res = service.approveTransfer(45L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(res.getMessage()).contains("maker of a transfer cannot approve");
        verify(studentInvoicesRepository, never()).save(any());
        verify(financeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approve rejects a transfer that is not pending")
    void approve_notPending_conflict() {
        authenticateAs(CHECKER_EMAIL);
        PaymentTransfer already = pendingTransfer(1L);
        already.setStatus(PaymentTransferStatus.APPROVED);
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(already));

        CustomResponse<?> res = service.approveTransfer(45L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        verify(studentInvoicesRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approve fails when availability dropped since the request was made")
    void approve_noLongerTransferable_conflict() {
        authenticateAs(CHECKER_EMAIL);
        when(userRepository.findByEmail(CHECKER_EMAIL)).thenReturn(Optional.of(user(2L, CHECKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));
        // Others already committed 4000 of the 5000 -> only 1000 left, transfer is 2000.
        when(paymentTransferRepository.sumCommittedForReferenceExcluding(REFERENCE, 45L))
                .thenReturn(new BigDecimal("4000"));

        CustomResponse<?> res = service.approveTransfer(45L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(res.getMessage()).contains("can no longer be transferred");
        verify(studentInvoicesRepository, never()).save(any());
    }

    // ---- reject ----------------------------------------------------------

    @Test
    @DisplayName("Reject sets REJECTED with a reason and moves no money")
    void reject_movesNoMoney() {
        authenticateAs(CHECKER_EMAIL);
        when(userRepository.findByEmail(CHECKER_EMAIL)).thenReturn(Optional.of(user(2L, CHECKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));
        when(paymentTransferRepository.save(any(PaymentTransfer.class))).thenAnswer(i -> i.getArgument(0));

        CustomResponse<?> res = service.rejectTransfer(45L, "Wrong destination");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        PaymentTransferResponseDTO dto = (PaymentTransferResponseDTO) res.getEntity();
        assertThat(dto.getStatus()).isEqualTo(PaymentTransferStatus.REJECTED);
        assertThat(dto.getRejectionReason()).isEqualTo("Wrong destination");
        verify(studentInvoicesRepository, never()).save(any());
        verify(financeRepository, never()).save(any());
        verify(financeTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Two audit transactions carry the source reference on approval")
    void approve_writesReferencedTransactions() {
        authenticateAs(CHECKER_EMAIL);
        when(userRepository.findByEmail(CHECKER_EMAIL)).thenReturn(Optional.of(user(2L, CHECKER_EMAIL)));
        when(paymentTransferRepository.findById(45L)).thenReturn(Optional.of(pendingTransfer(1L)));
        when(paymentTransferRepository.sumCommittedForReferenceExcluding(REFERENCE, 45L))
                .thenReturn(BigDecimal.ZERO);
        when(studentInvoicesRepository.findById(210L))
                .thenReturn(Optional.of(invoice(210L, student(10L, "Ann", "Doe"), "10000", "5000")));
        when(studentInvoicesRepository.findById(305L))
                .thenReturn(Optional.of(invoice(305L, student(22L, "Jon", "Kay"), "8000", "1000")));
        when(financeRepository.findByStudentIdAndTermAndYear(any(), any(), any()))
                .thenReturn(Optional.of(finance(10L, "10000", "5000")));
        when(paymentTransferRepository.save(any(PaymentTransfer.class))).thenAnswer(i -> i.getArgument(0));

        service.approveTransfer(45L);

        ArgumentCaptor<FinanceTransaction> captor = ArgumentCaptor.forClass(FinanceTransaction.class);
        verify(financeTransactionRepository, times(2)).save(captor.capture());
        List<FinanceTransaction> written = captor.getAllValues();
        assertThat(written).anyMatch(t -> t.getTransactionType() == FinanceTransaction.TransactionType.EXPENSE
                && t.getReference().contains(REFERENCE));
        assertThat(written).anyMatch(t -> t.getTransactionType() == FinanceTransaction.TransactionType.INCOME
                && t.getReference().contains(REFERENCE));
    }
}
