package com.etheric.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthErrorTest {

    @Test
    void allEnumValuesExist() {
        assertEquals(10, OAuthError.values().length);
    }

    @Test
    void invalidRequest_hasCorrectValues() {
        assertEquals("invalid_request", OAuthError.INVALID_REQUEST.getError());
        assertEquals("The request is missing a required parameter or is otherwise malformed.", OAuthError.INVALID_REQUEST.getErrorDescription());
    }

    @Test
    void unauthorizedClient_hasCorrectValues() {
        assertEquals("unauthorized_client", OAuthError.UNAUTHORIZED_CLIENT.getError());
    }

    @Test
    void accessDenied_hasCorrectValues() {
        assertEquals("access_denied", OAuthError.ACCESS_DENIED.getError());
    }

    @Test
    void unsupportedResponseType_hasCorrectValues() {
        assertEquals("unsupported_response_type", OAuthError.UNSUPPORTED_RESPONSE_TYPE.getError());
    }

    @Test
    void invalidScope_hasCorrectValues() {
        assertEquals("invalid_scope", OAuthError.INVALID_SCOPE.getError());
    }

    @Test
    void serverError_hasCorrectValues() {
        assertEquals("server_error", OAuthError.SERVER_ERROR.getError());
    }

    @Test
    void temporarilyUnavailable_hasCorrectValues() {
        assertEquals("temporarily_unavailable", OAuthError.TEMPORARILY_UNAVAILABLE.getError());
    }

    @Test
    void invalidGrant_hasCorrectValues() {
        assertEquals("invalid_grant", OAuthError.INVALID_GRANT.getError());
    }

    @Test
    void unsupportedGrantType_hasCorrectValues() {
        assertEquals("unsupported_grant_type", OAuthError.UNSUPPORTED_GRANT_TYPE.getError());
    }

    @Test
    void invalidClient_hasCorrectValues() {
        assertEquals("invalid_client", OAuthError.INVALID_CLIENT.getError());
    }

    @Test
    void getError_returnsStringValue() {
        for (OAuthError error : OAuthError.values()) {
            assertNotNull(error.getError());
            assertFalse(error.getError().isEmpty());
        }
    }

    @Test
    void getErrorDescription_returnsStringValue() {
        for (OAuthError error : OAuthError.values()) {
            assertNotNull(error.getErrorDescription());
            assertFalse(error.getErrorDescription().isEmpty());
        }
    }
}
