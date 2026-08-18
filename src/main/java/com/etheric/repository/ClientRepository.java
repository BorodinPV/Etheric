package com.etheric.repository;

import com.etheric.entity.Client;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ClientRepository implements PanacheRepository<Client> {

    @WithSession
    @CacheResult(cacheName = "clients")
    public Uni<Optional<Client>> findByClientId(String clientId) {
        return find("clientId", clientId).firstResult().map(Optional::ofNullable);
    }

    @WithSession
    public Uni<List<String>> getRedirectUris(String clientId) {
        return findByClientId(clientId)
                .map(opt -> opt.map(c -> c.redirectUris).orElse(Collections.emptyList()));
    }

    @WithSession
    public Uni<List<String>> getScopes(String clientId) {
        return findByClientId(clientId)
                .map(opt -> opt.map(c -> c.scopes).orElse(Collections.emptyList()));
    }

    @WithSession
    public Uni<List<String>> getGrantTypes(String clientId) {
        return findByClientId(clientId)
                .map(opt -> opt.map(c -> c.grantTypes).orElse(Collections.emptyList()));
    }

    @WithSession
    public Uni<Boolean> isRedirectUriValid(String clientId, String redirectUri) {
        return getRedirectUris(clientId).map(uris -> uris.contains(redirectUri));
    }

    @WithSession
    public Uni<Boolean> isScopeValid(String clientId, List<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return Uni.createFrom().item(true);
        }
        return getScopes(clientId).map(allowed -> allowed.containsAll(requestedScopes));
    }

    @WithSession
    public Uni<Boolean> isGrantTypeSupported(String clientId, String grantType) {
        return getGrantTypes(clientId).map(types -> types.contains(grantType));
    }

    @WithSession
    public Uni<Boolean> isRegisteredRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Uni.createFrom().item(false);
        }
        return find("enabled", true).list()
                .map(clients -> clients.stream()
                        .flatMap(client -> client.redirectUris.stream())
                        .anyMatch(registered -> registered.equals(redirectUri)));
    }

    @WithTransaction
    @CacheInvalidateAll(cacheName = "clients")
    public Uni<Client> persistClient(Client client) {
        return persist(client).replaceWith(client);
    }

    @WithTransaction
    @CacheInvalidateAll(cacheName = "clients")
    public Uni<Void> updateClient(Client client) {
        return Client.getSession()
                .flatMap(session -> session.merge(client))
                .replaceWithVoid();
    }

    @WithSession
    public Uni<List<Client>> findAllClients() {
        return listAll();
    }

    @WithTransaction
    @CacheInvalidateAll(cacheName = "clients")
    public Uni<Long> deleteAllExceptClientId(String clientId) {
        return delete("clientId <> ?1", clientId);
    }
}
