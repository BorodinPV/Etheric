package com.etheric.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

/**
 * PKCE helpers (RFC 7636).
 */
public final class PkceUtil {

    public static final String METHOD_S256 = "S256";
    private static final Set<String> SUPPORTED_METHODS = Set.of(METHOD_S256);

    private PkceUtil() {
    }

    public static boolean isSupportedMethod(String method) {
        return method != null && SUPPORTED_METHODS.contains(method);
    }

    public static String s256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * @return true if PKCE is satisfied (including when no challenge was stored)
     */
    public static boolean verify(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return true;
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            return false;
        }
        String method = (codeChallengeMethod == null || codeChallengeMethod.isBlank())
                ? METHOD_S256
                : codeChallengeMethod;
        if (!METHOD_S256.equals(method)) {
            return false;
        }
        return codeChallenge.equals(s256Challenge(codeVerifier));
    }
}
