package com.etheric.endpoint;

import com.etheric.exception.OAuthExceptionMapper;
import com.etheric.exception.GlobalExceptionMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthorizationEndpointTest {

    private static final String CLIENT_ID = "test-client";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";

    @Test
    void authorize_missingResponseType_returnsInvalidRequest() {
        given()
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "abc")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=invalid_request"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_missingClientId_returnsInvalidRequest() {
        given()
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "abc")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=invalid_request"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_missingRedirectUri_returnsInvalidRequest() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("state", "abc")
        .when()
            .get("/authorize")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void authorize_missingState_returnsInvalidRequest() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", containsString("error=invalid_request"));
    }

    @Test
    void authorize_unsupportedResponseType_redirectsWithError() {
        given()
            .queryParam("response_type", "token")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "abc")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=unsupported_response_type"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_invalidClientId_redirectsWithError() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", "nonexistent-client")
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "abc")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=unauthorized_client"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_invalidRedirectUri_redirectsWithError() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", "http://evil.com/callback")
            .queryParam("state", "abc")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=invalid_request"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_invalidScope_redirectsWithError() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "abc")
            .queryParam("scope", "admin")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=invalid_scope"),
                containsString("state=abc")
            ));
    }

    @Test
    void authorize_validRequest_noSession_redirectsToLogin() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "teststate123")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(303)
            .header("Location", containsString("/login?state=teststate123"));
    }

    @Test
    void authorize_plainPkceMethod_redirectsWithInvalidRequest() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "plainpkce")
            .queryParam("code_challenge", "some-challenge")
            .queryParam("code_challenge_method", "plain")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                containsString("error=invalid_request"),
                containsString("state=plainpkce")
            ));
    }

    @Test
    void authorize_validRequest_withScopes_savesStateToCache() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "scopetest")
            .queryParam("scope", "openid", "profile")
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(303)
            .header("Location", containsString("/login?state=scopetest"));
    }
}
