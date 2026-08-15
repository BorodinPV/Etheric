package com.etheric.endpoint;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class RateLimitTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "etheric.rate-limit.enabled", "true",
                "etheric.rate-limit.window-seconds", "60",
                "etheric.rate-limit.authorize-max", "2",
                "etheric.rate-limit.login-max", "1000",
                "etheric.rate-limit.token-max", "1000",
                "etheric.rate-limit.consent-max", "1000"
        );
    }
}
