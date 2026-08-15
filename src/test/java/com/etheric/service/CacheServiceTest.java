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
    void session_saveAndRetrieve() {
        String sessionId = "session-" + System.nanoTime();
        SessionData data = new SessionData("user1", "csrf", 1000L);
        awaitVoid(cacheService.saveSession(sessionId, data, 1800));
        SessionData retrieved = await(cacheService.getSession(sessionId));
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
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
