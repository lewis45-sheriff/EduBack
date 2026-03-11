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
public class RecipientResponse {

    private Long id;
    private String recipientType;
    private Long recipientId;
    private String recipientName;
    private String email;
    private String phone;
    private String deliveryStatus;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
}
