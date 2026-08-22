package com.etheric.bootstrap;

import com.etheric.entity.Client;
import com.etheric.entity.ClientOAuthSettings;
import com.etheric.entity.User;
import com.etheric.model.ClientOAuthPolicy;
import com.etheric.repository.ClientRepository;
import com.etheric.repository.UserRepository;
import com.etheric.service.AdminConsoleAuthService;
import com.etheric.service.PasswordService;
import com.etheric.service.TokenPolicyService;
import com.etheric.service.UserClientMembershipService;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
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
import java.time.ZoneOffset;
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

    private static final String ADMIN = "admin";
    @SuppressWarnings("java:S2068") // documented local-only seed credential
    private static final String DEV_USER_PASSWORD = "password";
    private static final String DEV_CLIENT_NAME = "Etheric Dev Application";

    private static final UUID DEV_CLIENT_UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEV_USER_UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DEV_ADMIN_UUID = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private static final List<String> DEV_CLIENT_REDIRECT_URIS = List.of(
            "http://localhost:8080/callback",
            "http://localhost:3000/callback",
            "http://localhost:5173/callback",
            "http://localhost:5173/");

    private final ClientRepository clientRepository;
    private final TokenPolicyService tokenPolicyService;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final UserClientMembershipService membershipService;
    private final String quarkusProfile;

    @Inject
    public DevSeedService(ClientRepository clientRepository,
                          TokenPolicyService tokenPolicyService,
                          UserRepository userRepository,
                          PasswordService passwordService,
                          UserClientMembershipService membershipService,
                          @ConfigProperty(name = "quarkus.profile") String quarkusProfile) {
        this.clientRepository = clientRepository;
        this.tokenPolicyService = tokenPolicyService;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.membershipService = membershipService;
        this.quarkusProfile = quarkusProfile;
    }

    void onStart(@Observes StartupEvent event) {
        try {
            VertxContextSupport.subscribeAndAwait(() ->
                    seedIfEmpty()
                            .chain(v -> normalizeDevSeedIfDev())
                            .chain(v -> ensureDevClient())
                            .chain(v -> ensureDevUser())
                            .chain(v -> ensureAdminUser())
                            .chain(v -> ensureDevMemberships()));
            LOG.info("Dev seed check completed");
        } catch (Throwable error) {
            LOG.error("Dev seed failed", error);
        }
    }

    @WithTransaction
    Uni<Void> seedIfEmpty() {
        return PanacheEntityBase.getSession()
                .flatMap(session -> session.createQuery("select count(c) from Client c", Long.class).getSingleResult())
                .flatMap(count -> {
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
                    return userRepository.deleteAllExceptUsernames("user", ADMIN);
                })
                .flatMap(deletedUsers -> {
                    if (deletedUsers > 0) {
                        LOG.infof("Removed %d extra dev user(s); keeping 'user' and '%s'", deletedUsers, ADMIN);
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
            if (!DEV_CLIENT_NAME.equals(client.clientName)) {
                client.clientName = DEV_CLIENT_NAME;
                changed = true;
            }
            if (!passwordService.verifyPassword(DEV_CLIENT_SECRET, client.clientSecretHash)) {
                LOG.info("Resetting dev client secret to default (test-client/secret)");
                client.clientSecretHash = passwordService.hashPassword(DEV_CLIENT_SECRET);
                changed = true;
            }
            boolean expectedSecure = !"dev".equals(quarkusProfile);
            if (client.sessionCookieSecure != expectedSecure) {
                client.sessionCookieSecure = expectedSecure;
                changed = true;
                LOG.infof("Applying %s profile: session_cookie_secure=%s on test-client",
                        quarkusProfile, expectedSecure);
            }
            if (!changed) {
                return Uni.createFrom().voidItem();
            }
            LOG.info("Updating dev client (test-client) redirect URIs / secret / OAuth settings");
            client.redirectUris = uris;
            return clientRepository.updateClient(client);
        });
    }

    @WithTransaction
    Uni<Void> ensureDevUser() {
        return userRepository.findByUsername("user").flatMap(existing -> {
            if (existing.isPresent()) {
                User user = existing.get();
                if (passwordService.verifyPassword(DEV_USER_PASSWORD, user.passwordHash)) {
                    return Uni.createFrom().voidItem();
                }
                LOG.info("Resetting dev user password to default (user/password)");
                user.passwordHash = passwordService.hashPassword(DEV_USER_PASSWORD);
                return userRepository.updateUser(user);
            }
            LOG.info("Creating dev user (user/password)");
            return userRepository.persist(createDevUser()).replaceWithVoid();
        });
    }

    @WithTransaction
    Uni<Void> ensureAdminUser() {
        return userRepository.findByUsername(ADMIN).flatMap(existing -> {
            if (existing.isPresent()) {
                User user = existing.get();
                boolean needsRole = !AdminConsoleAuthService.hasAdminRole(user.roles);
                boolean needsPasswordReset = !passwordService.verifyPassword(ADMIN, user.passwordHash);
                if (!needsRole && !needsPasswordReset) {
                    return Uni.createFrom().voidItem();
                }
                if (needsRole) {
                    LOG.infof("Adding admin role to existing user '%s'", ADMIN);
                    user.roles = List.of(ADMIN, "user");
                }
                if (needsPasswordReset) {
                    LOG.infof("Resetting dev admin password to default (%s/%s)", ADMIN, ADMIN);
                    user.passwordHash = passwordService.hashPassword(ADMIN);
                }
                return userRepository.updateUser(user);
            }
            LOG.infof("Creating dev admin user (%s/%s)", ADMIN, ADMIN);
            return userRepository.persist(createAdminUser()).replaceWithVoid();
        });
    }

    Uni<Void> ensureDevMemberships() {
        return userRepository.findByUsername("user").flatMap(userOpt -> {
            if (userOpt.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            Uni<Void> chain = membershipService.ensureMembership(userOpt.get().id, DEV_CLIENT_ID);
            return userRepository.findByUsername(ADMIN).flatMap(adminOpt -> {
                if (adminOpt.isEmpty()) {
                    return chain;
                }
                return chain.chain(v -> membershipService.ensureMembership(adminOpt.get().id, DEV_CLIENT_ID));
            });
        });
    }

    private Client createDevClient() {
        ClientOAuthPolicy defaults = tokenPolicyService.defaultOAuthPolicy();
        boolean secure = !"dev".equals(quarkusProfile);
        return new Client(
                DEV_CLIENT_UUID,
                DEV_CLIENT_ID,
                passwordService.hashPassword(DEV_CLIENT_SECRET),
                DEV_CLIENT_NAME,
                new ArrayList<>(DEV_CLIENT_REDIRECT_URIS),
                List.of("openid", "profile", "email"),
                List.of("authorization_code", "refresh_token"),
                true,
                OffsetDateTime.now(ZoneOffset.UTC),
                "Dev OAuth client (confidential + PKCE for SPA demo)",
                new ClientOAuthSettings(
                        (int) defaults.getAccessTokenLifetimeSeconds(),
                        (int) defaults.getRefreshTokenLifetimeSeconds(),
                        (int) defaults.getSessionLifetimeSeconds(),
                        defaults.getSessionCookieName(),
                        secure)
        );
    }

    private User createDevUser() {
        return new User(
                DEV_USER_UUID,
                "user",
                passwordService.hashPassword(DEV_USER_PASSWORD),
                "user@example.com",
                List.of("user"),
                true,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private User createAdminUser() {
        return new User(
                DEV_ADMIN_UUID,
                ADMIN,
                passwordService.hashPassword(ADMIN),
                "admin@example.com",
                List.of(ADMIN, "user"),
                true,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
