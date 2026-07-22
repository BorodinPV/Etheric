package com.etheric.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthExceptionTest {

    @Test
    void constructor_withRedirectUriAndState() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "http://redirect", "state123");
        assertEquals(OAuthError.INVALID_REQUEST, ex.getError());
        assertEquals("http://redirect", ex.getRedirectUri());
        assertEquals("state123", ex.getState());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void constructor_withoutRedirectUriAndState() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_GRANT, null, null);
        assertEquals(OAuthError.INVALID_GRANT, ex.getError());
        assertNull(ex.getRedirectUri());
        assertNull(ex.getState());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void constructor_withNullRedirectUri() {
        OAuthException ex = new OAuthException(OAuthError.UNSUPPORTED_GRANT_TYPE, null, "state");
        assertEquals(OAuthError.UNSUPPORTED_GRANT_TYPE, ex.getError());
        assertNull(ex.getRedirectUri());
        assertEquals("state", ex.getState());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void constructor_withEmptyRedirectUri() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "", "state123");
        assertFalse(ex.hasRedirectUri());
    }

    @Test
    void constructor_withCustomHttpStatus() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_CLIENT, 401);
        assertEquals(OAuthError.INVALID_CLIENT, ex.getError());
        assertEquals(401, ex.getHttpStatus());
        assertNull(ex.getRedirectUri());
        assertNull(ex.getState());
    }

    @Test
    void constructor_withDescriptionAndHttpStatus() {
        OAuthException ex = new OAuthException(OAuthError.SERVER_ERROR, "custom desc", 503);
        assertEquals(OAuthError.SERVER_ERROR, ex.getError());
        assertEquals("custom desc", ex.getMessage());
        assertEquals(503, ex.getHttpStatus());
    }

    @Test
    void hasRedirectUri_true() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "http://redirect", "state");
        assertTrue(ex.hasRedirectUri());
    }

    @Test
    void hasRedirectUri_false_null() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        assertFalse(ex.hasRedirectUri());
    }

    @Test
    void hasRedirectUri_false_empty() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "", null);
        assertFalse(ex.hasRedirectUri());
    }

    @Test
    void isRuntimeException() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void getMessage_returnsDescription() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, null, null);
        assertEquals(OAuthError.INVALID_REQUEST.getErrorDescription(), ex.getMessage());
    }
}
