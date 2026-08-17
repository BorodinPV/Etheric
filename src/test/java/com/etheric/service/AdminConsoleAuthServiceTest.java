package com.etheric.service;

import com.etheric.model.AdminSessionData;
import com.etheric.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AdminConsoleAuthServiceTest {

    @Inject
    AdminConsoleAuthService authService;

    @Test
    void hasAdminRole_requiresAdminRole() {
        assertTrue(AdminConsoleAuthService.hasAdminRole(List.of("admin", "user")));
        assertTrue(AdminConsoleAuthService.hasAdminRole(List.of("admin")));
        assertFalse(AdminConsoleAuthService.hasAdminRole(List.of("user")));
        assertFalse(AdminConsoleAuthService.hasAdminRole(null));
    }

    @Test
    void validateCsrf_matchesSessionToken() {
        AdminSessionData session = new AdminSessionData(
                UUID.randomUUID(), "admin", "token-123", System.currentTimeMillis());
        assertTrue(authService.validateCsrf(session, "token-123"));
        assertFalse(authService.validateCsrf(session, "wrong"));
        assertFalse(authService.validateCsrf(session, null));
    }

    @Test
    void createAuthenticatedSession_persistsSession() {
        User user = new User(
                UUID.randomUUID(), "admin", "hash", "admin@example.com",
                List.of("admin", "user"), true, null);

        AdminConsoleAuthService.AuthenticatedSessionResult result =
                authService.createAuthenticatedSession(user).await().indefinitely();

        assertNotNull(result.sessionId());
        assertEquals(user.id, result.session().getUserId());
        assertEquals("admin", result.session().getUsername());
        assertNotNull(result.session().getCsrfToken());
    }
}
