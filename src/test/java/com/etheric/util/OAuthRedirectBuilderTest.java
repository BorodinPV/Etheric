package com.etheric.util;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class OAuthRedirectBuilderTest {

    @Test
    void authorizationSuccess_encodesQueryParameters() {
        URI uri = OAuthRedirectBuilder.authorizationSuccess(
                "http://localhost:8080/callback",
                "code+with/special=chars",
                "state value&more"
        );

        assertEquals(
                "http://localhost:8080/callback?code=code%2Bwith%2Fspecial%3Dchars&state=state+value%26more",
                uri.toString()
        );
    }

    @Test
    void accessDenied_encodesErrorAndState() {
        URI uri = OAuthRedirectBuilder.accessDenied(
                "http://localhost:8080/callback?existing=1",
                "deny/state"
        );

        assertEquals(
                "http://localhost:8080/callback?existing=1&error=access_denied&state=deny%2Fstate",
                uri.toString()
        );
    }

    @Test
    void oauthError_encodesDescriptionAndState() {
        URI uri = OAuthRedirectBuilder.oauthError(
                "http://localhost:8080/callback",
                "invalid_request",
                "bad redirect uri",
                "xyz"
        );

        assertTrue(uri.toString().contains("error=invalid_request"));
        assertTrue(uri.toString().contains("error_description=bad+redirect+uri"));
        assertTrue(uri.toString().contains("state=xyz"));
    }
}
