package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;


import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderItemRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRejectionRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderUpdateRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Responses.PurchaseOrderItemResponseDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Responses.PurchaseOrderResponseDTO;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboardingRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    private final SupplierOnboardingRepository supplierOnboardingRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public CustomResponse<?> create(PurchaseOrderRequestDTO dto) {
        CustomResponse<PurchaseOrderResponseDTO> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierOnboardingRepository
                    .findById(dto.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));

            if (dto.getItems() == null || dto.getItems().isEmpty()) {
                response.setMessage("Purchase order must contain at least one item");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }
            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();
            System.out.println("this is my userName"+ email);

            User createdBy = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            String poNumber = generatePoNumber();

            PurchaseOrder purchaseOrder = new PurchaseOrder();
            purchaseOrder.setPoNumber(poNumber);
            purchaseOrder.setSupplier(supplier);
            purchaseOrder.setOrderDate(LocalDate.now());
            purchaseOrder.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
            purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);
            purchaseOrder.setCreatedBy(createdBy);
            purchaseOrder.setCreatedAt(LocalDateTime.now());

            List<PurchaseOrderItem> items = mapItems(dto.getItems(), purchaseOrder);
            BigDecimal totalAmount = calculateTotal(items);

            purchaseOrder.setItems(items);
            purchaseOrder.setTotalAmount(totalAmount);

            PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
