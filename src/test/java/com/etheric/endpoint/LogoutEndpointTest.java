package com.etheric.endpoint;

import com.etheric.model.SessionData;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
        cacheService.saveSession(sessionId, new SessionData("user1", null, System.currentTimeMillis()), 1800);

        given()
            .cookie("SESSIONID", sessionId)
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"))
            .header("Set-Cookie", containsString("SESSIONID=;"));

        // Verify session was deleted
        org.junit.jupiter.api.Assertions.assertNull(cacheService.getSession(sessionId));
    }

    @Test
    void logout_withRedirectUri_redirectsToSpecifiedUri() {
        given()
            .queryParam("redirect_uri", "http://example.com/bye")
            .redirects().follow(false)
        .when()
            .get("/logout")
        .then()
            .statusCode(303)
            .header("Location", "http://example.com/bye");
    }

    @Test
    void logout_clearsCookie() {
        String sessionId = UUID.randomUUID().toString();
        cacheService.saveSession(sessionId, new SessionData("user1", null, System.currentTimeMillis()), 1800);

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
                containsString("HttpOnly")
            ));
    }
}
