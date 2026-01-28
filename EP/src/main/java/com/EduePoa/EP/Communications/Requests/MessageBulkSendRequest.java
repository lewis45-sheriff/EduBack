package com.EduePoa.EP.Communications.Requests;

import com.EduePoa.EP.Communications.Enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class MessageBulkSendRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Message type is required")
    private MessageType messageType;

    @NotNull(message = "Target group is required")
    private TargetGroup targetGroup;

    private LocalDateTime scheduledAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetGroup {
        @NotBlank(message = "Target type is required")
        private String type; // e.g., "GRADE_PARENTS", "ALL_PARENTS", "ALL_STAFF"

        private List<String> grades; // For GRADE_PARENTS type
    }
}
