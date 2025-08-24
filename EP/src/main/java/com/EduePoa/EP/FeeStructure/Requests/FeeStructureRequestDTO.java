package com.EduePoa.EP.FeeStructure.Requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeStructureRequestDTO {
    private String grade;
    private List<TermDTO> terms;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermDTO {
        private String term;
        private List<FeeItemDTO> feeItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeItemDTO {
        private String name;
        private Double amount;
    }
}
