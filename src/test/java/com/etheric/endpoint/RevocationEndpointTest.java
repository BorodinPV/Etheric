package com.etheric.endpoint;

import com.etheric.model.AccessTokenData;
import com.etheric.model.RefreshTokenData;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class RevocationEndpointTest {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    private static final String TEST_USER_ID = "b0000000-0000-0000-0000-000000000001";

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString("test-client:secret".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void revoke_accessToken_deletesFromCache() {
        String accessToken = jwtService.generateAccessToken(TEST_USER_ID, List.of("user"), List.of("openid"));
        awaitVoid(cacheService.saveAccessToken(accessToken, new AccessTokenData(
            TEST_USER_ID, "test-client", List.of("openid"), System.currentTimeMillis() / 1000 + 3600
        ), 3600));

        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", basicAuth())
            .formParam("token", accessToken)
            .formParam("token_type_hint", "access_token")
        .when()
            .post("/revoke")
        .then()
            .statusCode(200);

        assertNull(await(cacheService.getAccessToken(accessToken)));
    }

    @Test
    void revoke_refreshToken_deletesFromCache() {
        String refreshToken = jwtService.generateRefreshToken(TEST_USER_ID, List.of("user"), List.of("openid"));
        awaitVoid(cacheService.saveRefreshToken(refreshToken, new RefreshTokenData(
            TEST_USER_ID, "test-client", List.of("openid")
        ), 604800));

        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", basicAuth())
            .formParam("token", refreshToken)
            .formParam("token_type_hint", "refresh_token")
        .when()
            .post("/revoke")
        .then()
            .statusCode(200);

        assertNull(await(cacheService.getRefreshToken(refreshToken)));
    }

    @Test
    void revoke_unknownToken_returns200() {
        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", basicAuth())
            .formParam("token", "nonexistent-token")
        .when()
            .post("/revoke")
        .then()
            .statusCode(200);
    }

    @Test
    void revoke_invalidClientAuth_returns401() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("token", "some-token")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "wrong")
        .when()
            .post("/revoke")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }
}
