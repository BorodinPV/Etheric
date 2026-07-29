package com.etheric.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretMaskerTest {

    @Test
    void masksPasswordInFormBody() {
        String masked = SecretMasker.mask("username=user&password=secret123&state=abc");
        assertTrue(masked.contains("password=***"));
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("username=user"));
    }

    @Test
    void masksClientSecretAndTokens() {
        String masked = SecretMasker.mask(
                "client_secret=topsecret&refresh_token=rt-value&access_token=at-value");
        assertTrue(masked.contains("client_secret=***"));
        assertTrue(masked.contains("refresh_token=***"));
        assertTrue(masked.contains("access_token=***"));
        assertFalse(masked.contains("topsecret"));
        assertFalse(masked.contains("rt-value"));
        assertFalse(masked.contains("at-value"));
    }

    @Test
    void masksBearerAndJwt() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        String masked = SecretMasker.mask("Authorization: Bearer " + jwt);
        assertTrue(masked.contains("Bearer ***"));
        assertFalse(masked.contains(jwt));
    }

    @Test
    void masksCacheKeys() {
        assertEquals("auth:code:***", SecretMasker.maskCacheKey("auth:code:abc-123"));
        assertEquals("auth:token:access:***", SecretMasker.maskCacheKey("auth:token:access:eyJ.abc.def"));
        assertEquals("auth:token:refresh:***", SecretMasker.maskCacheKey("auth:token:refresh:xyz"));
        assertEquals("auth:session:***", SecretMasker.maskCacheKey("auth:session:sid-1"));
        assertEquals("auth:request:state-1", SecretMasker.maskCacheKey("auth:request:state-1"));
    }

    @Test
    void maskNullAndEmpty() {
        assertNull(SecretMasker.mask(null));
        assertEquals("", SecretMasker.mask(""));
    }
}
