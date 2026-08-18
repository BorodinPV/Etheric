package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ConsentEndpointTest {

    private static final String TEST_CLIENT_ID = "test-client";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";

    @Inject
    CacheService cacheService;

    @Inject
    UserRepository userRepository;

    private String testUserId() {
        return await(() -> userRepository.findByUsername("user")).orElseThrow().id.toString();
    }

    private String createSession() {
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId,
                new SessionData(testUserId(), null, System.currentTimeMillis()), 1800));
        return sessionId;
    }

    private String createState() {
        return createState(TEST_CLIENT_ID, REDIRECT_URI);
    }

    private String createState(String clientId, String redirectUri) {
        String state = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
                clientId, redirectUri, List.of("openid", "profile"), state, testUserId(), null, null, null
        ), 600));
        return state;
    }

    private static Cookie sessionCookie(String sessionId) {
        return new Cookie.Builder("SESSIONID", sessionId).build();
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
        String state = createState();
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
        String sessionId = createSession();
        given()
            .queryParam("state", "nonexistent-state")
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(400);
    }

    @Test
    void getConsent_validSessionAndState_rendersConsentPage() {
        String sessionId = createSession();
        String state = createState();

        given()
            .queryParam("state", state)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("Etheric Dev Application"))
            .body(containsString("openid"))
            .body(containsString("profile"))
            .body(containsString("csrf_token"));
    }

    @Test
    void postConsent_approve_redirectsWithCode() {
        String sessionId = createSession();
        String state = createState();

        given()
            .queryParam("state", state)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("csrf_token"));

        String csrfToken = await(cacheService.getSession(sessionId)).getCsrfToken();

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                containsString("code="),
                containsString("state=" + state)
            ));

        assertNull(await(cacheService.getAuthorizationRequestState(state)));
    }

    @Test
    void postConsent_deny_redirectsWithError() {
        String sessionId = createSession();
        String state = createState();

        given()
            .queryParam("state", state)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(200)
            .body(containsString("csrf_token"));

        String csrfToken = await(cacheService.getSession(sessionId)).getCsrfToken();

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "deny")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie(sessionCookie(sessionId))
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
        String sessionId = createSession();
        String state = createState();

        given()
            .queryParam("state", state)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", "invalid-csrf-token")
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(403);
    }

    @Test
    void postConsent_approve_redirectsWithEncodedParameters() {
        String sessionId = createSession();
        String state = "state/with+special&chars";
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
                TEST_CLIENT_ID, REDIRECT_URI, List.of("openid"), state, testUserId(), null, null, null
        ), 600));

        given()
            .queryParam("state", state)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .get("/consent")
        .then()
            .statusCode(200);

        String csrfToken = await(cacheService.getSession(sessionId)).getCsrfToken();

        given()
            .contentType(ContentType.URLENC)
            .formParam("action", "approve")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie(sessionCookie(sessionId))
            .redirects().follow(false)
        .when()
            .post("/consent")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                containsString("code="),
                containsString("state=state%2Fwith%2Bspecial%26chars")
            ));
    }

    @Test
    void postConsent_noSession_redirectsToLogin() {
        String state = createState();
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
        String sessionId = createSession();
        String state = createState();
        given()
            .contentType(ContentType.URLENC)
            .formParam("state", state)
            .formParam("csrf_token", "token")
            .cookie(sessionCookie(sessionId))
        .when()
            .post("/consent")
        .then()
            .statusCode(400);
    }

    private void assertNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNull(obj);
    }
}