//            auditService.logAction("POST", "PURCHASE ORDER", saved, "Purchase order created: " + saved.getPoNumber() + " for supplier: " + supplier.getBusinessName());

            response.setMessage("Purchase order created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setEntity(mapToResponseDTO(saved));

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getById(Long id) {
        CustomResponse<PurchaseOrderResponseDTO> response = new CustomResponse<>();
        try {
            PurchaseOrder po = purchaseOrderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            response.setMessage("Purchase order retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(mapToResponseDTO(po));
//            auditService.logAction("GET", "PURCHASE ORDER", po, "Purchase order retrieved: " + po.getPoNumber());
        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setMessage("Error retrieving purchase order: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAll(int page, int size) {
        CustomResponse<Page<PurchaseOrderResponseDTO>> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending());
            Page<PurchaseOrder> purchaseOrderPage = purchaseOrderRepository.findAll(pageable);

            Page<PurchaseOrderResponseDTO> responsePage = purchaseOrderPage.map(this::mapToResponseDTO);

            response.setMessage("Purchase orders retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(responsePage);
//            auditService.logAction("GET", "PURCHASE ORDER", null, "Purchase orders retrieved, page: " + page + ", total: " + purchaseOrderPage.getTotalElements());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> update(Long id, PurchaseOrderUpdateRequestDTO dto) {
        CustomResponse<PurchaseOrderResponseDTO> response = new CustomResponse<>();
        try {
            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            // Prevent editing a cancelled or delivered order
            if (purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED
                    || purchaseOrder.getStatus() == PurchaseOrderStatus.DELIVERED) {
                response.setMessage("Cannot update a " + purchaseOrder.getStatus() + " purchase order");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Update supplier if changed
            if (dto.getSupplierId() != null) {
                SupplierOnboarding supplier = supplierOnboardingRepository
                        .findById(dto.getSupplierId())
                        .orElseThrow(() -> new RuntimeException("Supplier not found"));
                purchaseOrder.setSupplier(supplier);
            }

            // Update simple fields
            if (dto.getExpectedDeliveryDate() != null) {
                purchaseOrder.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
            }

            if (dto.getStatus() != null) {
                purchaseOrder.setStatus(dto.getStatus());
            }

            // Replace items entirely if provided
            if (dto.getItems() != null && !dto.getItems().isEmpty()) {
                purchaseOrder.getItems().clear(); // triggers orphanRemoval
                List<PurchaseOrderItem> newItems = mapItems(dto.getItems(), purchaseOrder);
                purchaseOrder.getItems().addAll(newItems);
                purchaseOrder.setTotalAmount(calculateTotal(newItems));
            }

            PurchaseOrder updated = purchaseOrderRepository.save(purchaseOrder);
//            auditService.logAction("PUT", "PURCHASE ORDER", updated, "Purchase order updated: " + updated.getPoNumber());

            response.setMessage("Purchase order updated successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(mapToResponseDTO(updated));

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error updating purchase order: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            // Safe delete: only allow deleting DRAFT orders
            if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
                response.setMessage("Only DRAFT purchase orders can be deleted. Consider cancelling instead.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            purchaseOrderRepository.delete(purchaseOrder);
//            auditService.logAction("DELETE", "PURCHASE ORDER", null, "Purchase order deleted: " + purchaseOrder.getPoNumber());

            response.setMessage("Purchase order deleted successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(null);

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error deleting purchase order: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> approvePurchaseOrder(Long id) {
        CustomResponse<PurchaseOrderResponseDTO> response = new CustomResponse<>();
        try {
            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            // Guard: only DRAFT orders can be approved
            if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
                response.setMessage("Only DRAFT purchase orders can be approved. Current status: "
                        + purchaseOrder.getStatus());
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            User approver = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            // Apply approval
            purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
            purchaseOrder.setApprovedBy(approver);
            purchaseOrder.setApprovedAt(LocalDateTime.now());

            PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
//            auditService.logAction("APPROVE", "PURCHASE ORDER", saved, "Purchase order approved: " + saved.getPoNumber());

            response.setMessage("Purchase order approved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(mapToResponseDTO(saved));

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error approving purchase order: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> rejectPurchaseOrder(Long id, PurchaseOrderRejectionRequestDTO dto) {
        CustomResponse<PurchaseOrderResponseDTO> response = new CustomResponse<>();
        try {
            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Purchase order not found"));

            // only DRAFT orders can be sent back
            if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
                response.setMessage("Only DRAFT purchase orders can be rejected. Current status: "
                        + purchaseOrder.getStatus());
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Resolve the authenticated user
            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            User rejector = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            // Record rejection details — status stays PENDING for resubmission
            purchaseOrder.setRejectedBy(rejector);
            purchaseOrder.setRejectedAt(LocalDateTime.now());
            purchaseOrder.setRejectionReason(dto.getRejectionReason()); // can be null
            purchaseOrder.setRejectionCount(purchaseOrder.getRejectionCount() + 1);

            // Clear previous approval data in case it was approved before
            purchaseOrder.setApprovedBy(null);
            purchaseOrder.setApprovedAt(null);

            // Status intentionally stays PENDING — ready for resubmission
            PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
//            auditService.logAction("REJECT", "PURCHASE ORDER", saved, "Purchase order rejected: " + saved.getPoNumber() + (dto.getRejectionReason() != null ? ", reason: " + dto.getRejectionReason() : ""));

            response.setMessage("Purchase order sent back for revision"
                    + (dto.getRejectionReason() != null ? ": " + dto.getRejectionReason() : ""));
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(mapToResponseDTO(saved));

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error rejecting purchase order: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private String generatePoNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = purchaseOrderRepository.count() + 1;
        return "PO-" + datePart + "-" + String.format("%04d", count);
    }

    private List<PurchaseOrderItem> mapItems(
            List<PurchaseOrderItemRequestDTO> itemDTOs, PurchaseOrder purchaseOrder) {
        return itemDTOs.stream()
                .map(itemDTO -> {
                    BigDecimal totalPrice = itemDTO.getUnitPrice()
                            .multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
                    return PurchaseOrderItem.builder()
                            .itemName(itemDTO.getItemName())
                            .description(itemDTO.getDescription())
                            .quantity(itemDTO.getQuantity())
                            .unitPrice(itemDTO.getUnitPrice())
                            .totalPrice(totalPrice)
                            .unitOfMeasure(itemDTO.getUnitOfMeasure())
                            .purchaseOrder(purchaseOrder)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private BigDecimal calculateTotal(List<PurchaseOrderItem> items) {
        return items.stream()
                .map(PurchaseOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PurchaseOrderResponseDTO mapToResponseDTO(PurchaseOrder po) {
        List<PurchaseOrderItemResponseDTO> itemDTOs = po.getItems().stream()
                .map(item -> PurchaseOrderItemResponseDTO.builder()
                        .id(item.getId())
                        .itemName(item.getItemName())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .unitOfMeasure(item.getUnitOfMeasure())
                        .build())
                .collect(Collectors.toList());

        return PurchaseOrderResponseDTO.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierName(po.getSupplier().getBusinessName()) // adjust to your field
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .status(po.getStatus().name())
                .totalAmount(po.getTotalAmount())
                .items(itemDTOs)
                .approvedByName(po.getApprovedBy() != null
                        ? po.getApprovedBy().getFirstName() + " " + po.getApprovedBy().getLastName()
                        : null)
                .approvedByEmail(po.getApprovedBy() != null
                        ? po.getApprovedBy().getEmail()
                        : null)
                .approvedAt(po.getApprovedAt())
                .rejectedByName(po.getRejectedBy() != null
                        ? po.getRejectedBy().getFirstName() + " " + po.getRejectedBy().getLastName()
                        : null)
                .rejectedByEmail(po.getRejectedBy() != null
                        ? po.getRejectedBy().getEmail()
                        : null)
                .rejectedAt(po.getRejectedAt())
                .rejectionReason(po.getRejectionReason())
                .rejectionCount(po.getRejectionCount())
                .build();
    }

}
