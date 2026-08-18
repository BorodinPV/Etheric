package com.etheric.repository;

import com.etheric.entity.UserClientMembership;
import com.etheric.entity.UserClientMembershipId;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserClientMembershipRepository implements PanacheRepository<UserClientMembership> {

    @WithSession
    public Uni<Boolean> isMember(UUID userId, String clientId) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().item(false);
        }
        return count("userId = ?1 and clientId = ?2", userId, clientId)
                .map(count -> count > 0);
    }

    @WithSession
    public Uni<List<String>> findClientIdsForUser(UUID userId) {
        return find("userId", userId).list()
                .map(rows -> rows.stream().map(row -> row.clientId).sorted().toList());
    }

    @WithSession
    public Uni<List<UUID>> findUserIdsForClient(String clientId) {
        return find("clientId", clientId).list()
                .map(rows -> rows.stream().map(row -> row.userId).toList());
    }

    @WithTransaction
    public Uni<Void> replaceUserClients(UUID userId, List<String> clientIds) {
        return delete("userId", userId).flatMap(ignored -> {
            if (clientIds == null || clientIds.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            OffsetDateTime now = OffsetDateTime.now();
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (String clientId : clientIds.stream().distinct().toList()) {
                if (clientId == null || clientId.isBlank()) {
                    continue;
                }
                UserClientMembership membership = new UserClientMembership(userId, clientId.trim(), now);
                chain = chain.flatMap(v -> persist(membership).replaceWithVoid());
            }
            return chain;
        });
    }

    @WithTransaction
    public Uni<Void> replaceClientUsers(String clientId, List<UUID> userIds) {
        return delete("clientId", clientId).flatMap(ignored -> {
            if (userIds == null || userIds.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            OffsetDateTime now = OffsetDateTime.now();
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (UUID userId : userIds.stream().distinct().toList()) {
                if (userId == null) {
                    continue;
                }
                UserClientMembership membership = new UserClientMembership(userId, clientId, now);
                chain = chain.flatMap(v -> persist(membership).replaceWithVoid());
            }
            return chain;
        });
    }

    @WithTransaction
    public Uni<Void> ensureMembership(UUID userId, String clientId) {
        return count("userId = ?1 and clientId = ?2", userId, clientId)
                .flatMap(existing -> {
                    if (existing > 0) {
                        return Uni.createFrom().voidItem();
                    }
                    return persist(new UserClientMembership(userId, clientId, OffsetDateTime.now()))
                            .replaceWithVoid();
                });
    }
}
