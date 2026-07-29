package com.etheric.util;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
