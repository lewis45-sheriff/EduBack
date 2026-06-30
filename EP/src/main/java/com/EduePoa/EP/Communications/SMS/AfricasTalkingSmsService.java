package com.EduePoa.EP.Communications.SMS;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.service.TenantConfigurationService;
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
    private final TenantConfigurationService tenantConfigurationService;

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

        // Resolve tenant-specific SMS config with fallback to application-level defaults
        String senderId = resolveSenderId();
        String apiKey = resolveApiKey();

        try {
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("username", smsConfig.getUsername())
                    .add("to", normalised)
                    .add("message", message);

            if (senderId != null && !senderId.isBlank()) {
                formBuilder.add("from", senderId);
            }

            Request request = new Request.Builder()
                    .url(smsConfig.getSms().getUrl())
                    .addHeader("Accept", "application/json")
                    .addHeader("apiKey", apiKey)
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

    /**
     * Resolves the SMS sender ID from tenant-specific configuration.
     * Falls back to the application-level SmsConfig if no tenant config is set or TenantContext is absent.
     */
    private String resolveSenderId() {
        if (TenantContext.isSet()) {
            String tenantId = TenantContext.getCurrentTenant();
            String tenantSenderId = tenantConfigurationService.getConfigOrDefault(tenantId, "sms.sender_id", null);
            if (tenantSenderId != null && !tenantSenderId.isBlank()) {
                return tenantSenderId;
            }
        }
        // Fallback to application-level config
        return smsConfig.getSms().getSenderId();
    }

    /**
     * Resolves the SMS API key from tenant-specific configuration.
     * Falls back to the application-level SmsConfig if no tenant config is set or TenantContext is absent.
     */
    private String resolveApiKey() {
        if (TenantContext.isSet()) {
            String tenantId = TenantContext.getCurrentTenant();
            String tenantApiKey = tenantConfigurationService.getConfigOrDefault(tenantId, "sms.api_key", null);
            if (tenantApiKey != null && !tenantApiKey.isBlank()) {
                return tenantApiKey;
            }
        }
        // Fallback to application-level config
        return smsConfig.getApiKey();
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
