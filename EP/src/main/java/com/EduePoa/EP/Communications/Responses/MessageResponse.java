package com.EduePoa.EP.Communications.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private String subject;
    private String content;
    private String messageType;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<RecipientResponse> recipients;
    private DeliveryStatsResponse deliveryStats;
}
