package com.EduePoa.EP.Communications.WhatsApp;

import com.EduePoa.EP.Communications.SMS.SmsDispatchResult;
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

/**
 * WhatsApp gateway backed by the Meta (Facebook) WhatsApp Cloud API.
 * <p>
 * Mirrors {@code AfricasTalkingSmsService}: outbound calls use OkHttp and
 * per-tenant credentials are resolved via {@link TenantConfigurationService}
 * (keys {@code whatsapp.access_token}, {@code whatsapp.phone_number_id},
 * {@code whatsapp.api_url}), falling back to application-level {@link WhatsAppConfig}
 * when no tenant value is set or the tenant context is absent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppService implements WhatsAppGatewayService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final WhatsAppConfig whatsAppConfig;
    private final TenantConfigurationService tenantConfigurationService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public SmsDispatchResult sendWhatsApp(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return SmsDispatchResult.failed("Phone number is blank — WhatsApp not sent");
        }
        if (message == null || message.isBlank()) {
            return SmsDispatchResult.failed("Message content is blank — WhatsApp not sent");
        }

        String accessToken = resolveAccessToken();
        String phoneNumberId = resolvePhoneNumberId();
        String apiUrl = resolveApiUrl();

        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            return SmsDispatchResult.failed("WhatsApp is not configured for this tenant");
        }

        String to = normalisePhone(phoneNumber);
        log.info("[WhatsApp] Dispatching to {} via Meta Cloud API", to);

        // Meta Cloud API "text" message payload.
        JsonObject body = new JsonObject();
        body.addProperty("messaging_product", "whatsapp");
        body.addProperty("recipient_type", "individual");
        body.addProperty("to", to);
        body.addProperty("type", "text");
        JsonObject text = new JsonObject();
        text.addProperty("preview_url", false);
        text.addProperty("body", message);
        body.add("text", text);

        String url = trimTrailingSlash(apiUrl) + "/" + phoneNumberId + "/messages";

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.debug("[WhatsApp] Meta response [{}]: {}", response.code(), responseBody);

                if (!response.isSuccessful()) {
                    log.error("[WhatsApp] Meta HTTP error {}: {}", response.code(), responseBody);
                    return SmsDispatchResult.failed("HTTP " + response.code() + ": " + parseError(responseBody));
                }

                return parseMetaResponse(responseBody);
            }

        } catch (IOException e) {
            log.error("[WhatsApp] Network error contacting Meta Cloud API: {}", e.getMessage(), e);
            return SmsDispatchResult.failed("Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[WhatsApp] Unexpected error dispatching WhatsApp: {}", e.getMessage(), e);
            return SmsDispatchResult.failed("Unexpected error: " + e.getMessage());
        }
    }

    private String resolveAccessToken() {
        String tenantValue = tenantConfig("whatsapp.access_token");
        return tenantValue != null ? tenantValue : whatsAppConfig.getAccessToken();
    }

    private String resolvePhoneNumberId() {
        String tenantValue = tenantConfig("whatsapp.phone_number_id");
        return tenantValue != null ? tenantValue : whatsAppConfig.getPhoneNumberId();
    }

    private String resolveApiUrl() {
        String tenantValue = tenantConfig("whatsapp.api_url");
        return tenantValue != null ? tenantValue : whatsAppConfig.getApiUrl();
    }

    /**
     * Reads a per-tenant config value when the tenant context is set, returning
     * {@code null} when unset or when there is no tenant-specific override.
     */
    private String tenantConfig(String key) {
        if (TenantContext.isSet()) {
            String tenantId = TenantContext.getCurrentTenant();
            String value = tenantConfigurationService.getConfigOrDefault(tenantId, key, null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalisePhone(String phone) {
        // Meta expects the number in international format WITHOUT a leading '+'.
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("07") || cleaned.startsWith("01")) {
            return "254" + cleaned.substring(1);
        }
        return cleaned;
    }

    private String trimTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private SmsDispatchResult parseMetaResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray messages = root.has("messages") ? root.getAsJsonArray("messages") : null;
            if (messages != null && !messages.isEmpty()) {
                JsonObject first = messages.get(0).getAsJsonObject();
                String messageId = first.has("id") ? first.get("id").getAsString() : null;
                log.info("[WhatsApp] Accepted by Meta — id: {}", messageId);
                return SmsDispatchResult.ok(messageId, null);
            }
            return SmsDispatchResult.failed("No message id in Meta response: " + json);
        } catch (Exception e) {
            log.error("[WhatsApp] Failed to parse Meta response: {}", e.getMessage());
            return SmsDispatchResult.failed("Response parse error: " + e.getMessage());
        }
    }

    private String parseError(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return json;
    }
}
