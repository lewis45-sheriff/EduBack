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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public BulkSmsDispatchResult sendBulkSms(List<String> phoneNumbers, String message) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            return BulkSmsDispatchResult.failed("No recipients — bulk SMS not sent");
        }
        if (message == null || message.isBlank()) {
            return BulkSmsDispatchResult.failed("Message content is blank — bulk SMS not sent");
        }

        // Normalise, drop blanks, de-duplicate, then join with commas (AT bulk "to" format).
        String to = phoneNumbers.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(this::normalisePhone)
                .distinct()
                .collect(Collectors.joining(","));

        if (to.isBlank()) {
            return BulkSmsDispatchResult.failed("No valid recipients after normalisation");
        }

        String senderId = resolveSenderId();
        String apiKey = resolveApiKey();
        int count = (int) java.util.Arrays.stream(to.split(",")).filter(s -> !s.isBlank()).count();
        log.info("[SMS] Bulk dispatch to {} recipient(s) via Africa's Talking", count);
        // Diagnostic: confirm what config the app actually resolved (key masked).
        log.info("[SMS-DIAG] username='{}' url='{}' senderId='{}' apiKeyLen={} apiKeyMasked='{}'",
                smsConfig.getUsername(),
                smsConfig.getSms().getUrl(),
                senderId,
                apiKey == null ? 0 : apiKey.length(),
                maskKey(apiKey));

        try {
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("username", smsConfig.getUsername())
                    .add("to", to)
                    .add("message", message)
                    .add("bulkSMSMode", "1")   // treat as bulk send
                    .add("enqueue", "1");      // recommended for large batches

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
                log.debug("[SMS] AT bulk response [{}]: {}", response.code(), responseBody);

                if (!response.isSuccessful()) {
                    log.error("[SMS] Africa's Talking bulk HTTP error {}: {}", response.code(), responseBody);
                    return BulkSmsDispatchResult.failed("HTTP " + response.code() + ": " + responseBody);
                }

                return parseBulkResponse(responseBody);
            }

        } catch (IOException e) {
            log.error("[SMS] Network error contacting Africa's Talking (bulk): {}", e.getMessage(), e);
            return BulkSmsDispatchResult.failed("Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[SMS] Unexpected error dispatching bulk SMS: {}", e.getMessage(), e);
            return BulkSmsDispatchResult.failed("Unexpected error: " + e.getMessage());
        }
    }

    private BulkSmsDispatchResult parseBulkResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject smsData = root.getAsJsonObject("SMSMessageData");
            JsonArray recipients = smsData != null ? smsData.getAsJsonArray("Recipients") : null;

            List<BulkSmsDispatchResult.Recipient> out = new ArrayList<>();
            if (recipients != null) {
                for (var el : recipients) {
                    JsonObject r = el.getAsJsonObject();
                    int code = r.has("statusCode") ? r.get("statusCode").getAsInt() : -1;
                    out.add(BulkSmsDispatchResult.Recipient.builder()
                            .number(r.has("number") ? r.get("number").getAsString() : null)
                            .statusCode(code)
                            .status(r.has("status") ? r.get("status").getAsString() : null)
                            .messageId(r.has("messageId") ? r.get("messageId").getAsString() : null)
                            .cost(r.has("cost") ? r.get("cost").getAsString() : null)
                            .success(code == 101 || code == 102)
                            .build());
                }
            }

            if (out.isEmpty()) {
                String msg = (smsData != null && smsData.has("Message"))
                        ? smsData.get("Message").getAsString()
                        : "No recipients in AT response";
                return BulkSmsDispatchResult.failed(msg);
            }

            boolean anyAccepted = out.stream().anyMatch(BulkSmsDispatchResult.Recipient::isSuccess);
            long accepted = out.stream().filter(BulkSmsDispatchResult.Recipient::isSuccess).count();
            log.info("[SMS] AT bulk accepted {}/{} recipient(s)", accepted, out.size());

            return BulkSmsDispatchResult.builder()
                    .success(anyAccepted)
                    .errorMessage(anyAccepted ? null : "No recipients accepted by Africa's Talking")
                    .recipients(out)
                    .build();

        } catch (Exception e) {
            log.error("[SMS] Failed to parse AT bulk response: {}", e.getMessage());
            return BulkSmsDispatchResult.failed("Response parse error: " + e.getMessage());
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


    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "<EMPTY>";
        if (key.length() <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
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
