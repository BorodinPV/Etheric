package com.etheric.endpoint;

import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.RefreshTokenData;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TokenEndpointTest {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

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
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", "user1", "http://localhost:8080/callback", List.of("openid", "profile")
        ), 600);

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
            .body("scope", equalTo("openid profile"));

        // Verify code was deleted (one-time use)
        assertNull(cacheService.getAuthorizationCode(code));
    }

    @Test
    void token_authCode_codeOneTimeUse_codeDeletedAfterUse() {
        String code = "one-time-code";
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", "user1", "http://localhost:8080/callback", List.of("openid")
        ), 600);

        // First request succeeds
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

        // Second request with same code fails
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
    void token_authCode_wrongRedirectUri_returnsInvalidRequest() {
        String code = "wrong-redirect-code";
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", "user1", "http://localhost:8080/callback", List.of("openid")
        ), 600);

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
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_authCode_withoutScope_usesCodeScopes() {
        String code = "default-scope-code";
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", "user1", "http://localhost:8080/callback", List.of("openid", "email")
        ), 600);

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
        cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            "user1", "test-client", List.of("openid")
        ), 604800);

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
            .body("scope", equalTo("openid"));

        // Old refresh token should be deleted
        assertNull(cacheService.getRefreshToken(refreshToken));
    }

    @Test
    void token_refreshToken_withScope_usesProvidedScope() {
        String refreshToken = "scope-refresh-token";
        cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            "user1", "test-client", List.of("openid")
        ), 604800);

        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "test-client")
            .formParam("scope", "openid email")
        .when()
            .post("/token")
        .then()
            .statusCode(200)
            .body("scope", equalTo("openid email"));
    }

    @Test
    void token_authCode_tokensAreStoredInCache() {
        String code = "cache-storage-code";
        cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
            "test-client", "user1", "http://localhost:8080/callback", List.of("openid")
        ), 600);

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

        // Extract access_token and refresh_token from response
        String accessToken = io.restassured.path.json.JsonPath.from(body).getString("access_token");
        String refreshTokenVal = io.restassured.path.json.JsonPath.from(body).getString("refresh_token");

        assertNotNull(cacheService.getAccessToken(accessToken));
        assertNotNull(cacheService.getRefreshToken(refreshTokenVal));
    }

    private void assertNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNull(obj);
    }

    private void assertNotNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNotNull(obj);
    }
}
