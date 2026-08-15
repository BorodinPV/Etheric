package com.etheric.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ValidProdStartupProfile.class)
class ProductionConfigValidatorProdStartupTest {

    @Test
    void prodStartupWithValidConfig_succeeds() {
        // Application started with prod profile and safe overrides.
    }
}
