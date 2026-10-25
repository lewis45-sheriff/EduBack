package com.EduePoa.EP.Authentication.Auth.Request;

import lombok.Data;

/**
 * Payload for "Sign in with Google" (SSO, Option A). The frontend obtains a
 * Google ID token via Google Identity Services and posts it here; the backend
 * verifies it and issues the app's own JWT.
 */
@Data
public class GoogleLoginRequest {

    /** The Google ID token (a signed JWT) returned by Google Identity Services. */
    private String idToken;

    /**
     * Optional tenant identifier. Because a user's email is unique only per
     * tenant, a user whose Google email exists in more than one school must
     * supply this hint so the correct account is resolved. For the common case
     * (email in a single tenant) it may be omitted.
     */
    private String tenantId;
}
