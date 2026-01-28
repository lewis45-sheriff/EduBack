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
public class AnnouncementResponse {

    private Long id;
    private String title;
    private String content;
    private String priority;
    private String targetAudience;
    private List<String> targetGrades;
    private String status;
    private LocalDateTime publishedDate;
    private LocalDateTime expiryDate;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentResponse> attachments;
    private Long viewCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentResponse {
        private Long id;
        private String filename;
        private String fileUrl;
        private Long fileSize;
        private String fileType;
    }
}
