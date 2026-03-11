package com.EduePoa.EP.Procurement.Inventory.Requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequisitionRequestDTO {
    private String purpose;
    private List<StockRequisitionItemRequestDTO> items;
}
