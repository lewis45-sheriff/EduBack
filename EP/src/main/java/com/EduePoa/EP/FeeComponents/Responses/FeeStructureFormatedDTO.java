package com.EduePoa.EP.FeeComponents.Responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeeStructureFormatedDTO {
    private Long id;
    private String name;
}
