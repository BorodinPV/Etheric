package com.etheric.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds and parses session cookies for browser-based OAuth flows.
 */
@ApplicationScoped
public class SessionCookieFactory {

    public static final String COOKIE_NAME = "SESSIONID";

    @ConfigProperty(name = "etheric.session.cookie.secure", defaultValue = "true")
    boolean secure;

    public String create(String sessionId) {
        return build(sessionId, null);
    }

    public String clear() {
        return build("", 0);
    }

    /**
     * Reads the session id from the Cookie header, if present.
     *
     * @param headers incoming HTTP headers
     * @return session id or {@code null} when the cookie is absent
     */
    public static String extractSessionId(HttpHeaders headers) {
        String cookie = headers.getHeaderString("Cookie");
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
        cookie.append("; Path=/; HttpOnly; SameSite=Lax");
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge);
        }
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }
}
