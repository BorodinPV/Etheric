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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds dev/test data when PostgreSQL tables are empty (after Flyway).
 * Dev profile also normalizes DB to exactly one client and two users (user + admin).
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class DevSeedService {

    private static final Logger LOG = Logger.getLogger(DevSeedService.class);

    static final String DEV_CLIENT_ID = "test-client";
    static final String DEV_CLIENT_SECRET = "secret";

    private static final UUID DEV_CLIENT_UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEV_USER_UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DEV_ADMIN_UUID = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private static final List<String> DEV_CLIENT_REDIRECT_URIS = List.of(
            "http://localhost:8080/callback",
            "http://localhost:3000/callback",
            "http://localhost:5173/callback",
            "http://localhost:5173/");

    @Inject
    ClientRepository clientRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    @ConfigProperty(name = "quarkus.profile")
    String quarkusProfile;

    void onStart(@Observes StartupEvent event) {
        try {
            VertxContextSupport.subscribeAndAwait(() ->
                    seedIfEmpty()
                            .chain(v -> normalizeDevSeedIfDev())
                            .chain(v -> ensureDevClient())
                            .chain(v -> ensureDevUser())
                            .chain(v -> ensureAdminUser()));
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
            LOG.info("Seeding dev client (test-client), user, and admin into PostgreSQL");
            return clientRepository.persistClient(createDevClient())
                    .flatMap(c -> userRepository.persist(createDevUser()))
                    .flatMap(u -> userRepository.persist(createAdminUser()))
                    .replaceWithVoid();
        });
    }

    Uni<Void> normalizeDevSeedIfDev() {
        if (!"dev".equals(quarkusProfile)) {
            return Uni.createFrom().voidItem();
        }
        return normalizeDevSeed();
    }

    @WithTransaction
    Uni<Void> normalizeDevSeed() {
        return clientRepository.deleteAllExceptClientId(DEV_CLIENT_ID)
                .flatMap(deletedClients -> {
                    if (deletedClients > 0) {
                        LOG.infof("Removed %d extra dev client(s); keeping '%s'", deletedClients, DEV_CLIENT_ID);
                    }
                    return userRepository.deleteAllExceptUsernames("user", "admin");
                })
                .flatMap(deletedUsers -> {
                    if (deletedUsers > 0) {
                        LOG.infof("Removed %d extra dev user(s); keeping 'user' and 'admin'", deletedUsers);
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    @WithTransaction
    Uni<Void> ensureDevClient() {
        return clientRepository.findByClientId(DEV_CLIENT_ID).flatMap(existing -> {
            if (existing.isEmpty()) {
                LOG.info("Creating dev client (test-client)");
                return clientRepository.persistClient(createDevClient()).replaceWithVoid();
            }
            Client client = existing.get();
            boolean changed = false;
            List<String> uris = new ArrayList<>(client.redirectUris);
            for (String uri : DEV_CLIENT_REDIRECT_URIS) {
                if (!uris.contains(uri)) {
                    uris.add(uri);
                    changed = true;
                }
            }
            if (!"Etheric Dev Application".equals(client.clientName)) {
                client.clientName = "Etheric Dev Application";
                changed = true;
            }
            if (!passwordService.verifyPassword(DEV_CLIENT_SECRET, client.clientSecretHash)) {
                LOG.info("Resetting dev client secret to default (test-client/secret)");
                client.clientSecretHash = passwordService.hashPassword(DEV_CLIENT_SECRET);
                changed = true;
            }
            if (!changed) {
                return Uni.createFrom().voidItem();
            }
            LOG.info("Updating dev client (test-client) redirect URIs / secret");
            client.redirectUris = uris;
            return clientRepository.updateClient(client);
        });
    }

    @WithTransaction
    Uni<Void> ensureDevUser() {
        return userRepository.findByUsername("user").flatMap(existing -> {
            if (existing.isPresent()) {
                User user = existing.get();
                if (passwordService.verifyPassword("password", user.passwordHash)) {
                    return Uni.createFrom().voidItem();
                }
                LOG.info("Resetting dev user password to default (user/password)");
                user.passwordHash = passwordService.hashPassword("password");
                return userRepository.updateUser(user);
            }
            LOG.info("Creating dev user (user/password)");
            return userRepository.persist(createDevUser()).replaceWithVoid();
        });
    }

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
            return userRepository.persist(createAdminUser()).replaceWithVoid();
        });
    }

    private Client createDevClient() {
        return new Client(
                DEV_CLIENT_UUID,
                DEV_CLIENT_ID,
                passwordService.hashPassword(DEV_CLIENT_SECRET),
                "Etheric Dev Application",
                new ArrayList<>(DEV_CLIENT_REDIRECT_URIS),
                List.of("openid", "profile", "email"),
                List.of("authorization_code", "refresh_token"),
                true,
                OffsetDateTime.now(),
                "Dev OAuth client (confidential + PKCE for SPA demo)"
        );
    }

    private User createDevUser() {
        return new User(
                DEV_USER_UUID,
                "user",
                passwordService.hashPassword("password"),
                "user@example.com",
                List.of("user"),
                true,
                OffsetDateTime.now()
        );
    }

    private User createAdminUser() {
        return new User(
                DEV_ADMIN_UUID,
                "admin",
                passwordService.hashPassword("admin"),
                "admin@example.com",
                List.of("admin", "user"),
                true,
                OffsetDateTime.now()
        );
    }
}
