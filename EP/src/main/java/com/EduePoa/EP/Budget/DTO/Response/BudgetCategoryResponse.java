package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCategoryResponse {
    private Long id;
    private String name;
    private CategoryType type;
    private String description;
    private CategoryStatus status;
    private String mappingKey;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
