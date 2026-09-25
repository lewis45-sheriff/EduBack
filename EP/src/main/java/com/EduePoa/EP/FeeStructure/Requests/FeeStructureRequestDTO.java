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
    /** DAY or BOARDING. Defaults to DAY when not provided. */
    private String mode;
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
        /** Whether this line item may be assigned to students as an optional fee. Defaults false. */
        private Boolean optional;
        /** Whether a parent may self-assign this optional line item. Defaults false. */
        private Boolean parentAssignable;
    }
}
