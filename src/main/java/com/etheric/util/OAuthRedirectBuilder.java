package com.etheric.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds OAuth redirect URIs with encoded query parameters.
 */
public final class OAuthRedirectBuilder {

    private OAuthRedirectBuilder() {
    }

    public static URI authorizationSuccess(String redirectUri, String code, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("state", state);
        return build(redirectUri, params);
    }

    public static URI accessDenied(String redirectUri, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("error", "access_denied");
        params.put("state", state);
        return build(redirectUri, params);
    }

    public static URI oauthError(String redirectUri, String error, String errorDescription, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("error", error);
        if (errorDescription != null) {
            params.put("error_description", errorDescription);
        }
        if (state != null) {
            params.put("state", state);
        }
        return build(redirectUri, params);
    }

    public static URI build(String baseUri, Map<String, String> queryParams) {
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (queryParams == null || queryParams.isEmpty()) {
            return URI.create(uriBuilder.toString());
        }

        uriBuilder.append(baseUri.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                uriBuilder.append("&");
            }
            uriBuilder.append(encode(entry.getKey()))
                    .append("=")
                    .append(encode(entry.getValue()));
            first = false;
        }
        return URI.create(uriBuilder.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
