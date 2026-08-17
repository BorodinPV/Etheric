package com.etheric.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigValidatorTest {

    private static final String EXTERNAL_DB = "postgresql://db.example.com:5432/etheric";
    private static final String EXTERNAL_REDIS = "redis://redis.example.com:6379";

    @Test
    void validateProductionConfig_defaultAdminKey_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        ProductionConfigValidator.DEFAULT_ADMIN_API_KEY, false, "",
                        EXTERNAL_DB, EXTERNAL_REDIS, "strong-password", true));

        assertTrue(error.getMessage().contains("ETHERIC_ADMIN_API_KEY"));
    }

    @Test
    void validateProductionConfig_corsEnabledWithBlankOrigins_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", true, "",
                        EXTERNAL_DB, EXTERNAL_REDIS, "strong-password", true));

        assertTrue(error.getMessage().contains("ETHERIC_CORS_ORIGINS"));
    }

    @Test
    void validateProductionConfig_corsEnabledWithWildcard_throws() {
        assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", true, "*",
                        EXTERNAL_DB, EXTERNAL_REDIS, "strong-password", true));
    }

    @Test
    void validateProductionConfig_localhostDbUrl_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, "",
                        "postgresql://localhost:5432/etheric", EXTERNAL_REDIS, "strong-password", true));

        assertTrue(error.getMessage().contains("ETHERIC_DB_REACTIVE_URL"));
    }

    @Test
    void validateProductionConfig_localhostRedisUrl_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, "",
                        EXTERNAL_DB, "redis://127.0.0.1:6379", "strong-password", true));

        assertTrue(error.getMessage().contains("ETHERIC_REDIS_URL"));
    }

    @Test
    void validateProductionConfig_defaultDevDbPassword_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, "",
                        EXTERNAL_DB, EXTERNAL_REDIS, ProductionConfigValidator.DEFAULT_DEV_DB_PASSWORD, true));

        assertTrue(error.getMessage().contains("ETHERIC_DB_PASSWORD"));
    }

    @Test
    void validateProductionConfig_infrastructureCheckCanBeDisabled() {
        assertDoesNotThrow(() ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, "",
                        "postgresql://localhost:5432/etheric", "redis://localhost:6379", "etheric", false));
    }

    @Test
    void validateProductionConfig_validConfig_passes() {
        assertDoesNotThrow(() ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", false, "",
                        EXTERNAL_DB, EXTERNAL_REDIS, "strong-password", true));
        assertDoesNotThrow(() ->
                ProductionConfigValidator.validateProductionConfig(
                        "prod-secret-key", true, "https://app.example.com",
                        EXTERNAL_DB, EXTERNAL_REDIS, "strong-password", true));
    }

    @Test
    void isUnsafeCorsOrigins_detectsBlankAndWildcard() {
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins(null));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("  "));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("*"));
        assertTrue(ProductionConfigValidator.isUnsafeCorsOrigins("https://a.com,*"));
        assertFalse(ProductionConfigValidator.isUnsafeCorsOrigins("https://app.example.com"));
    }

    @Test
    void pointsToLocalhost_detectsLocalHosts() {
        assertTrue(ProductionConfigValidator.pointsToLocalhost("postgresql://localhost:5432/etheric"));
        assertTrue(ProductionConfigValidator.pointsToLocalhost("redis://127.0.0.1:6379"));
        assertFalse(ProductionConfigValidator.pointsToLocalhost("postgresql://db.example.com:5432/etheric"));
        assertFalse(ProductionConfigValidator.pointsToLocalhost(null));
        assertFalse(ProductionConfigValidator.pointsToLocalhost(""));
    }
}
