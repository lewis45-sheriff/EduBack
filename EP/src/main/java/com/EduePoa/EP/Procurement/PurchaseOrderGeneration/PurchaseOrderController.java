package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;


import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRejectionRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderRequestDTO;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests.PurchaseOrderUpdateRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/purchase-orders/")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService purchaseOrderService;

    @PostMapping("/register-supplier")
    @PreAuthorize("hasPermission(null, 'purchase_order:create')")
    public ResponseEntity<?> createPurchaseOrders(@RequestBody PurchaseOrderRequestDTO purchaseOrderRequestDTO) {
        var response = purchaseOrderService.create(purchaseOrderRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-by-id/{id}")
    @PreAuthorize("hasPermission(null, 'purchase_order:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = purchaseOrderService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-all")
    @PreAuthorize("hasPermission(null, 'purchase_order:read')")
    public ResponseEntity<?> getAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var response = purchaseOrderService.getAll(page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("update/{id}")
    @PreAuthorize("hasPermission(null, 'purchase_order:update')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PurchaseOrderUpdateRequestDTO dto) {
        var response = purchaseOrderService.update(id, dto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @DeleteMapping("delete/{id}")
    @PreAuthorize("hasPermission(null, 'purchase_order:delete')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = purchaseOrderService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PatchMapping("approve-purchase-order/{id}")
    @PreAuthorize("hasPermission(null, 'purchase_order:approve')")
    public ResponseEntity<?> approvePurchaseOrder(@PathVariable Long id) {
        var response = purchaseOrderService.approvePurchaseOrder(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PatchMapping("reject-purchase-order/{id}")
    @PreAuthorize("hasPermission(null, 'purchase_order:reject')")
    public ResponseEntity<?> rejectPurchaseOrder(@PathVariable Long id, @RequestBody PurchaseOrderRejectionRequestDTO dto) {
        var response = purchaseOrderService.rejectPurchaseOrder(id,dto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-purchase-oder-per-supplier/{supplierId}")
    @PreAuthorize("hasPermission(null, 'purchase_order:reject')")
    public ResponseEntity<?> getPurchaseOrderPerSupplier(@PathVariable Long supplierId) {
        var response = purchaseOrderService.getPurchaseOrderPerSupplier(supplierId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasPermission(null, 'purchase_order:read')")
    public ResponseEntity<?> getPurchaseOrdersBySupplier(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        var response = purchaseOrderService.getBySupplier(supplierId, page, size, sortBy, sortDir);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
