package com.EduePoa.EP.Procurement.SupplierDashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SupplierDashboardSummaryDTO {
    private Long supplierId;
    private String supplierName;
    private long pendingOrders;
    private long outstandingInvoices;
    private BigDecimal outstandingAmount;
    private BigDecimal paymentsThisMonth;
    private double fulfillmentRate;
}
