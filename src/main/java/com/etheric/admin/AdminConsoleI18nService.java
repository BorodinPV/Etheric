package com.etheric.admin;

import com.etheric.util.AdminLocaleCookieFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.List;
import java.util.ResourceBundle;

@ApplicationScoped
public class AdminConsoleI18nService {

    private static final String BUNDLE_BASE = "i18n.admin-console";

    public AdminConsoleI18n resolve(HttpHeaders headers) {
        return resolveLocale(extractCookie(headers.getHeaderString("Cookie")));
    }

    public AdminConsoleI18n resolve(MultivaluedMap<String, String> headers) {
        return resolveLocale(extractCookie(headerValue(headers, "Cookie")));
    }

    private AdminConsoleI18n resolveLocale(String cookieValue) {
        AdminConsoleLocale locale = AdminConsoleLocale.EN;
        String fromCookie = AdminLocaleCookieFactory.extractLocaleFromCookie(cookieValue);
        if (fromCookie != null) {
            locale = AdminConsoleLocale.parse(fromCookie);
        }
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale.toLocale());
        return new AdminConsoleI18n(locale, bundle);
    }

    private static String extractCookie(String cookieHeader) {
        return cookieHeader;
    }

    private static String headerValue(MultivaluedMap<String, String> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
