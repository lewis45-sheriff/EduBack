package com.EduePoa.EP.Authentication.Config;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

@Slf4j
@Service
public class CookieService {

    private static final int REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds
    private static final String ACCESS_TOKEN_NAME = "accessToken";
    private static final String REFRESH_TOKEN_NAME = "refreshToken";

    @Value("${cookie.access-token.max-age:1200}") // 20 minutes default
    private int accessTokenMaxAge;

    @Value("${cookie.refresh-token.max-age:2592000}") // 30 days default
    private int refreshTokenMaxAge;

    @Value("${cookie.domain:}")
    private String cookieDomain;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:Strict}")
    private String sameSitePolicy;

    @Value("${cookie.path:/}")
    private String cookiePath;


    public void createAccessTokenCookie(HttpServletResponse response, String token) {
        if (token == null || token.isBlank()) {
            log.warn("Attempted to create access token cookie with null or empty token");
            return;
        }
        createCookie(response, ACCESS_TOKEN_NAME, token, accessTokenMaxAge, sameSitePolicy);
        log.debug("Access token cookie created");
    }


    public void createRefreshTokenCookie(HttpServletResponse response, String token) {
        if (token == null || token.isBlank()) {
            log.warn("Attempted to create refresh token cookie with null or empty token");
            return;
        }
        createCookie(response, REFRESH_TOKEN_NAME, token, refreshTokenMaxAge, sameSitePolicy);
        log.debug("Refresh token cookie created");
    }


    public void createCookie(HttpServletResponse response, String name, String value,
                             int maxAge, String sameSite) {
        if (name == null || name.isBlank()) {
            log.error("Cannot create cookie with null or empty name");
            throw new IllegalArgumentException("Cookie name cannot be null or empty");
        }

        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true); // Prevent JavaScript access (XSS protection)
        cookie.setPath(cookiePath);
        cookie.setMaxAge(maxAge);

        // Set secure flag - should be true in production
        cookie.setSecure(cookieSecure);

        // Set domain if specified
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }

        // Set SameSite attribute
        if (sameSite != null && !sameSite.isBlank()) {
            cookie.setAttribute("SameSite", sameSite);

            // If SameSite=None, Secure must be true
            if ("None".equalsIgnoreCase(sameSite) && !cookieSecure) {
                log.warn("SameSite=None requires Secure flag. Setting Secure=true");
                cookie.setSecure(true);
            }
        }

        response.addCookie(cookie);
        log.trace("Cookie '{}' created with maxAge={}, secure={}, sameSite={}",
                name, maxAge, cookie.getSecure(), sameSite);
    }


    public void createCookie(HttpServletResponse response, String name, String value, int maxAge) {
        createCookie(response, name, value, maxAge, sameSitePolicy);
    }


    public String getCookieValue(HttpServletRequest request, String name) {
        if (request == null || name == null || name.isBlank()) {
            log.warn("Invalid request or cookie name");
            return null;
        }

        Cookie cookie = WebUtils.getCookie(request, name);
        if (cookie != null) {
            log.trace("Cookie '{}' retrieved", name);
            return cookie.getValue();
        }

        log.trace("Cookie '{}' not found in request", name);
        return null;
    }


    public String getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_NAME);
    }


    public String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_NAME);
    }

    public void deleteCookie(HttpServletResponse response, String name) {
        if (name == null || name.isBlank()) {
            log.warn("Cannot delete cookie with null or empty name");
            return;
        }

        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath(cookiePath);
        cookie.setMaxAge(0); // Expire immediately

        // Match security settings from creation
        cookie.setSecure(cookieSecure);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }

        // SameSite must match creation for proper deletion
        cookie.setAttribute("SameSite", sameSitePolicy);

        // Ensure Secure flag if SameSite=None
        if ("None".equalsIgnoreCase(sameSitePolicy)) {
            cookie.setSecure(true);
        }

        response.addCookie(cookie);
        log.debug("Cookie '{}' deleted", name);
    }


    public void deleteAuthCookies(HttpServletResponse response) {
        deleteCookie(response, ACCESS_TOKEN_NAME);
        deleteCookie(response, REFRESH_TOKEN_NAME);
        log.info("All authentication cookies deleted");
    }


    public boolean hasCookie(HttpServletRequest request, String name) {
        if (request == null || name == null || name.isBlank()) {
            return false;
        }
        return WebUtils.getCookie(request, name) != null;
    }


    public boolean hasAccessToken(HttpServletRequest request) {
        return hasCookie(request, ACCESS_TOKEN_NAME);
    }


    public boolean hasRefreshToken(HttpServletRequest request) {
        return hasCookie(request, REFRESH_TOKEN_NAME);
    }
}