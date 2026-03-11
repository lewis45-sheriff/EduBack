package com.EduePoa.EP.Procurement.SupplierInvoice;

import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceUploadRequestDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;



@Service
public class EtimsValidationService {



    public boolean validateTINFormat(String tin) {
        if (tin == null || tin.isBlank()) {
            return false;
        }
        String tinPattern = "^[A-Z0-9]{11}$";
        return tin.matches(tinPattern);
    }



    public boolean validateVATCalculation(BigDecimal totalAmount, BigDecimal vatAmount) {
        if (totalAmount == null || vatAmount == null) {
            return false;
        }

        // Calculate subtotal (total - VAT)
        BigDecimal subtotal = totalAmount.subtract(vatAmount);

        // Calculate expected VAT (16% of subtotal)
        BigDecimal expectedVAT = subtotal.multiply(new BigDecimal("0.16"))
                .setScale(2, RoundingMode.HALF_UP);

        // Allow small rounding differences (within 0.01)
        BigDecimal difference = vatAmount.subtract(expectedVAT).abs();
        return difference.compareTo(new BigDecimal("0.01")) <= 0;
    }


    public boolean validateTotalMatchesItems(SupplierInvoiceUploadRequestDTO request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return false;
        }

        // Calculate sum of all line items
        BigDecimal itemsTotal = request.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getInvoicedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expected total = items total + VAT
        BigDecimal expectedTotal = itemsTotal.add(request.getVatAmount())
                .setScale(2, RoundingMode.HALF_UP);

        // Allow small rounding differences (within 0.01)
        BigDecimal difference = request.getTotalAmount().subtract(expectedTotal).abs();
        return difference.compareTo(new BigDecimal("0.01")) <= 0;
    }


    public void validateETIMS(SupplierInvoiceUploadRequestDTO request) {
        // 1. Validate TIN format
        if (!validateTINFormat(request.getSupplierTIN())) {
            throw new RuntimeException("Invalid TIN format. Must be 11 alphanumeric characters (e.g., P051234567M)");
        }

//        // 2. Validate VAT calculation
//        if (!validateVATCalculation(request.getTotalAmount(), request.getVatAmount())) {
//            throw new RuntimeException("VAT calculation is incorrect. Expected 16% VAT rate on subtotal");
//        }

        // 3. Validate total matches line items
//        if (!validateTotalMatchesItems(request)) {
//            throw new RuntimeException("Total amount does not match sum of line items plus VAT");
//        }
    }
}
