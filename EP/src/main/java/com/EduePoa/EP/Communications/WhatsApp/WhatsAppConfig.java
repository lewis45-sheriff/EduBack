package com.EduePoa.EP.Communications.WhatsApp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Application-level fallback configuration for the WhatsApp (Meta Cloud API)
 * integration. Per-tenant values stored in tenant configuration take precedence;
 * these are used when no tenant-specific value is available.
 * <p>
 * Bind from application.properties under the {@code whatsapp.*} prefix, e.g.
 * <pre>
 * whatsapp.api-url=https://graph.facebook.com/v20.0
 * whatsapp.phone-number-id=...
 * whatsapp.access-token=...
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "whatsapp")
@Getter
@Setter
public class WhatsAppConfig {

    /** Base Graph API URL, e.g. https://graph.facebook.com/v20.0 */
    private String apiUrl = "https://graph.facebook.com/v20.0";

    /** The WhatsApp Business phone number id used as the sender. */
    private String phoneNumberId;

    /** Permanent/system-user access token for the Cloud API. */
    private String accessToken;
}
