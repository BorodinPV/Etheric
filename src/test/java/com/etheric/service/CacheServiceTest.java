package com.etheric.service;

import com.etheric.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CacheServiceTest {

    @Inject
    CacheService cacheService;

    @Test
    void authorizationCode_saveAndGet() {
        String code = "test-code-1";
        AuthorizationCodeData data = new AuthorizationCodeData("client1", "user1", "http://redirect", List.of("openid"));
        cacheService.saveAuthorizationCode(code, data, 600);

        AuthorizationCodeData retrieved = cacheService.getAuthorizationCode(code);
        assertNotNull(retrieved);
        assertEquals("client1", retrieved.getClientId());
        assertEquals("user1", retrieved.getUserId());
        assertEquals("http://redirect", retrieved.getRedirectUri());
        assertEquals(List.of("openid"), retrieved.getScopes());
    }

    @Test
    void authorizationCode_delete() {
        String code = "test-code-2";
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData("c", "u", "r", List.of()), 600);
        assertNotNull(cacheService.getAuthorizationCode(code));

        cacheService.deleteAuthorizationCode(code);
        assertNull(cacheService.getAuthorizationCode(code));
    }

    @Test
    void authorizationCode_notFound() {
        assertNull(cacheService.getAuthorizationCode("nonexistent"));
    }

    @Test
    void accessToken_saveAndGet() {
        String token = "access-token-1";
        AccessTokenData data = new AccessTokenData("user1", "client1", List.of("profile"), 1234567890L);
        cacheService.saveAccessToken(token, data, 3600);

        AccessTokenData retrieved = cacheService.getAccessToken(token);
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
        assertEquals("client1", retrieved.getClientId());
        assertEquals(List.of("profile"), retrieved.getScopes());
        assertEquals(1234567890L, retrieved.getExpiresAt());
    }

    @Test
    void accessToken_delete() {
        String token = "access-token-2";
        cacheService.saveAccessToken(token, new AccessTokenData("u", "c", List.of(), 0L), 3600);
        cacheService.deleteAccessToken(token);
        assertNull(cacheService.getAccessToken(token));
    }

    @Test
    void accessToken_notFound() {
        assertNull(cacheService.getAccessToken("nonexistent"));
    }

    @Test
    void refreshToken_saveAndGet() {
        String token = "refresh-token-1";
        RefreshTokenData data = new RefreshTokenData("user1", "client1", List.of("email"));
        cacheService.saveRefreshToken(token, data, 604800);

        RefreshTokenData retrieved = cacheService.getRefreshToken(token);
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
        assertEquals("client1", retrieved.getClientId());
        assertEquals(List.of("email"), retrieved.getScopes());
    }

    @Test
    void refreshToken_delete() {
        String token = "refresh-token-2";
        cacheService.saveRefreshToken(token, new RefreshTokenData("u", "c", List.of()), 604800);
        cacheService.deleteRefreshToken(token);
        assertNull(cacheService.getRefreshToken(token));
    }

    @Test
    void refreshToken_notFound() {
        assertNull(cacheService.getRefreshToken("nonexistent"));
    }

    @Test
    void session_saveAndGet() {
        String sessionId = "session-1";
        SessionData data = new SessionData("user1", "csrf-token", 1000L);
        cacheService.saveSession(sessionId, data, 1800);

        SessionData retrieved = cacheService.getSession(sessionId);
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
        assertEquals("csrf-token", retrieved.getCsrfToken());
        assertEquals(1000L, retrieved.getCreatedAt());
    }

    @Test
    void session_delete() {
        String sessionId = "session-2";
        cacheService.saveSession(sessionId, new SessionData("u", null, 0L), 1800);
        cacheService.deleteSession(sessionId);
        assertNull(cacheService.getSession(sessionId));
    }

    @Test
    void session_notFound() {
        assertNull(cacheService.getSession("nonexistent"));
    }

    @Test
    void csrfToken_save() {
        String sessionId = "session-csrf";
        cacheService.saveSession(sessionId, new SessionData("user1", null, 1000L), 1800);

        cacheService.saveCsrfToken(sessionId, "my-csrf-token");

        SessionData session = cacheService.getSession(sessionId);
        assertNotNull(session);
        assertEquals("my-csrf-token", session.getCsrfToken());
    }

    @Test
    void authorizationRequestState_saveAndGet() {
        String state = "state-1";
        AuthorizationRequestState data = new AuthorizationRequestState("client1", "http://redirect", List.of("openid"), "state1", null);
        cacheService.saveAuthorizationRequestState(state, data, 600);

        AuthorizationRequestState retrieved = cacheService.getAuthorizationRequestState(state);
        assertNotNull(retrieved);
        assertEquals("client1", retrieved.getClientId());
        assertEquals("http://redirect", retrieved.getRedirectUri());
        assertEquals(List.of("openid"), retrieved.getScope());
        assertEquals("state1", retrieved.getState());
        assertNull(retrieved.getUserId());
    }

    @Test
    void authorizationRequestState_delete() {
        String state = "state-2";
        cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState("c", "r", List.of(), "s", null), 600);
        cacheService.deleteAuthorizationRequestState(state);
        assertNull(cacheService.getAuthorizationRequestState(state));
    }

    @Test
    void authorizationRequestState_notFound() {
        assertNull(cacheService.getAuthorizationRequestState("nonexistent"));
    }

    @Test
    void authorizationRequestState_withUserId() {
        String state = "state-3";
        AuthorizationRequestState data = new AuthorizationRequestState("client1", "http://redirect", List.of("openid"), "state3", null);
        cacheService.saveAuthorizationRequestState(state, data, 600);

        AuthorizationRequestState retrieved = cacheService.getAuthorizationRequestState(state);
        retrieved.setUserId("user1");
        cacheService.saveAuthorizationRequestState(state, retrieved, 600);

        AuthorizationRequestState updated = cacheService.getAuthorizationRequestState(state);
        assertEquals("user1", updated.getUserId());
    }

    @Test
    void exists_true() {
        String key = "exists-test";
        cacheService.saveAuthorizationCode(key, new AuthorizationCodeData("c", "u", "r", List.of()), 600);
        assertTrue(cacheService.exists("auth:code:" + key));
    }

    @Test
    void exists_false() {
        assertFalse(cacheService.exists("auth:code:nonexistent"));
    }
}
