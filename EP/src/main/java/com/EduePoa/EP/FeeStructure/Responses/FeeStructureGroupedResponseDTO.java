package com.EduePoa.EP.FeeStructure.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeeStructureGroupedResponseDTO {
    private Long id;
    private String grade;
    private LocalDateTime createdOn;
    private LocalDateTime updatedOn;
    private List<TermDTO> terms;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TermDTO {
        private String term;
        private List<FeeItemDTO> feeItems;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeItemDTO {
        private Long id;
        private String name;
        private Double amount;
    }
}
