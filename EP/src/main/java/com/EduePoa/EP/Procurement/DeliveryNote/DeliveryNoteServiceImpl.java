package com.EduePoa.EP.Procurement.DeliveryNote;


import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItem;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem.DeliveryNoteItemRepository;
import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteApprovalRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteItemRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Responses.DeliveryNoteItemResponseDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Responses.DeliveryNoteResponseDTO;
import com.EduePoa.EP.Procurement.Inventory.InventoryService;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItemRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboardingRepository;

@Service
@RequiredArgsConstructor
public class DeliveryNoteServiceImpl implements DeliveryNoteService {

    private final DeliveryNoteRepository deliveryNoteRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final UserRepository userRepository;
    private final DeliveryNoteItemRepository deliveryNoteItemRepository;
    private final AuditService auditService;
    private final InventoryService inventoryService;
    private final SupplierOnboardingRepository supplierOnboardingRepository;

    @Override
    @Audit(module = "DELIVERY NOTE", action = "CREATE")
//    @Transactional
    public CustomResponse<?> create(DeliveryNoteRequestDTO dto) {
        CustomResponse<DeliveryNoteResponseDTO> response = new CustomResponse<>();
        try {
            PurchaseOrder purchaseOrder = purchaseOrderRepository
                    .findById(dto.getPurchaseOrderId())
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            DeliveryNote deliveryNote = new DeliveryNote();
            deliveryNote.setPurchaseOrder(purchaseOrder);
            deliveryNote.setDeliveryDate(dto.getDeliveryDate());
            deliveryNote.setReceivedBy(dto.getReceivedBy());
            deliveryNote.setStatus(DeliveryNoteStatus.DRAFT);
            deliveryNote.setCreatedAt(LocalDateTime.now());
            deliveryNote.setDeliveredBy(dto.getDeliveredBy());

            List<DeliveryNoteItem> items = buildItems(dto.getItems(), deliveryNote);
            deliveryNote.setItems(items);

            DeliveryNote saved = deliveryNoteRepository.save(deliveryNote);

            response.setMessage("Delivery note created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setEntity(toResponseDTO(saved));

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error creating delivery note: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getById(Long id) {
        CustomResponse<DeliveryNoteResponseDTO> response = new CustomResponse<>();
        try {
            DeliveryNote deliveryNote = deliveryNoteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(
                            "Delivery note not found with id: " + id));

            response.setMessage("Delivery note fetched successfully");
            response.setEntity(toResponseDTO(deliveryNote));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getAll() {
        CustomResponse<List<DeliveryNoteResponseDTO>> response = new CustomResponse<>();
        try {
            List<DeliveryNoteResponseDTO> all = deliveryNoteRepository.findAll()
                    .stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setMessage("Delivery notes fetched successfully");
            response.setEntity(all);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage("Error fetching delivery notes: " + e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "DELIVERY NOTE", action = "UPDATE")
    @Transactional
    public CustomResponse<?> update(Long id, DeliveryNoteRequestDTO dto) {
        CustomResponse<DeliveryNoteResponseDTO> response = new CustomResponse<>();
        try {
            DeliveryNote existing = deliveryNoteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Delivery note not found"));

            // Validate PO if updating
            if (dto.getPurchaseOrderId() != null) {
                PurchaseOrder purchaseOrder = purchaseOrderRepository
                        .findById(dto.getPurchaseOrderId())
                        .orElseThrow(() -> new RuntimeException("Purchase order not found"));
                existing.setPurchaseOrder(purchaseOrder);
            }

            existing.setDeliveryDate(dto.getDeliveryDate());
            existing.setReceivedBy(dto.getReceivedBy());

            // Clear and replace items (orphanRemoval handles DB cleanup)
            existing.getItems().clear();
            List<DeliveryNoteItem> updatedItems = buildItems(dto.getItems(), existing);
            existing.getItems().addAll(updatedItems);

            DeliveryNote saved = deliveryNoteRepository.save(existing);

            response.setMessage("Delivery note updated successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error updating delivery note: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "DELIVERY NOTE", action = "DELETE")
    @Transactional
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            if (!deliveryNoteRepository.existsById(id)) {
                throw new RuntimeException("Delivery note not found");
            }

            deliveryNoteRepository.deleteById(id);

            response.setMessage("Delivery note deleted successfully");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error deleting delivery note: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeliveryNoteResponseDTO> getAllPaged(Pageable pageable) {
        return deliveryNoteRepository.findAll(pageable)
                .map(this::toResponseDTO);
    }

    @Override
    @Audit(module = "DELIVERY NOTE", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approveDeliveryNote(DeliveryNoteApprovalRequestDTO request) {
        CustomResponse<DeliveryNoteResponseDTO> response = new CustomResponse<>();
        try {
            DeliveryNote deliveryNote = deliveryNoteRepository
                    .findById(request.getDeliveryNoteId())
                    .orElseThrow(() -> new RuntimeException("Delivery note not found"));

            // Validate status
            if (deliveryNote.getStatus() != DeliveryNoteStatus.DRAFT) {
                throw new RuntimeException("Only DRAFT delivery notes can be approved. Current status: "
                        + deliveryNote.getStatus());
            }

            User currentUser = getCurrentUser();

            if (request.getApproved()) {
                deliveryNote.setStatus(DeliveryNoteStatus.APPROVED);
                deliveryNote.setApprovedBy(currentUser);
                deliveryNote.setApprovedAt(LocalDateTime.now());
                deliveryNote.setRejectionReason(null);
            } else {
                deliveryNote.setStatus(DeliveryNoteStatus.REJECTED);
                deliveryNote.setRejectedBy(currentUser);
                deliveryNote.setRejectedAt(LocalDateTime.now());
                deliveryNote.setRejectionReason(request.getRejectionReason());
            }

            DeliveryNote saved = deliveryNoteRepository.save(deliveryNote);

            // Stock inventory on approval
            if (request.getApproved()) {
                inventoryService.processDeliveryNoteApproval(saved);
            }

            response.setMessage("Delivery note " + (request.getApproved() ? "approved" : "rejected") + " successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error processing delivery note approval: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "DELIVERY NOTE", action = "REJECT")
    @Transactional
    public CustomResponse<?> rejectDeliveryNote(DeliveryNoteApprovalRequestDTO request) {
        request.setApproved(false);
        return approveDeliveryNote(request);
    }

    @Override
    public CustomResponse<?> deliveryNotePerSupplierId(Long id) {
        CustomResponse<List<DeliveryNoteResponseDTO>> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierOnboardingRepository.findById(id)
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(id)
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id)));
            Long actualSupplierId = supplier.getId();

            List<DeliveryNote> deliveryNoteList = deliveryNoteRepository.findByPurchaseOrder_Supplier_Id(actualSupplierId);

            if (deliveryNoteList == null || deliveryNoteList.isEmpty()) {
                response.setMessage("No delivery notes found for the given supplier");
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                return response;
            }

            List<DeliveryNoteResponseDTO> deliveryNoteResponseDTOS = deliveryNoteList.stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setMessage("Delivery notes retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(deliveryNoteResponseDTOS);

        } catch (Exception e) {
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private List<DeliveryNoteItem> buildItems(List<DeliveryNoteItemRequestDTO> itemDTOs, DeliveryNote deliveryNote) {
        return itemDTOs.stream().map(itemDTO -> {
            PurchaseOrderItem poItem = purchaseOrderItemRepository
                    .findById(itemDTO.getPurchaseOrderItemId())
                    .orElseThrow(() -> new RuntimeException(
                            "Purchase order item not found with id: "
                                    + itemDTO.getPurchaseOrderItemId()));

            // Validate that delivered quantity does not exceed remaining PO quantity
            Integer alreadyDelivered = deliveryNoteItemRepository
                    .getTotalDeliveredQuantityByPurchaseOrderItemId(poItem.getId());
            Integer orderedQuantity = poItem.getQuantity();
            Integer remainingQuantity = orderedQuantity - alreadyDelivered;

            if (itemDTO.getDeliveredQuantity() > remainingQuantity) {
                throw new RuntimeException(
                        "Delivered quantity (" + itemDTO.getDeliveredQuantity() + ") for item '"
                        + poItem.getItemName() + "' exceeds remaining quantity. "
                        + "Ordered: " + orderedQuantity + ", Already delivered: " + alreadyDelivered
                        + ", Remaining: " + remainingQuantity);
            }

            return DeliveryNoteItem.builder()
                    .deliveryNote(deliveryNote)
                    .purchaseOrderItem(poItem)
                    .deliveredQuantity(itemDTO.getDeliveredQuantity())
                    .remarks(itemDTO.getRemarks())
                    .build();
        }).collect(Collectors.toList());
    }

    private DeliveryNoteResponseDTO toResponseDTO(DeliveryNote deliveryNote) {
        List<DeliveryNoteItemResponseDTO> itemDTOs = deliveryNote.getItems() == null
                ? List.of()
                : deliveryNote.getItems().stream()
                        .map(this::toItemResponseDTO)
                        .collect(Collectors.toList());

        return DeliveryNoteResponseDTO.builder()
                .id(deliveryNote.getId())
                .purchaseOrderId(deliveryNote.getPurchaseOrder().getId())
                .purchaseOrderNumber(deliveryNote.getPurchaseOrder().getPoNumber()) // adjust field name as needed
                .deliveryDate(deliveryNote.getDeliveryDate())
                .deliveredBy(deliveryNote.getDeliveredBy())
                .receivedBy(deliveryNote.getReceivedBy())
                .status(deliveryNote.getStatus())

                // .deliveryDocument(deliveryNote.getDeliveryDocument())
                .items(itemDTOs)
                .build();
    }

    private DeliveryNoteItemResponseDTO toItemResponseDTO(DeliveryNoteItem item) {
        return DeliveryNoteItemResponseDTO.builder()
                .id(item.getId())
                .deliveryNoteId(item.getDeliveryNote().getId())

                .itemName(item.getPurchaseOrderItem().getItemName())

                .itemDescription(item.getPurchaseOrderItem().getDescription())
                .orderedQuantity(item.getPurchaseOrderItem().getQuantity())
                .purchaseOrderItemId(item.getPurchaseOrderItem().getId())
                .deliveredQuantity(item.getDeliveredQuantity())
                .remarks(item.getRemarks())
                .build();
    }
}
