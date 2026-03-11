package com.EduePoa.EP.Procurement.DeliveryNote;


import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteApprovalRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/delivery-note/")
@RequiredArgsConstructor
public class DeliveryNoteController {
    private final DeliveryNoteService deliveryNoteService;

    @PostMapping("create")
    @PreAuthorize("hasPermission(null, 'delivery_note:create')")
    public ResponseEntity<?> createStudent(@RequestBody DeliveryNoteRequestDTO deliveryNoteRequestDTO) {
        var response = deliveryNoteService.create(deliveryNoteRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-all")
    @PreAuthorize("hasPermission(null, 'delivery_note:read')")
    public ResponseEntity<?> getAll() {
        var response = deliveryNoteService.getAll();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-by-id/{id}")
    @PreAuthorize("hasPermission(null, 'delivery_note:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = deliveryNoteService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("edit/{id}")
    @PreAuthorize("hasPermission(null, 'delivery_note:update')")
    public ResponseEntity<?> update(@PathVariable Long id,
            @Valid @RequestBody DeliveryNoteRequestDTO deliveryNoteRequestDTO) {
        var response = deliveryNoteService.update(id, deliveryNoteRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("delete/{id}")
    @PreAuthorize("hasPermission(null, 'delivery_note:delete')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = deliveryNoteService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("approve")
    @PreAuthorize("hasPermission(null, 'delivery_note:approve')")
    public ResponseEntity<?> approveDeliveryNote(@Valid @RequestBody DeliveryNoteApprovalRequestDTO request) {
        var response = deliveryNoteService.approveDeliveryNote(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("reject/")
    @PreAuthorize("hasPermission(null, 'delivery_note:reject')")
    public ResponseEntity<?> rejectDeliveryNote(@PathVariable Long id,
            @Valid @RequestBody DeliveryNoteApprovalRequestDTO request) {
        var response = deliveryNoteService.rejectDeliveryNote(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @DeleteMapping("delivery-note-per-supplier/{id}")
    @PreAuthorize("hasPermission(null, 'delivery_note:delete')")
    public ResponseEntity<?> deliveryNotePerSupplierId(@PathVariable Long id) {
        var response = deliveryNoteService.deliveryNotePerSupplierId(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
