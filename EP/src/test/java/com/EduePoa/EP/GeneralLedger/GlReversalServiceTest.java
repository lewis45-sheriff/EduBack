package com.EduePoa.EP.GeneralLedger;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.GeneralLedger.entity.GlReversalRequest;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlReversalRequestRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalEntryRepository;
import com.EduePoa.EP.GeneralLedger.service.GeneralLedgerService;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.GlReversalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the maker-checker reversal workflow: a maker cannot approve their own request,
 * a request can only be created once per journal, and only a PENDING request can be decided.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlReversalServiceTest {

    @Mock GlReversalRequestRepository reversalRepository;
    @Mock JournalEntryRepository journalRepository;
    @Mock GeneralLedgerService generalLedgerService;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;

    @InjectMocks GlReversalService service;

    private User maker;
    private User checker;

    @BeforeEach
    void setUp() {
        maker = User.builder().id(1L).email("maker@t.com").username("maker").build();
        checker = User.builder().id(2L).email("checker@t.com").username("checker").build();
        lenient().when(reversalRepository.save(any(GlReversalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), "x"));
        when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
    }

    private JournalEntry postedJournal() {
        return JournalEntry.builder().id(10L).entryNumber("JNL-10").status(JournalStatus.POSTED).build();
    }

    @Test
    @DisplayName("Create stores a PENDING_APPROVAL request with a maker and reason")
    void createStoresPending() {
        authAs(maker);
        when(journalRepository.findById(10L)).thenReturn(Optional.of(postedJournal()));
        when(reversalRepository.existsByTargetJournalIdAndStatusIn(anyLong(), any())).thenReturn(false);

        var dto = service.createRequest(10L, "wrong amount");
        assertThat(dto.status()).isEqualTo(GlReversalStatus.PENDING_APPROVAL);
        assertThat(dto.reason()).isEqualTo("wrong amount");
    }

    @Test
    @DisplayName("A reason is required to create a reversal")
    void reasonRequired() {
        authAs(maker);
        assertThatThrownBy(() -> service.createRequest(10L, "  ")).isInstanceOf(GlException.class);
    }

    @Test
    @DisplayName("A second reversal for the same journal is rejected")
    void duplicateRejected() {
        authAs(maker);
        when(journalRepository.findById(10L)).thenReturn(Optional.of(postedJournal()));
        when(reversalRepository.existsByTargetJournalIdAndStatusIn(anyLong(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createRequest(10L, "again")).isInstanceOf(GlException.class)
                .hasMessageContaining("already pending or applied");
    }

    @Test
    @DisplayName("The maker cannot approve their own reversal")
    void makerCannotApprove() {
        GlReversalRequest req = GlReversalRequest.builder().id(5L).targetJournalId(10L)
                .status(GlReversalStatus.PENDING_APPROVAL).createdBy(maker).build();
        when(reversalRepository.findById(5L)).thenReturn(Optional.of(req));
        authAs(maker); // same user approves

        assertThatThrownBy(() -> service.approve(5L)).isInstanceOf(GlException.class)
                .hasMessageContaining("maker of a reversal cannot approve");
        verify(generalLedgerService, never()).reverse(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("A different checker approves and a counter-journal is created")
    void checkerApproves() {
        GlReversalRequest req = GlReversalRequest.builder().id(6L).targetJournalId(10L)
                .reason("wrong amount").status(GlReversalStatus.PENDING_APPROVAL).createdBy(maker).build();
        when(reversalRepository.findById(6L)).thenReturn(Optional.of(req));
        when(journalRepository.findById(10L)).thenReturn(Optional.of(postedJournal()));
        JournalEntry counter = JournalEntry.builder().id(20L).entryNumber("JNL-20").build();
        when(generalLedgerService.reverse(any(), anyString(), any(), anyString())).thenReturn(counter);
        authAs(checker);

        var dto = service.approve(6L);
        assertThat(dto.status()).isEqualTo(GlReversalStatus.APPROVED);
        assertThat(dto.counterJournalId()).isEqualTo(20L);
        verify(generalLedgerService).reverse(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Only a PENDING request can be approved")
    void onlyPendingApprovable() {
        GlReversalRequest req = GlReversalRequest.builder().id(7L).targetJournalId(10L)
                .status(GlReversalStatus.APPROVED).createdBy(maker).build();
        when(reversalRepository.findById(7L)).thenReturn(Optional.of(req));
        authAs(checker);
        assertThatThrownBy(() -> service.approve(7L)).isInstanceOf(GlException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    @DisplayName("Rejecting a pending request leaves it REJECTED with no counter-journal")
    void rejectLeavesOriginal() {
        GlReversalRequest req = GlReversalRequest.builder().id(8L).targetJournalId(10L)
                .status(GlReversalStatus.PENDING_APPROVAL).createdBy(maker).build();
        when(reversalRepository.findById(8L)).thenReturn(Optional.of(req));
        authAs(checker);

        var dto = service.reject(8L, "not needed");
        assertThat(dto.status()).isEqualTo(GlReversalStatus.REJECTED);
        verify(generalLedgerService, never()).reverse(any(), anyString(), any(), anyString());
    }
}
