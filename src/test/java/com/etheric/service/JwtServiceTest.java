package com.etheric.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JwtServiceTest {

    @Inject
    JwtService jwtService;

    @Test
    void generateAccessToken_returnsNonNull() {
        String token = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateAccessToken_containsExpectedParts() {
        String token = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts separated by dots");
    }

    @Test
    void generateRefreshToken_returnsNonNull() {
        String token = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid"));
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateRefreshToken_containsExpectedParts() {
        String token = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid"));
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts separated by dots");
    }

    @Test
    void generateAccessToken_differentTokensEachCall() {
        String token1 = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        String token2 = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        assertNotEquals(token1, token2, "Each token should be unique (different iat/exp)");
    }

    @Test
    void generateRefreshToken_differentTokensEachCall() {
        String token1 = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid"));
        String token2 = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid"));
        assertNotEquals(token1, token2);
    }

    @Test
    void generateAuthorizationCode_returnsNonNull() {
        String code = jwtService.generateAuthorizationCode();
        assertNotNull(code);
        assertFalse(code.isEmpty());
    }

    @Test
    void generateAuthorizationCode_unique() {
        String code1 = jwtService.generateAuthorizationCode();
        String code2 = jwtService.generateAuthorizationCode();
        assertNotEquals(code1, code2);
    }

    @Test
    void generateAuthorizationCode_isUUID() {
        String code = jwtService.generateAuthorizationCode();
        assertDoesNotThrow(() -> java.util.UUID.fromString(code));
    }

    @Test
    void verifyToken_validAccessToken() {
        String token = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        assertTrue(jwtService.verifyToken(token));
    }

    @Test
    void verifyToken_validRefreshToken() {
        String token = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid"));
        assertTrue(jwtService.verifyToken(token));
    }

    @Test
    void verifyToken_invalidToken() {
        assertFalse(jwtService.verifyToken("invalid.token.here"));
    }

    @Test
    void verifyToken_emptyToken() {
        assertFalse(jwtService.verifyToken(""));
    }

    @Test
    void verifyToken_tamperedToken() {
        String token = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtService.verifyToken(tampered));
    }

    @Test
    void parseToken_validToken() {
        String token = jwtService.generateAccessToken("user1", List.of("user"), List.of("openid"));
        JsonWebToken jwt = jwtService.parseToken(token);
        assertNotNull(jwt);
        assertEquals("user1", jwt.getSubject());
    }

    @Test
    void parseToken_invalidToken() {
        assertNull(jwtService.parseToken("invalid.token.here"));
    }

    @Test
    void parseToken_refreshTokenHasClaims() {
        String token = jwtService.generateRefreshToken("user1", List.of("user"), List.of("openid", "email"));
        JsonWebToken jwt = jwtService.parseToken(token);
        assertNotNull(jwt);
        assertEquals("user1", jwt.getSubject());
        assertNotNull(jwt.getClaim("token_type"));
        assertEquals("refresh", jwt.getClaim("token_type"));
    }

    @Test
    void getPublicKey_returnsNonNull() {
        assertNotNull(jwtService.getPublicKey());
    }

    @Test
    void getKeyId_returnsNonNull() {
        assertNotNull(jwtService.getKeyId());
    }

    @Test
    void getKeyId_returnsSameValue() {
        assertEquals(jwtService.getKeyId(), jwtService.getKeyId());
    }

    @Test
    void getJwks_returnsValidStructure() {
        var jwks = jwtService.getJwks();
        assertNotNull(jwks);
        assertNotNull(jwks.getKeys());
        assertEquals(1, jwks.getKeys().size());

        var key = jwks.getKeys().get(0);
        assertEquals("RSA", key.getKty());
        assertEquals(jwtService.getKeyId(), key.getKid());
        assertEquals("sig", key.getUse());
        assertEquals("RS256", key.getAlg());
        assertNotNull(key.getN());
        assertNotNull(key.getE());
        assertFalse(key.getN().isEmpty());
        assertFalse(key.getE().isEmpty());
    }
}
