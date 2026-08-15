package com.etheric.repository;

import com.etheric.entity.User;
import com.etheric.service.PasswordService;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    @Inject
    PasswordService passwordService;

    @WithSession
    public Uni<Optional<User>> findByUsername(String username) {
        return find("username", username).firstResult().map(Optional::ofNullable);
    }

    @WithSession
    public Uni<Optional<User>> findUserById(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @WithSession
    public Uni<Optional<User>> authenticate(String username, String password) {
        return findByUsername(username).flatMap(userOpt -> {
            if (userOpt.isEmpty() || !userOpt.get().enabled) {
                return Uni.createFrom().item(Optional.<User>empty());
            }
            User user = userOpt.get();
            if (passwordService.verifyPassword(password, user.passwordHash)) {
                return Uni.createFrom().item(Optional.of(user));
            }
            return Uni.createFrom().item(Optional.empty());
        });
    }

    @WithTransaction
    public Uni<Void> updateUser(User user) {
        return User.getSession()
                .flatMap(session -> session.merge(user))
                .replaceWithVoid();
    }
}
