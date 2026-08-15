package com.etheric.health;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class RedisHealthCheck implements AsyncHealthCheck {

    @Inject
    ReactiveRedisDataSource redis;

    @Override
    public Uni<HealthCheckResponse> call() {
        return redis.value(String.class).get("health:ping")
                .flatMap(v -> redis.value(String.class).set("health:ping", "ok"))
                .map(v -> HealthCheckResponse.named("redis").up().build())
                .onFailure().recoverWithItem(error ->
                        HealthCheckResponse.named("redis").down().withData("error", error.getMessage()).build());
    }
}
