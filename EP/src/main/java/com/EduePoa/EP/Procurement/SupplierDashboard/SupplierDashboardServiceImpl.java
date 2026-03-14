package com.EduePoa.EP.Procurement.SupplierDashboard;

import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItem;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderRepository;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoice;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoiceRepository;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboardingRepository;
import com.EduePoa.EP.Procurement.SupplierPayments.Payment;
import com.EduePoa.EP.Procurement.SupplierPayments.PaymentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierDashboardServiceImpl implements SupplierDashboardService {

    private final SupplierOnboardingRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;

    @Override
    public CustomResponse<?> getSummary(Long supplierId) {
        CustomResponse<SupplierDashboardSummaryDTO> response = new CustomResponse<>();

        try {
            SupplierOnboarding supplier = supplierRepository.findById(supplierId)
                    .orElseGet(() -> supplierRepository.findByUser_Id(supplierId)
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId)));
            Long actualSupplierId = supplier.getId();

            // 1. Pending Orders (DRAFT)
            List<PurchaseOrder> pos = purchaseOrderRepository.findBySupplierId(actualSupplierId);
            long pendingOrders = pos.stream()
                    .filter(po -> po.getStatus() == PurchaseOrderStatus.DRAFT || po.getStatus() == PurchaseOrderStatus.APPROVED)
                    .count();

            // 2. Outstanding Invoices & Amount
            List<SupplierInvoice> invoices = invoiceRepository.findBySupplierId(actualSupplierId);
            long outstandingInvoices = invoices.stream()
                    .filter(inv -> inv.getStatus() == InvoiceStatus.PENDING_APPROVAL || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                    .count();

            BigDecimal outstandingAmount = invoices.stream()
                    .filter(inv -> inv.getStatus() != InvoiceStatus.PAID && inv.getStatus() != InvoiceStatus.REJECTED)
                    .map(inv -> inv.getOutstandingBalance() != null ? inv.getOutstandingBalance() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 3. Payments this month
            LocalDate startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            LocalDate endOfMonth = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
            
            List<Payment> allPaymentsThisMonth = paymentRepository.findByPaymentDateBetween(startOfMonth, endOfMonth);
            BigDecimal paymentsThisMonth = allPaymentsThisMonth.stream()
                    .filter(p -> p.getSupplierInvoice().getSupplier().getId().equals(actualSupplierId))
                    .filter(p -> p.getStatus() == com.EduePoa.EP.Authentication.Enum.PaymentStatus.APPROVED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 4. Fulfillment Rate (delivered qty / ordered qty across all POs)
            List<DeliveryNote> dns = deliveryNoteRepository.findByPurchaseOrder_Supplier_Id(actualSupplierId);

            double fulfillmentRate = getFulfillmentRate(dns, pos);

            SupplierDashboardSummaryDTO summaryDTO = SupplierDashboardSummaryDTO.builder()
                    .supplierId(supplier.getId())
                    .supplierName(supplier.getBusinessName())
                    .pendingOrders(pendingOrders)
                    .outstandingInvoices(outstandingInvoices)
                    .outstandingAmount(outstandingAmount)
                    .paymentsThisMonth(paymentsThisMonth)
                    .fulfillmentRate(fulfillmentRate)
                    .build();

            response.setMessage("Supplier dashboard summary fetched successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(summaryDTO);

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setEntity(null);
        } catch (Exception e) {
            response.setMessage("Error fetching dashboard summary: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    private static double getFulfillmentRate(List<DeliveryNote> dns, List<PurchaseOrder> pos) {
        double totalOrdered = 0;
        double totalDelivered = 0;

        for (DeliveryNote dn : dns) {
            if (dn.getStatus() != com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus.REJECTED) {
                for (DeliveryNoteItem item : dn.getItems()) {
                    totalDelivered += item.getDeliveredQuantity();
                }
            }
        }

        for (PurchaseOrder po : pos) {
            if (po.getStatus() != PurchaseOrderStatus.CANCELLED) {
                for (PurchaseOrderItem item : po.getItems()) {
                    totalOrdered += item.getQuantity();
                }
            }
        }
        double fulfillmentRate = totalOrdered > 0 ? (totalDelivered / totalOrdered) * 100.0 : 0.0;
        // Round to 1 decimal place
        fulfillmentRate = Math.round(fulfillmentRate * 10.0) / 10.0;
        // Cap at 100% just in case of over-delivery
        if (fulfillmentRate > 100.0) fulfillmentRate = 100.0;
        return fulfillmentRate;
    }
}
