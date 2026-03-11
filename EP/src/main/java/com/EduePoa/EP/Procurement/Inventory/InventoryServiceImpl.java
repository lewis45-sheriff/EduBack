package com.EduePoa.EP.Procurement.Inventory;


import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.RequisitionStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItem;
import com.EduePoa.EP.Procurement.Inventory.Requests.StockRequisitionItemRequestDTO;
import com.EduePoa.EP.Procurement.Inventory.Requests.StockRequisitionRequestDTO;
import com.EduePoa.EP.Procurement.Inventory.Responses.InventoryItemResponseDTO;
import com.EduePoa.EP.Procurement.Inventory.Responses.InventoryTransactionResponseDTO;
import com.EduePoa.EP.Procurement.Inventory.Responses.StockRequisitionItemResponseDTO;
import com.EduePoa.EP.Procurement.Inventory.Responses.StockRequisitionResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StockRequisitionRepository stockRequisitionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ============================================================
    // STOCK-IN: Triggered by delivery note approval
    // ============================================================

    @Override
    @Transactional
    public void processDeliveryNoteApproval(DeliveryNote deliveryNote) {
        User currentUser = deliveryNote.getApprovedBy();

        // Check for duplicate processing
        if (inventoryTransactionRepository.existsByReferenceTypeAndReferenceId(
                "DELIVERY_NOTE", deliveryNote.getId())) {
            log.warn("Delivery note #{} already processed for inventory. Skipping.", deliveryNote.getId());

            return;
        }

        for (DeliveryNoteItem dnItem : deliveryNote.getItems()) {
            String itemName = dnItem.getPurchaseOrderItem().getItemName();
            String unitOfMeasure = dnItem.getPurchaseOrderItem().getUnitOfMeasure();
            String description = dnItem.getPurchaseOrderItem().getDescription();
            int deliveredQty = dnItem.getDeliveredQuantity();

            InventoryItem inventoryItem = inventoryItemRepository
                    .findByItemNameIgnoreCaseAndUnitOfMeasureIgnoreCase(itemName, unitOfMeasure)
                    .orElse(null);

            int previousQty;
            if (inventoryItem == null) {
                // Create new inventory item
                inventoryItem = InventoryItem.builder()
                        .itemName(itemName)
                        .description(description)
                        .unitOfMeasure(unitOfMeasure)
                        .currentQuantity(0)
                        .reorderLevel(0)
                        .createdAt(LocalDateTime.now())
                        .build();
                previousQty = 0;
            } else {
                previousQty = inventoryItem.getCurrentQuantity();
            }

            int newQty = previousQty + deliveredQty;
            inventoryItem.setCurrentQuantity(newQty);
            inventoryItem.setLastRestockedAt(LocalDateTime.now());
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem = inventoryItemRepository.save(inventoryItem);

            // Record the transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .inventoryItem(inventoryItem)
                    .transactionType("STOCK_IN")
                    .quantity(deliveredQty)
                    .previousQuantity(previousQty)
                    .newQuantity(newQty)
                    .referenceType("DELIVERY_NOTE")
                    .referenceId(deliveryNote.getId())
                    .remarks("Stock from delivery note #" + deliveryNote.getId()
                            + " (PO: " + deliveryNote.getPurchaseOrder().getPoNumber() + ")")
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();
            inventoryTransactionRepository.save(transaction);

            log.info("Inventory STOCK_IN: {} x {} {} from DN #{}",
                    deliveredQty, itemName, unitOfMeasure, deliveryNote.getId());
        }

//        auditService.logAction("POST", "INVENTORY", deliveryNote,
//                "Inventory stocked from delivery note #" + deliveryNote.getId()
//                        + " (" + deliveryNote.getItems().size() + " items)");
    }



    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getInventoryItems(Pageable pageable) {
        CustomResponse<Page<InventoryItemResponseDTO>> response = new CustomResponse<>();
        try {
            Page<InventoryItemResponseDTO> items = inventoryItemRepository
                    .findAll(pageable)
                    .map(this::toItemDTO);

            response.setMessage("Inventory items retrieved successfully");
            response.setEntity(items);
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("GET", "INVENTORY", null,
//                    "Inventory items retrieved, page: " + pageable.getPageNumber() + ", total: " + items.getTotalElements());

        } catch (Exception e) {
            response.setMessage("Error retrieving inventory: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getInventoryItemById(Long id) {
        CustomResponse<InventoryItemResponseDTO> response = new CustomResponse<>();
        try {
            InventoryItem item = inventoryItemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Inventory item not found"));

            response.setMessage("Inventory item retrieved successfully");
            response.setEntity(toItemDTO(item));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("GET", "INVENTORY", item, "Inventory item retrieved: " + item.getItemName());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving inventory item: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getTransactionHistory(Long itemId, Pageable pageable) {
        CustomResponse<Page<InventoryTransactionResponseDTO>> response = new CustomResponse<>();
        try {
            inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Inventory item not found"));

            Page<InventoryTransactionResponseDTO> transactions = inventoryTransactionRepository
                    .findByInventoryItemIdOrderByCreatedAtDesc(itemId, pageable)
                    .map(this::toTransactionDTO);

            response.setMessage("Transaction history retrieved successfully");
            response.setEntity(transactions);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving transaction history: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getLowStockItems(int threshold, Pageable pageable) {
        CustomResponse<Page<InventoryItemResponseDTO>> response = new CustomResponse<>();
        try {
            Page<InventoryItemResponseDTO> items = inventoryItemRepository
                    .findByCurrentQuantityLessThanEqual(threshold, pageable)
                    .map(this::toItemDTO);

            response.setMessage("Low stock items retrieved successfully");
            response.setEntity(items);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage("Error retrieving low stock items: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    @Override
    @Transactional
    public CustomResponse<?> createRequisition(StockRequisitionRequestDTO request) {
        CustomResponse<StockRequisitionResponseDTO> response = new CustomResponse<>();
        try {
            User currentUser = getCurrentUser();

            if (request.getItems() == null || request.getItems().isEmpty()) {
                response.setMessage("Requisition must contain at least one item");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Validate all items exist and have sufficient stock
            List<StockRequisitionItem> requisitionItems = new ArrayList<>();
            for (StockRequisitionItemRequestDTO itemReq : request.getItems()) {
                InventoryItem inventoryItem = inventoryItemRepository
                        .findById(itemReq.getInventoryItemId())
                        .orElseThrow(() -> new RuntimeException(
                                "Inventory item #" + itemReq.getInventoryItemId() + " not found"));

                if (itemReq.getRequestedQuantity() <= 0) {
                    throw new RuntimeException("Requested quantity must be greater than 0");
                }

                if (inventoryItem.getCurrentQuantity() < itemReq.getRequestedQuantity()) {
                    throw new RuntimeException(String.format(
                            "Insufficient stock for '%s'. Available: %d, Requested: %d",
                            inventoryItem.getItemName(),
                            inventoryItem.getCurrentQuantity(),
                            itemReq.getRequestedQuantity()));
                }

                StockRequisitionItem sri = StockRequisitionItem.builder()
                        .inventoryItem(inventoryItem)
                        .requestedQuantity(itemReq.getRequestedQuantity())
                        .remarks(itemReq.getRemarks())
                        .build();
                requisitionItems.add(sri);
            }

            String requisitionNumber = generateRequisitionNumber();

            StockRequisition requisition = StockRequisition.builder()
                    .requisitionNumber(requisitionNumber)
                    .purpose(request.getPurpose())
                    .status(RequisitionStatus.PENDING)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .items(new ArrayList<>())
                    .build();

            // Set back-references
            for (StockRequisitionItem item : requisitionItems) {
                item.setStockRequisition(requisition);
            }
            requisition.setItems(requisitionItems);

            StockRequisition saved = stockRequisitionRepository.save(requisition);

            response.setMessage("Stock requisition created successfully");
            response.setEntity(toRequisitionDTO(saved));
            response.setStatusCode(HttpStatus.CREATED.value());
//            auditService.logAction("POST", "STOCK REQUISITION", saved,
//                    "Stock requisition created: " + saved.getRequisitionNumber());

        } catch (RuntimeException e) {
            response.setMessage("Requisition failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error creating requisition: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> approveRequisition(Long id) {
        CustomResponse<StockRequisitionResponseDTO> response = new CustomResponse<>();
        try {
            User approver = getCurrentUser();

            StockRequisition requisition = stockRequisitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Requisition not found"));

            if (requisition.getStatus() != RequisitionStatus.PENDING) {
                throw new RuntimeException("Only PENDING requisitions can be approved. Current status: " + requisition.getStatus());
            }

            // Re-validate stock availability at approval time
            for (StockRequisitionItem item : requisition.getItems()) {
                InventoryItem inventoryItem = item.getInventoryItem();
                if (inventoryItem.getCurrentQuantity() < item.getRequestedQuantity()) {
                    throw new RuntimeException(String.format(
                            "Insufficient stock for '%s'. Available: %d, Requested: %d. Stock may have changed since requisition was created.",
                            inventoryItem.getItemName(),
                            inventoryItem.getCurrentQuantity(),
                            item.getRequestedQuantity()));
                }
            }

            // Deduct stock and create transactions
            for (StockRequisitionItem item : requisition.getItems()) {
                InventoryItem inventoryItem = item.getInventoryItem();
                int previousQty = inventoryItem.getCurrentQuantity();
                int newQty = previousQty - item.getRequestedQuantity();

                inventoryItem.setCurrentQuantity(newQty);
                inventoryItem.setUpdatedAt(LocalDateTime.now());
                inventoryItemRepository.save(inventoryItem);

                InventoryTransaction transaction = InventoryTransaction.builder()
                        .inventoryItem(inventoryItem)
                        .transactionType("STOCK_OUT")
                        .quantity(item.getRequestedQuantity())
                        .previousQuantity(previousQty)
                        .newQuantity(newQty)
                        .referenceType("STOCK_REQUISITION")
                        .referenceId(requisition.getId())
                        .remarks("Stock requisition " + requisition.getRequisitionNumber()
                                + " - " + (requisition.getPurpose() != null ? requisition.getPurpose() : ""))
                        .createdBy(approver)
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(transaction);
            }

            // Mark requisition as approved
            requisition.setStatus(RequisitionStatus.APPROVED);
            requisition.setApprovedBy(approver);
            requisition.setApprovedAt(LocalDateTime.now());
            stockRequisitionRepository.save(requisition);

            response.setMessage("Stock requisition approved. Inventory has been deducted.");
            response.setEntity(toRequisitionDTO(requisition));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("APPROVE", "STOCK REQUISITION", requisition,
//                    "Stock requisition approved: " + requisition.getRequisitionNumber());

        } catch (RuntimeException e) {
            response.setMessage("Approval failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error approving requisition: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> rejectRequisition(Long id, String reason) {
        CustomResponse<StockRequisitionResponseDTO> response = new CustomResponse<>();
        try {
            User rejector = getCurrentUser();

            StockRequisition requisition = stockRequisitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Requisition not found"));

            if (requisition.getStatus() != RequisitionStatus.PENDING) {
                throw new RuntimeException("Only PENDING requisitions can be rejected. Current status: " + requisition.getStatus());
            }

            requisition.setStatus(RequisitionStatus.REJECTED);
            requisition.setRejectedBy(rejector);
            requisition.setRejectedAt(LocalDateTime.now());
            requisition.setRejectionReason(reason);
            stockRequisitionRepository.save(requisition);

            response.setMessage("Stock requisition rejected");
            response.setEntity(toRequisitionDTO(requisition));
            response.setStatusCode(HttpStatus.OK.value());
//            auditService.logAction("REJECT", "STOCK REQUISITION", requisition,
//                    "Stock requisition rejected: " + requisition.getRequisitionNumber()
//                            + (reason != null ? ", reason: " + reason : ""));

        } catch (RuntimeException e) {
            response.setMessage("Rejection failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error rejecting requisition: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getRequisitions(Pageable pageable) {
        CustomResponse<Page<StockRequisitionResponseDTO>> response = new CustomResponse<>();
        try {
            Page<StockRequisitionResponseDTO> page = stockRequisitionRepository
                    .findAll( pageable)
                    .map(this::toRequisitionDTO);

            response.setMessage("Requisitions retrieved successfully");
            response.setEntity(page);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage("Error retrieving requisitions: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getRequisitionById(Long id) {
        CustomResponse<StockRequisitionResponseDTO> response = new CustomResponse<>();
        try {
            StockRequisition requisition = stockRequisitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Requisition not found"));

            response.setMessage("Requisition retrieved successfully");
            response.setEntity(toRequisitionDTO(requisition));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving requisition: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private String generateRequisitionNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stockRequisitionRepository.count() + 1;
        return "SR-" + datePart + "-" + String.format("%04d", count);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private InventoryItemResponseDTO toItemDTO(InventoryItem item) {
        return InventoryItemResponseDTO.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .unitOfMeasure(item.getUnitOfMeasure())
                .currentQuantity(item.getCurrentQuantity())
                .reorderLevel(item.getReorderLevel())
                .lastRestockedAt(item.getLastRestockedAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private InventoryTransactionResponseDTO toTransactionDTO(InventoryTransaction txn) {
        return InventoryTransactionResponseDTO.builder()
                .id(txn.getId())
                .inventoryItemId(txn.getInventoryItem().getId())
                .itemName(txn.getInventoryItem().getItemName())
                .transactionType(txn.getTransactionType())
                .quantity(txn.getQuantity())
                .previousQuantity(txn.getPreviousQuantity())
                .newQuantity(txn.getNewQuantity())
                .referenceType(txn.getReferenceType())
                .referenceId(txn.getReferenceId())
                .remarks(txn.getRemarks())
                .createdBy(txn.getCreatedBy() != null ? txn.getCreatedBy().getEmail() : null)
                .createdAt(txn.getCreatedAt())
                .build();
    }

    private StockRequisitionResponseDTO toRequisitionDTO(StockRequisition req) {
        List<StockRequisitionItemResponseDTO> itemDTOs = req.getItems() != null
                ? req.getItems().stream().map(this::toRequisitionItemDTO).collect(Collectors.toList())
                : List.of();

        return StockRequisitionResponseDTO.builder()
                .id(req.getId())
                .requisitionNumber(req.getRequisitionNumber())
                .purpose(req.getPurpose())
                .status(req.getStatus().name())
                .items(itemDTOs)
                .createdBy(req.getCreatedBy() != null ? req.getCreatedBy().getEmail() : null)
                .createdAt(req.getCreatedAt())
                .approvedBy(req.getApprovedBy() != null ? req.getApprovedBy().getEmail() : null)
                .approvedAt(req.getApprovedAt())
                .rejectedBy(req.getRejectedBy() != null ? req.getRejectedBy().getEmail() : null)
                .rejectedAt(req.getRejectedAt())
                .rejectionReason(req.getRejectionReason())
                .build();
    }

    private StockRequisitionItemResponseDTO toRequisitionItemDTO(StockRequisitionItem item) {
        InventoryItem inv = item.getInventoryItem();
        return StockRequisitionItemResponseDTO.builder()
                .id(item.getId())
                .inventoryItemId(inv.getId())
                .itemName(inv.getItemName())
                .unitOfMeasure(inv.getUnitOfMeasure())
                .requestedQuantity(item.getRequestedQuantity())
                .availableQuantity(inv.getCurrentQuantity())
                .remarks(item.getRemarks())
                .build();
    }
}
