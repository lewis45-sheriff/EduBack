package com.EduePoa.EP.GeneralLedger.controller;

import com.EduePoa.EP.GeneralLedger.dto.GlAccountDTO;
import com.EduePoa.EP.GeneralLedger.dto.GlAccountRequest;
import com.EduePoa.EP.GeneralLedger.dto.JournalEntryDTO;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.service.GlAccountService;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.GlQueryService;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Read + account-management endpoints for the General Ledger. All responses use
 * the standard {@link CustomResponse} envelope. Every endpoint is permission-gated
 * at the API layer; the service layer additionally enforces tenant isolation (the
 * Hibernate tenant filter scopes every query/account resolution).
 */
@RestController
@RequestMapping("/api/v1/gl")
@RequiredArgsConstructor
@Slf4j
public class GlController {

    private final GlQueryService queryService;
    private final GlAccountService accountService;

    // ------------------------------------------------------- chart of accounts

    @GetMapping("/accounts")
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<List<GlAccountDTO>>> accounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<GlAccountDTO> accounts = queryService.accountsWithBalances(asOf, activeOnly);
        return ok("Chart of accounts retrieved", accounts);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasPermission(null, 'gl:manage')")
    public ResponseEntity<CustomResponse<GlAccountDTO>> createAccount(@RequestBody GlAccountRequest request) {
        try {
            GlAccount created = accountService.create(request);
            List<GlAccountDTO> dto = queryService.accountsWithBalances(LocalDate.now(), false);
            GlAccountDTO result = dto.stream().filter(a -> a.id().equals(created.getId())).findFirst().orElse(null);
            return response(HttpStatus.CREATED, "Account created", result);
        } catch (GlException e) {
            return response(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasPermission(null, 'gl:manage')")
    public ResponseEntity<CustomResponse<GlAccountDTO>> updateAccount(
            @PathVariable Long id, @RequestBody GlAccountRequest request) {
        try {
            GlAccount updated = accountService.update(id, request);
            List<GlAccountDTO> dto = queryService.accountsWithBalances(LocalDate.now(), false);
            GlAccountDTO result = dto.stream().filter(a -> a.id().equals(updated.getId())).findFirst().orElse(null);
            return ok("Account updated", result);
        } catch (GlException e) {
            return response(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    // --------------------------------------------------------------- journals

    @GetMapping("/journals")
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<Page<JournalEntryDTO>>> journals(
            @RequestParam(required = false) GlSourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "accountingDate", "id"));
        Page<JournalEntryDTO> result = queryService.searchJournals(sourceType, from, to, pageable);
        return ok("Journals retrieved", result);
    }

    @GetMapping("/journals/{id}")
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<JournalEntryDTO>> journal(@PathVariable Long id) {
        try {
            return ok("Journal retrieved", queryService.journalDetail(id));
        } catch (GlException e) {
            return response(HttpStatus.NOT_FOUND, e.getMessage(), null);
        }
    }

    // ----------------------------------------------------- ledger / statement

    @GetMapping("/accounts/{id}/ledger")
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<com.EduePoa.EP.GeneralLedger.dto.AccountStatementDTO>> ledger(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ok("Account statement retrieved", queryService.accountStatement(id, from, to));
        } catch (GlException e) {
            return response(HttpStatus.NOT_FOUND, e.getMessage(), null);
        }
    }

    // --------------------------------------------------------- trial balance

    @GetMapping("/trial-balance")
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<com.EduePoa.EP.GeneralLedger.dto.TrialBalanceDTO>> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok("Trial balance retrieved", queryService.trialBalance(asOf));
    }

    // ----------------------------------------------------------------- helpers

    private <T> ResponseEntity<CustomResponse<T>> ok(String message, T entity) {
        return response(HttpStatus.OK, message, entity);
    }

    private <T> ResponseEntity<CustomResponse<T>> response(HttpStatus status, String message, T entity) {
        CustomResponse<T> body = new CustomResponse<>(message, status.value(), entity);
        return ResponseEntity.status(status).body(body);
    }
}
