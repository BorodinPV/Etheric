package com.etheric.repository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.etheric.testsupport.TestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ClientRepositoryTest {

    @Inject
    ClientRepository clientRepository;

    @Test
    void findByClientId_confidentialDemo() {
        var client = await(() -> clientRepository.findByClientId("confidential-demo"));
        assertTrue(client.isPresent());
        assertEquals("confidential-demo", client.get().clientId);
        assertTrue(client.get().redirectUris.contains("http://localhost:5174/callback"));
    }

    @Test
    void findByClientId_existingClient() {
        var client = await(() -> clientRepository.findByClientId("test-client"));
        assertTrue(client.isPresent());
        assertEquals("test-client", client.get().clientId);
        assertEquals("Etheric Dev Application", client.get().clientName);
    }

    @Test
    void findByClientId_nonExistingClient() {
        assertFalse(await(() -> clientRepository.findByClientId("nonexistent")).isPresent());
    }

    @Test
    void getRedirectUris_existingClient() {
        List<String> uris = await(() -> clientRepository.getRedirectUris("test-client"));
        assertEquals(4, uris.size());
        assertTrue(uris.contains("http://localhost:8080/callback"));
        assertTrue(uris.contains("http://localhost:3000/callback"));
        assertTrue(uris.contains("http://localhost:5173/callback"));
        assertTrue(uris.contains("http://localhost:5173/"));
    }

    @Test
    void getRedirectUris_nonExistingClient() {
        assertTrue(await(() -> clientRepository.getRedirectUris("nonexistent")).isEmpty());
    }

    @Test
    void getScopes_existingClient() {
        List<String> scopes = await(() -> clientRepository.getScopes("test-client"));
        assertEquals(3, scopes.size());
        assertTrue(scopes.contains("openid"));
    }

    @Test
    void isRedirectUriValid_validUri() {
        assertTrue(await(() -> clientRepository.isRedirectUriValid("test-client", "http://localhost:8080/callback")));
    }

    @Test
    void isRedirectUriValid_invalidUri() {
        assertFalse(await(() -> clientRepository.isRedirectUriValid("test-client", "http://evil.com/callback")));
    }

    @Test
    void isScopeValid_validScopes() {
        assertTrue(await(() -> clientRepository.isScopeValid("test-client", List.of("openid", "profile"))));
    }

    @Test
    void isScopeValid_invalidScope() {
        assertFalse(await(() -> clientRepository.isScopeValid("test-client", List.of("openid", "admin"))));
    }

    @Test
    void isGrantTypeSupported_supportedType() {
        assertTrue(await(() -> clientRepository.isGrantTypeSupported("test-client", "authorization_code")));
    }

    @Test
    void isRegisteredRedirectUri_registeredForAnyClient() {
        assertTrue(await(() -> clientRepository.isRegisteredRedirectUri("http://localhost:8080/callback")));
    }

    @Test
    void isRegisteredRedirectUri_unregisteredUri() {
        assertFalse(await(() -> clientRepository.isRegisteredRedirectUri("http://evil.com/callback")));
    }

    @Test
    void clientIsEnabled() {
        var client = await(() -> clientRepository.findByClientId("test-client"));
        assertTrue(client.isPresent());
        assertTrue(client.get().enabled);
    }
}
