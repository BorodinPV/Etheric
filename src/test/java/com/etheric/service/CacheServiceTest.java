package com.etheric.service;

import com.etheric.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CacheServiceTest {

    @Inject
    CacheService cacheService;

    @Test
    void authorizationCode_saveAndRetrieve() {
        String code = "test-code-" + System.nanoTime();
        AuthorizationCodeData data = new AuthorizationCodeData("client1", "user1", "http://redirect", List.of("openid"), null, null, null);
        awaitVoid(cacheService.saveAuthorizationCode(code, data, 600));

        AuthorizationCodeData retrieved = await(cacheService.getAuthorizationCode(code));
        assertNotNull(retrieved);
        assertEquals("client1", retrieved.getClientId());
    }

    @Test
    void authorizationCode_delete() {
        String code = "delete-code-" + System.nanoTime();
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData("c", "u", "r", List.of(), null, null, null), 600));
        assertNotNull(await(cacheService.getAuthorizationCode(code)));
        awaitVoid(cacheService.deleteAuthorizationCode(code));
        assertNull(await(cacheService.getAuthorizationCode(code)));
    }

    @Test
    void accessToken_saveAndRetrieve() {
        String token = "access-" + System.nanoTime();
        AccessTokenData data = new AccessTokenData("user1", "client1", List.of("openid"), 9999999999L);
        awaitVoid(cacheService.saveAccessToken(token, data, 3600));
        AccessTokenData retrieved = await(cacheService.getAccessToken(token));
        assertNotNull(retrieved);
        assertEquals("client1", retrieved.getClientId());
    }

    @Test
    void refreshToken_saveAndRetrieve() {
        String token = "refresh-" + System.nanoTime();
        RefreshTokenData data = new RefreshTokenData("user1", "client1", List.of("openid"));
        awaitVoid(cacheService.saveRefreshToken(token, data, 604800));
        RefreshTokenData retrieved = await(cacheService.getRefreshToken(token));
        assertNotNull(retrieved);
        assertEquals("client1", retrieved.getClientId());
    }

    @Test
    void rotateRefreshTokenAtomically_replacesOldRefreshToken() {
        String oldRefresh = "old-refresh-" + System.nanoTime();
        String newAccess = "new-access-" + System.nanoTime();
        String newRefresh = "new-refresh-" + System.nanoTime();
        awaitVoid(cacheService.saveRefreshToken(oldRefresh, new RefreshTokenData(
                "user1", "test-client", List.of("openid")), 604800));

        boolean rotated = await(cacheService.rotateRefreshTokenAtomically(
                oldRefresh,
                newAccess, new AccessTokenData("user1", "test-client", List.of("openid"), 999L), 3600,
                newRefresh, new RefreshTokenData("user1", "test-client", List.of("openid")), 604800));

        assertTrue(rotated);
        assertNull(await(cacheService.getRefreshToken(oldRefresh)));
        assertNotNull(await(cacheService.getRefreshToken(newRefresh)));
        assertNotNull(await(cacheService.getAccessToken(newAccess)));
    }

    @Test
    void rotateRefreshTokenAtomically_missingOldToken_returnsFalse() {
        boolean rotated = await(cacheService.rotateRefreshTokenAtomically(
                "missing-refresh-" + System.nanoTime(),
                "access-" + System.nanoTime(),
                new AccessTokenData("user1", "test-client", List.of("openid"), 999L), 3600,
                "refresh-" + System.nanoTime(),
                new RefreshTokenData("user1", "test-client", List.of("openid")), 604800));

        assertFalse(rotated);
    }

    @Test
    void session_saveAndRetrieve() {
        String sessionId = "session-" + System.nanoTime();
        SessionData data = new SessionData("user1", "csrf", 1000L);
        awaitVoid(cacheService.saveSession(sessionId, data, 1800));
        SessionData retrieved = await(cacheService.getSession(sessionId));
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
    }

    @Test
    void session_deleteAllUserSessions() {
        String sessionId1 = "session-a-" + System.nanoTime();
        String sessionId2 = "session-b-" + System.nanoTime();
        SessionData data = new SessionData("user1", "csrf", 1000L);
        awaitVoid(cacheService.saveSession(sessionId1, data, 1800));
        awaitVoid(cacheService.saveSession(sessionId2, data, 1800));

        awaitVoid(cacheService.deleteAllUserSessions("user1"));

        assertNull(await(cacheService.getSession(sessionId1)));
        assertNull(await(cacheService.getSession(sessionId2)));
    }

    @Test
    void authorizationRequestState_saveAndRetrieve() {
        String state = "state-" + System.nanoTime();
        AuthorizationRequestState data = new AuthorizationRequestState("c", "r", List.of("openid"), state, null, null, null, null);
        awaitVoid(cacheService.saveAuthorizationRequestState(state, data, 600));
        AuthorizationRequestState retrieved = await(cacheService.getAuthorizationRequestState(state));
        assertNotNull(retrieved);
        assertEquals("c", retrieved.getClientId());
    }

    @Test
    void exists_returnsTrueForExistingKey() {
        String key = "exists-code-" + System.nanoTime();
        awaitVoid(cacheService.saveAuthorizationCode(key, new AuthorizationCodeData("c", "u", "r", List.of(), null, null, null), 600));
        assertTrue(await(cacheService.exists("auth:code:" + key)));
    }
}
