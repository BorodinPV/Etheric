package com.etheric.util;

import java.util.regex.Pattern;

/**
 * Redacts sensitive OAuth/auth values from log messages and cache key names.
 */
public final class SecretMasker {

    private static final String MASK = "***";

    private static final Pattern FORM_OR_JSON_SECRET = Pattern.compile(
            "(?i)(password|client_secret|refresh_token|access_token|authorization_code|csrf_token)"
                    + "([=\"\\s:]*)([^&\\s\",;}]+)");

    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)(Bearer\\s+)([A-Z0-9._~+/=-]+=*)");

    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    private static final Pattern CACHE_SECRET_KEY = Pattern.compile(
            "(auth:(?:code|token:access|token:refresh|session):)([^\\s,]+)");

    private SecretMasker() {
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String masked = FORM_OR_JSON_SECRET.matcher(input).replaceAll("$1$2" + MASK);
        masked = BEARER_TOKEN.matcher(masked).replaceAll("$1" + MASK);
        masked = JWT.matcher(masked).replaceAll(MASK);
        masked = CACHE_SECRET_KEY.matcher(masked).replaceAll("$1" + MASK);
        return masked;
    }

    public static String maskCacheKey(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith("auth:code:")) {
            return "auth:code:" + MASK;
        }
        if (key.startsWith("auth:token:access:")) {
            return "auth:token:access:" + MASK;
        }
        if (key.startsWith("auth:token:refresh:")) {
            return "auth:token:refresh:" + MASK;
        }
        if (key.startsWith("auth:session:")) {
            return "auth:session:" + MASK;
        }
        return key;
    }
}
