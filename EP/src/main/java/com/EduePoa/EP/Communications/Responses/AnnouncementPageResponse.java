package com.EduePoa.EP.Communications.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementPageResponse {

    private List<AnnouncementResponse> announcements;
    private Long totalElements;
    private Integer totalPages;
    private Integer currentPage;
}
