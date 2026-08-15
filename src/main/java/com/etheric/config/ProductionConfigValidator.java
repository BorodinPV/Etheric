package com.etheric.config;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Fail-fast validation of production-critical settings at startup.
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class ProductionConfigValidator {

    private static final Logger LOG = Logger.getLogger(ProductionConfigValidator.class);

    static final String DEFAULT_ADMIN_API_KEY = "change-me-admin-key";

    @ConfigProperty(name = "etheric.admin.api-key")
    String adminApiKey;

    @ConfigProperty(name = "quarkus.http.cors")
    boolean corsEnabled;

    @Inject
    Config config;

    void onStart(@Observes StartupEvent event) {
        String corsOrigins = config.getOptionalValue("quarkus.http.cors.origins", String.class).orElse("");
        validateProductionConfig(adminApiKey, corsEnabled, corsOrigins);
    }

    static void validateProductionConfig(String adminApiKey, boolean corsEnabled, String corsOrigins) {
        if (DEFAULT_ADMIN_API_KEY.equals(adminApiKey)) {
            String message = "Production startup blocked: etheric.admin.api-key must not use the default value '"
                    + DEFAULT_ADMIN_API_KEY + "'. Set ETHERIC_ADMIN_API_KEY to a strong secret.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        if (corsEnabled && isUnsafeCorsOrigins(corsOrigins)) {
            String message = "Production startup blocked: quarkus.http.cors is enabled but origins are blank or wildcard (*). "
                    + "Set ETHERIC_CORS_ORIGINS to explicit origins (comma-separated), "
                    + "or disable CORS with ETHERIC_CORS_ENABLED=false.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }
    }

    static boolean isUnsafeCorsOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            return true;
        }
        for (String origin : origins.split(",")) {
            if ("*".equals(origin.trim())) {
                return true;
            }
        }
        return false;
    }
}
