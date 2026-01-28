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
public class DeliveryReportResponse {

    private Long messageId;
    private String subject;
    private LocalDateTime sentAt;
    private StatisticsData statistics;
    private List<FailedRecipient> failedRecipients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsData {
        private Long totalRecipients;
        private Long delivered;
        private Long failed;
        private Long read;
        private Long pending;
        private Double deliveryRate;
        private Double readRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRecipient {
        private String recipientName;
        private String recipientType;
        private String phone;
        private String email;
        private String failureReason;
    }
}
