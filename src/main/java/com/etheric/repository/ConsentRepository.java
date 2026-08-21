package com.etheric.repository;

import com.etheric.entity.UserConsent;
import com.etheric.util.ScopeUtil;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ConsentRepository implements PanacheRepository<UserConsent> {

    @WithSession
    public Uni<UserConsent> find(UUID userId, String clientId) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return find("userId = ?1 and clientId = ?2", userId, clientId).firstResult();
    }

    @WithTransaction
    public Uni<UserConsent> upsertMergeScopes(UUID userId, String clientId, List<String> scopes) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<String> incoming = scopes != null ? scopes : List.of();
        return find("userId = ?1 and clientId = ?2", userId, clientId).firstResult()
                .flatMap(existing -> {
                    if (existing != null) {
                        existing.scopes = ScopeUtil.mergeScopes(existing.scopes, incoming);
                        existing.updatedAt = now;
                        return persist(existing);
                    }
                    UserConsent consent = new UserConsent(userId, clientId, incoming, now, now);
                    return persist(consent);
                });
    }

    @WithTransaction
    public Uni<Void> delete(UUID userId, String clientId) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        return delete("userId = ?1 and clientId = ?2", userId, clientId).replaceWithVoid();
    }
}
