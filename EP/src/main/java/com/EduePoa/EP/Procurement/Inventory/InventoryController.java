package com.EduePoa.EP.Procurement.Inventory;


import com.EduePoa.EP.Procurement.Inventory.Requests.StockRequisitionRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/items")
    public ResponseEntity<?> getInventoryItems(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("itemName").ascending());
        CustomResponse<?> response = inventoryService.getInventoryItems(pageable);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<?> getInventoryItemById(@PathVariable Long id) {
        CustomResponse<?> response = inventoryService.getInventoryItemById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/items/{id}/transactions")
    public ResponseEntity<?> getTransactionHistory(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        CustomResponse<?> response = inventoryService.getTransactionHistory(id, pageable);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/items/low-stock")
    public ResponseEntity<?> getLowStockItems(@RequestParam(defaultValue = "10") int threshold, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("currentQuantity").ascending());
        CustomResponse<?> response = inventoryService.getLowStockItems(threshold, pageable);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ========================
    // STOCK REQUISITIONS
    // ========================

    @PostMapping("/requisitions")
    public ResponseEntity<?> createRequisition(@RequestBody StockRequisitionRequestDTO request) {
        CustomResponse<?> response = inventoryService.createRequisition(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/requisitions")
    public ResponseEntity<?> getRequisitions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        CustomResponse<?> response = inventoryService.getRequisitions(pageable);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/requisitions/{id}")
    public ResponseEntity<?> getRequisitionById(@PathVariable Long id) {
        CustomResponse<?> response = inventoryService.getRequisitionById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/requisitions/{id}/approve")
    public ResponseEntity<?> approveRequisition(@PathVariable Long id) {
        CustomResponse<?> response = inventoryService.approveRequisition(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/requisitions/{id}/reject")
    public ResponseEntity<?> rejectRequisition(@PathVariable Long id, @RequestParam(required = false) String reason) {
        CustomResponse<?> response = inventoryService.rejectRequisition(id, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
