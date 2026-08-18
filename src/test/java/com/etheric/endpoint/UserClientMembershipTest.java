package com.etheric.endpoint;

import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
import com.etheric.service.CacheService;
import com.etheric.service.UserClientMembershipService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserClientMembershipTest {

    private static final String ADMIN_KEY = "test-admin-key";
    private static final String TEST_USER_ID = "b0000000-0000-0000-0000-000000000001";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";

    @Inject
    CacheService cacheService;

    @Inject
    UserClientMembershipService membershipService;

    @Inject
    UserRepository userRepository;

    @Test
    void authorize_withoutMembership_returnsAccessDenied() {
        String clientId = registerClient("Membership Deny Client");
        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId,
                new SessionData(TEST_USER_ID, null, System.currentTimeMillis()), 1800));

        String state = UUID.randomUUID().toString();
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", state)
            .queryParam("scope", "openid")
            .cookie(new Cookie.Builder("SESSIONID", sessionId).build())
            .redirects().follow(false)
        .when()
            .get("/authorize")
        .then()
            .statusCode(302)
            .header("Location", allOf(
                    containsString("error=access_denied"),
                    containsString("state=" + state)));
    }

    @Test
    void authorize_withMembership_continuesToConsent() {
        String clientId = registerClient("Membership Allow Client");
        AuthenticatedSession auth = loginAsAdmin();

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", auth.sessionId())
            .formParam("csrf_token", auth.csrfToken())
            .formParam("client_ids", clientId)
            .redirects().follow(false)
        .when()
            .post("/admin/console/users/" + TEST_USER_ID + "/clients")
        .then()
            .statusCode(200);

        String sessionId = UUID.randomUUID().toString();
        awaitVoid(cacheService.saveSession(sessionId,
                new SessionData(TEST_USER_ID, null, System.currentTimeMillis()), 1800));

        String state = UUID.randomUUID().toString();
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
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
    void adminConsole_userClientAssignment_roundTrip() {
        UUID userId = await(() -> userRepository.findByUsername("user")).orElseThrow().id;
        AuthenticatedSession auth = loginAsAdmin();

        given()
            .cookie("ADMIN_SESSION", auth.sessionId())
            .get("/admin/console/users/" + userId + "/clients")
        .then()
            .statusCode(200)
            .body(containsString("Clients"))
            .body(containsString("test-client"));

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", auth.sessionId())
            .formParam("csrf_token", auth.csrfToken())
            .formParam("client_ids", "test-client")
            .redirects().follow(false)
        .when()
            .post("/admin/console/users/" + userId + "/clients")
        .then()
            .statusCode(200)
            .body(containsString("Client assignments updated successfully"));

        assertTrue(await(() -> membershipService.isMember(userId.toString(), "test-client")));
    }

    @Test
    void adminConsole_clientUserAssignment_roundTrip() {
        AuthenticatedSession auth = loginAsAdmin();

        given()
            .cookie("ADMIN_SESSION", auth.sessionId())
            .get("/admin/console/clients/test-client/users")
        .then()
            .statusCode(200)
            .body(containsString("Users"))
            .body(containsString("user"));

        UUID userId = await(() -> userRepository.findByUsername("user")).orElseThrow().id;

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", auth.sessionId())
            .formParam("csrf_token", auth.csrfToken())
            .formParam("user_ids", userId.toString())
            .redirects().follow(false)
        .when()
            .post("/admin/console/clients/test-client/users")
        .then()
            .statusCode(200)
            .body(containsString("User assignments updated successfully"));

        assertTrue(await(() -> membershipService.isMember(userId.toString(), "test-client")));
    }

    private String registerClient(String name) {
        Response response = given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                    "client_name", name,
                    "redirect_uris", List.of(REDIRECT_URI)))
        .when()
            .post("/admin/clients");
        response.then().statusCode(201);
        return response.jsonPath().getString("client_id");
    }

    private AuthenticatedSession loginAsAdmin() {
        Response loginPage = given().get("/admin/console/login");
        String challengeId = extractCookie(loginPage.getHeader("Set-Cookie"));
        String csrf = extractCsrf(loginPage.body().asString());

        Response response = given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", challengeId)
            .formParam("username", "admin")
            .formParam("password", "admin")
            .formParam("csrf_token", csrf)
            .redirects().follow(false)
        .when()
            .post("/admin/console/login");

        response.then().statusCode(303).header("Set-Cookie", containsString("ADMIN_SESSION="));

        String sessionId = extractCookie(response.getHeader("Set-Cookie"));
        String pageCsrf = given()
            .cookie("ADMIN_SESSION", sessionId)
            .get("/admin/console/clients")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        return new AuthenticatedSession(sessionId, extractCsrf(pageCsrf));
    }

    private static String extractCookie(String setCookie) {
        int start = setCookie.indexOf("ADMIN_SESSION=");
        assertTrue(start >= 0);
        int valueStart = start + "ADMIN_SESSION=".length();
        int end = setCookie.indexOf(';', valueStart);
        return end >= 0 ? setCookie.substring(valueStart, end) : setCookie.substring(valueStart);
    }

    private static String extractCsrf(String body) {
        int marker = body.indexOf("name=\"csrf_token\"");
        assertTrue(marker >= 0);
        int valueStart = body.indexOf("value=\"", marker) + "value=\"".length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }

    private record AuthenticatedSession(String sessionId, String csrfToken) {
    }
}
