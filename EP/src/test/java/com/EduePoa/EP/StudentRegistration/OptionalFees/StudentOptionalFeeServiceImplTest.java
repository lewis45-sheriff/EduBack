package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfigRepository;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.Parents.Parent;
import com.EduePoa.EP.Parents.ParentRepository;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeAssignRequest;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Response.StudentOptionalFeeResponseDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentGuardian;
import com.EduePoa.EP.StudentRegistration.StudentGuardianRepository;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for optional-fee assignment. The optional item is a fee-structure
 * line item ({@link FeeComponentConfig}). Covers optional/mandatory validation,
 * the amount snapshot, duplicate protection, soft removal, parent authorization
 * / IDOR protection and parent-assignable restrictions.
 */
@ExtendWith(MockitoExtension.class)
class StudentOptionalFeeServiceImplTest {

    @Mock private StudentOptionalFeeRepository optionalFeeRepository;
    @Mock private FeeComponentConfigRepository feeComponentConfigRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentGuardianRepository studentGuardianRepository;
    @Mock private ParentRepository parentRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository studentInvoicesRepository;
    @Mock private com.EduePoa.EP.Finance.FinanceRepository financeRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private StudentOptionalFeeServiceImpl service;

    private static final String ADMIN_EMAIL = "admin@school.test";
    private static final String PARENT_EMAIL = "parent@school.test";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ---------------------------------------------------------

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private User user(long id, String email, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        return User.builder().id(id).email(email).role(role).build();
    }

    private Student student(long id) {
        Student s = new Student();
        s.setId(id);
        s.setFirstName("Test");
        s.setLastName("Student");
        return s;
    }

    private FeeComponentConfig config(int id, boolean optional, boolean parentAssignable,
                                      BigDecimal amount, String term) {
        FeeStructure fs = new FeeStructure();
        fs.setId(1L);
        fs.setIsDeleted('N');
        fs.setDeleted('N');
        FeeComponentConfig c = new FeeComponentConfig();
        c.setId(id);
        c.setName("Swimming");
        c.setAmount(amount);
        c.setTerm(term);
        c.setOptional(optional);
        c.setParentAssignable(parentAssignable);
        c.setFeeStructure(fs);
        return c;
    }

    private StudentOptionalFeeAssignRequest request(long studentId, long configId) {
        StudentOptionalFeeAssignRequest r = new StudentOptionalFeeAssignRequest();
        r.setStudentId(studentId);
        r.setFeeComponentConfigId(configId);
        r.setTerm(Term.TERM_1);
        r.setAcademicYear(Year.of(2026));
        return r;
    }

    // ---- assignment succeeds & snapshots amount --------------------------

