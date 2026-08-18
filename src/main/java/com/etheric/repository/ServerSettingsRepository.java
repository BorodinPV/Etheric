package com.etheric.repository;

import com.etheric.entity.ServerSettings;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ServerSettingsRepository implements PanacheRepository<ServerSettings> {

    private static final int SETTINGS_ID = 1;

    @WithSession
    public Uni<ServerSettings> getSettings() {
        return find("id", SETTINGS_ID).firstResult();
    }

    @WithTransaction
    public Uni<Void> updateSettings(ServerSettings settings) {
        settings.id = SETTINGS_ID;
        return ServerSettings.getSession()
                .flatMap(session -> session.merge(settings))
                .replaceWithVoid();
    }
}
