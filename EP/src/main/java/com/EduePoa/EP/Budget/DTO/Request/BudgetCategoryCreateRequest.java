package com.EduePoa.EP.Budget.DTO.Request;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCategoryCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "type is required (INCOME or EXPENSE)")
    private CategoryType type;

    private String description;

    /** Optional ledger referenceType mapping, e.g. "SUPPLIER_PAYMENT" or "FEE_PAYMENT". */
    private String mappingKey;
}
