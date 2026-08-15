package com.etheric.util;

import java.util.ArrayList;
import java.util.List;

public final class ScopeUtil {

    private ScopeUtil() {
    }

    /**
     * Resolves granted scopes: uses requested subset when provided, otherwise falls back to stored scopes.
     *
     * @throws com.etheric.exception.OAuthException with invalid_scope when requested is not a subset
     */
    public static List<String> resolveGrantedScopes(List<String> requested, List<String> stored) {
        List<String> base = stored != null ? stored : List.of();
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(base);
        }
        if (!base.containsAll(requested)) {
            throw new com.etheric.exception.OAuthException(
                    com.etheric.exception.OAuthError.INVALID_SCOPE, null, null);
        }
        return new ArrayList<>(requested);
    }

    public static boolean coversScopes(List<String> granted, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        if (granted == null || granted.isEmpty()) {
            return false;
        }
        return granted.containsAll(requested);
    }
}
