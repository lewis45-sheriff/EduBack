package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests;

import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderUpdateRequestDTO {
    private Long supplierId;
    private LocalDate expectedDeliveryDate;
    private PurchaseOrderStatus status;
    private List<PurchaseOrderItemRequestDTO> items;
}
