package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AdminConsoleEndpointTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
    private static final Pattern ADMIN_SESSION_PATTERN = Pattern.compile(
            "ADMIN_SESSION=([^;]+)");

    @Test
    void getLogin_rendersSignInPage() {
        given()
            .when()
            .get("/admin/console/login")
        .then()
            .statusCode(200)
            .body(containsString("Sign in to your account"))
            .header("Set-Cookie", containsString("ADMIN_SESSION="));
    }

    @Test
    void protectedRoute_withoutSession_redirectsToLogin() {
        given()
            .redirects().follow(false)
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(303)
            .header("Location", containsString("/admin/console/login"));
    }

    @Test
    void login_adminUser_accessesClientsList() {
        AdminLoginSession session = loginAsAdmin();

        given()
            .cookie("ADMIN_SESSION", session.sessionId())
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(200)
            .body(containsString("Clients"))
            .body(containsString("test-client"))
            .body(containsString("Create client"));
    }

    @Test
    void login_nonAdminUser_showsAccessDenied() {
        AdminLoginSession challenge = openLoginForm();

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", challenge.sessionId())
            .formParam("username", "user")
            .formParam("password", "password")
            .formParam("csrf_token", challenge.csrfToken())
        .when()
            .post("/admin/console/login")
        .then()
            .statusCode(200)
            .body(containsString("Access denied"));
    }

    @Test
    void logout_clearsSession() {
        AdminLoginSession session = loginAsAdmin();

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", session.sessionId())
            .formParam("csrf_token", session.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/admin/console/logout")
        .then()
            .statusCode(303)
            .header("Location", containsString("/admin/console/login"))
            .header("Set-Cookie", containsString("ADMIN_SESSION="));

        given()
            .cookie("ADMIN_SESSION", session.sessionId())
            .redirects().follow(false)
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(303);
    }

    @Test
    void index_redirectsToClients() {
        AdminLoginSession session = loginAsAdmin();

        given()
            .cookie("ADMIN_SESSION", session.sessionId())
            .redirects().follow(false)
        .when()
            .get("/admin/console")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/admin/console/clients"));
    }

    private AdminLoginSession loginAsAdmin() {
        AdminLoginSession challenge = openLoginForm();

        Response response = given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", challenge.sessionId())
            .formParam("username", "admin")
            .formParam("password", "admin")
            .formParam("csrf_token", challenge.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/admin/console/login");

        response.then().statusCode(303);

        String newSessionId = extractCookie(response.getHeader("Set-Cookie"));
        String csrf = fetchCsrf(newSessionId);
        return new AdminLoginSession(newSessionId, csrf);
    }

    private String fetchCsrf(String sessionId) {
        String body = given()
            .cookie("ADMIN_SESSION", sessionId)
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        return extractCsrf(body);
    }

    private AdminLoginSession openLoginForm() {
        Response response = given().get("/admin/console/login");
        response.then().statusCode(200);
        String sessionId = extractCookie(response.getHeader("Set-Cookie"));
        String csrf = extractCsrf(response.body().asString());
        return new AdminLoginSession(sessionId, csrf);
    }

    private static String extractCookie(String setCookie) {
        Matcher matcher = ADMIN_SESSION_PATTERN.matcher(setCookie);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String extractCsrf(String body) {
        Matcher matcher = CSRF_PATTERN.matcher(body);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private record AdminLoginSession(String sessionId, String csrfToken) {
    }
}
