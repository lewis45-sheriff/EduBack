package com.EduePoa.EP.Procurement.SupplierPayments;


import com.EduePoa.EP.Procurement.SupplierPayments.Requests.PaymentRequestDTO;
import com.EduePoa.EP.Procurement.SupplierPayments.Responses.PaymentResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface SupplierPaymentService {

    CustomResponse<?> recordPayment(PaymentRequestDTO requestDTO);

    CustomResponse<PaymentResponseDTO> getPaymentById(Long id);

    Page<PaymentResponseDTO> getAllPayments(Pageable pageable);

    Page<PaymentResponseDTO> getPaymentsBySupplier(Long supplierId, Pageable pageable);

    Page<PaymentResponseDTO> getPaymentsByInvoice(Long invoiceId, Pageable pageable);

    CustomResponse<?> getSupplierBalance(Long supplierId);

    CustomResponse<?> approvePayment(Long paymentId);

    CustomResponse<?> rejectPayment(Long paymentId, String reason);

    CustomResponse<?> editPayment(Long paymentId, PaymentRequestDTO request);
}
