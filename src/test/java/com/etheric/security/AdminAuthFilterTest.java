package com.etheric.security;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminAuthFilterTest {

    private static final String ADMIN_KEY = "test-admin-key";

    @Test
    void jsonApi_withoutApiKey_returnsUnauthorizedJson() {
        given()
        .when()
            .get("/admin/clients")
        .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void jsonApi_withValidApiKey_returnsOk() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/clients")
        .then()
            .statusCode(200)
            .body("client_id", hasItem("test-client"));
    }

    @Test
    void console_withoutSession_redirectsToLogin() {
        given()
            .redirects().follow(false)
        .when()
            .get("/admin/console/users")
        .then()
            .statusCode(303)
            .header("Location", allOf(
                    containsString("/admin/console/login"),
                    containsString("redirect_uri=")));
    }

    @Test
    void consoleLogin_isPublic() {
        given()
        .when()
            .get("/admin/console/login")
        .then()
            .statusCode(200)
            .body(containsString("Sign in"));
    }

    @Test
    void adminCss_isPublic() {
        given()
        .when()
            .get("/admin/admin-console.css")
        .then()
            .statusCode(200)
            .body(containsString("--sidebar-width"));
    }

    @Test
    void console_doesNotAcceptApiKeyInsteadOfCookie() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .redirects().follow(false)
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(303)
            .header("Location", containsString("/admin/console/login"));
    }

    @Test
    void jsonApi_stillRequiresApiKeyWhenCookiePresent() {
        given()
            .contentType(ContentType.JSON)
            .cookie("ADMIN_SESSION", "fake-session")
            .body(Map.of("client_name", "App", "redirect_uris", List.of("http://localhost/cb")))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(401);
    }
}
