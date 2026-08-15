package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.model.ConsentData;
import com.etheric.model.SessionData;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ConsentSkipTest {

    private static final String CLIENT_ID = "test-client";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String TEST_USER_ID = "b0000000-0000-0000-0000-000000000001";

    @Inject
    CacheService cacheService;

    @Test
    void authorize_withExistingConsent_skipsConsentPage() {
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId, new SessionData(TEST_USER_ID, null, System.currentTimeMillis()), 1800));
        awaitVoid(cacheService.saveConsent(TEST_USER_ID, CLIENT_ID,
                new ConsentData(List.of("openid", "profile", "email"), System.currentTimeMillis()), 86400));

        String state = UUID.randomUUID().toString();

        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", state)
            .queryParam("scope", "openid")
            .queryParam("scope", "profile")
            .cookie(new Cookie.Builder("SESSIONID", sessionId).build())
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                containsString(REDIRECT_URI),
                containsString("code="),
                containsString("state=" + state)
            ));
    }

    @Test
    void authorize_withoutConsent_redirectsToConsent() {
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId, new SessionData(TEST_USER_ID, null, System.currentTimeMillis()), 1800));
        awaitVoid(cacheService.deleteConsent(TEST_USER_ID, CLIENT_ID));

        String state = UUID.randomUUID().toString();

        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", state)
            .queryParam("scope", "openid")
            .cookie(new Cookie.Builder("SESSIONID", sessionId).build())
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(303)
            .header("Location", containsString("/consent?state=" + state));
    }

    @Test
    void login_withExistingConsent_redirectsToAuthorize() {
        String sessionId = UUID.randomUUID().toString();
        String csrfToken = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId,
                new SessionData(null, csrfToken, System.currentTimeMillis()), 1800));

        String state = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            CLIENT_ID, REDIRECT_URI, List.of("openid"), state, null, null, null, null
        ), 600));
        awaitVoid(cacheService.saveConsent(TEST_USER_ID, CLIENT_ID,
                new ConsentData(List.of("openid", "profile", "email"), System.currentTimeMillis()), 86400));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("state", state)
            .formParam("csrf_token", csrfToken)
            .cookie(new Cookie.Builder("SESSIONID", sessionId).build())
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303)
            .header("Location", containsString("/authorize?state=" + state));
    }
}
