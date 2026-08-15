package com.etheric.endpoint;

import com.etheric.model.AccessTokenData;
import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.RefreshTokenData;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import com.etheric.util.PkceUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TokenEndpointTest {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    private static final String TEST_USER_ID = "b0000000-0000-0000-0000-000000000001";

    @Test
    void token_missingGrantType_returnsInvalidRequest() {
        given()
            .contentType(ContentType.URLENC)
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_unsupportedGrantType_returnsUnsupportedGrantType() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "implicit")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    void token_authCode_missingParams_returnsInvalidRequest() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_authCode_missingCode_returnsInvalidRequest() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_authCode_invalidCode_returnsInvalidGrant() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", "nonexistent-code")
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_authCode_validCode_returnsTokens() {
        String code = "valid-test-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid", "profile"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("expires_in", equalTo(3600))
            .body("refresh_token", notNullValue())
            .body("scope", equalTo("openid profile"))
            .body("id_token", notNullValue());

        assertNull(await(cacheService.getAuthorizationCode(code)));
    }

    @Test
    void token_authCode_withoutOpenidScope_noIdToken() {
        String code = "no-openid-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("profile"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("id_token", nullValue());
    }

    @Test
    void token_authCode_withOpenidScope_includesIdTokenWithNonce() {
        String code = "openid-nonce-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback",
            List.of("openid", "email"), null, null, "test-nonce-123"
        ), 600));

        String idToken = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("id_token", notNullValue())
            .extract().path("id_token");

        var parsed = jwtService.parseToken(idToken);
        org.junit.jupiter.api.Assertions.assertTrue(parsed.isPresent());
        org.junit.jupiter.api.Assertions.assertEquals("test-nonce-123", parsed.get().getClaim("nonce"));
        org.junit.jupiter.api.Assertions.assertEquals("test-client", parsed.get().getAudience().iterator().next());
    }

    @Test
    void token_authCode_codeOneTimeUse_codeDeletedAfterUse() {
        String code = "one-time-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_authCode_wrongRedirectUri_returnsInvalidGrant() {
        String code = "wrong-redirect-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://evil.com/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_authCode_withoutScope_usesCodeScopes() {
        String code = "default-scope-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid", "email"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("scope", equalTo("openid email"));
    }

    @Test
    void token_authCode_scopeNotSubset_returnsInvalidScope() {
        String code = "invalid-scope-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
            .formParam("scope", "openid email")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_scope"));
    }

    @Test
    void token_refreshToken_missingParams_returnsInvalidRequest() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_refreshToken_invalidToken_returnsInvalidGrant() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", "nonexistent-refresh-token")
            .formParam("client_id", "test-client")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_refreshToken_validToken_returnsTokens() {
        String refreshToken = "valid-refresh-token";
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "test-client", List.of("openid")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "test-client")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("expires_in", equalTo(3600))
            .body("refresh_token", notNullValue())
            .body("scope", equalTo("openid"))
            .body("id_token", notNullValue());

        assertNull(await(cacheService.getRefreshToken(refreshToken)));
    }

    @Test
    void token_refreshToken_withScope_usesProvidedScope() {
        String refreshToken = "scope-refresh-token";
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "test-client", List.of("openid", "email")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "test-client")
            .formParam("scope", "openid")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("scope", equalTo("openid"));
    }

    @Test
    void token_refreshToken_scopeNotSubset_returnsInvalidScope() {
        String refreshToken = "invalid-scope-refresh";
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "test-client", List.of("openid")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "test-client")
            .formParam("scope", "openid email")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_scope"));
    }

    @Test
    void token_authCode_invalidClientSecret_returnsInvalidClient() {
        String code = "invalid-secret-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "wrong-secret")
        .when()
            .post("/token")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    @Test
    void token_authCode_basicAuth_returnsTokens() {
        String code = "basic-auth-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        String basic = Base64.getEncoder().encodeToString("test-client:secret".getBytes(StandardCharsets.UTF_8));

        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", "Basic " + basic)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue());
    }

    @Test
    void token_authCode_unknownClient_returnsInvalidClient() {
        String code = "unknown-client-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "unknown-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    @Test
    void token_authCode_wrongClientIdForCode_returnsInvalidGrant() {
        String code = "mismatch-client-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "other-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_refreshToken_wrongClientId_returnsInvalidGrant() {
        String refreshToken = "mismatch-refresh-token";
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "other-client", List.of("openid")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "test-client")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_refreshToken_unknownClient_returnsInvalidClient() {
        String refreshToken = "unknown-client-refresh-token";
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "test-client", List.of("openid")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "unknown-client")
        .when()
            .post("/token")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    private static final String PKCE_VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void token_authCode_withPkce_validVerifier_returnsTokens() {
        String code = "pkce-valid-code";
        String challenge = PkceUtil.s256Challenge(PKCE_VERIFIER);
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"),
            challenge, "S256", null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
            .formParam("code_verifier", PKCE_VERIFIER)
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue());
    }

    @Test
    void token_authCode_withPkce_missingVerifier_returnsInvalidGrant() {
        String code = "pkce-missing-verifier-code";
        String challenge = PkceUtil.s256Challenge(PKCE_VERIFIER);
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"),
            challenge, "S256", null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_authCode_withPkce_wrongVerifier_returnsInvalidGrant() {
        String code = "pkce-wrong-verifier-code";
        String challenge = PkceUtil.s256Challenge(PKCE_VERIFIER);
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"),
            challenge, "S256", null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
            .formParam("code_verifier", "wrong-verifier")
        .when()
            .post("/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void token_authCode_withoutPkce_doesNotRequireVerifier() {
        String code = "no-pkce-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue());
    }

    @Test
    void token_authCode_tokensAreStoredInCache() {
        String code = "cache-storage-code";
        awaitVoid(cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", TEST_USER_ID, "http://localhost:8080/callback", List.of("openid"), null, null, null
        ), 600));

        String body = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "secret")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .extract().body().asString();

        String accessToken = io.restassured.path.json.JsonPath.from(body).getString("access_token");
        String refreshTokenVal = io.restassured.path.json.JsonPath.from(body).getString("refresh_token");

        assertNotNull(await(cacheService.getAccessToken(accessToken)));
        assertNotNull(await(cacheService.getRefreshToken(refreshTokenVal)));
    }

    private void assertNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNull(obj);
    }

    private void assertNotNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNotNull(obj);
    }
}
