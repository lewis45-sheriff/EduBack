package com.EduePoa.EP.Communications.Requests;

import com.EduePoa.EP.Communications.Enums.AnnouncementPriority;
import com.EduePoa.EP.Communications.Enums.AnnouncementStatus;
import com.EduePoa.EP.Communications.Enums.TargetAudience;
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
public class AnnouncementUpdateRequest {

    private String title;

    private String content;

    private AnnouncementPriority priority;

    private TargetAudience targetAudience;

    private List<String> targetGrades;

    private AnnouncementStatus status;

    private LocalDateTime publishedDate;

    private LocalDateTime expiryDate;
}
