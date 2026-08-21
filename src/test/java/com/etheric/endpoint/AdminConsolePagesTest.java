package com.etheric.endpoint;

import com.etheric.model.AdminSessionData;
import com.etheric.repository.UserRepository;
import com.etheric.service.CacheService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.etheric.testsupport.TestSupport.await;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AdminConsolePagesTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "name=\"csrf_token\"\\s+value=\"([^\"]+)\"");
    private static final Pattern ADMIN_SESSION_PATTERN = Pattern.compile(
            "ADMIN_SESSION=([^;]+)");

    @Inject
    CacheService cacheService;

    @Inject
    UserRepository userRepository;

    @Test
    void loginPage_setsAnonymousSessionCookie_andStoresInRedis() {
        Response response = given().get("/admin/console/login");
        response.then().statusCode(200).header("Set-Cookie", containsString("ADMIN_SESSION="));

        String sessionId = extractCookie(response.getHeader("Set-Cookie"));
        assertFalse(sessionId.isBlank());

        SetCookieAttributes attrs = parseSetCookie(response.getHeader("Set-Cookie"));
        assertTrue(attrs.cookieHeader.contains("HttpOnly"));
        assertTrue(attrs.cookieHeader.contains("Path=/admin"));
        assertTrue(attrs.cookieHeader.contains("SameSite=Lax"));

        AdminSessionData session = await(() -> cacheService.getAdminSession(sessionId));
        assertNotNull(session);
        assertNull(session.getUserId(), "anonymous login challenge must not have userId");
        assertNotNull(session.getCsrfToken());
    }

    @Test
    void authenticatedSession_allPagesReturn200() {
        UUID adminUserId = await(() -> userRepository.findByUsername("admin")).orElseThrow().id;
        AuthenticatedSession auth = loginAsAdmin();

        AdminSessionData redisSession = await(() -> cacheService.getAdminSession(auth.sessionId()));
        assertNotNull(redisSession);
        assertEquals(adminUserId, redisSession.getUserId());
        assertEquals("admin", redisSession.getUsername());
        assertNotNull(redisSession.getCsrfToken());

        String cookie = auth.sessionId();

        given().cookie("ADMIN_SESSION", cookie).redirects().follow(false)
                .get("/admin/console").then().statusCode(303)
                .header("Location", endsWith("/admin/console/clients"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/clients").then()
                .statusCode(200).body(containsString("Clients"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/clients?search=test").then()
                .statusCode(200).body(containsString("Clients"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/clients/create").then()
                .statusCode(200).body(containsString("Create client"));

        given().cookie("ADMIN_SESSION", cookie).redirects().follow(false)
                .get("/admin/console/clients/test-client").then()
                .statusCode(303)
                .header("Location", endsWith("/admin/console/clients/test-client/settings"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/clients/test-client/settings").then()
                .statusCode(200)
                .body(containsString("Settings"))
                .body(containsString("Token &amp; session settings"))
                .body(containsString("SESSIONID"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/clients/test-client/credentials").then()
                .statusCode(200).body(containsString("Credentials"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/clients/test-client/users").then()
                .statusCode(200).body(containsString("Users"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/users").then()
                .statusCode(200).body(containsString("Users"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/users?search=admin").then()
                .statusCode(200).body(containsString("admin"));

        given().cookie("ADMIN_SESSION", cookie).get("/admin/console/users/create").then()
                .statusCode(200).body(containsString("Create user"));

        given().cookie("ADMIN_SESSION", cookie).redirects().follow(false)
                .get("/admin/console/users/" + adminUserId).then()
                .statusCode(303)
                .header("Location", endsWith("/admin/console/users/" + adminUserId + "/details"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/users/" + adminUserId + "/details").then()
                .statusCode(200).body(containsString("Details"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/users/" + adminUserId + "/credentials").then()
                .statusCode(200).body(containsString("Credentials"));

        given().cookie("ADMIN_SESSION", cookie)
                .get("/admin/console/users/" + adminUserId + "/clients").then()
                .statusCode(200).body(containsString("Clients"));
    }

    @Test
    void logout_removesSessionFromRedis() {
        AuthenticatedSession auth = loginAsAdmin();
        assertNotNull(await(() -> cacheService.getAdminSession(auth.sessionId())));

        given()
            .contentType(ContentType.URLENC)
            .cookie("ADMIN_SESSION", auth.sessionId())
            .formParam("csrf_token", auth.csrfToken())
            .redirects().follow(false)
        .when()
            .post("/admin/console/logout")
        .then()
            .statusCode(303)
            .header("Set-Cookie", containsString("ADMIN_SESSION="));

        assertNull(await(() -> cacheService.getAdminSession(auth.sessionId())));

        given()
            .cookie("ADMIN_SESSION", auth.sessionId())
            .redirects().follow(false)
        .when()
            .get("/admin/console/clients")
        .then()
            .statusCode(303)
            .header("Location", containsString("/admin/console/login"));
    }

    @Test
    void staticCss_accessibleWithoutSession() {
        given().get("/admin/admin-console.css").then().statusCode(200);
    }

    @Test
    void russianLocale_rendersTranslatedUi() {
        AuthenticatedSession auth = loginAsAdmin();

        given()
                .cookie("ADMIN_SESSION", auth.sessionId())
                .cookie("ADMIN_LOCALE", "ru")
                .redirects().follow(false)
                .get("/admin/console/locale?lang=ru")
                .then()
                .statusCode(303)
                .header("Set-Cookie", containsString("ADMIN_LOCALE=ru"));

        given()
                .cookie("ADMIN_SESSION", auth.sessionId())
                .cookie("ADMIN_LOCALE", "ru")
                .get("/admin/console/clients")
                .then()
                .statusCode(200)
                .body(containsString("Клиенты"))
                .body(containsString("Создать клиента"));

        given()
                .cookie("ADMIN_LOCALE", "ru")
                .get("/admin/console/login")
                .then()
                .statusCode(200)
                .body(containsString("Вход в аккаунт"))
                .body(containsString("Русский"));
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
        assertNotEquals(challengeId, sessionId, "authenticated session must replace anonymous session id");

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
        Matcher matcher = ADMIN_SESSION_PATTERN.matcher(setCookie);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String extractCsrf(String body) {
        Matcher matcher = CSRF_PATTERN.matcher(body);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static SetCookieAttributes parseSetCookie(String setCookie) {
        return new SetCookieAttributes(setCookie);
    }

    private record AuthenticatedSession(String sessionId, String csrfToken) {
    }

    private record SetCookieAttributes(String cookieHeader) {
    }
}
