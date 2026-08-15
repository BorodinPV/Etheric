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

import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class IntrospectionEndpointTest {

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    private static final String TEST_USER_ID = "b0000000-0000-0000-0000-000000000001";

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString("test-client:secret".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void introspect_missingToken_returnsInactive() {
        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", basicAuth())
        .when()
            .post("/introspect")
        .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    @Test
    void introspect_unknownToken_returnsInactive() {
        given()
            .contentType(ContentType.URLENC)
            .header("Authorization", basicAuth())
            .formParam("token", "unknown-token-value")
        .when()
            .post("/introspect")
        .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    @Test
    void introspect_validAccessToken_returnsActive() {
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
            .post("/introspect")
        .then()
            .statusCode(200)
            .body("active", equalTo(true))
            .body("client_id", equalTo("test-client"))
            .body("sub", equalTo(TEST_USER_ID))
            .body("token_type", equalTo("Bearer"))
            .body("scope", equalTo("openid"));
    }

    @Test
    void introspect_validRefreshToken_returnsActive() {
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
            .post("/introspect")
        .then()
            .statusCode(200)
            .body("active", equalTo(true))
            .body("client_id", equalTo("test-client"))
            .body("sub", equalTo(TEST_USER_ID));
    }

    @Test
    void introspect_invalidClientAuth_returns401() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("token", "some-token")
            .formParam("client_id", "test-client")
            .formParam("client_secret", "wrong")
        .when()
            .post("/introspect")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }
}
