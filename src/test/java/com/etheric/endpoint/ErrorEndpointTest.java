package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ErrorEndpointTest {

    @Test
    void error_withParams_rendersErrorPage() {
        given()
            .queryParam("error", "server_error")
            .queryParam("description", "Something went wrong")
        .when()
            .get("/error")
        .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("server_error"))
            .body(containsString("Something went wrong"));
    }

    @Test
    void error_withoutParams_rendersErrorPage() {
        given()
        .when()
            .get("/error")
        .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("Произошла ошибка"));
    }
}
