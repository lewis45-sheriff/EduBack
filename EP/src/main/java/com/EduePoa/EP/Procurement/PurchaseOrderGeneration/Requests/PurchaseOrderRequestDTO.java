package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderRequestDTO {
    private Long supplierId;
    private LocalDate expectedDeliveryDate;
    private List<PurchaseOrderItemRequestDTO> items;
}
