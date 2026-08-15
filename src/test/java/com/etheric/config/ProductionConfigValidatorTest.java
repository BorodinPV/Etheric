package com.etheric.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigValidatorTest {

    @Test
    void validateProductionConfig_defaultAdminKey_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        ProductionConfigValidator.DEFAULT_ADMIN_API_KEY, false, ""));

        assertTrue(error.getMessage().contains("ETHERIC_ADMIN_API_KEY"));
    }

    @Test
    void validateProductionConfig_corsEnabledWithBlankOrigins_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig("prod-secret-key", true, ""));

        assertTrue(error.getMessage().contains("ETHERIC_CORS_ORIGINS"));
    }

    @Test
    void validateProductionConfig_corsEnabledWithWildcard_throws() {
        assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig("prod-secret-key", true, "*"));
    }

    @Test
    void validateProductionConfig_validConfig_passes() {
        assertDoesNotThrow(() ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, ""));
        assertDoesNotThrow(() ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", true, "https://app.example.com"));
    }

    @Test
    void isUnsafeCorsOrigins_detectsBlankAndWildcard() {
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins(null));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("  "));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("*"));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("https://a.com,*"));
        assertFalse(ProductionConfigValidator.isUnsafeCorsOrigins("https://app.example.com"));
    }
}
