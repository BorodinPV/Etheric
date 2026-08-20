package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.repository.UserRepository;
import com.etheric.service.CacheService;
import com.etheric.service.UserClientMembershipService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RegistrationEndpointTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "name=\"csrf_token\"\\s+value=\"([^\"]+)\"");

    @Inject
    CacheService cacheService;

    @Inject
    UserRepository userRepository;

    @Inject
    UserClientMembershipService membershipService;

    @Test
    void getRegister_rendersFormWithCsrf() {
        given()
            .when()
            .get("/register")
        .then()
            .statusCode(200)
            .body(containsString("Регистрация"))
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
    void getRegister_withState_includesHiddenField() {
        given()
            .queryParam("state", "register-test-state")
        .when()
            .get("/register")
        .then()
            .statusCode(200)
            .body(containsString("register-test-state"))
            .body(containsString("csrf_token"));
    }

    @Test
    void postRegister_validData_assignsMembershipAndRedirectsToLogin() {
        String username = "reg-user-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterChallenge challenge = openRegister(null, "test-client", null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", username)
            .formParam("password", "password123")
            .formParam("client_id", "test-client")
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/register")
        .then()
            .statusCode(303)
            .header("Location", containsString("/login?registered=1"));

        var user = await(() -> userRepository.findByUsername(username));
        assertTrue(user.isPresent());
        assertTrue(await(() -> membershipService.isMember(user.get().id.toString(), "test-client")));
    }

    @Test
    void postRegister_withReturnUri_redirectsToSpa() {
        String username = "reg-spa-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterChallenge challenge = openRegister(null, "test-client", "http://localhost:5173/");

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", username)
            .formParam("password", "password123")
            .formParam("client_id", "test-client")
            .formParam("return_uri", "http://localhost:5173/")
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/register")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                    containsString("http://localhost:5173/"),
                    containsString("registered=1")));
    }

    @Test
    void postRegister_withOAuthState_redirectsToConsent() {
        String username = "reg-oauth-" + UUID.randomUUID().toString().substring(0, 8);
        String state = "register-oauth-state-" + UUID.randomUUID();
        awaitVoid(cacheService.deleteConsent("b0000000-0000-0000-0000-000000000001", "test-client"));
        awaitVoid(cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            "test-client", "http://localhost:8080/callback", List.of("openid"), state, null, null, null, null
        ), 600));

        RegisterChallenge challenge = openRegister(state, null, null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", username)
            .formParam("password", "password123")
            .formParam("state", state)
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/register")
        .then()
            .statusCode(303)
            .header("Location", containsString("/consent?state=" + state));

        var user = await(() -> userRepository.findByUsername(username));
        assertTrue(user.isPresent());
        assertTrue(await(() -> membershipService.isMember(user.get().id.toString(), "test-client")));
    }

    @Test
    void postRegister_duplicateUsername_showsError() {
        RegisterChallenge challenge = openRegister(null, "test-client", null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password123")
            .formParam("client_id", "test-client")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/register")
        .then()
            .statusCode(200)
            .body(containsString("Имя пользователя уже занято"));
    }

    @Test
    void postRegister_shortPassword_showsError() {
        RegisterChallenge challenge = openRegister(null, "test-client", null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "short-pass-user")
            .formParam("password", "short")
            .formParam("client_id", "test-client")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/register")
        .then()
            .statusCode(200)
            .body(containsString("Пароль должен быть не короче"));
    }

    @Test
    void postRegister_missingCsrf_returnsForbidden() {
        RegisterChallenge challenge = openRegister(null, "test-client", null);

        given()
            .contentType(ContentType.URLENC)
            .cookie("SESSIONID", challenge.sessionId())
            .formParam("username", "missing-csrf-user")
            .formParam("password", "password123")
            .formParam("client_id", "test-client")
        .when()
            .post("/register")
        .then()
            .statusCode(403)
            .body(containsString("Invalid CSRF token"));
    }

    @Test
    void getLogin_showsRegisterLink() {
        given()
            .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body(containsString("/register"));
    }

    private RegisterChallenge openRegister(String state, String clientId, String returnUri) {
        var request = given();
        if (state != null) {
            request = request.queryParam("state", state);
        }
        if (clientId != null) {
            request = request.queryParam("client_id", clientId);
        }
        if (returnUri != null) {
            request = request.queryParam("return_uri", returnUri);
        }

        Response response = request.when().get("/register");
        response.then().statusCode(200);

        String body = response.getBody().asString();
        Matcher matcher = CSRF_PATTERN.matcher(body);
        assertTrue(matcher.find(), "CSRF token missing in register form");
        String csrfToken = matcher.group(1);

        String sessionId = response.getCookie("SESSIONID");
        assertNotNull(sessionId, "SESSIONID cookie missing on GET /register");
        return new RegisterChallenge(sessionId, csrfToken);
    }

    private record RegisterChallenge(String sessionId, String csrfToken) {
    }
}
