package com.etheric.endpoint;

import com.etheric.model.SessionData;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LogoutEndpointTest {

    @Inject
    CacheService cacheService;

    @Test
    void logout_noSession_redirectsToRoot() {
        given()
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"))
            .header("Set-Cookie", containsString("SESSIONID=;"));
    }

    @Test
    void logout_withSession_deletesSession() {
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId, new SessionData("user1", null, System.currentTimeMillis()), 1800));

        given()
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"))
            .header("Set-Cookie", containsString("SESSIONID=;"));

        org.junit.jupiter.api.Assertions.assertNull(await(cacheService.getSession(sessionId)));
    }

    @Test
    void logout_deletesAllUserSessionsEvenWhenOnlyOneCookiePresent() {
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId1, new SessionData("user1", null, System.currentTimeMillis()), 1800));
        awaitVoid(cacheService.saveSession(sessionId2, new SessionData("user1", null, System.currentTimeMillis()), 1800));

        given()
            .cookie("SESSIONID", sessionId1)
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303);

        org.junit.jupiter.api.Assertions.assertNull(await(cacheService.getSession(sessionId1)));
        org.junit.jupiter.api.Assertions.assertNull(await(cacheService.getSession(sessionId2)));
    }

    @Test
    void logout_withClientId_deletesOnlyMatchingClientSessions() {
        String clientASession = UUID.randomUUID().toString();
        String clientBSession = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(clientASession,
                new SessionData("user1", null, System.currentTimeMillis(), "test-client"), 1800));
        awaitVoid(cacheService.saveSession(clientBSession,
                new SessionData("user1", null, System.currentTimeMillis(), "other-client"), 1800));

        given()
            .cookie("SESSIONID", clientASession)
            .queryParam("client_id", "test-client")
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303);

        org.junit.jupiter.api.Assertions.assertNull(await(cacheService.getSession(clientASession)));
        org.junit.jupiter.api.Assertions.assertNotNull(await(cacheService.getSession(clientBSession)));
    }

    @Test
    void logout_withRedirectUri_redirectsToRegisteredUri() {
        given()
            .queryParam("redirect_uri", "http://localhost:8080/callback")
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", "http://localhost:8080/callback");
    }

    @Test
    void logout_withUnregisteredRedirectUri_redirectsToRoot() {
        given()
            .queryParam("redirect_uri", "http://evil.com/bye")
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"));
    }

    @Test
    void logout_clearsCookie() {
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId, new SessionData("user1", null, System.currentTimeMillis()), 1800));

        given()
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Set-Cookie", allOf(
                containsString("SESSIONID="),
                containsString("Max-Age=0"),
                containsString("Path=/"),
                containsString("HttpOnly"),
                containsString("Secure"),
                containsString("SameSite=Lax")
            ));
    }
}
