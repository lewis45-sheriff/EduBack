package com.EduePoa.EP.Procurement.SupplierInvoice;


import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceApprovalRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceUploadRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface SupplierInvoiceService {
    CustomResponse<?> create(SupplierInvoiceRequestDTO requestDTO);

    CustomResponse<?> uploadInvoice(SupplierInvoiceUploadRequestDTO requestDTO);

    CustomResponse<?> approveInvoice(SupplierInvoiceApprovalRequestDTO requestDTO);

    CustomResponse<?> rejectInvoice(SupplierInvoiceApprovalRequestDTO requestDTO);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> getAll();

    CustomResponse<?> getByStatus(InvoiceStatus status);

    CustomResponse<?> update(Long id, SupplierInvoiceRequestDTO requestDTO);

    CustomResponse<?> updateStatus(Long id, InvoiceStatus status);

    CustomResponse<?> delete(Long id);
    CustomResponse<?>InvoicePerSupplier(Long id);
}
