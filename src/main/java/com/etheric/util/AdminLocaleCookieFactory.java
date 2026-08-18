package com.etheric.util;

import com.etheric.admin.AdminConsoleLocale;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Persists admin console language preference in a cookie.
 */
@ApplicationScoped
public class AdminLocaleCookieFactory {

    public static final String COOKIE_NAME = "ADMIN_LOCALE";
    private static final int MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    @ConfigProperty(name = "etheric.session.cookie.secure", defaultValue = "true")
    boolean secure;

    public String create(AdminConsoleLocale locale) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(COOKIE_NAME).append('=').append(locale.code());
        cookie.append("; Path=/admin; Max-Age=").append(MAX_AGE_SECONDS).append("; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    public static String extractLocale(HttpHeaders headers) {
        return extractLocaleFromCookie(headers.getHeaderString("Cookie"));
    }

    public static String extractLocaleFromCookie(String cookieHeader) {
        if (cookieHeader == null || !cookieHeader.contains(COOKIE_NAME + "=")) {
            return null;
        }
        String[] parts = cookieHeader.split(COOKIE_NAME + "=");
        if (parts.length <= 1) {
            return null;
        }
        String value = parts[1].split(";")[0].trim();
        return value.isEmpty() ? null : value;
    }
}
