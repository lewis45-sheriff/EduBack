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
import com.EduePoa.EP.Communications.Responses.*;
import com.EduePoa.EP.Utils.CustomResponse;

public interface CommunicationService {

    // Announcement methods
    CustomResponse<?> createAnnouncement(AnnouncementCreateRequest request, String username);

    CustomResponse<?> updateAnnouncement(Long id, AnnouncementUpdateRequest request);

    CustomResponse<?> deleteAnnouncement(Long id);

    CustomResponse<?> getAnnouncementById(Long id);

    CustomResponse<?> getAllAnnouncements(AnnouncementStatus status, AnnouncementPriority priority, TargetAudience targetAudience, int page, int size);

    // Message methods
    CustomResponse<?> sendMessage(MessageSendRequest request, String username);

    CustomResponse<?> sendBulkMessage(MessageBulkSendRequest request, String username);

    CustomResponse<?> getMessageById(Long id);

    CustomResponse<?> getAllMessages(MessageStatus status, MessageType messageType, String startDate, String endDate, int page, int size);

    // Delivery tracking methods
    CustomResponse<?> getDeliveryReport(Long messageId);

    CustomResponse<?> getScheduledMessages();

    CustomResponse<?> cancelScheduledMessage(Long id);
}
