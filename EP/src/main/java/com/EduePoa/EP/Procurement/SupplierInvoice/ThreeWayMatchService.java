package com.EduePoa.EP.Procurement.SupplierInvoice;


import com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus;
import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItem;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderRepository;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceItemRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceUploadRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ThreeWayMatchService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;


    public void performThreeWayMatch(Long poId, List<Long> deliveryNoteIds,
            SupplierInvoiceUploadRequestDTO request) {
        //  Get and validate Purchase Order
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found with ID: " + poId));

        if (po.getStatus() != PurchaseOrderStatus.APPROVED &&
                po.getStatus() != PurchaseOrderStatus.SENT &&
                po.getStatus() != PurchaseOrderStatus.DELIVERED) {
            throw new RuntimeException(
                    "Purchase Order must be APPROVED, SENT, or DELIVERED. Current status: " + po.getStatus());
        }

        //  Amount matching: Invoice total must match PO total
        BigDecimal invoiceTotal = request.getTotalAmount();
        BigDecimal poTotal = po.getTotalAmount();
        if (invoiceTotal != null && poTotal != null) {
            BigDecimal amountDifference = invoiceTotal.subtract(poTotal).abs();
            if (amountDifference.compareTo(new BigDecimal("0.01")) > 0) {
                throw new RuntimeException(String.format(
                        "Invoice total amount (%.2f) does not match Purchase Order total amount (%.2f)",
                        invoiceTotal, poTotal));
            }
        }

        //  Get and validate all Delivery Notes
        List<DeliveryNote> deliveryNotes = new ArrayList<>();
        for (Long dnId : deliveryNoteIds) {
            DeliveryNote deliveryNote = deliveryNoteRepository.findById(dnId)
                    .orElseThrow(() -> new RuntimeException("Delivery Note not found with ID: " + dnId));

            if (deliveryNote.getStatus() != DeliveryNoteStatus.APPROVED) {
                throw new RuntimeException(
                        "Delivery Note (ID: " + dnId + ") must be APPROVED. Current status: " + deliveryNote.getStatus());
            }

            if (!deliveryNote.getPurchaseOrder().getId().equals(poId)) {
                throw new RuntimeException(
                        "Delivery Note (ID: " + dnId + ") does not belong to the specified Purchase Order");
            }

            deliveryNotes.add(deliveryNote);
        }

        //  Match each invoice item against PO items and aggregate delivered quantities across all DNs
        for (SupplierInvoiceItemRequestDTO invoiceItem : request.getItems()) {
            validateInvoiceItem(invoiceItem, po, deliveryNotes);
        }
    }

    private void validateInvoiceItem(SupplierInvoiceItemRequestDTO invoiceItem,
            PurchaseOrder po,
            List<DeliveryNote> deliveryNotes) {
        // Find corresponding PO item
        PurchaseOrderItem poItem = po.getItems().stream()
                .filter(item -> item.getId().equals(invoiceItem.getPurchaseOrderItemId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "PO item not found with ID: " + invoiceItem.getPurchaseOrderItemId()));

        // Aggregate delivered quantity across all delivery notes for this PO item
        int totalDeliveredQuantity = 0;
        for (DeliveryNote dn : deliveryNotes) {
            totalDeliveredQuantity += dn.getItems().stream()
                    .filter(item -> item.getPurchaseOrderItem().getId().equals(invoiceItem.getPurchaseOrderItemId()))
                    .mapToInt(DeliveryNoteItem::getDeliveredQuantity)
                    .sum();
        }

        // Validate quantities: Invoice quantity ≤ total delivered quantity across all DNs
        if (invoiceItem.getInvoicedQuantity() > totalDeliveredQuantity) {
            throw new RuntimeException(String.format(
                    "Invoiced quantity (%d) exceeds total delivered quantity (%d) across selected delivery notes for item: %s",
                    invoiceItem.getInvoicedQuantity(),
                    totalDeliveredQuantity,
                    poItem.getItemName()));
        }

        // Validate unit prices match (allow small differences for rounding)
        BigDecimal priceDifference = invoiceItem.getUnitPrice()
                .subtract(poItem.getUnitPrice())
                .abs();

        if (priceDifference.compareTo(new BigDecimal("0.01")) > 0) {
            throw new RuntimeException(String.format(
                    "Invoice unit price (%.2f) differs from PO price (%.2f) for item: %s",
                    invoiceItem.getUnitPrice(),
                    poItem.getUnitPrice(),
                    poItem.getItemName()));
        }
    }
}
