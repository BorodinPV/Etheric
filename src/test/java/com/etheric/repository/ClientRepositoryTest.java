package com.etheric.repository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ClientRepositoryTest {

    @Inject
    ClientRepository clientRepository;

    @Test
    void findByClientId_existingClient() {
        var client = clientRepository.findByClientId("test-client");
        assertTrue(client.isPresent());
        assertEquals("test-client", client.get().getClientId());
        assertEquals("Test Application", client.get().getClientName());
    }

    @Test
    void findByClientId_nonExistingClient() {
        assertFalse(clientRepository.findByClientId("nonexistent").isPresent());
    }

    @Test
    void isValidClient_validCredentials() {
        String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        assertTrue(clientRepository.isValidClient("test-client", hash));
    }

    @Test
    void isValidClient_wrongSecret() {
        assertFalse(clientRepository.isValidClient("test-client", "wrong-hash"));
    }

    @Test
    void isValidClient_nonExistingClient() {
        assertFalse(clientRepository.isValidClient("nonexistent", "any"));
    }

    @Test
    void getRedirectUris_existingClient() {
        List<String> uris = clientRepository.getRedirectUris("test-client");
        assertEquals(2, uris.size());
        assertTrue(uris.contains("http://localhost:8080/callback"));
        assertTrue(uris.contains("http://localhost:3000/callback"));
    }

    @Test
    void getRedirectUris_nonExistingClient() {
        assertTrue(clientRepository.getRedirectUris("nonexistent").isEmpty());
    }

    @Test
    void getScopes_existingClient() {
        List<String> scopes = clientRepository.getScopes("test-client");
        assertEquals(3, scopes.size());
        assertTrue(scopes.contains("openid"));
        assertTrue(scopes.contains("profile"));
        assertTrue(scopes.contains("email"));
    }

    @Test
    void getScopes_nonExistingClient() {
        assertTrue(clientRepository.getScopes("nonexistent").isEmpty());
    }

    @Test
    void getGrantTypes_existingClient() {
        List<String> types = clientRepository.getGrantTypes("test-client");
        assertEquals(2, types.size());
        assertTrue(types.contains("authorization_code"));
        assertTrue(types.contains("refresh_token"));
    }

    @Test
    void getGrantTypes_nonExistingClient() {
        assertTrue(clientRepository.getGrantTypes("nonexistent").isEmpty());
    }

    @Test
    void isRedirectUriValid_validUri() {
        assertTrue(clientRepository.isRedirectUriValid("test-client", "http://localhost:8080/callback"));
    }

    @Test
    void isRedirectUriValid_invalidUri() {
        assertFalse(clientRepository.isRedirectUriValid("test-client", "http://evil.com/callback"));
    }

    @Test
    void isRedirectUriValid_nonExistingClient() {
        assertFalse(clientRepository.isRedirectUriValid("nonexistent", "http://localhost:8080/callback"));
    }

    @Test
    void isScopeValid_validScopes() {
        assertTrue(clientRepository.isScopeValid("test-client", List.of("openid", "profile")));
    }

    @Test
    void isScopeValid_allScopes() {
        assertTrue(clientRepository.isScopeValid("test-client", List.of("openid", "profile", "email")));
    }

    @Test
    void isScopeValid_invalidScope() {
        assertFalse(clientRepository.isScopeValid("test-client", List.of("openid", "admin")));
    }

    @Test
    void isScopeValid_emptyScopes() {
        assertTrue(clientRepository.isScopeValid("test-client", List.of()));
    }

    @Test
    void isScopeValid_nullScopes() {
        assertTrue(clientRepository.isScopeValid("test-client", null));
    }

    @Test
    void isGrantTypeSupported_supportedType() {
        assertTrue(clientRepository.isGrantTypeSupported("test-client", "authorization_code"));
        assertTrue(clientRepository.isGrantTypeSupported("test-client", "refresh_token"));
    }

    @Test
    void isGrantTypeSupported_unsupportedType() {
        assertFalse(clientRepository.isGrantTypeSupported("test-client", "password"));
        assertFalse(clientRepository.isGrantTypeSupported("test-client", "client_credentials"));
    }

    @Test
    void isGrantTypeSupported_nonExistingClient() {
        assertFalse(clientRepository.isGrantTypeSupported("nonexistent", "authorization_code"));
    }

    @Test
    void clientIsEnabled() {
        var client = clientRepository.findByClientId("test-client");
        assertTrue(client.isPresent());
        assertTrue(client.get().isEnabled());
    }

    @Test
    void clientHasExpectedFields() {
        var client = clientRepository.findByClientId("test-client").get();
        assertNotNull(client.getId());
        assertNotNull(client.getCreatedAt());
        assertEquals("Test Application", client.getClientName());
    }
}