    @Test
    @DisplayName("Admin assigns an optional line item: succeeds and snapshots the amount")
    void adminAssign_success_snapshotsAmount() {
        authenticateAs(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN")));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, true, false, new BigDecimal("5000"), "TERM_1")));
        when(optionalFeeRepository
                .existsByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
                        eq(10L), eq(5), eq(Term.TERM_1), eq(Year.of(2026)), eq('N')))
                .thenReturn(false);
        when(optionalFeeRepository.save(any(StudentOptionalFee.class)))
                .thenAnswer(inv -> {
                    StudentOptionalFee f = inv.getArgument(0);
                    f.setId(99L);
                    return f;
                });

        CustomResponse<?> resp = service.assign(request(10L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        StudentOptionalFeeResponseDTO dto = (StudentOptionalFeeResponseDTO) resp.getEntity();
        assertThat(dto.getAmount()).isEqualByComparingTo("5000");
        assertThat(dto.getAssignedBy()).isEqualTo(AssignedBy.ADMIN);
        assertThat(dto.getFeeComponentConfigId()).isEqualTo(5L);
        verify(auditService).log(eq("OPTIONAL_FEE"), any(String[].class));
    }

    // ---- mandatory line item cannot be assigned as optional --------------

    @Test
    @DisplayName("Rejects assigning a mandatory (optional=false) line item")
    void assign_rejectsMandatoryComponent() {
        authenticateAs(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN")));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, false, false, new BigDecimal("5000"), "TERM_1")));

        CustomResponse<?> resp = service.assign(request(10L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(resp.getMessage()).contains("mandatory");
        verify(optionalFeeRepository, never()).save(any());
    }

    // ---- duplicate assignment protection ---------------------------------

    @Test
    @DisplayName("Rejects a duplicate active assignment for same student+config+term+year")
    void assign_rejectsDuplicate() {
        authenticateAs(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN")));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, true, false, new BigDecimal("5000"), "TERM_1")));
        when(optionalFeeRepository
                .existsByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
                        eq(10L), eq(5), eq(Term.TERM_1), eq(Year.of(2026)), eq('N')))
                .thenReturn(true);

        CustomResponse<?> resp = service.assign(request(10L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        verify(optionalFeeRepository, never()).save(any());
    }

    // ---- parent authorization --------------------------------------------

    @Test
    @DisplayName("Parent assigns a parent-assignable line item to their own child: succeeds")
    void parentAssign_ownChild_success() {
        authenticateAs(PARENT_EMAIL);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(user(2L, PARENT_EMAIL, "PARENT")));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, true, true, new BigDecimal("2000"), "TERM_1")));

        Parent parent = new Parent();
        parent.setId(77L);
        when(parentRepository.findByUser_Id(2L)).thenReturn(Optional.of(parent));
        StudentGuardian link = new StudentGuardian();
        link.setParent(parent);
        when(studentGuardianRepository.findByStudent_IdAndParent_Id(10L, 77L))
                .thenReturn(Optional.of(link));
        when(optionalFeeRepository
                .existsByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
                        anyLong(), any(), any(), any(), eq('N')))
                .thenReturn(false);
        when(optionalFeeRepository.save(any(StudentOptionalFee.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CustomResponse<?> resp = service.assign(request(10L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        StudentOptionalFeeResponseDTO dto = (StudentOptionalFeeResponseDTO) resp.getEntity();
        assertThat(dto.getAssignedBy()).isEqualTo(AssignedBy.PARENT);
    }

    @Test
    @DisplayName("Parent cannot assign to a child they are not linked to (IDOR protection)")
    void parentAssign_otherChild_forbidden() {
        authenticateAs(PARENT_EMAIL);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(user(2L, PARENT_EMAIL, "PARENT")));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(student(20L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, true, true, new BigDecimal("2000"), "TERM_1")));

        Parent parent = new Parent();
        parent.setId(77L);
        when(parentRepository.findByUser_Id(2L)).thenReturn(Optional.of(parent));
        when(studentGuardianRepository.findByStudent_IdAndParent_Id(20L, 77L))
                .thenReturn(Optional.empty());

        CustomResponse<?> resp = service.assign(request(20L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(optionalFeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Parent cannot assign a non-parent-assignable optional line item")
    void parentAssign_notParentAssignable_forbidden() {
        authenticateAs(PARENT_EMAIL);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(user(2L, PARENT_EMAIL, "PARENT")));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(feeComponentConfigRepository.findById(5L))
                .thenReturn(Optional.of(config(5, true, false, new BigDecimal("2000"), "TERM_1")));

        Parent parent = new Parent();
        parent.setId(77L);
        when(parentRepository.findByUser_Id(2L)).thenReturn(Optional.of(parent));
        StudentGuardian link = new StudentGuardian();
        link.setParent(parent);
        when(studentGuardianRepository.findByStudent_IdAndParent_Id(10L, 77L))
                .thenReturn(Optional.of(link));

        CustomResponse<?> resp = service.assign(request(10L, 5L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(resp.getMessage()).contains("cannot be assigned by a parent");
        verify(optionalFeeRepository, never()).save(any());
    }

    // ---- soft removal ----------------------------------------------------

    @Test
    @DisplayName("Removing an un-invoiced assignment soft-deletes it (preserves history)")
    void remove_softDeletesUninvoiced() {
        authenticateAs(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN")));
        StudentOptionalFee fee = StudentOptionalFee.builder()
                .id(99L)
                .student(student(10L))
                .feeComponentConfig(config(5, true, false, new BigDecimal("5000"), "TERM_1"))
                .amount(new BigDecimal("5000"))
                .term(Term.TERM_1)
                .academicYear(Year.of(2026))
                .assignedBy(AssignedBy.ADMIN)
                .isInvoiced('N')
                .isDeleted('N')
                .build();
        when(optionalFeeRepository.findByIdAndIsDeleted(99L, 'N')).thenReturn(Optional.of(fee));
        when(optionalFeeRepository.save(any(StudentOptionalFee.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CustomResponse<?> resp = service.remove(99L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(fee.getIsDeleted()).isEqualTo('Y');
    }

    @Test
    @DisplayName("Removing an invoiced assignment deducts its amount from the invoice, leaving amountPaid untouched")
    void remove_invoiced_reversesFromInvoice() {
        authenticateAs(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN")));
        com.EduePoa.EP.StudentRegistration.Student s = student(10L);
        StudentOptionalFee fee = StudentOptionalFee.builder()
                .id(99L)
                .student(s)
                .feeComponentConfig(config(5, true, false, new BigDecimal("5000"), "TERM_1"))
                .amount(new BigDecimal("5000"))
                .term(Term.TERM_1)
                .academicYear(Year.of(2026))
                .isInvoiced('Y')
                .isDeleted('N')
                .build();
        when(optionalFeeRepository.findByIdAndIsDeleted(99L, 'N')).thenReturn(Optional.of(fee));
        when(optionalFeeRepository.save(any(StudentOptionalFee.class))).thenAnswer(inv -> inv.getArgument(0));

        com.EduePoa.EP.StudentInvoices.StudentInvoices invoice =
                com.EduePoa.EP.StudentInvoices.StudentInvoices.builder()
                        .id(1000L)
                        .student(s)
                        .term(Term.TERM_1)
                        .academicYear(Year.of(2026))
                        .totalAmount(new BigDecimal("35000"))
                        .amountPaid(new BigDecimal("10000"))
                        .balance(new BigDecimal("25000"))
                        .status('P')
                        .isDeleted('N')
                        .build();
        when(studentInvoicesRepository.findByStudentAndTermAndAcademicYear(s, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(invoice));
        when(studentInvoicesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.EduePoa.EP.Finance.Finance finance = new com.EduePoa.EP.Finance.Finance();
        finance.setStudentId(10L);
        finance.setTotalFeeAmount(new BigDecimal("35000"));
        finance.setPaidAmount(new BigDecimal("10000"));
        finance.setBalance(new BigDecimal("25000"));
        when(financeRepository.findByStudentIdAndTermAndYear(10L, Term.TERM_1, Year.of(2026)))
                .thenReturn(Optional.of(finance));
        when(financeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomResponse<?> resp = service.remove(99L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(fee.getIsDeleted()).isEqualTo('Y');
        // Invoice total dropped by 5000; amountPaid untouched; balance recomputed.
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("30000");
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("10000");
        assertThat(invoice.getBalance()).isEqualByComparingTo("20000");
        // Finance mirrors the invoice.
        assertThat(finance.getTotalFeeAmount()).isEqualByComparingTo("30000");
        assertThat(finance.getPaidAmount()).isEqualByComparingTo("10000");
        assertThat(finance.getBalance()).isEqualByComparingTo("20000");
    }

    // ---- parent cannot list another student's fees -----------------------

    @Test
    @DisplayName("Parent cannot list optional fees for a child they are not linked to")
    void list_parentForeignStudent_forbidden() {
        authenticateAs(PARENT_EMAIL);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(user(2L, PARENT_EMAIL, "PARENT")));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(student(20L)));
        Parent parent = new Parent();
        parent.setId(77L);
        when(parentRepository.findByUser_Id(2L)).thenReturn(Optional.of(parent));
        when(studentGuardianRepository.findByStudent_IdAndParent_Id(20L, 77L))
                .thenReturn(Optional.empty());

        CustomResponse<?> resp = service.listForStudent(20L, Term.TERM_1, Year.of(2026));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
