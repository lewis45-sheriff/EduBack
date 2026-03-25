package com.EduePoa.EP.Communications.SMS;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SmsDispatchResult {

    private final boolean success;
    private final String messageId;
    private final String errorMessage;
    private final String cost;
    public static SmsDispatchResult ok(String messageId, String cost) {
        return SmsDispatchResult.builder()
                .success(true)
                .messageId(messageId)
                .cost(cost)
                .build();
    }
    public static SmsDispatchResult failed(String errorMessage) {
        return SmsDispatchResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
