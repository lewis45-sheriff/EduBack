package com.EduePoa.EP.Communications.Requests;

import com.EduePoa.EP.Communications.Enums.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class MessageSendRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Message type is required")
    private MessageType messageType;

    @NotEmpty(message = "At least one recipient is required")
    @Valid
    private List<RecipientRequest> recipients;

    private LocalDateTime scheduledAt;
}
