package com.EduePoa.EP.Procurement.SupplierInvoice;


import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Authentication.Enum.PaymentMethod;
import com.EduePoa.EP.Authentication.Enum.PaymentStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItemRepository;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderRepository;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceApprovalRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceItemRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceUploadRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Responses.SupplierInvoiceItemResponseDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Responses.SupplierInvoiceResponseDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoiceItem.SupplierInvoiceItem;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboardingRepository;
import com.EduePoa.EP.Procurement.SupplierPayments.Payment;
import com.EduePoa.EP.Procurement.SupplierPayments.PaymentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierInvoiceServiceImpl implements SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final SupplierOnboardingRepository supplierOnboardingRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final UserRepository userRepository;
    private final EtimsValidationService etimsValidationService;
    private final ThreeWayMatchService threeWayMatchService;
    private final PaymentRepository paymentRepository;

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "CREATE")
//    @Transactional
    public CustomResponse<?> create(SupplierInvoiceRequestDTO dto) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            if (supplierInvoiceRepository.existsByInvoiceNumber(dto.getInvoiceNumber())) {
                throw new RuntimeException("Invoice number already exists: " + dto.getInvoiceNumber());
            }


            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                    .orElseThrow(() -> new RuntimeException(
                            "Purchase order not found with id: " + dto.getPurchaseOrderId()));

            // Support partial delivery: Validate that the specific Delivery Notes haven't been invoiced yet
            List<SupplierInvoice> existingInvoices = supplierInvoiceRepository.findByPurchaseOrderId(purchaseOrder.getId());
            for (SupplierInvoice existingInvoice : existingInvoices) {
                if (existingInvoice.getStatus() != com.EduePoa.EP.Authentication.Enum.InvoiceStatus.REJECTED) {
                    for (DeliveryNote dn : existingInvoice.getDeliveryNotes()) {
                        if (dto.getDeliveryNoteIds().contains(dn.getId())) {
                            throw new RuntimeException("Delivery Note ID: " + dn.getId() + " has already been invoiced.");
                        }
                    }
                }
            }

            // Fetch all delivery notes
            List<DeliveryNote> deliveryNotes = new ArrayList<>();
            for (Long dnId : dto.getDeliveryNoteIds()) {
                DeliveryNote dn = deliveryNoteRepository.findById(dnId)
                        .orElseThrow(() -> new RuntimeException(
                                "Delivery note not found with id: " + dnId));
                deliveryNotes.add(dn);
            }


            SupplierOnboarding supplier = supplierOnboardingRepository.findById(dto.getSupplierId())
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(dto.getSupplierId())
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + dto.getSupplierId())));


            SupplierInvoice invoice = SupplierInvoice.builder()
                    .purchaseOrder(purchaseOrder)
                    .deliveryNotes(deliveryNotes)
                    .supplier(supplier)
                    .invoiceNumber(dto.getInvoiceNumber())
                    .invoiceDate(dto.getInvoiceDate())
                    .vatAmount(dto.getVatAmount())
                    .createdAt(LocalDateTime.now())
                    .status(InvoiceStatus.PENDING_APPROVAL)
                    .invoiceDocument(dto.getInvoiceDocument())
                    .build();

            List<SupplierInvoiceItem> items = buildItems(dto.getItems(), invoice);
            invoice.setItems(items);

            BigDecimal subtotal = computeSubtotal(items);
            BigDecimal total = subtotal.add(dto.getVatAmount());
            invoice.setTotalAmount(subtotal);
            invoice.setOutstandingBalance(subtotal);
            invoice.setPaidAmount(BigDecimal.ZERO);

            SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

            response.setMessage("Supplier invoice created successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "UPLOAD")
    public CustomResponse<?> uploadInvoice(SupplierInvoiceUploadRequestDTO request) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            //  ETIMS Validation
            etimsValidationService.validateETIMS(request);

            //  Validate invoice number uniqueness
            if (supplierInvoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
                throw new RuntimeException("Invoice number already exists: " + request.getInvoiceNumber());
            }

            PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                    .orElseThrow(() -> new RuntimeException("Purchase Order not found"));

            // Support partial delivery: Validate that the specific Delivery Notes haven't been invoiced yet
            List<SupplierInvoice> existingInvoices = supplierInvoiceRepository.findByPurchaseOrderId(po.getId());
            for (SupplierInvoice existingInvoice : existingInvoices) {
                if (existingInvoice.getStatus() != InvoiceStatus.REJECTED) {
                    for (DeliveryNote dn : existingInvoice.getDeliveryNotes()) {
                        if (request.getDeliveryNoteIds().contains(dn.getId())) {
                            throw new RuntimeException("Delivery Note ID: " + dn.getId() + " has already been invoiced.");
                        }
                    }
                }
            }

            //  Validate all DNs and same PO
            List<DeliveryNote> deliveryNotes = new ArrayList<>();
            for (Long dnId : request.getDeliveryNoteIds()) {
                DeliveryNote dn = deliveryNoteRepository.findById(dnId)
                        .orElseThrow(() -> new RuntimeException("Delivery Note (ID: " + dnId + ") not found"));

                // Cross-validate: each DN must be linked to the same PO
                if (!dn.getPurchaseOrder().getId().equals(po.getId())) {
                    throw new RuntimeException("Delivery Note (ID: " + dnId + ") is not linked to the specified Purchase Order");
                }
                deliveryNotes.add(dn);
            }

            SupplierOnboarding supplier = supplierOnboardingRepository.findById(request.getSupplierId())
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(request.getSupplierId())
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId())));

            // 3-Way Match + Amount Matching (after all validations pass)
            threeWayMatchService.performThreeWayMatch(
                    request.getPurchaseOrderId(),
                    request.getDeliveryNoteIds(),
                    request);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new RuntimeException("No authenticated user found");
            }
            String email = auth.getName();
            Optional<User> optionalUser = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(email, email);
            User createdBy = optionalUser.get();

            //  Create invoice
            SupplierInvoice invoice = SupplierInvoice.builder()
                    .purchaseOrder(po)
                    .deliveryNotes(deliveryNotes)
                    .supplier(supplier)
                    .invoiceNumber(request.getInvoiceNumber())
                    .invoiceDate(request.getInvoiceDate())
                    .totalAmount(request.getTotalAmount())
                    .vatAmount(request.getVatAmount())
                    .supplierTIN(request.getSupplierTIN())
                    .createdBy(createdBy)
                    .createdAt(LocalDateTime.now())
                    .status(InvoiceStatus.PENDING_APPROVAL)
                    .invoiceDocument(request.getInvoiceDocument())
                    .build();

            invoice.setOutstandingBalance(request.getTotalAmount());
            invoice.setPaidAmount(BigDecimal.ZERO);

            List<SupplierInvoiceItem> items = buildItems(request.getItems(), invoice);
            invoice.setItems(items);

            SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

            response.setMessage("Invoice uploaded successfully and pending approval");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (RuntimeException e) {
            response.setMessage("Invoice upload failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Invoice upload failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approveInvoice(SupplierInvoiceApprovalRequestDTO request) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            SupplierInvoice invoice = supplierInvoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            // Validate status
            if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) {
                throw new RuntimeException("Only PENDING_APPROVAL invoices can be approved. Current status: "
                        + invoice.getStatus());
            }

            User currentUser = getCurrentUser();

            if (request.getApproved()) {
                invoice.setStatus(InvoiceStatus.APPROVED);
                invoice.setApprovedBy(currentUser);
                invoice.setApprovedAt(LocalDateTime.now());
                invoice.setRejectionReason(null);

                SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

                // Auto-log a payment record representing the amount now owed to the supplier
                SupplierOnboarding supplier = invoice.getSupplier();
                BigDecimal invoiceTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;

//                Payment invoicePayment = Payment.builder()
//                        .supplierInvoice(saved)
//                        .supplier(supplier)
//                        .paymentDate(LocalDate.now())
//                        .amount(invoiceTotal)
////                        .paymentMethod(PaymentMethod.BANK_TRANSFER)
//                        .referenceNumber("INV-" + saved.getInvoiceNumber())
//                        .remarks("Auto-logged on invoice approval")
////                        .status(PaymentStatus.PENDING_APPROVAL)
//                        .createdBy(currentUser)
//                        .createdAt(LocalDateTime.now())
//                        .build();
//                paymentRepository.save(invoicePayment);

                // Credit the supplier's current balance (money now owed to them)
                BigDecimal currentBalance = supplier.getCurrentBalance() != null
                        ? supplier.getCurrentBalance()
                        : BigDecimal.ZERO;
                supplier.setCurrentBalance(currentBalance.add(invoiceTotal));
                supplierOnboardingRepository.save(supplier);

                response.setMessage("Invoice approved successfully. Payment of " + invoiceTotal + " logged and supplier balance updated.");
                response.setEntity(toResponseDTO(saved));
                response.setStatusCode(HttpStatus.OK.value());
            } else {
                invoice.setStatus(InvoiceStatus.REJECTED);
                invoice.setRejectedBy(currentUser);
                invoice.setRejectedAt(LocalDateTime.now());
                invoice.setRejectionReason(request.getRejectionReason());

                SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

                response.setMessage("Invoice rejected successfully");
                response.setEntity(toResponseDTO(saved));
                response.setStatusCode(HttpStatus.OK.value());
            }

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error processing invoice approval: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> rejectInvoice(SupplierInvoiceApprovalRequestDTO request) {
        request.setApproved(false);
        return approveInvoice(request);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "GET_BY_ID")
    public CustomResponse<?> getById(Long id) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            SupplierInvoice invoice = supplierInvoiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(
                            "Supplier invoice not found with id: " + id));

            response.setMessage("Supplier invoice fetched successfully");
            response.setEntity(toResponseDTO(invoice));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "GET_ALL")
    public CustomResponse<?> getAll() {
        CustomResponse<List<SupplierInvoiceResponseDTO>> response = new CustomResponse<>();
        try {
            List<SupplierInvoiceResponseDTO> all = supplierInvoiceRepository.findAll()
                    .stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setMessage("Supplier invoices fetched successfully");
            response.setEntity(all);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getByStatus(InvoiceStatus status) {
        CustomResponse<List<SupplierInvoiceResponseDTO>> response = new CustomResponse<>();
        try {
            List<SupplierInvoiceResponseDTO> filtered = supplierInvoiceRepository.findByStatus(status)
                    .stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setMessage("Supplier invoices fetched successfully");
            response.setEntity(filtered);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> update(Long id, SupplierInvoiceRequestDTO dto) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            SupplierInvoice existing = supplierInvoiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(
                            "Supplier invoice not found with id: " + id));

            // Guard duplicate invoice number on a different record
            supplierInvoiceRepository.findByInvoiceNumber(dto.getInvoiceNumber())
                    .ifPresent(found -> {
                        if (!found.getId().equals(id)) {
                            throw new RuntimeException(
                                    "Invoice number already in use: " + dto.getInvoiceNumber());
                        }
                    });

            PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                    .orElseThrow(() -> new RuntimeException(
                            "Purchase order not found with id: " + dto.getPurchaseOrderId()));

            // Fetch all delivery notes
            List<DeliveryNote> deliveryNotes = new ArrayList<>();
            for (Long dnId : dto.getDeliveryNoteIds()) {
                DeliveryNote dn = deliveryNoteRepository.findById(dnId)
                        .orElseThrow(() -> new RuntimeException(
                                "Delivery note not found with id: " + dnId));
                deliveryNotes.add(dn);
            }

            SupplierOnboarding supplier = supplierOnboardingRepository.findById(dto.getSupplierId())
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(dto.getSupplierId())
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + dto.getSupplierId())));

            existing.setPurchaseOrder(purchaseOrder);
            existing.setDeliveryNotes(deliveryNotes);
            existing.setSupplier(supplier);
            existing.setInvoiceNumber(dto.getInvoiceNumber());
            existing.setInvoiceDate(dto.getInvoiceDate());
            existing.setVatAmount(dto.getVatAmount());
            // existing.setStatus(InvoiceStatus.PENDING);
            existing.setInvoiceDocument(dto.getInvoiceDocument());

            // Replace items (orphanRemoval handles DB cleanup)
            existing.getItems().clear();
            List<SupplierInvoiceItem> updatedItems = buildItems(dto.getItems(), existing);
            existing.getItems().addAll(updatedItems);

            BigDecimal subtotal = computeSubtotal(updatedItems);
            existing.setTotalAmount(subtotal.add(dto.getVatAmount()));

            SupplierInvoice saved = supplierInvoiceRepository.save(existing);

            response.setMessage("Supplier invoice updated successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> updateStatus(Long id, InvoiceStatus status) {
        CustomResponse<SupplierInvoiceResponseDTO> response = new CustomResponse<>();
        try {
            SupplierInvoice existing = supplierInvoiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(
                            "Supplier invoice not found with id: " + id));

            existing.setStatus(status);
            SupplierInvoice saved = supplierInvoiceRepository.save(existing);

            response.setMessage("Invoice status updated to " + status);
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            if (!supplierInvoiceRepository.existsById(id)) {
                throw new RuntimeException("Supplier invoice not found with id: " + id);
            }

            supplierInvoiceRepository.deleteById(id);

            response.setMessage("Supplier invoice deleted successfully");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }
    @Override
    public CustomResponse<?> InvoicePerSupplier(Long id) {
        CustomResponse<List<SupplierInvoiceResponseDTO>> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierOnboardingRepository.findById(id)
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(id)
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id)));
            Long actualSupplierId = supplier.getId();

            List<SupplierInvoice> supplierInvoiceList = supplierInvoiceRepository.findBySupplierId(actualSupplierId);

            if (supplierInvoiceList == null || supplierInvoiceList.isEmpty()) {
                response.setMessage("No invoices found for the given supplier");
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(null);
                return response;
            }

            List<SupplierInvoiceResponseDTO> supplierInvoiceItemResponseDTOS = supplierInvoiceList.stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setEntity(supplierInvoiceItemResponseDTOS);
            response.setMessage("Supplier invoices retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "SUPPLIER INVOICE", action = "GET_BY_SUPPLIER")
    public CustomResponse<?> getBySupplier(Long supplierId, int page, int size, String sortBy, String sortDir) {
        CustomResponse<Page<SupplierInvoiceResponseDTO>> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierOnboardingRepository.findById(supplierId)
                    .orElseGet(() -> supplierOnboardingRepository.findByUser_Id(supplierId)
                            .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId)));
            Long actualSupplierId = supplier.getId();

            Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            Page<SupplierInvoiceResponseDTO> resultPage = supplierInvoiceRepository
                    .findBySupplierId(actualSupplierId, pageable)
                    .map(this::toResponseDTO);

            response.setMessage("Supplier invoices fetched successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(resultPage);

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setEntity(null);
        } catch (Exception e) {
            response.setMessage("Error fetching supplier invoices: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    private List<SupplierInvoiceItem> buildItems(List<SupplierInvoiceItemRequestDTO> itemDTOs,
            SupplierInvoice invoice) {
        return itemDTOs.stream().map(dto -> {
            PurchaseOrderItem poItem = purchaseOrderItemRepository.findById(dto.getPurchaseOrderItemId())
                    .orElseThrow(() -> new RuntimeException(
                            "Purchase order item not found with id: " + dto.getPurchaseOrderItemId()));

            // Validate: invoice unit price must match the PO unit price
            if (dto.getUnitPrice().compareTo(poItem.getUnitPrice()) != 0) {
                throw new RuntimeException(String.format(
                        "Unit price mismatch for item '%s' (PO Item ID: %d). " +
                        "Expected: %.2f (from Purchase Order), but invoice provided: %.2f",
                        poItem.getItemName(), poItem.getId(),
                        poItem.getUnitPrice(), dto.getUnitPrice()));
            }

            // Use PO unit price as the authoritative value
            BigDecimal total = poItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(dto.getInvoicedQuantity()));

            return SupplierInvoiceItem.builder()
                    .supplierInvoice(invoice)
                    .purchaseOrderItem(poItem)
                    .invoicedQuantity(dto.getInvoicedQuantity())
                    .unitPrice(poItem.getUnitPrice())
                    .totalPrice(total)
                    .build();
        }).collect(Collectors.toList());
    }

    private BigDecimal computeSubtotal(List<SupplierInvoiceItem> items) {
        return items.stream()
                .map(SupplierInvoiceItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SupplierInvoiceResponseDTO toResponseDTO(SupplierInvoice invoice) {
        List<SupplierInvoiceItemResponseDTO> itemDTOs = invoice.getItems() == null
                ? List.of()
                : invoice.getItems().stream()
                        .map(this::toItemResponseDTO)
                        .collect(Collectors.toList());

        List<Long> deliveryNoteIds = invoice.getDeliveryNotes() == null
                ? List.of()
                : invoice.getDeliveryNotes().stream()
                        .map(DeliveryNote::getId)
                        .collect(Collectors.toList());

        return SupplierInvoiceResponseDTO.builder()
                .id(invoice.getId())
                .purchaseOrderId(invoice.getPurchaseOrder().getId())
                .purchaseOrderReference(invoice.getPurchaseOrder().getPoNumber())
                .deliveryNoteIds(deliveryNoteIds)
                .supplierId(invoice.getSupplier().getId())
                .supplierName(invoice.getSupplier().getBusinessName())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate())
                .totalAmount(invoice.getTotalAmount())
                .vatAmount(invoice.getVatAmount())
                .status(invoice.getStatus())
                .invoiceDocument(invoice.getInvoiceDocument())
                .items(itemDTOs)
                .build();
    }

    private SupplierInvoiceItemResponseDTO toItemResponseDTO(SupplierInvoiceItem item) {
        return SupplierInvoiceItemResponseDTO.builder()
                .id(item.getId())
                .supplierInvoiceId(item.getSupplierInvoice().getId())
                .purchaseOrderItemId(item.getPurchaseOrderItem().getId())
                .purchaseOrderItemDescription(item.getPurchaseOrderItem().getDescription()) // adjust field name as
                                                                                            // needed
                .invoicedQuantity(item.getInvoicedQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
