package com.EduePoa.EP.Communications.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledMessageResponse {

    private Long id;
    private String subject;
    private String messageType;
    private LocalDateTime scheduledAt;
    private Long recipientCount;
    private String status;
}
