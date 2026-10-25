package com.EduePoa.EP.Authentication.Config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Verifies Google Sign-In ID tokens for the Google SSO flow.
 * <p>
 * Validation performed by {@link GoogleIdTokenVerifier}: the token's signature
 * against Google's rotating public certificates, the issuer (accounts.google.com),
 * the audience (our OAuth client id) and the expiry. A token that passes all
 * checks yields a verified, Google-asserted email which the login flow trusts.
 * <p>
 * The configured client id comes from {@code google.client-id} (env var
 * {@code GOOGLE_CLIENT_ID}). When it is blank, Google login is disabled and
 * verification returns {@code null}, so the endpoint reports a clear error
 * instead of accepting unverifiable tokens.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    @Value("${google.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.warn("google.client-id is not configured; Google SSO is disabled until GOOGLE_CLIENT_ID is set");
            return;
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    /** True when a client id is configured and Google login can be used. */
    public boolean isEnabled() {
        return verifier != null;
    }

    /**
     * Verifies a Google ID token and returns its payload, or {@code null} when the
     * token is invalid/expired, has the wrong audience, or Google login is disabled.
     * The payload exposes the Google-verified {@code email} and {@code email_verified}.
     */
    public Payload verify(String idTokenString) {
        if (!isEnabled()) {
            log.warn("Google SSO is disabled (no client id); rejecting Google token verification");
            return null;
        }
        if (idTokenString == null || idTokenString.isBlank()) {
            return null;
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token failed verification (bad signature/audience/expiry)");
                return null;
            }
            return idToken.getPayload();
        } catch (Exception e) {
            log.error("Error verifying Google ID token: {}", e.getMessage());
            return null;
        }
    }
}
