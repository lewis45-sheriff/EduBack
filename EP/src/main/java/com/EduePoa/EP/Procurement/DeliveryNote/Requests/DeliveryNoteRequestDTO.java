package com.EduePoa.EP.Procurement.DeliveryNote.Requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteRequestDTO {

    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;

    @NotNull(message = "Delivery date is required")
    private LocalDate deliveryDate;

    @NotBlank(message = "Delivered by is required")
    private String deliveredBy;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<DeliveryNoteItemRequestDTO> items;

    private String deliveryDocument; // Base64 encoded
}
