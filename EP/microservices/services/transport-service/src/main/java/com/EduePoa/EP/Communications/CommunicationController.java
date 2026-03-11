package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Communications.Enums.AnnouncementPriority;
import com.EduePoa.EP.Communications.Enums.AnnouncementStatus;
import com.EduePoa.EP.Communications.Enums.MessageStatus;
import com.EduePoa.EP.Communications.Enums.MessageType;
import com.EduePoa.EP.Communications.Enums.TargetAudience;
import com.EduePoa.EP.Communications.Requests.AnnouncementCreateRequest;
import com.EduePoa.EP.Communications.Requests.AnnouncementUpdateRequest;
import com.EduePoa.EP.Communications.Requests.MessageBulkSendRequest;
import com.EduePoa.EP.Communications.Requests.MessageSendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/communication/")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;

    // ==================== ANNOUNCEMENT ENDPOINTS ====================

    @GetMapping("announcements")
    public ResponseEntity<?> getAllAnnouncements(@RequestParam(required = false) AnnouncementStatus status, @RequestParam(required = false) AnnouncementPriority priority, @RequestParam(required = false) TargetAudience targetAudience, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var response = communicationService.getAllAnnouncements(status, priority, targetAudience, page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("announcements/{id}")
    public ResponseEntity<?> getAnnouncementById(@PathVariable Long id) {
        var response = communicationService.getAnnouncementById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("announcements")
    public ResponseEntity<?> createAnnouncement(@Valid @RequestBody AnnouncementCreateRequest request, Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.createAnnouncement(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("announcements/{id}")
    public ResponseEntity<?> updateAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementUpdateRequest request) {
        var response = communicationService.updateAnnouncement(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("announcements/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        var response = communicationService.deleteAnnouncement(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ==================== MESSAGE ENDPOINTS ====================

    @GetMapping("messages")
    public ResponseEntity<?> getAllMessages(@RequestParam(required = false) MessageStatus status, @RequestParam(required = false) MessageType messageType, @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var response = communicationService.getAllMessages(status, messageType, startDate, endDate, page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("messages/{id}")
    public ResponseEntity<?> getMessageById(@PathVariable Long id) {
        var response = communicationService.getMessageById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("messages/send")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody MessageSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendMessage(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("messages/send-bulk")
    public ResponseEntity<?> sendBulkMessage(@Valid @RequestBody MessageBulkSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendBulkMessage(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ==================== DELIVERY TRACKING ENDPOINTS ====================

    @GetMapping("delivery-report/{messageId}")
    public ResponseEntity<?> getDeliveryReport(@PathVariable Long messageId) {
        var response = communicationService.getDeliveryReport(messageId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("scheduled-messages")
    public ResponseEntity<?> getScheduledMessages() {
        var response = communicationService.getScheduledMessages();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("scheduled-messages/{id}")
    public ResponseEntity<?> cancelScheduledMessage(@PathVariable Long id) {
        var response = communicationService.cancelScheduledMessage(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
