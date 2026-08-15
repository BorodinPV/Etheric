package com.etheric.config;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

import static java.util.Map.entry;

public class ValidProdStartupProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "prod";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                entry("etheric.admin.api-key", "prod-test-secret-key"),
                entry("quarkus.http.cors", "false"),
                entry("quarkus.datasource.devservices.enabled", "false"),
                entry("quarkus.redis.devservices.enabled", "false"),
                entry("quarkus.datasource.username", "etheric"),
                entry("quarkus.datasource.password", "etheric"),
                entry("quarkus.datasource.reactive.url", "postgresql://localhost:5432/etheric"),
                entry("quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:5432/etheric"),
                entry("quarkus.redis.hosts", "redis://localhost:6379"),
                entry("quarkus.hibernate-orm.database.generation", "none"),
                entry("quarkus.flyway.migrate-at-start", "true"),
                entry("quarkus.flyway.locations", "db/migration"),
                entry("etheric.rate-limit.enabled", "false")
        );
    }
}
