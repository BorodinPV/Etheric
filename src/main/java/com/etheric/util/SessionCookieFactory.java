package com.etheric.util;

import com.etheric.model.ClientOAuthPolicy;
import com.etheric.service.TokenPolicyService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.List;

/**
 * Builds and parses session cookies for browser-based OAuth flows.
 */
@ApplicationScoped
public class SessionCookieFactory {

    @Inject
    TokenPolicyService tokenPolicyService;

    public String create(String sessionId) {
        return create(sessionId, tokenPolicyService.defaultOAuthPolicy());
    }

    public String create(String sessionId, ClientOAuthPolicy policy) {
        return build(sessionId, null, policy);
    }

    public String clear() {
        return clear(tokenPolicyService.defaultOAuthPolicy());
    }

    public String clear(ClientOAuthPolicy policy) {
        return build("", 0, policy);
    }

    public Uni<List<String>> clearAllKnown() {
        return tokenPolicyService.knownOAuthPolicies()
                .map(policies -> policies.stream().map(this::clear).toList());
    }

    /** @deprecated Prefer {@link #cookieName(ClientOAuthPolicy)} */
    public String cookieName() {
        return tokenPolicyService.defaultOAuthPolicy().getSessionCookieName();
    }

    public String cookieName(ClientOAuthPolicy policy) {
        return policy.getSessionCookieName();
    }

    public String extractSessionId(HttpHeaders headers) {
        return extractSessionId(headers, tokenPolicyService.defaultOAuthPolicy());
    }

    public String extractSessionId(HttpHeaders headers, ClientOAuthPolicy policy) {
        return extractSessionIdFromCookie(headers.getHeaderString("Cookie"), policy);
    }

    public Uni<String> extractSessionIdAny(HttpHeaders headers) {
        return tokenPolicyService.knownOAuthPolicies().map(policies -> {
            String cookieHeader = headers.getHeaderString("Cookie");
            for (ClientOAuthPolicy policy : policies) {
                String sessionId = extractSessionIdFromCookie(cookieHeader, policy);
                if (sessionId != null) {
                    return sessionId;
                }
            }
            return null;
        });
    }

    public String extractSessionIdFromCookie(String cookieHeader) {
        return extractSessionIdFromCookie(cookieHeader, tokenPolicyService.defaultOAuthPolicy());
    }

    public String extractSessionIdFromCookie(String cookieHeader, ClientOAuthPolicy policy) {
        String name = policy.getSessionCookieName();
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

    private String build(String value, Integer maxAge, ClientOAuthPolicy policy) {
        String name = policy.getSessionCookieName();
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append('=').append(value);
        cookie.append("; Path=/; HttpOnly; SameSite=Lax");
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge);
        }
        if (policy.isSessionCookieSecure()) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }
}
