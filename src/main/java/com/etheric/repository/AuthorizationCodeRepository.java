package com.etheric.repository;

import com.etheric.entity.AuthorizationCodeEntity;
import com.etheric.model.AuthorizationCodeData;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
public class AuthorizationCodeRepository implements PanacheRepository<AuthorizationCodeEntity> {

    @WithTransaction
    public Uni<Void> saveBackup(String code, AuthorizationCodeData data, Instant expiresAt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AuthorizationCodeEntity entity = new AuthorizationCodeEntity(
                code, data.getClientId(), data.getUserId(), data.getRedirectUri(),
                data.getScopes(), now, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return persist(entity).replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> markUsed(String code) {
        return find("code", code).firstResult().flatMap(entity -> {
            if (entity == null) {
                return Uni.createFrom().voidItem();
            }
            entity.usedAt = OffsetDateTime.now(ZoneOffset.UTC);
            return persist(entity).replaceWithVoid();
        });
    }
}
