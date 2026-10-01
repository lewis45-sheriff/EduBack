package com.EduePoa.EP.Transport.Boarding.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-event outcome inside a scan response. Placed in exactly one of the accepted / duplicates /
 * rejected buckets. {@code reason} is only populated for rejections; it never contains SQL,
 * stack traces, tenant ids or token values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardingEventOutcomeResponse {

    private String clientEventId;

    /** Populated for accepted events, and for duplicates when the existing event is safely known. */
    private Long eventId;

    private Long studentId;

    /** RECORDED | DUPLICATE | REJECTED */
    private String status;

    private String reason;
}
