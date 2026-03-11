package com.EduePoa.EP.Communications.Requests;

import com.EduePoa.EP.Communications.Enums.AnnouncementPriority;
import com.EduePoa.EP.Communications.Enums.AnnouncementStatus;
import com.EduePoa.EP.Communications.Enums.TargetAudience;
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
public class AnnouncementCreateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Priority is required")
    private AnnouncementPriority priority;

    @NotNull(message = "Target audience is required")
    private TargetAudience targetAudience;

    private List<String> targetGrades;

    @NotNull(message = "Status is required")
    private AnnouncementStatus status;

    private LocalDateTime publishedDate;

    private LocalDateTime expiryDate;
}
