package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.model.SessionData;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ConsentEndpointTest {

    @Inject
    CacheService cacheService;

    private String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        cacheService.saveSession(sessionId, new SessionData(userId, null, System.currentTimeMillis()), 1800);
        return sessionId;
    }

    private String createState(String clientId, String redirectUri, String userId) {
        String state = UUID.randomUUID().toString();
        cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            clientId, redirectUri, List.of("openid", "profile"), state, userId
        ), 600);
        return state;
    }

    @Test
    void getConsent_noState_returns400() {
        given()
            .when()
            .get("/consent")
        .then()
            .statusCode(400);
    }

    @Test
    void getConsent_noSession_redirectsToLogin() {
        String state = createState("test-client", "http://localhost:8080/callback", "user1");
        given()
            .queryParam("state", state)
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(303)
            .header("Location", containsString("/login?state=" + state));
    }

    @Test
    void getConsent_invalidState_returns400() {
        String sessionId = createSession("user1");
        given()
            .queryParam("state", "nonexistent-state")
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(400);
    }

    @Test
    void getConsent_validSessionAndState_rendersConsentPage() {
        String sessionId = createSession("user1");
        String state = createState("test-client", "http://localhost:8080/callback", "user1");

        given()
            .queryParam("state", state)
            .cookie("SESSIONID", sessionId)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("Test Application"))
            .body(containsString("openid"))
            .body(containsString("profile"))
            .body(containsString("csrf_token"));
    }

    @Test
    void postConsent_approve_redirectsWithCode() {
        String sessionId = createSession("user1");
        String state = createState("test-client", "http://localhost:8080/callback", "user1");

        // First get the consent page to obtain CSRF token
        given()
            .queryParam("state", state)
            .cookie("SESSIONID", sessionId)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("csrf_token"));

        String csrfToken = cacheService.getSession(sessionId).getCsrfToken();

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                containsString("code="),
                containsString("state=" + state)
            ));

        // Verify request state was deleted
        assertNull(cacheService.getAuthorizationRequestState(state));
    }

    @Test
    void postConsent_deny_redirectsWithError() {
        String sessionId = createSession("user1");
        String state = createState("test-client", "http://localhost:8080/callback", "user1");

        given()
            .queryParam("state", state)
            .cookie("SESSIONID", sessionId)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("csrf_token"));

        String csrfToken = cacheService.getSession(sessionId).getCsrfToken();

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "deny")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                containsString("error=access_denied"),
                containsString("state=" + state)
            ));
    }

    @Test
    void postConsent_invalidCsrf_returns403() {
        String sessionId = createSession("user1");
        String state = createState("test-client", "http://localhost:8080/callback", "user1");

        given()
            .queryParam("state", state)
            .cookie("SESSIONID", sessionId)
        .when()
            .get("/consent")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", "invalid-csrf-token")
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(403);
    }

    @Test
    void postConsent_noSession_redirectsToLogin() {
        String state = createState("test-client", "http://localhost:8080/callback", "user1");
        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", "anything")
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(303)
            .header("Location", containsString("/login?state=" + state));
    }

    @Test
    void postConsent_noState_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("csrf_token", "token")
        .when()
            .post("/consent")
        .then()
            .statusCode(400);
    }

    @Test
    void postConsent_noAction_returns400() {
        String sessionId = createSession("user1");
        String state = createState("test-client", "http://localhost:8080/callback", "user1");
        given()
            .contentType(ContentType.URLENC)
            .formParam("state", state)
            .formParam("csrf_token", "token")
            .cookie("SESSIONID", sessionId)
        .when()
            .post("/consent")
        .then()
            .statusCode(400);
    }

    private void assertNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNull(obj);
    }
}
