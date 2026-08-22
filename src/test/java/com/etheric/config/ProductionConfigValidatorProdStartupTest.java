package com.etheric.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ValidProdStartupProfile.class)
class ProductionConfigValidatorProdStartupTest {

    @Inject
    Config config;

    @Test
    void prodStartupWithValidConfig_succeeds() {
        assertNotNull(config);
        assertEquals("prod-test-secret-key", config.getValue("etheric.admin.api-key", String.class));
    }
}
