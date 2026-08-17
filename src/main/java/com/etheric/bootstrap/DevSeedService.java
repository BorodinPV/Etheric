package com.etheric.bootstrap;

import com.etheric.entity.Client;
import com.etheric.entity.User;
import com.etheric.repository.ClientRepository;
import com.etheric.repository.UserRepository;
import com.etheric.service.AdminConsoleAuthService;
import com.etheric.service.PasswordService;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Seeds dev/test data when PostgreSQL tables are empty (after Flyway).
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class DevSeedService {

    private static final Logger LOG = Logger.getLogger(DevSeedService.class);
    private static final UUID TEST_CLIENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID TEST_USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    @Inject
    ClientRepository clientRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    void onStart(@Observes StartupEvent event) {
        try {
            VertxContextSupport.subscribeAndAwait(() -> seedIfEmpty().chain(this::ensureAdminUser));
            LOG.info("Dev seed check completed");
        } catch (Throwable error) {
            LOG.error("Dev seed failed", error);
        }
    }

    @WithTransaction
    Uni<Void> seedIfEmpty() {
        return Client.count().flatMap(count -> {
            if (count > 0) {
                return Uni.createFrom().voidItem();
            }
            LOG.info("Seeding test-client, user, and admin into PostgreSQL");
            Client client = new Client(
                    TEST_CLIENT_ID,
                    "test-client",
                    passwordService.hashPassword("secret"),
                    "Test Application",
                    List.of("http://localhost:8080/callback", "http://localhost:3000/callback"),
                    List.of("openid", "profile", "email"),
                    List.of("authorization_code", "refresh_token"),
                    true,
                    OffsetDateTime.now(),
                    "A test OAuth client application"
            );
            User user = new User(
                    TEST_USER_ID,
                    "user",
                    passwordService.hashPassword("password"),
                    "user@example.com",
                    List.of("user"),
                    true,
                    OffsetDateTime.now()
            );
            User admin = new User(
                    ADMIN_USER_ID,
                    "admin",
                    passwordService.hashPassword("admin"),
                    "admin@example.com",
                    List.of("admin", "user"),
                    true,
                    OffsetDateTime.now()
            );
            return clientRepository.persistClient(client)
                    .flatMap(c -> userRepository.persist(user))
                    .flatMap(u -> userRepository.persist(admin))
                    .replaceWithVoid();
        });
    }

    /**
     * Ensures dev admin exists even when DB was seeded before admin user was added to this service.
     */
    @WithTransaction
    Uni<Void> ensureAdminUser() {
        return userRepository.findByUsername("admin").flatMap(existing -> {
            if (existing.isPresent()) {
                User user = existing.get();
                boolean needsRole = !AdminConsoleAuthService.hasAdminRole(user.roles);
                boolean needsPasswordReset = !passwordService.verifyPassword("admin", user.passwordHash);
                if (!needsRole && !needsPasswordReset) {
                    return Uni.createFrom().voidItem();
                }
                if (needsRole) {
                    LOG.info("Adding admin role to existing user 'admin'");
                    user.roles = List.of("admin", "user");
                }
                if (needsPasswordReset) {
                    LOG.info("Resetting dev admin password to default (admin/admin)");
                    user.passwordHash = passwordService.hashPassword("admin");
                }
                return userRepository.updateUser(user);
            }
            LOG.info("Creating dev admin user (admin/admin)");
            User admin = new User(
                    UUID.randomUUID(),
                    "admin",
                    passwordService.hashPassword("admin"),
                    "admin@example.com",
                    List.of("admin", "user"),
                    true,
                    OffsetDateTime.now()
            );
            return userRepository.persist(admin).replaceWithVoid();
        });
    }
}
