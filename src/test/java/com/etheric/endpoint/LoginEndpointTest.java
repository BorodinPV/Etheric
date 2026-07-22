package com.etheric.endpoint;

import com.etheric.model.AuthorizationRequestState;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LoginEndpointTest {

    @Inject
    CacheService cacheService;

    @Test
    void getLogin_rendersForm() {
        given()
            .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body(containsString("Вход в систему"))
            .body(containsString("form"))
            .body(containsString("username"))
            .body(containsString("password"));
    }

    @Test
    void getLogin_withState_includesHiddenField() {
        given()
            .queryParam("state", "test-state")
        .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body(containsString("test-state"));
    }

    @Test
    void postLogin_validCredentials_setsSessionCookie() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "password")
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/"))
            .header("Set-Cookie", containsString("SESSIONID="));
    }

    @Test
    void postLogin_validCredentials_withState_redirectsToConsent() {
        // First create an authorization request state
        String state = "login-test-state";
        cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            "test-client", "http://localhost:8080/callback", List.of("openid"), state, null
        ), 600);

        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("state", state)
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303)
            .header("Location", containsString("/consent?state=" + state));
    }

    @Test
    void postLogin_invalidCredentials_showsError() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "wrongpassword")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"));
    }

    @Test
    void postLogin_nonExistingUser_showsError() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "nonexistent")
            .formParam("password", "password")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"));
    }

    @Test
    void postLogin_emptyUsername_showsError() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "")
            .formParam("password", "password")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("Неверное имя пользователя или пароль"));
    }

    @Test
    void postLogin_withState_preservesStateOnFailure() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "wrongpassword")
            .formParam("state", "error-state")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body(containsString("error-state"));
    }

    @Test
    void postLogin_validCredentials_updatesRequestState() {
        String state = "update-test-state";
        cacheService.saveAuthorizationRequestState(state, new AuthorizationRequestState(
            "test-client", "http://localhost:8080/callback", List.of("openid"), state, null
        ), 600);

        given()
            .contentType(ContentType.URLENC)
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("state", state)
            .redirects().follow(false)
        .when()
            .post("/login")
        .then()
            .statusCode(303);

        var updatedState = cacheService.getAuthorizationRequestState(state);
        if (updatedState != null) {
            assert updatedState.getUserId() != null;
        }
    }
}
