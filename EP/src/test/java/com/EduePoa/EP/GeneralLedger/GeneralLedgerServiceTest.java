package com.EduePoa.EP.GeneralLedger;

import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.entity.JournalLine;
import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalEntryRepository;
import com.EduePoa.EP.GeneralLedger.service.GeneralLedgerService;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalCommand;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the core posting/reversal engine with mocked repositories. Covers balance
 * validation, line-shape validation, minimum lines, idempotent return of an existing journal,
 * tenant-safe account resolution failure, and the reversal line swap + eligibility guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneralLedgerServiceTest {

    @Mock JournalEntryRepository journalRepository;
    @Mock GlAccountRepository accountRepository;

    @InjectMocks GeneralLedgerService service;

    private GlAccount cash;
    private GlAccount income;

    @BeforeEach
    void setUp() {
        cash = GlAccount.builder().id(1L).code("1000").name("Cash").accountType(GlAccountType.ASSET)
                .normalBalance(GlNormalBalance.DEBIT).systemKey(GlAccountKeys.CASH_BANK).active(true).build();
        income = GlAccount.builder().id(2L).code("4000").name("Fees").accountType(GlAccountType.INCOME)
                .normalBalance(GlNormalBalance.CREDIT).systemKey(GlAccountKeys.FEES_INCOME).active(true).build();
        lenient().when(accountRepository.findBySystemKey(GlAccountKeys.CASH_BANK)).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findBySystemKey(GlAccountKeys.FEES_INCOME)).thenReturn(Optional.of(income));
        lenient().when(journalRepository.maxId()).thenReturn(10L);
        lenient().when(journalRepository.findBySourceTypeAndSourceId(any(), any())).thenReturn(Optional.empty());
        lenient().when(journalRepository.saveAndFlush(any(JournalEntry.class)))
                .thenAnswer(inv -> { JournalEntry j = inv.getArgument(0); j.setId(99L); return j; });
    }

    private PostJournalCommand balancedCmd() {
        return new PostJournalCommand(GlSourceType.STUDENT_FEE_RECEIPT, 1L, LocalDate.now(), "R1", "d",
                List.of(PostJournalLine.debit(GlAccountKeys.CASH_BANK, new BigDecimal("100.00"), "a"),
                        PostJournalLine.credit(GlAccountKeys.FEES_INCOME, new BigDecimal("100.00"), "b")),
                null, "sys");
    }

    @Test
    @DisplayName("A balanced journal posts with two lines and a generated entry number")
    void postsBalanced() {
        JournalEntry j = service.post(balancedCmd());
        assertThat(j.getLines()).hasSize(2);
        assertThat(j.getEntryNumber()).startsWith("JNL-");
        assertThat(j.getStatus()).isEqualTo(JournalStatus.POSTED);
    }

    @Test
    @DisplayName("An unbalanced journal is rejected")
    void rejectsUnbalanced() {
        PostJournalCommand cmd = new PostJournalCommand(GlSourceType.STUDENT_FEE_RECEIPT, 1L, LocalDate.now(), "R", "d",
                List.of(PostJournalLine.debit(GlAccountKeys.CASH_BANK, new BigDecimal("100.00"), "a"),
                        PostJournalLine.credit(GlAccountKeys.FEES_INCOME, new BigDecimal("90.00"), "b")),
                null, "sys");
        assertThatThrownBy(() -> service.post(cmd)).isInstanceOf(GlException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    @DisplayName("A line with both debit and credit positive is rejected")
    void rejectsBothSides() {
        PostJournalCommand cmd = new PostJournalCommand(GlSourceType.STUDENT_FEE_RECEIPT, 1L, LocalDate.now(), "R", "d",
                List.of(new PostJournalLine(GlAccountKeys.CASH_BANK, new BigDecimal("100.00"), new BigDecimal("100.00"), "a"),
                        PostJournalLine.credit(GlAccountKeys.FEES_INCOME, new BigDecimal("100.00"), "b")),
                null, "sys");
        assertThatThrownBy(() -> service.post(cmd)).isInstanceOf(GlException.class)
                .hasMessageContaining("exactly one positive");
    }

    @Test
    @DisplayName("A negative amount is rejected")
    void rejectsNegative() {
        PostJournalCommand cmd = new PostJournalCommand(GlSourceType.STUDENT_FEE_RECEIPT, 1L, LocalDate.now(), "R", "d",
                List.of(new PostJournalLine(GlAccountKeys.CASH_BANK, new BigDecimal("-100.00"), BigDecimal.ZERO, "a"),
                        PostJournalLine.credit(GlAccountKeys.FEES_INCOME, new BigDecimal("100.00"), "b")),
                null, "sys");
        assertThatThrownBy(() -> service.post(cmd)).isInstanceOf(GlException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("A journal with fewer than two lines is rejected")
    void rejectsSingleLine() {
        PostJournalCommand cmd = new PostJournalCommand(GlSourceType.STUDENT_FEE_RECEIPT, 1L, LocalDate.now(), "R", "d",
                List.of(PostJournalLine.debit(GlAccountKeys.CASH_BANK, new BigDecimal("100.00"), "a")),
                null, "sys");
        assertThatThrownBy(() -> service.post(cmd)).isInstanceOf(GlException.class)
                .hasMessageContaining("at least two lines");
    }

    @Test
    @DisplayName("A duplicate source event returns the existing journal (idempotent)")
    void idempotentReturnsExisting() {
        JournalEntry existing = JournalEntry.builder().id(77L).entryNumber("JNL-X").status(JournalStatus.POSTED).build();
        when(journalRepository.findBySourceTypeAndSourceId(GlSourceType.STUDENT_FEE_RECEIPT, 1L))
                .thenReturn(Optional.of(existing));
        JournalEntry j = service.post(balancedCmd());
        assertThat(j.getId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("An unknown account systemKey (or foreign tenant's) resolves to not-configured")
    void unknownAccountRejected() {
        when(accountRepository.findBySystemKey(GlAccountKeys.FEES_INCOME)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.post(balancedCmd())).isInstanceOf(GlException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("An inactive account cannot be posted to")
    void inactiveAccountRejected() {
        income.setActive(false);
        assertThatThrownBy(() -> service.post(balancedCmd())).isInstanceOf(GlException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Reversal swaps debit and credit, marks original REVERSED, links counter-journal")
    void reverseSwaps() {
        JournalEntry original = JournalEntry.builder().id(50L).entryNumber("JNL-50").status(JournalStatus.POSTED).build();
        original.addLine(JournalLine.builder().account(cash).debit(new BigDecimal("100.00")).credit(BigDecimal.ZERO).build());
        original.addLine(JournalLine.builder().account(income).debit(BigDecimal.ZERO).credit(new BigDecimal("100.00")).build());
        when(journalRepository.existsByReversalOfJournalId(50L)).thenReturn(false);

        JournalEntry counter = service.reverse(original, "oops", null, "checker");

        assertThat(counter.getSourceType()).isEqualTo(GlSourceType.REVERSAL);
        assertThat(counter.getReversalOfJournalId()).isEqualTo(50L);
        // The cash line was a debit originally -> credit on the counter; income credit -> debit.
        JournalLine cashLine = counter.getLines().stream().filter(l -> l.getAccount() == cash).findFirst().orElseThrow();
        JournalLine incomeLine = counter.getLines().stream().filter(l -> l.getAccount() == income).findFirst().orElseThrow();
        assertThat(cashLine.getCredit()).isEqualByComparingTo("100.00");
        assertThat(cashLine.getDebit()).isEqualByComparingTo("0.00");
        assertThat(incomeLine.getDebit()).isEqualByComparingTo("100.00");
        assertThat(original.getStatus()).isEqualTo(JournalStatus.REVERSED);
        assertThat(original.getReversedByJournalId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("An already-reversed journal cannot be reversed again")
    void doubleReverseRejected() {
        JournalEntry original = JournalEntry.builder().id(51L).entryNumber("JNL-51").status(JournalStatus.REVERSED).build();
        assertThatThrownBy(() -> service.reverse(original, "x", null, "c")).isInstanceOf(GlException.class)
                .hasMessageContaining("already reversed");
    }

    @Test
    @DisplayName("A journal that already has a reversal cannot be reversed again (concurrent guard)")
    void existingReversalRejected() {
        JournalEntry original = JournalEntry.builder().id(52L).entryNumber("JNL-52").status(JournalStatus.POSTED).build();
        when(journalRepository.existsByReversalOfJournalId(52L)).thenReturn(true);
        assertThatThrownBy(() -> service.reverse(original, "x", null, "c")).isInstanceOf(GlException.class)
                .hasMessageContaining("already has a reversal");
    }
}
