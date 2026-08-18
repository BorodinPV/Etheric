package com.etheric.util;

import com.etheric.service.TokenPolicyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Builds and parses session cookies for browser-based OAuth flows.
 */
@ApplicationScoped
public class SessionCookieFactory {

    @Inject
    TokenPolicyService tokenPolicyService;

    public String create(String sessionId) {
        return build(sessionId, null);
    }

    public String clear() {
        return build("", 0);
    }

    public String cookieName() {
        return tokenPolicyService.oauthSessionCookieName();
    }

    /**
     * Reads the session id from the Cookie header, if present.
     */
    public String extractSessionId(HttpHeaders headers) {
        return extractSessionIdFromCookie(headers.getHeaderString("Cookie"));
    }

    public String extractSessionIdFromCookie(String cookieHeader) {
        String name = tokenPolicyService.oauthSessionCookieName();
        if (cookieHeader == null || !cookieHeader.contains(name + "=")) {
            return null;
        }
        String[] parts = cookieHeader.split(name + "=");
        if (parts.length <= 1) {
            return null;
        }
        String value = parts[1].split(";")[0].trim();
        return value.isEmpty() ? null : value;
    }

    private String build(String value, Integer maxAge) {
        String name = tokenPolicyService.oauthSessionCookieName();
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append('=').append(value);
        cookie.append("; Path=/; HttpOnly; SameSite=Lax");
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge);
        }
        if (tokenPolicyService.sessionCookieSecure()) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }
}
