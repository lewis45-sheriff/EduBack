package com.EduePoa.EP.Budget.DTO.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Year;

/**
 * Update budget header fields (DRAFT only). Lines are managed via dedicated endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetUpdateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private Term academicTerm;

    private Year academicYear;

    private LocalDate startDate;

    private LocalDate endDate;
}
