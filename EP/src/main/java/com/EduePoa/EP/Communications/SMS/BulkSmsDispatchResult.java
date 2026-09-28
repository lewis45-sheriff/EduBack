package com.EduePoa.EP.Communications.SMS;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Result of a bulk SMS dispatch via Africa's Talking. A single AT request may target many
 * recipients; the {@link #recipients} list holds the per-number outcome parsed from the
 * {@code SMSMessageData.Recipients} array in the AT response.
 */
@Getter
@Builder
public class BulkSmsDispatchResult {

    /** True if the request was accepted by AT and at least one recipient was queued/sent. */
    private final boolean success;

    /** Set when the whole request failed (network error, HTTP error, no valid recipients). */
    private final String errorMessage;

    /** Per-recipient outcomes. Empty when the request failed before AT processed it. */
    private final List<Recipient> recipients;

    @Getter
    @Builder
    public static class Recipient {
        private final String number;
        private final int statusCode;   // 101 = success, 102 = sent (no DLR yet), other = failure
        private final String status;
        private final String messageId;
        private final String cost;
        private final boolean success;
    }

    public static BulkSmsDispatchResult failed(String errorMessage) {
        return BulkSmsDispatchResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .recipients(List.of())
                .build();
    }
}
