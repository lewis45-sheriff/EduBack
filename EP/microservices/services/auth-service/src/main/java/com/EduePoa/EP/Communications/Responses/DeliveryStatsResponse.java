package com.EduePoa.EP.Communications.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatsResponse {

    private Long totalRecipients;
    private Long delivered;
    private Long failed;
    private Long read;
    private Long pending;
    private Double deliveryRate;
    private Double readRate;
}
