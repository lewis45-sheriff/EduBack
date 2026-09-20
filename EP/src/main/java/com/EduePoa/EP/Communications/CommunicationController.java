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
import com.EduePoa.EP.Communications.Requests.SmsSendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/communication/")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;


    @GetMapping("announcements")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getAllAnnouncements(@RequestParam(required = false) AnnouncementStatus status, @RequestParam(required = false) AnnouncementPriority priority, @RequestParam(required = false) TargetAudience targetAudience, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var response = communicationService.getAllAnnouncements(status, priority, targetAudience, page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("announcements/{id}")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getAnnouncementById(@PathVariable Long id) {
        var response = communicationService.getAnnouncementById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("announcements")
    @PreAuthorize("hasPermission(null, 'announcement:create')")
    public ResponseEntity<?> createAnnouncement(@Valid @RequestBody AnnouncementCreateRequest request, Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.createAnnouncement(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("announcements/{id}")
    @PreAuthorize("hasPermission(null, 'announcement:create')")
    public ResponseEntity<?> updateAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementUpdateRequest request) {
        var response = communicationService.updateAnnouncement(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("announcements/{id}")
    @PreAuthorize("hasPermission(null, 'announcement:create')")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        var response = communicationService.deleteAnnouncement(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


    @GetMapping("messages")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getAllMessages(@RequestParam(required = false) MessageStatus status, @RequestParam(required = false) MessageType messageType, @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var response = communicationService.getAllMessages(status, messageType, startDate, endDate, page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("messages/{id}")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getMessageById(@PathVariable Long id) {
        var response = communicationService.getMessageById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("messages/send")
    @PreAuthorize("hasPermission(null, 'message:send')")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody MessageSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendMessage(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("messages/send-bulk")
    @PreAuthorize("hasPermission(null, 'message:send')")
    public ResponseEntity<?> sendBulkMessage(@Valid @RequestBody MessageBulkSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendBulkMessage(request, username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ==================== DELIVERY TRACKING ENDPOINTS ====================

    @GetMapping("delivery-report/{messageId}")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getDeliveryReport(@PathVariable Long messageId) {
        var response = communicationService.getDeliveryReport(messageId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("scheduled-messages")
    @PreAuthorize("hasPermission(null, 'communication:read')")
    public ResponseEntity<?> getScheduledMessages() {
        var response = communicationService.getScheduledMessages();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("scheduled-messages/{id}")
    @PreAuthorize("hasPermission(null, 'message:send')")
    public ResponseEntity<?> cancelScheduledMessage(@PathVariable Long id) {
        var response = communicationService.cancelScheduledMessage(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


    @PostMapping("sms/send-to-parent/{studentId}")
    @PreAuthorize("hasPermission(null, 'message:send')")
    public ResponseEntity<?> sendSmsToParent(@PathVariable Long studentId, @Valid @RequestBody SmsSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendSmsToParentOfStudent(studentId, request.getContent(), username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("sms/send-to-all-parents")
    @PreAuthorize("hasPermission(null, 'message:send')")
    public ResponseEntity<?> sendSmsToAllParents(@Valid @RequestBody SmsSendRequest request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        var response = communicationService.sendSmsToAllParents(request.getContent(), username);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
