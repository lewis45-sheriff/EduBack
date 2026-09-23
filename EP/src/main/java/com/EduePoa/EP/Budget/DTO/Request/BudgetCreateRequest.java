package com.EduePoa.EP.Budget.DTO.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Create a budget. Provide either an academic period (term + year) or an explicit
 * date range (or both, which are validated for consistency). At least one period
 * basis is required.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private Term academicTerm;

    private Year academicYear;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Optional initial lines; can also be added later while DRAFT. */
    @Valid
    private List<BudgetLineRequest> lines;
}
