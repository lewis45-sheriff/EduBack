package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;

import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRejectionRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderUpdateRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;


public interface PurchaseOrderService {
    CustomResponse<?> create(PurchaseOrderRequestDTO purchaseOrderRequestDTO);
    CustomResponse<?> getById(Long id);
    CustomResponse<?> getAll(int page, int size);
    CustomResponse<?> update(Long id, PurchaseOrderUpdateRequestDTO purchaseOrderRequestDTO);
    CustomResponse<?> delete(Long id);
    CustomResponse<?>approvePurchaseOrder(Long id);
    CustomResponse<?>rejectPurchaseOrder(Long id, PurchaseOrderRejectionRequestDTO dto);
    CustomResponse<?>getPurchaseOrderPerSupplier(Long supplierId);
    CustomResponse<?> getBySupplier(Long supplierId, int page, int size, String sortBy, String sortDir);
}
