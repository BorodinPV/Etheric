package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminClientsEndpointTest {

    private static final String ADMIN_KEY = "test-admin-key";

    @Test
    void register_withoutApiKey_returnsUnauthorized() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "client_name", "App",
                "redirect_uris", List.of("http://localhost:8080/callback")
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(401)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void register_withInvalidApiKey_returnsUnauthorized() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", "wrong-key")
            .body(Map.of(
                "client_name", "App",
                "redirect_uris", List.of("http://localhost:8080/callback")
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(401);
    }

    @Test
    void register_missingName_returnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("redirect_uris", List.of("http://localhost:8080/callback")))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void register_validRequest_returnsCreatedWithSecret() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                "client_name", "My App",
                "redirect_uris", List.of("http://localhost:3000/cb"),
                "client_description", "Demo client"
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(201)
            .body("client_id", startsWith("client-"))
            .body("client_secret", notNullValue())
            .body("client_name", equalTo("My App"))
            .body("redirect_uris", hasItem("http://localhost:3000/cb"))
            .body("scopes", hasItems("openid", "profile", "email"))
            .body("grant_types", hasItems("authorization_code", "refresh_token"))
            .body("enabled", equalTo(true))
            .body("client_description", equalTo("Demo client"));
    }

    @Test
    void register_customClientId_usesProvidedId() {
        String clientId = "custom-client-" + System.nanoTime();

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                "client_id", clientId,
                "client_name", "Custom ID App",
                "redirect_uris", List.of("https://example.com/oauth/callback"),
                "scopes", List.of("openid"),
                "grant_types", List.of("authorization_code")
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(201)
            .body("client_id", equalTo(clientId))
            .body("scopes", equalTo(List.of("openid")))
            .body("grant_types", equalTo(List.of("authorization_code")));
    }

    @Test
    void register_duplicateClientId_returnsConflict() {
        String clientId = "dup-client-" + System.nanoTime();
        Map<String, Object> body = Map.of(
            "client_id", clientId,
            "client_name", "First",
            "redirect_uris", List.of("http://localhost/cb")
        );

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(body)
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(body)
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(409)
            .body("error", equalTo("conflict"));
    }

    @Test
    void list_returnsClients() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/clients")
        .then()
            .statusCode(200)
            .body("client_id", hasItem("test-client"))
            .body("find { it.client_id == 'test-client' }.client_secret", nullValue());
    }

    @Test
    void get_existingClient_returnsWithoutSecret() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/clients/test-client")
        .then()
            .statusCode(200)
            .body("client_id", equalTo("test-client"))
            .body("client_name", equalTo("Etheric Dev Application"))
            .body("client_secret", nullValue());
    }

    @Test
    void get_missingClient_returnsNotFound() {
        given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .get("/admin/clients/does-not-exist")
        .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    void update_changesClientSettings() {
        String clientId = "update-client-" + System.nanoTime();
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                "client_id", clientId,
                "client_name", "Before Update",
                "redirect_uris", List.of("http://localhost:8080/callback")
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                "client_name", "After Update",
                "redirect_uris", List.of("http://localhost:8080/callback", "http://localhost:3000/cb"),
                "scopes", List.of("openid", "profile"),
                "grant_types", List.of("authorization_code"),
                "enabled", false,
                "client_description", "Updated description"
            ))
        .when()
            .put("/admin/clients/" + clientId)
        .then()
            .statusCode(200)
            .body("client_name", equalTo("After Update"))
            .body("redirect_uris", hasItems("http://localhost:8080/callback", "http://localhost:3000/cb"))
            .body("scopes", equalTo(List.of("openid", "profile")))
            .body("grant_types", equalTo(List.of("authorization_code")))
            .body("enabled", equalTo(false))
            .body("client_description", equalTo("Updated description"));
    }

    @Test
    void update_unknownClient_returnsNotFound() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of("client_name", "Ghost"))
        .when()
            .put("/admin/clients/does-not-exist")
        .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    void regenerateSecret_returnsNewSecret() {
        String clientId = "secret-client-" + System.nanoTime();
        String originalSecret = given()
            .contentType(ContentType.JSON)
            .header("X-Admin-Api-Key", ADMIN_KEY)
            .body(Map.of(
                "client_id", clientId,
                "client_name", "Secret Test",
                "redirect_uris", List.of("http://localhost:8080/callback")
            ))
        .when()
            .post("/admin/clients")
        .then()
            .statusCode(201)
            .extract().path("client_secret");

        String newSecret = given()
            .header("X-Admin-Api-Key", ADMIN_KEY)
        .when()
            .put("/admin/clients/" + clientId + "/secret")
        .then()
            .statusCode(200)
            .body("client_id", equalTo(clientId))
            .body("client_secret", notNullValue())
            .extract().path("client_secret");

        org.junit.jupiter.api.Assertions.assertNotEquals(originalSecret, newSecret);
    }
}
