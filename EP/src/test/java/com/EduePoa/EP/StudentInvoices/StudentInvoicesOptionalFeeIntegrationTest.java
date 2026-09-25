package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeMode;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.FeeStructure.FeeStructureRepository;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.StudentInvoices.Responses.StudentInvoiceResponseDTO;
import com.EduePoa.EP.StudentRegistration.BoardingStatus;
import com.EduePoa.EP.StudentRegistration.OptionalFees.AssignedBy;
import com.EduePoa.EP.StudentRegistration.OptionalFees.StudentOptionalFee;
import com.EduePoa.EP.StudentRegistration.OptionalFees.StudentOptionalFeeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the student-type fee-mode selection and optional-fee integration in
 * {@link StudentInvoicesServiceImpl#create}. These tests exercise the current
 * term resolved from {@link Term#getCurrentTerm()}; if there is no active term
 * for the test run date, the tests are skipped.
 */
@ExtendWith(MockitoExtension.class)
class StudentInvoicesOptionalFeeIntegrationTest {

    @Mock private StudentRepository studentRepository;
    @Mock private FeeStructureRepository feeStructureRepository;
    @Mock private StudentInvoicesRepository studentInvoicesRepository;
    @Mock private FinanceRepository financeRepository;
    @Mock private StudentOptionalFeeRepository studentOptionalFeeRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private StudentInvoicesServiceImpl service;

    private Term currentTerm;
    private String termString;
    private int currentYear;

    @BeforeEach
    void setUp() {
        currentTerm = Term.getCurrentTerm();
        Assumptions.assumeTrue(currentTerm != null,
                "No active term for today; invoice create() tests require an active term");
        termString = currentTerm.name();
        currentYear = Year.now().getValue();
    }

    private Grade grade(long id, String name) {
        Grade g = new Grade();
        g.setId(id);
        g.setName(name);
        return g;
    }

    private Student student(long id, Grade grade, BoardingStatus status) {
        Student s = new Student();
        s.setId(id);
        s.setFirstName("Ann");
        s.setLastName("Doe");
        s.setAdmissionNumber("ADM-" + id);
        s.setGrade(grade);
        s.setBoardingStatus(status);
        return s;
    }

    private FeeStructure feeStructure(Grade grade, FeeMode mode, BigDecimal termAmount) {
        FeeStructure fs = new FeeStructure();
        fs.setId(1L);
        fs.setName(mode.name() + " structure");
        fs.setGrade(grade);
        fs.setMode(mode);
        FeeComponentConfig cfg = new FeeComponentConfig();
        cfg.setName("Tuition");
        cfg.setTerm(termString);
        cfg.setAmount(termAmount);
        cfg.setFeeStructure(fs);
        fs.getTermComponents().add(cfg);
        return fs;
    }

    private void stubCommonSaves() {
        when(studentInvoicesRepository.save(any(StudentInvoices.class)))
                .thenAnswer(inv -> {
                    StudentInvoices i = inv.getArgument(0);
                    i.setId(1000L);
                    return i;
                });
        lenient().when(financeRepository.save(any(Finance.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(financeRepository.findByStudentIdAndTermAndYear(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
    }

    // ---- Test 1: DAY student uses DAY structure --------------------------

    @Test
    @DisplayName("DAY student is invoiced using the DAY fee structure")
    void dayStudent_usesDayStructure() {
        Grade grade = grade(1L, "Grade 6");
        Student s = student(10L, grade, BoardingStatus.DAY);
        FeeStructure day = feeStructure(grade, FeeMode.DAY, new BigDecimal("10000"));

        when(studentRepository.findById(10L)).thenReturn(Optional.of(s));
        when(feeStructureRepository.findByGradeAndModeAndYear(grade, FeeMode.DAY, currentYear))
                .thenReturn(Optional.of(day));
        when(studentInvoicesRepository.findByStudentAndTermAndAcademicYear(eq(s), eq(currentTerm), any()))
                .thenReturn(Optional.empty());
        when(studentOptionalFeeRepository.findByStudent_IdAndTermAndAcademicYearAndIsDeleted(
                eq(10L), eq(currentTerm), any(), eq('N'))).thenReturn(List.of());
        stubCommonSaves();

        CustomResponse<?> resp = service.create(10L, termString);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        StudentInvoiceResponseDTO dto = (StudentInvoiceResponseDTO) resp.getEntity();
        assertThat(dto.getFeeMode()).isEqualTo(FeeMode.DAY);
        assertThat(dto.getMandatoryFeesAmount()).isEqualByComparingTo("10000");
        assertThat(dto.getOptionalFeesAmount()).isEqualByComparingTo("0");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("10000");
        verify(feeStructureRepository).findByGradeAndModeAndYear(grade, FeeMode.DAY, currentYear);
    }

    // ---- Test 2: BOARDING student uses BOARDING structure ----------------

    @Test
    @DisplayName("BOARDING student is invoiced using the BOARDING fee structure")
    void boardingStudent_usesBoardingStructure() {
        Grade grade = grade(1L, "Grade 6");
        Student s = student(11L, grade, BoardingStatus.BOARDING);
        FeeStructure boarding = feeStructure(grade, FeeMode.BOARDING, new BigDecimal("25000"));

        when(studentRepository.findById(11L)).thenReturn(Optional.of(s));
        when(feeStructureRepository.findByGradeAndModeAndYear(grade, FeeMode.BOARDING, currentYear))
                .thenReturn(Optional.of(boarding));
        when(studentInvoicesRepository.findByStudentAndTermAndAcademicYear(eq(s), eq(currentTerm), any()))
                .thenReturn(Optional.empty());
        when(studentOptionalFeeRepository.findByStudent_IdAndTermAndAcademicYearAndIsDeleted(
                eq(11L), eq(currentTerm), any(), eq('N'))).thenReturn(List.of());
        stubCommonSaves();

        CustomResponse<?> resp = service.create(11L, termString);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        StudentInvoiceResponseDTO dto = (StudentInvoiceResponseDTO) resp.getEntity();
        assertThat(dto.getFeeMode()).isEqualTo(FeeMode.BOARDING);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("25000");
        verify(feeStructureRepository).findByGradeAndModeAndYear(grade, FeeMode.BOARDING, currentYear);
    }

    // ---- Test 3: missing fee structure gives a clear message -------------

    @Test
    @DisplayName("Missing fee structure fails with an explicit mode/grade/year message")
    void missingStructure_clearMessage() {
        Grade grade = grade(2L, "Grade 6");
        Student s = student(12L, grade, BoardingStatus.BOARDING);

        when(studentRepository.findById(12L)).thenReturn(Optional.of(s));
        when(feeStructureRepository.findByGradeAndModeAndYear(grade, FeeMode.BOARDING, currentYear))
                .thenReturn(Optional.empty());

        CustomResponse<?> resp = service.create(12L, termString);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(resp.getMessage())
                .contains("BOARDING")
                .contains("Grade 6")
                .contains(String.valueOf(currentYear))
                .contains("boarding students");
    }

    // ---- Test 4: optional fees added to invoice total --------------------

    @Test
    @DisplayName("Optional fees are added to the invoice total and marked invoiced")
    void optionalFees_addedToTotal() {
        Grade grade = grade(1L, "Grade 6");
        Student s = student(13L, grade, BoardingStatus.DAY);
        FeeStructure day = feeStructure(grade, FeeMode.DAY, new BigDecimal("10000"));

        StudentOptionalFee swim = StudentOptionalFee.builder()
                .id(1L).student(s).amount(new BigDecimal("5000"))
                .term(currentTerm).academicYear(Year.of(currentYear))
                .assignedBy(AssignedBy.ADMIN).isInvoiced('N').isDeleted('N').build();

        when(studentRepository.findById(13L)).thenReturn(Optional.of(s));
        when(feeStructureRepository.findByGradeAndModeAndYear(grade, FeeMode.DAY, currentYear))
                .thenReturn(Optional.of(day));
        when(studentInvoicesRepository.findByStudentAndTermAndAcademicYear(eq(s), eq(currentTerm), any()))
                .thenReturn(Optional.empty());
        when(studentOptionalFeeRepository.findByStudent_IdAndTermAndAcademicYearAndIsDeleted(
                eq(13L), eq(currentTerm), any(), eq('N'))).thenReturn(List.of(swim));
        stubCommonSaves();

        CustomResponse<?> resp = service.create(13L, termString);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        StudentInvoiceResponseDTO dto = (StudentInvoiceResponseDTO) resp.getEntity();
        assertThat(dto.getMandatoryFeesAmount()).isEqualByComparingTo("10000");
        assertThat(dto.getOptionalFeesAmount()).isEqualByComparingTo("5000");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("15000");

        // Test 11 (double-charge prevention): the assignment is marked invoiced.
        ArgumentCaptor<List<StudentOptionalFee>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentOptionalFeeRepository).saveAll(captor.capture());
        assertThat(swim.getIsInvoiced()).isEqualTo('Y');

        // Finance total reflects the invoice total (Test 12).
        ArgumentCaptor<Finance> financeCaptor = ArgumentCaptor.forClass(Finance.class);
        verify(financeRepository).save(financeCaptor.capture());
        assertThat(financeCaptor.getValue().getTotalFeeAmount()).isEqualByComparingTo("15000");
        assertThat(financeCaptor.getValue().getBalance()).isEqualByComparingTo("15000");
    }

    // ---- Test 11: regeneration does not double count ---------------------

    @Test
    @DisplayName("Re-invoicing the same student/term is rejected (no double-charge)")
    void reinvoice_isRejected() {
        Grade grade = grade(1L, "Grade 6");
        Student s = student(14L, grade, BoardingStatus.DAY);
        FeeStructure day = feeStructure(grade, FeeMode.DAY, new BigDecimal("10000"));

        when(studentRepository.findById(14L)).thenReturn(Optional.of(s));
        when(feeStructureRepository.findByGradeAndModeAndYear(grade, FeeMode.DAY, currentYear))
                .thenReturn(Optional.of(day));
        // An invoice already exists for this student/term/year.
        when(studentInvoicesRepository.findByStudentAndTermAndAcademicYear(eq(s), eq(currentTerm), any()))
                .thenReturn(Optional.of(new StudentInvoices()));

        CustomResponse<?> resp = service.create(14L, termString);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(resp.getMessage()).contains("already exists");
        verify(studentInvoicesRepository, never()).save(any());
        verify(studentOptionalFeeRepository, never()).saveAll(any());
    }
}
