package com.EduePoa.EP.Communications.SMS;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingSmsService implements SmsGatewayService {

    private final SmsConfig smsConfig;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public SmsDispatchResult sendSms(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return SmsDispatchResult.failed("Phone number is blank — SMS not sent");
        }
        if (message == null || message.isBlank()) {
            return SmsDispatchResult.failed("Message content is blank — SMS not sent");
        }

        String normalised = normalisePhone(phoneNumber);
        log.info("[SMS] Dispatching to {} via Africa's Talking", normalised);

        try {
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("username", smsConfig.getUsername())
                    .add("to", normalised)
                    .add("message", message);

            String senderId = smsConfig.getSms().getSenderId();
            if (senderId != null && !senderId.isBlank()) {
                formBuilder.add("from", senderId);
            }

            Request request = new Request.Builder()
                    .url(smsConfig.getSms().getUrl())
                    .addHeader("Accept", "application/json")
                    .addHeader("apiKey", smsConfig.getApiKey())
                    .post(formBuilder.build())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.debug("[SMS] AT response [{}]: {}", response.code(), responseBody);

                if (!response.isSuccessful()) {
                    log.error("[SMS] Africa's Talking HTTP error {}: {}", response.code(), responseBody);
                    return SmsDispatchResult.failed("HTTP " + response.code() + ": " + responseBody);
                }

                return parseAtResponse(responseBody);
            }

        } catch (IOException e) {
            log.error("[SMS] Network error contacting Africa's Talking: {}", e.getMessage(), e);
            return SmsDispatchResult.failed("Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[SMS] Unexpected error dispatching SMS: {}", e.getMessage(), e);
            return SmsDispatchResult.failed("Unexpected error: " + e.getMessage());
        }
    }


    private String normalisePhone(String phone) {
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("07") || cleaned.startsWith("01")) {
            return "+254" + cleaned.substring(1);
        }
        if (cleaned.startsWith("254") && !cleaned.startsWith("+")) {
            return "+" + cleaned;
        }
        return cleaned; // already in E.164 or unknown format — pass as-is
    }


    private SmsDispatchResult parseAtResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject smsData = root.getAsJsonObject("SMSMessageData");
            JsonArray recipients = smsData.getAsJsonArray("Recipients");

            if (recipients == null || recipients.isEmpty()) {
                String msg = smsData.has("Message") ? smsData.get("Message").getAsString() : "No recipients in response";
                return SmsDispatchResult.failed(msg);
            }

            JsonObject first = recipients.get(0).getAsJsonObject();
            int statusCode = first.get("statusCode").getAsInt();
            String status = first.get("status").getAsString();
            String messageId = first.has("messageId") ? first.get("messageId").getAsString() : null;
            String cost = first.has("cost") ? first.get("cost").getAsString() : null;

            // statusCode 101 = Success, 102 = Sent (no delivery report yet), others = failure
            if (statusCode == 101 || statusCode == 102) {
                log.info("[SMS] Accepted by AT — id: {}, cost: {}", messageId, cost);
                return SmsDispatchResult.ok(messageId, cost);
            } else {
                log.warn("[SMS] AT rejected message — statusCode: {}, status: {}", statusCode, status);
                return SmsDispatchResult.failed("AT rejected: " + status + " (code " + statusCode + ")");
            }

        } catch (Exception e) {
            log.error("[SMS] Failed to parse AT response: {}", e.getMessage());
            return SmsDispatchResult.failed("Response parse error: " + e.getMessage());
        }
    }
}
