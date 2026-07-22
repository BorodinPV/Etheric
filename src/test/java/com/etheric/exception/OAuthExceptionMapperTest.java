package com.etheric.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthExceptionMapperTest {

    private final OAuthExceptionMapper mapper = new OAuthExceptionMapper();

    @Test
    void toResponse_withRedirectUri_returnsRedirect() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "http://example.com/callback", "state123");
        Response response = mapper.toResponse(ex);

        assertEquals(302, response.getStatus());
        assertNotNull(response.getLocation());
        String location = response.getLocation().toString();
        assertTrue(location.startsWith("http://example.com/callback"));
        assertTrue(location.contains("error=invalid_request"));
        assertTrue(location.contains("state=state123"));
        assertTrue(location.contains("error_description="));
    }

    @Test
    void toResponse_withoutRedirectUri_returnsJson() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_GRANT, null, null);
        Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    void toResponse_unauthorizedClient_withRedirect() {
        OAuthException ex = new OAuthException(OAuthError.UNAUTHORIZED_CLIENT, "http://example.com/cb", "st");
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=unauthorized_client"));
    }

    @Test
    void toResponse_accessDenied_withRedirect() {
        OAuthException ex = new OAuthException(OAuthError.ACCESS_DENIED, "http://example.com/cb", "st");
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=access_denied"));
    }

    @Test
    void toResponse_unsupportedResponseType_withRedirect() {
        OAuthException ex = new OAuthException(OAuthError.UNSUPPORTED_RESPONSE_TYPE, "http://example.com/cb", "st");
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=unsupported_response_type"));
    }

    @Test
    void toResponse_invalidScope_withRedirect() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_SCOPE, "http://example.com/cb", "st");
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=invalid_scope"));
    }

    @Test
    void toResponse_unsupportedGrantType_withoutRedirect() {
        OAuthException ex = new OAuthException(OAuthError.UNSUPPORTED_GRANT_TYPE, null, null);
        Response response = mapper.toResponse(ex);
        assertEquals(400, response.getStatus());
    }

    @Test
    void toResponse_invalidClient_withCustomStatus() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_CLIENT, 401);
        Response response = mapper.toResponse(ex);
        assertEquals(401, response.getStatus());
    }

    @Test
    void toResponse_serverError_withCustomStatus() {
        OAuthException ex = new OAuthException(OAuthError.SERVER_ERROR, 500);
        Response response = mapper.toResponse(ex);
        assertEquals(500, response.getStatus());
    }

    @Test
    void toResponse_redirect_containsErrorDescription() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_SCOPE, "http://example.com/cb", "s");
        Response response = mapper.toResponse(ex);
        String location = response.getLocation().toString();
        assertTrue(location.contains("error_description="));
    }

    @Test
    void toResponse_redirect_noState() {
        OAuthException ex = new OAuthException(OAuthError.ACCESS_DENIED, "http://example.com/cb", null);
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        assertFalse(response.getLocation().toString().contains("state="));
    }

    @Test
    void toResponse_redirect_existingQueryParams() {
        OAuthException ex = new OAuthException(OAuthError.INVALID_REQUEST, "http://example.com/cb?existing=param", "st");
        Response response = mapper.toResponse(ex);
        assertEquals(302, response.getStatus());
        String location = response.getLocation().toString();
        assertTrue(location.contains("existing=param"));
        assertTrue(location.contains("error=invalid_request"));
    }
}
