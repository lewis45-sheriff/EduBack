package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private CategoryType categoryType;
    private BigDecimal allocatedAmount;
    private List<MonthlyAllocationResponse> monthlyAllocations;
}
