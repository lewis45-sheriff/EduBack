package com.EduePoa.EP.FeeComponents.Responses;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeComponentResponse {
    private Long id;
    private String name;
    private String description;
    private String type;
    private String category;
    private String status;
    private LocalDateTime timeCreated;

}
