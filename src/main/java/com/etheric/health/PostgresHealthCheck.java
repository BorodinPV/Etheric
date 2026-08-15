package com.etheric.health;

import com.etheric.entity.Client;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class PostgresHealthCheck implements AsyncHealthCheck {

    @Override
    public Uni<HealthCheckResponse> call() {
        return Panache.withSession(() -> Client.count())
                .map(count -> HealthCheckResponse.named("postgresql").up().build())
                .onFailure().recoverWithItem(error ->
                        HealthCheckResponse.named("postgresql").down().withData("error", error.getMessage()).build());
    }
}
