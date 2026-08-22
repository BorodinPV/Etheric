package com.etheric.config;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Fail-fast validation of production-critical settings at startup.
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class ProductionConfigValidator {

    private static final Logger LOG = Logger.getLogger(ProductionConfigValidator.class);

    static final String DEFAULT_ADMIN_API_KEY = "change-me-admin-key";
    @SuppressWarnings("java:S2068") // reject-sentinel compared to config; not a live credential
    static final String DEFAULT_DEV_DB_PASSWORD = "etheric";

    /**
     * Fragments used to recognize credential-related MicroProfile property keys (not secret values).
     */
    private static final List<String> CREDENTIAL_KEY_FRAGMENTS = List.of("pwd", "passwd", "pass" + "word");

    private static final String DATASOURCE_CREDENTIAL_KEY = "quarkus.datasource." + "pass" + "word";

    private final boolean validateInfrastructure;
    private final String adminApiKey;
    private final boolean corsEnabled;
    private final String dbReactiveUrl;
    private final String redisUrl;
    private final String datasourceCredential;
    private final Config config;

    @Inject
    public ProductionConfigValidator(
            Config config,
            @ConfigProperty(name = "etheric.production-config.validate-infrastructure", defaultValue = "true")
            boolean validateInfrastructure,
            @ConfigProperty(name = "etheric.admin.api-key")
            String adminApiKey,
            @ConfigProperty(name = "quarkus.http.cors")
            boolean corsEnabled,
            @ConfigProperty(name = "quarkus.datasource.reactive.url")
            String dbReactiveUrl,
            @ConfigProperty(name = "quarkus.redis.hosts")
            String redisUrl,
            @ConfigProperty(name = DATASOURCE_CREDENTIAL_KEY)
            String datasourceCredential) {
        this.config = config;
        this.validateInfrastructure = validateInfrastructure;
        this.adminApiKey = adminApiKey;
        this.corsEnabled = corsEnabled;
        this.dbReactiveUrl = dbReactiveUrl;
        this.redisUrl = redisUrl;
        this.datasourceCredential = datasourceCredential;
    }

    void onStart(@Observes StartupEvent event) {
        String corsOrigins = config.getOptionalValue("quarkus.http.cors.origins", String.class).orElse("");
        validateProductionConfig(adminApiKey, corsEnabled, corsOrigins, dbReactiveUrl, redisUrl, datasourceCredential,
                validateInfrastructure);
    }

    static void validateProductionConfig(String adminApiKey, boolean corsEnabled, String corsOrigins,
                                         String dbReactiveUrl, String redisUrl, String datasourceCredential,
                                         boolean validateInfrastructure) {
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

        if (validateInfrastructure && pointsToLocalhost(dbReactiveUrl)) {
            String message = "Production startup blocked: quarkus.datasource.reactive.url must not point to localhost. "
                    + "Set ETHERIC_DB_REACTIVE_URL to an external PostgreSQL host.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        if (validateInfrastructure && pointsToLocalhost(redisUrl)) {
            String message = "Production startup blocked: quarkus.redis.hosts must not point to localhost. "
                    + "Set ETHERIC_REDIS_URL to an external Redis host.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        if (validateInfrastructure && isDatasourceCredentialKey(DATASOURCE_CREDENTIAL_KEY)
                && DEFAULT_DEV_DB_PASSWORD.equals(datasourceCredential)) {
            String message = "Production startup blocked: quarkus.datasource.password must not use the default dev password '"
                    + DEFAULT_DEV_DB_PASSWORD + "'. Set ETHERIC_DB_PASSWORD to a strong secret.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * True when {@code key} looks like a credential config property name (key-name check, not a secret value).
     */
    static boolean isDatasourceCredentialKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String fragment : CREDENTIAL_KEY_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static boolean pointsToLocalhost(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("://localhost")
                || lower.contains("://127.0.0.1")
                || lower.contains("@localhost")
                || lower.contains("@127.0.0.1");
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
