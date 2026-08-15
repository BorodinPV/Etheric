package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class LoginEndpointTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "name=\"csrf_token\"\\s+value=\"([^\"]+)\"");

    @Inject
    CacheService cacheService;

    @Test
    void getLogin_rendersFormWithCsrf() {
        given()
            .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body(containsString("Вход в систему"))
            .body(containsString("form"))
            .body(containsString("username"))
            .body(containsString("password"))
            .body(containsString("csrf_token"))
            .header("Set-Cookie", allOf(
                containsString("SESSIONID="),
                containsString("HttpOnly"),
                containsString("Secure"),
                containsString("SameSite=Lax")
            ));
    }

    @Test
    void getLogin_withState_includesHiddenField() {
        given()
            .queryParam("state", "test-state")
        .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body(containsString("test-state"))
            .body(containsString("csrf_token"));
    }

    @Test
    void postLogin_validCredentials_setsSessionCookie() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"))
            .header("Set-Cookie", allOf(
                containsString("SESSIONID="),
                containsString("HttpOnly"),
                containsString("Secure"),
                containsString("SameSite=Lax")
            ));
    }

    @Test
    void postLogin_validCredentials_withState_redirectsToConsent() {
        String state = "login-test-state";
        awaitVoid(cacheService.deleteConsent("b0000000-0000-0000-0000-000000000001", "test-client"));
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            "test-client", "http://localhost:8080/callback", List.of("openid"), state, null, null, null, null
        ), 600));

        LoginChallenge challenge = openLogin(state);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("state", state)
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303)
            .header("Location", containsString("/consent?state=" + state));
    }

    @Test
    void postLogin_invalidCredentials_showsError() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "wrongpassword")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"))
            .body(containsString("csrf_token"));
    }

    @Test
    void postLogin_nonExistingUser_showsError() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "nonexistent")
            .formParam("password", "password")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"));
    }

    @Test
    void postLogin_emptyUsername_showsError() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "")
            .formParam("password", "password")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"));
    }

    @Test
    void postLogin_withState_preservesStateOnFailure() {
        LoginChallenge challenge = openLogin("error-state");

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "wrongpassword")
            .formParam("state", "error-state")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("error-state"));
    }

    @Test
    void postLogin_validCredentials_updatesRequestState() {
        String state = "update-test-state";
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            "test-client", "http://localhost:8080/callback", List.of("openid"), state, null, null, null, null
        ), 600));

        LoginChallenge challenge = openLogin(state);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("state", state)
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303);

        var updatedState = await(cacheService.getAuthorizationRequestState(state));
        if (updatedState != null) {
            assert updatedState.getUserId() != null;
        }
    }

    @Test
    void postLogin_missingCsrf_returnsForbidden() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
        .when()
            .post("/login")
        .then()
            .statusCode(403)
            .body(containsString("Invalid CSRF token"));
    }

    @Test
    void postLogin_invalidCsrf_returnsForbidden() {
        LoginChallenge challenge = openLogin(null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("csrf_token", "not-the-real-token")
        .when()
            .post("/login")
        .then()
            .statusCode(403)
            .body(containsString("Invalid CSRF token"));
    }

    @Test
    void postLogin_withoutSessionCookie_returnsForbidden() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("csrf_token", "anything")
        .when()
            .post("/login")
        .then()
            .statusCode(403)
            .body(containsString("Invalid CSRF token"));
    }

    private LoginChallenge openLogin(String state) {
        var request = given();
        if (state != null) {
            request = request.queryParam("state", state);
        }

        Response response = request.when().get("/login");
        response.then().statusCode(200);

        String body = response.getBody().asString();
        Matcher matcher = CSRF_PATTERN.matcher(body);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "CSRF token missing in login form");
        String csrfToken = matcher.group(1);

        String sessionId = response.getCookie("SESSIONID");
        assertNotNull(sessionId, "SESSIONID cookie missing on GET /login");
        return new LoginChallenge(sessionId, csrfToken);
    }

    private record LoginChallenge(String sessionId, String csrfToken) {
    }
}
