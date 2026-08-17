package com.etheric.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds and parses admin console session cookies.
 */
@ApplicationScoped
public class AdminSessionCookieFactory {

    public static final String COOKIE_NAME = "ADMIN_SESSION";

    @ConfigProperty(name = "etheric.session.cookie.secure", defaultValue = "true")
    boolean secure;

    public String create(String sessionId) {
        return build(sessionId, null);
    }

    public String clear() {
        return build("", 0);
    }

    public static String extractSessionId(HttpHeaders headers) {
        return extractSessionIdFromCookie(headers.getHeaderString("Cookie"));
    }

    public static String extractSessionIdFromCookie(String cookie) {
        if (cookie == null || !cookie.contains(COOKIE_NAME + "=")) {
            return null;
        }
        String[] parts = cookie.split(COOKIE_NAME + "=");
        if (parts.length <= 1) {
            return null;
        }
        String value = parts[1].split(";")[0].trim();
        return value.isEmpty() ? null : value;
    }

    private String build(String value, Integer maxAge) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(COOKIE_NAME).append('=').append(value);
        cookie.append("; Path=/admin; HttpOnly; SameSite=Lax");
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge);
        }
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }
}
