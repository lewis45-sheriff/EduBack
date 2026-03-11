package com.EduePoa.EP.Procurement.Inventory;


import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.Inventory.Requests.StockRequisitionRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    // Stock-In: triggered by delivery note approval
    void processDeliveryNoteApproval(DeliveryNote deliveryNote);

    // Inventory queries
    CustomResponse<?> getInventoryItems(Pageable pageable);
    CustomResponse<?> getInventoryItemById(Long id);
    CustomResponse<?> getTransactionHistory(Long itemId, Pageable pageable);
    CustomResponse<?> getLowStockItems(int threshold, Pageable pageable);

    // Stock requisition (stock-out)
    CustomResponse<?> createRequisition(StockRequisitionRequestDTO request);
    CustomResponse<?> getRequisitions(Pageable pageable);
    CustomResponse<?> getRequisitionById(Long id);
    CustomResponse<?> approveRequisition(Long id);
    CustomResponse<?> rejectRequisition(Long id, String reason);
}
