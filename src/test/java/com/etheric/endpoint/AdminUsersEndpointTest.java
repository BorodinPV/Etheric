package com.etheric.endpoint;

import com.etheric.repository.UserRepository;
import com.etheric.service.PasswordService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AdminUsersEndpointTest {

    private static final String ADMIN_KEY = "test-admin-key";

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @Test
    void create_withoutApiKey_returnsUnauthorized() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "new-user", "password", "secret123"))
        .when()
            .post("/admin/users")
        .then()
            .statusCode(401)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void create_missingPassword_returnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("username", "no-password-user"))
        .when()
            .post("/admin/users")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void create_validRequest_returnsCreated() {
        String username = "admin-created-" + UUID.randomUUID().toString().substring(0, 8);

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                    "username", username,
                    "password", "secret123",
                    "email", "created@example.com",
                    "roles", List.of("user", "admin")
            ))
        .when()
            .post("/admin/users")
        .then()
            .statusCode(201)
            .body("username", equalTo(username))
            .body("email", equalTo("created@example.com"))
            .body("roles", hasItems("user", "admin"))
            .body("enabled", equalTo(true))
            .body("id", notNullValue());
    }

    @Test
    void list_returnsUsers() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/users")
        .then()
            .statusCode(200)
            .body("username", hasItem("user"));
    }

    @Test
    void get_existingUser_returnsUser() {
        UUID userId = await(() -> userRepository.findByUsername("user")).orElseThrow().id;

        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/users/" + userId)
        .then()
            .statusCode(200)
            .body("username", equalTo("user"));
    }

    @Test
    void get_unknownUser_returnsNotFound() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/users/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    void update_changesEmailAndRoles() {
        UUID userId = await(() -> userRepository.findByUsername("user")).orElseThrow().id;
        String originalEmail = await(() -> userRepository.findUserById(userId)).orElseThrow().email;

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("email", "patched@example.com", "roles", List.of("user")))
        .when()
            .put("/admin/users/" + userId)
        .then()
            .statusCode(200)
            .body("email", equalTo("patched@example.com"));

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("email", originalEmail != null ? originalEmail : "user@example.com"))
        .when()
            .put("/admin/users/" + userId)
        .then()
            .statusCode(200);
    }

    @Test
    void changePassword_allowsLoginWithNewPassword() {
        String username = "pwd-change-" + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = createUser(username, "oldpass12");

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("new_password", "newpass123"))
        .when()
            .put("/admin/users/" + userId + "/password")
        .then()
            .statusCode(204);

        assertTrue(await(() -> userRepository.authenticate(username, "newpass123")).isPresent());
        assertTrue(await(() -> userRepository.authenticate(username, "oldpass12")).isEmpty());
    }

    private UUID createUser(String username, String password) {
        return UUID.fromString(
                given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Api-Key", ADMIN_KEY)
                    .body(Map.of("username", username, "password", password))
                .when()
                    .post("/admin/users")
                .then()
                    .statusCode(201)
                    .extract()
                    .path("id"));
    }
}
