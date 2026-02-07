package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Communications.Enums.*;
import com.EduePoa.EP.Communications.Requests.*;
import com.EduePoa.EP.Communications.Responses.*;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationServiceImpl implements CommunicationService {

    private final AnnouncementRepository announcementRepository;
    private final MessageRepository messageRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final AttachmentRepository attachmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final GradeRepository gradeRepository;
    private final AuditService auditService;

    // ==================== ANNOUNCEMENT METHODS ====================

    @Override
    @Transactional
    public CustomResponse<?> createAnnouncement(AnnouncementCreateRequest request, String username) {
        CustomResponse<AnnouncementResponse> response = new CustomResponse<>();
        try {
            Announcement announcement = Announcement.builder()
                    .title(request.getTitle())
                    .content(request.getContent())
                    .priority(request.getPriority())
                    .targetAudience(request.getTargetAudience())
                    .targetGrades(request.getTargetGrades() != null ? request.getTargetGrades() : new ArrayList<>())
                    .status(request.getStatus())
                    .publishedDate(request.getPublishedDate())
                    .expiryDate(request.getExpiryDate())
                    .createdBy(username)
                    .viewCount(0L)
                    .deletedFlag('N')
                    .attachments(new ArrayList<>())
                    .build();

            announcement = announcementRepository.save(announcement);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Announcement created successfully");
            response.setEntity(mapToAnnouncementResponse(announcement));
            auditService.log("COMMUNICATION", "Created announcement:", announcement.getTitle(), "with ID:",
                    String.valueOf(announcement.getId()));

        } catch (Exception e) {
            log.error("Error creating announcement: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error creating announcement: " + e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> updateAnnouncement(Long id, AnnouncementUpdateRequest request) {
        CustomResponse<AnnouncementResponse> response = new CustomResponse<>();
        try {
            Announcement announcement = announcementRepository.findByIdAndDeletedFlag(id, 'N')
                    .orElseThrow(() -> new RuntimeException("Announcement not found"));

            if (request.getTitle() != null)
                announcement.setTitle(request.getTitle());
            if (request.getContent() != null)
                announcement.setContent(request.getContent());
            if (request.getPriority() != null)
                announcement.setPriority(request.getPriority());
            if (request.getTargetAudience() != null)
                announcement.setTargetAudience(request.getTargetAudience());
            if (request.getTargetGrades() != null)
                announcement.setTargetGrades(request.getTargetGrades());
            if (request.getStatus() != null)
                announcement.setStatus(request.getStatus());
            if (request.getPublishedDate() != null)
                announcement.setPublishedDate(request.getPublishedDate());
            if (request.getExpiryDate() != null)
                announcement.setExpiryDate(request.getExpiryDate());

            announcement = announcementRepository.save(announcement);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Announcement updated successfully");
            response.setEntity(mapToAnnouncementResponse(announcement));
            auditService.log("COMMUNICATION", "Updated announcement:", announcement.getTitle(), "with ID:",
                    String.valueOf(id));

        } catch (Exception e) {
            log.error("Error updating announcement: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating announcement: " + e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> deleteAnnouncement(Long id) {
        CustomResponse<String> response = new CustomResponse<>();
        try {
            Announcement announcement = announcementRepository.findByIdAndDeletedFlag(id, 'N')
                    .orElseThrow(() -> new RuntimeException("Announcement not found"));

            announcement.setDeletedFlag('Y');
            announcement.setStatus(AnnouncementStatus.ARCHIVED);
            announcementRepository.save(announcement);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Announcement archived successfully");
            auditService.log("COMMUNICATION", "Archived announcement with ID:", String.valueOf(id));

        } catch (Exception e) {
            log.error("Error deleting announcement: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error deleting announcement: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAnnouncementById(Long id) {
        CustomResponse<AnnouncementResponse> response = new CustomResponse<>();
        try {
            Announcement announcement = announcementRepository.findByIdAndDeletedFlag(id, 'N')
                    .orElseThrow(() -> new RuntimeException("Announcement not found"));

            // Increment view count
            announcement.setViewCount(announcement.getViewCount() + 1);
            announcementRepository.save(announcement);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Announcement retrieved successfully");
            response.setEntity(mapToAnnouncementResponse(announcement));

        } catch (Exception e) {
            log.error("Error getting announcement: ", e);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage("Announcement not found: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllAnnouncements(AnnouncementStatus status, AnnouncementPriority priority,
            TargetAudience targetAudience, int page, int size) {
        CustomResponse<AnnouncementPageResponse> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Announcement> announcementPage;

            if (status != null || priority != null || targetAudience != null) {
                announcementPage = announcementRepository.findWithFilters(status, priority, targetAudience, pageable);
            } else {
                announcementPage = announcementRepository.findByDeletedFlag('N', pageable);
            }

            List<AnnouncementResponse> announcements = announcementPage.getContent().stream()
                    .map(this::mapToAnnouncementResponse)
                    .collect(Collectors.toList());

            AnnouncementPageResponse pageResponse = AnnouncementPageResponse.builder()
                    .announcements(announcements)
                    .totalElements(announcementPage.getTotalElements())
                    .totalPages(announcementPage.getTotalPages())
                    .currentPage(page)
                    .build();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Announcements retrieved successfully");
            response.setEntity(pageResponse);

        } catch (Exception e) {
            log.error("Error getting announcements: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error getting announcements: " + e.getMessage());
        }
        return response;
    }

    // ==================== MESSAGE METHODS ====================

    @Override
    @Transactional
    public CustomResponse<?> sendMessage(MessageSendRequest request, String username) {
        CustomResponse<MessageResponse> response = new CustomResponse<>();
        try {
            Message message = Message.builder()
                    .subject(request.getSubject())
                    .content(request.getContent())
                    .messageType(request.getMessageType())
                    .status(request.getScheduledAt() != null ? MessageStatus.SCHEDULED : MessageStatus.SENT)
                    .scheduledAt(request.getScheduledAt())
                    .sentAt(request.getScheduledAt() == null ? LocalDateTime.now() : null)
                    .createdBy(username)
                    .deletedFlag('N')
                    .build();

            message = messageRepository.save(message);

            // Create recipients
            List<MessageRecipient> recipients = new ArrayList<>();
            for (RecipientRequest recipientReq : request.getRecipients()) {
                MessageRecipient recipient = MessageRecipient.builder()
                        .recipientType(recipientReq.getRecipientType())
                        .recipientId(recipientReq.getRecipientId())
                        .recipientName(getRecipientName(recipientReq))
                        .email(recipientReq.getEmail())
                        .phone(recipientReq.getPhone())
                        .deliveryStatus(
                                request.getScheduledAt() != null ? DeliveryStatus.PENDING : DeliveryStatus.DELIVERED)
                        .deliveredAt(request.getScheduledAt() == null ? LocalDateTime.now() : null)
                        .message(message)
                        .build();
                recipients.add(recipient);
            }

            messageRecipientRepository.saveAll(recipients);
            message.setRecipients(recipients);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage(
                    "Message " + (request.getScheduledAt() != null ? "scheduled" : "sent") + " successfully");
            response.setEntity(mapToMessageResponse(message));
            auditService.log("COMMUNICATION", "Sent message:", message.getSubject(), "to",
                    String.valueOf(request.getRecipients().size()), "recipients");

        } catch (Exception e) {
            log.error("Error sending message: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error sending message: " + e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> sendBulkMessage(MessageBulkSendRequest request, String username) {
        CustomResponse<MessageResponse> response = new CustomResponse<>();
        try {
            Message message = Message.builder()
                    .subject(request.getSubject())
                    .content(request.getContent())
                    .messageType(request.getMessageType())
                    .status(request.getScheduledAt() != null ? MessageStatus.SCHEDULED : MessageStatus.SENT)
                    .scheduledAt(request.getScheduledAt())
                    .sentAt(request.getScheduledAt() == null ? LocalDateTime.now() : null)
                    .createdBy(username)
                    .deletedFlag('N')
                    .build();

            message = messageRepository.save(message);

            // Resolve recipients based on target group
            List<MessageRecipient> recipients = resolveTargetGroup(request.getTargetGroup(), message,
                    request.getScheduledAt());
            messageRecipientRepository.saveAll(recipients);
            message.setRecipients(recipients);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage(
                    "Bulk message " + (request.getScheduledAt() != null ? "scheduled" : "sent") + " successfully");
            response.setEntity(mapToMessageResponse(message));
            auditService.log("COMMUNICATION", "Sent bulk message:", message.getSubject(), "to",
                    String.valueOf(recipients.size()), "recipients");

        } catch (Exception e) {
            log.error("Error sending bulk message: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error sending bulk message: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getMessageById(Long id) {
        CustomResponse<MessageResponse> response = new CustomResponse<>();
        try {
            Message message = messageRepository.findByIdAndDeletedFlag(id, 'N')
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Message retrieved successfully");
            response.setEntity(mapToMessageResponse(message));

        } catch (Exception e) {
            log.error("Error getting message: ", e);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage("Message not found: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllMessages(
            MessageStatus status,
            MessageType messageType,
            String startDate,
            String endDate,
            int page,
            int size) {
        CustomResponse<MessagePageResponse> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Message> messagePage;

            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate, DateTimeFormatter.ISO_DATE_TIME)
                    : null;
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate, DateTimeFormatter.ISO_DATE_TIME) : null;

            if (status != null || messageType != null || start != null || end != null) {
                messagePage = messageRepository.findWithFilters(status, messageType, start, end, pageable);
            } else {
                messagePage = messageRepository.findByDeletedFlag('N', pageable);
            }

            List<MessageResponse> messages = messagePage.getContent().stream()
                    .map(this::mapToMessageResponseSimple)
                    .collect(Collectors.toList());

            MessagePageResponse pageResponse = MessagePageResponse.builder()
                    .messages(messages)
                    .totalElements(messagePage.getTotalElements())
                    .totalPages(messagePage.getTotalPages())
                    .currentPage(page)
                    .build();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Messages retrieved successfully");
            response.setEntity(pageResponse);

        } catch (Exception e) {
            log.error("Error getting messages: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error getting messages: " + e.getMessage());
        }
        return response;
    }

    // ==================== DELIVERY TRACKING METHODS ====================

    @Override
    public CustomResponse<?> getDeliveryReport(Long messageId) {
        CustomResponse<DeliveryReportResponse> response = new CustomResponse<>();
        try {
            Message message = messageRepository.findByIdAndDeletedFlag(messageId, 'N')
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            DeliveryStatsResponse stats = calculateDeliveryStats(messageId);
            List<MessageRecipient> failedRecipients = messageRecipientRepository
                    .findByMessageIdAndDeliveryStatus(messageId, DeliveryStatus.FAILED);

            List<DeliveryReportResponse.FailedRecipient> failed = failedRecipients.stream()
                    .map(r -> DeliveryReportResponse.FailedRecipient.builder()
                            .recipientName(r.getRecipientName())
                            .recipientType(r.getRecipientType().name())
                            .phone(r.getPhone())
                            .email(r.getEmail())
                            .failureReason("Delivery failed")
                            .build())
                    .collect(Collectors.toList());

            DeliveryReportResponse.StatisticsData statistics = DeliveryReportResponse.StatisticsData.builder()
                    .totalRecipients(stats.getTotalRecipients())
                    .delivered(stats.getDelivered())
                    .failed(stats.getFailed())
                    .read(stats.getRead())
                    .pending(stats.getPending())
                    .deliveryRate(stats.getDeliveryRate())
                    .readRate(stats.getReadRate())
                    .build();

            DeliveryReportResponse report = DeliveryReportResponse.builder()
                    .messageId(messageId)
                    .subject(message.getSubject())
                    .sentAt(message.getSentAt())
                    .statistics(statistics)
                    .failedRecipients(failed)
                    .build();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Delivery report retrieved successfully");
            response.setEntity(report);

        } catch (Exception e) {
            log.error("Error getting delivery report: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error getting delivery report: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getScheduledMessages() {
        CustomResponse<List<ScheduledMessageResponse>> response = new CustomResponse<>();
        try {
            List<Message> scheduledMessages = messageRepository.findScheduledMessages();

            List<ScheduledMessageResponse> messages = scheduledMessages.stream()
                    .map(msg -> ScheduledMessageResponse.builder()
                            .id(msg.getId())
                            .subject(msg.getSubject())
                            .messageType(msg.getMessageType().name())
                            .scheduledAt(msg.getScheduledAt())
                            .recipientCount((long) msg.getRecipients().size())
                            .status(msg.getStatus().name())
                            .build())
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Scheduled messages retrieved successfully");
            response.setEntity(messages);

        } catch (Exception e) {
            log.error("Error getting scheduled messages: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error getting scheduled messages: " + e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> cancelScheduledMessage(Long id) {
        CustomResponse<String> response = new CustomResponse<>();
        try {
            Message message = messageRepository.findByIdAndDeletedFlag(id, 'N')
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            if (!message.getStatus().equals(MessageStatus.SCHEDULED)) {
                throw new RuntimeException("Message is not scheduled");
            }

            message.setDeletedFlag('Y');
            message.setStatus(MessageStatus.FAILED);
            messageRepository.save(message);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Scheduled message cancelled successfully");
            auditService.log("COMMUNICATION", "Cancelled scheduled message with ID:", String.valueOf(id));

        } catch (Exception e) {
            log.error("Error cancelling scheduled message: ", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error cancelling scheduled message: " + e.getMessage());
        }
        return response;
    }

    // ==================== HELPER METHODS ====================

    private AnnouncementResponse mapToAnnouncementResponse(Announcement announcement) {
        List<AnnouncementResponse.AttachmentResponse> attachments = announcement.getAttachments().stream()
                .map(a -> AnnouncementResponse.AttachmentResponse.builder()
                        .id(a.getId())
                        .filename(a.getFilename())
                        .fileUrl(a.getFileUrl())
                        .fileSize(a.getFileSize())
                        .fileType(a.getFileType())
                        .build())
                .collect(Collectors.toList());

        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .priority(announcement.getPriority().name())
                .targetAudience(announcement.getTargetAudience().name())
                .targetGrades(announcement.getTargetGrades())
                .status(announcement.getStatus().name())
                .publishedDate(announcement.getPublishedDate())
                .expiryDate(announcement.getExpiryDate())
                .createdBy(announcement.getCreatedBy())
                .createdAt(announcement.getCreatedAt())
                .updatedAt(announcement.getUpdatedAt())
                .attachments(attachments)
                .viewCount(announcement.getViewCount())
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message) {
        List<RecipientResponse> recipients = message.getRecipients().stream()
                .map(r -> RecipientResponse.builder()
                        .id(r.getId())
                        .recipientType(r.getRecipientType().name())
                        .recipientId(r.getRecipientId())
                        .recipientName(r.getRecipientName())
                        .email(r.getEmail())
                        .phone(r.getPhone())
                        .deliveryStatus(r.getDeliveryStatus().name())
                        .deliveredAt(r.getDeliveredAt())
                        .readAt(r.getReadAt())
                        .build())
                .collect(Collectors.toList());

        DeliveryStatsResponse stats = calculateDeliveryStats(message.getId());

        return MessageResponse.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .status(message.getStatus().name())
                .scheduledAt(message.getScheduledAt())
                .sentAt(message.getSentAt())
                .createdBy(message.getCreatedBy())
                .createdAt(message.getCreatedAt())
                .recipients(recipients)
                .deliveryStats(stats)
                .build();
    }

    private MessageResponse mapToMessageResponseSimple(Message message) {
        DeliveryStatsResponse stats = calculateDeliveryStats(message.getId());

        return MessageResponse.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .status(message.getStatus().name())
                .scheduledAt(message.getScheduledAt())
                .sentAt(message.getSentAt())
                .createdBy(message.getCreatedBy())
                .createdAt(message.getCreatedAt())
                .recipients(null) // Don't load all recipients for list view
                .deliveryStats(stats)
                .build();
    }

    private DeliveryStatsResponse calculateDeliveryStats(Long messageId) {
        Long total = messageRecipientRepository.countByMessageId(messageId);
        Long delivered = messageRecipientRepository.countByMessageIdAndStatus(messageId, DeliveryStatus.DELIVERED);
        Long failed = messageRecipientRepository.countByMessageIdAndStatus(messageId, DeliveryStatus.FAILED);
        Long read = messageRecipientRepository.countByMessageIdAndStatus(messageId, DeliveryStatus.READ);
        Long pending = messageRecipientRepository.countByMessageIdAndStatus(messageId, DeliveryStatus.PENDING);

        double deliveryRate = total > 0 ? (delivered.doubleValue() / total * 100) : 0.0;
        double readRate = total > 0 ? (read.doubleValue() / total * 100) : 0.0;

        return DeliveryStatsResponse.builder()
                .totalRecipients(total)
                .delivered(delivered)
                .failed(failed)
                .read(read)
                .pending(pending)
                .deliveryRate(Math.round(deliveryRate * 100.0) / 100.0)
                .readRate(Math.round(readRate * 100.0) / 100.0)
                .build();
    }

    private String getRecipientName(RecipientRequest recipientReq) {
        try {
            return switch (recipientReq.getRecipientType()) {
                case STUDENT -> studentRepository.findById(recipientReq.getRecipientId())
                        .map(s -> s.getFirstName() + " " + s.getLastName())
                        .orElse("Unknown Student");
                case PARENT, STAFF -> userRepository.findById(recipientReq.getRecipientId())
                        .map(u -> u.getFirstName() + " " + u.getLastName())
                        .orElse("Unknown User");
                default -> "Unknown";
            };
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private List<MessageRecipient> resolveTargetGroup(
            MessageBulkSendRequest.TargetGroup targetGroup,
            Message message,
            LocalDateTime scheduledAt) {

        List<MessageRecipient> recipients = new ArrayList<>();
        DeliveryStatus status = scheduledAt != null ? DeliveryStatus.PENDING : DeliveryStatus.DELIVERED;
        LocalDateTime deliveredAt = scheduledAt == null ? LocalDateTime.now() : null;

        switch (targetGroup.getType().toUpperCase()) {
            case "GRADE_PARENTS":
                if (targetGroup.getGrades() != null) {
                    for (String gradeName : targetGroup.getGrades()) {
                        List<Student> students = studentRepository.findAll().stream()
                                .filter(s -> gradeName.equals(s.getGradeName()) && !s.getIsDeleted())
                                .toList();

                        for (Student student : students) {
                            if (student.getParent() != null) {
                                recipients.add(MessageRecipient.builder()
                                        .recipientType(RecipientType.PARENT)
                                        .recipientId(student.getParent().getId())
                                        .recipientName(student.getParent().getFirstName() + " "
                                                + student.getParent().getLastName())
                                        .email(student.getParent().getEmail())
                                        .phone(student.getParent().getPhoneNumber())
                                        .deliveryStatus(status)
                                        .deliveredAt(deliveredAt)
                                        .message(message)
                                        .build());
                            }
                        }
                    }
                }
                break;

            case "ALL_PARENTS":
                List<Student> allStudents = studentRepository.findAllByIsDeleted(false);
                for (Student student : allStudents) {
                    if (student.getParent() != null) {
                        recipients.add(MessageRecipient.builder()
                                .recipientType(RecipientType.PARENT)
                                .recipientId(student.getParent().getId())
                                .recipientName(
                                        student.getParent().getFirstName() + " " + student.getParent().getLastName())
                                .email(student.getParent().getEmail())
                                .phone(student.getParent().getPhoneNumber())
                                .deliveryStatus(status)
                                .deliveredAt(deliveredAt)
                                .message(message)
                                .build());
                    }
                }
                break;

            case "ALL_STAFF":
                List<User> allStaff = userRepository.findAll();
                for (User user : allStaff) {
                    recipients.add(MessageRecipient.builder()
                            .recipientType(RecipientType.STAFF)
                            .recipientId(user.getId())
                            .recipientName(user.getFirstName() + " " + user.getLastName())
                            .email(user.getEmail())
                            .phone(user.getPhoneNumber())
                            .deliveryStatus(status)
                            .deliveredAt(deliveredAt)
                            .message(message)
                            .build());
                }
                break;
        }

        return recipients;
    }
}
