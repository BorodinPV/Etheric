package com.etheric.repository;

import com.etheric.entity.Client;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ClientRepository {

    private final Map<String, Client> clientsByClientId = new ConcurrentHashMap<>();

    public ClientRepository() {
        initTestData();
    }

    public Optional<Client> findByClientId(String clientId) {
        return Optional.ofNullable(clientsByClientId.get(clientId));
    }

    public boolean isValidClient(String clientId, String clientSecretHash) {
        return clientsByClientId.values().stream()
                .filter(c -> c.getClientId().equals(clientId))
                .filter(Client::isEnabled)
                .anyMatch(c -> c.getClientSecretHash().equals(clientSecretHash));
    }

    public List<String> getRedirectUris(String clientId) {
        return findByClientId(clientId)
                .map(Client::getRedirectUris)
                .orElse(Collections.emptyList());
    }

    public List<String> getScopes(String clientId) {
        return findByClientId(clientId)
                .map(Client::getScopes)
                .orElse(Collections.emptyList());
    }

    public List<String> getGrantTypes(String clientId) {
        return findByClientId(clientId)
                .map(Client::getGrantTypes)
                .orElse(Collections.emptyList());
    }

    public boolean isRedirectUriValid(String clientId, String redirectUri) {
        return getRedirectUris(clientId).contains(redirectUri);
    }

    public boolean isScopeValid(String clientId, List<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return true;
        }
        List<String> allowedScopes = getScopes(clientId);
        return allowedScopes.containsAll(requestedScopes);
    }

    public boolean isGrantTypeSupported(String clientId, String grantType) {
        return getGrantTypes(clientId).contains(grantType);
    }

    private void initTestData() {
        Client testClient = new Client(
                UUID.randomUUID(),
                "test-client",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Test Application",
                List.of("http://localhost:8080/callback", "http://localhost:3000/callback"),
                List.of("openid", "profile", "email"),
                List.of("authorization_code", "refresh_token"),
                true,
                LocalDateTime.now(),
                null,
                "A test OAuth client application"
        );
        clientsByClientId.put(testClient.getClientId(), testClient);
    }
}
