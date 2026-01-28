package com.EduePoa.EP.Communications.Requests;

import com.EduePoa.EP.Communications.Enums.RecipientType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientRequest {

    @NotNull(message = "Recipient type is required")
    private RecipientType recipientType;

    @NotNull(message = "Recipient ID is required")
    private Long recipientId;

    private String email;

    private String phone;

    private Map<String, String> placeholderData; // For template variables
}
