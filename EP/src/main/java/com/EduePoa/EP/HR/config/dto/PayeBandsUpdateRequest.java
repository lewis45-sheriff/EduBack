package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Replace the PAYE band schedule with a new effective-dated set. All bands share the one
 * {@code effectiveFrom}; a change is a new set with a later date, leaving historical runs
 * reproducible (they read their frozen snapshot, not the live tables).
 */
@Data
public class PayeBandsUpdateRequest {
    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;

    @NotEmpty(message = "At least one PAYE band is required")
    @Valid
    private List<PayeBandRequest> bands;
}
