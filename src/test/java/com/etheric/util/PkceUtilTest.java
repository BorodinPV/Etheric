package com.etheric.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PkceUtilTest {

    private static final String SAMPLE_VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void s256Challenge_isDeterministic() {
        assertEquals(PkceUtil.s256Challenge(SAMPLE_VERIFIER), PkceUtil.s256Challenge(SAMPLE_VERIFIER));
    }

    @Test
    void verify_noChallengeStored_succeedsWithoutVerifier() {
        assertTrue(PkceUtil.verify(null, null, null));
        assertTrue(PkceUtil.verify(null, "", null));
    }

    @Test
    void verify_s256_validVerifier() {
        String challenge = PkceUtil.s256Challenge(SAMPLE_VERIFIER);
        assertTrue(PkceUtil.verify(SAMPLE_VERIFIER, challenge, PkceUtil.METHOD_S256));
    }

    @Test
    void verify_s256_defaultsMethodWhenBlank() {
        String challenge = PkceUtil.s256Challenge(SAMPLE_VERIFIER);
        assertTrue(PkceUtil.verify(SAMPLE_VERIFIER, challenge, null));
    }

    @Test
    void verify_s256_missingVerifier() {
        String challenge = PkceUtil.s256Challenge(SAMPLE_VERIFIER);
        assertFalse(PkceUtil.verify(null, challenge, PkceUtil.METHOD_S256));
    }

    @Test
    void verify_s256_wrongVerifier() {
        String challenge = PkceUtil.s256Challenge(SAMPLE_VERIFIER);
        assertFalse(PkceUtil.verify("wrong-verifier", challenge, PkceUtil.METHOD_S256));
    }

    @Test
    void verify_plain_rejected() {
        String verifier = "plain-verifier-value";
        assertFalse(PkceUtil.verify(verifier, verifier, "plain"));
    }

    @Test
    void verify_unsupportedMethod() {
        String challenge = PkceUtil.s256Challenge(SAMPLE_VERIFIER);
        assertFalse(PkceUtil.verify(SAMPLE_VERIFIER, challenge, "unknown"));
    }

    @Test
    void isSupportedMethod() {
        assertTrue(PkceUtil.isSupportedMethod(PkceUtil.METHOD_S256));
        assertFalse(PkceUtil.isSupportedMethod("plain"));
        assertFalse(PkceUtil.isSupportedMethod("unknown"));
        assertFalse(PkceUtil.isSupportedMethod(null));
    }
}
