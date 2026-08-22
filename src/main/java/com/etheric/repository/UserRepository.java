package com.etheric.repository;

import com.etheric.entity.User;
import com.etheric.service.PasswordService;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    private final PasswordService passwordService;

    @Inject
    public UserRepository(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

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

    @WithSession
    public Uni<List<User>> findAllUsers() {
        return listAll();
    }

    @WithSession
    public Uni<Boolean> usernameExists(String username) {
        return count("username", username).map(count -> count > 0);
    }

    @WithTransaction
    public Uni<User> persistUser(User user) {
        return persist(user).replaceWith(user);
    }

    @WithTransaction
    public Uni<Void> updateUser(User user) {
        return PanacheEntityBase.getSession()
                .flatMap(session -> session.merge(user))
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Long> deleteAllExceptUsernames(String username1, String username2) {
        return delete("username <> ?1 and username <> ?2", username1, username2);
    }
}
