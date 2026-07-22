package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class JwksEndpointTest {

    @Test
    void jwks_returnsValidJson() {
        given()
            .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("keys", notNullValue())
            .body("keys.size()", equalTo(1));
    }

    @Test
    void jwks_keyHasRequiredFields() {
        given()
            .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .body("keys[0].kty", equalTo("RSA"))
            .body("keys[0].use", equalTo("sig"))
            .body("keys[0].alg", equalTo("RS256"))
            .body("keys[0].kid", notNullValue())
            .body("keys[0].n", notNullValue())
            .body("keys[0].e", notNullValue());
    }

    @Test
    void jwks_keyIdIsNonEmpty() {
        given()
            .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .body("keys[0].kid", not(emptyOrNullString()));
    }

    @Test
    void jwks_modulusIsNonEmpty() {
        given()
            .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .body("keys[0].n", not(emptyOrNullString()));
    }

    @Test
    void jwks_exponentIsNonEmpty() {
        given()
            .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .body("keys[0].e", not(emptyOrNullString()));
    }
}
