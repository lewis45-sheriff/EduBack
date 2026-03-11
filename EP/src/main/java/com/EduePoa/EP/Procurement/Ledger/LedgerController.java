package com.EduePoa.EP.Procurement.Ledger;

import com.EduePoa.EP.Authentication.Enum.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/entries")
    @PreAuthorize("hasPermission(null, 'ledger:read')")
    public ResponseEntity<Page<LedgerEntryResponseDTO>> getLedgerEntries(@RequestParam(required = false) TransactionType type, @RequestParam(required = false) String referenceType, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "DESC") String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<LedgerEntryResponseDTO> entries = ledgerService.getLedgerEntries(
                type, referenceType, startDate, endDate, pageable);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasPermission(null, 'ledger:read')")
    public ResponseEntity<LedgerSummaryDTO> getLedgerSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LedgerSummaryDTO summary = ledgerService.getLedgerSummary(startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
