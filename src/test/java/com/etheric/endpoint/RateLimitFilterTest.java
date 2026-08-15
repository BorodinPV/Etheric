package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(RateLimitTestProfile.class)
class RateLimitFilterTest {

    private static final String AUTHORIZE_URL =
            "/authorize?response_type=code&client_id=test-client"
                    + "&redirect_uri=http://localhost:8080/callback&state=rate-limit-test";
    @Test
    void authorize_exceedsRateLimit_returns429() {
        // Unique IP avoids Redis bucket leakage between repeated test runs.
        String testIp = "203.0.113." + UUID.randomUUID().toString().substring(0, 2);

        given().header("X-Forwarded-For", testIp).redirects().follow(false).when().get(AUTHORIZE_URL).then().statusCode(303);
        given().header("X-Forwarded-For", testIp).redirects().follow(false).when().get(AUTHORIZE_URL).then().statusCode(303);

        given()
            .header("X-Forwarded-For", testIp)
            .redirects().follow(false)
        .when()
            .get(AUTHORIZE_URL)
        .then()
            .statusCode(429)
            .body("error", equalTo("temporarily_unavailable"));
    }
}
