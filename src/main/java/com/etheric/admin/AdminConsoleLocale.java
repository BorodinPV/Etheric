package com.etheric.admin;

import java.util.Locale;

/**
 * Supported admin console locales.
 */
public enum AdminConsoleLocale {

    EN("en", Locale.ENGLISH),
    RU("ru", Locale.forLanguageTag("ru"));

    private final String code;
    private final Locale locale;

    AdminConsoleLocale(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public Locale toLocale() {
        return locale;
    }

    public static AdminConsoleLocale parse(String value) {
        if (value == null || value.isBlank()) {
            return EN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ru", "ru-ru" -> RU;
            default -> EN;
        };
    }
}
