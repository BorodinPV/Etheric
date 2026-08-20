package com.etheric.service;

import com.etheric.model.UserCreateRequest;
import com.etheric.repository.ClientRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RegistrationService {

    @Inject
    AdminUserService adminUserService;

    @Inject
    UserClientMembershipService membershipService;

    @Inject
    ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    public Uni<RegistrationResult> register(String username, String password, String email,
                                            String state, String clientIdParam) {
        UserCreateRequest request = new UserCreateRequest(
                username, password, blankToNull(email), List.of("user"), true);
        return adminUserService.create(request).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(RegistrationResult.failure(translateCreateError(result)));
            }
            UUID userId = result.value().getId();
            return resolveClientId(state, clientIdParam).flatMap(clientId -> {
                if (clientId == null) {
                    return Uni.createFrom().item(RegistrationResult.success(userId.toString()));
                }
                return membershipService.ensureMembership(userId, clientId)
                        .replaceWith(RegistrationResult.success(userId.toString()));
            });
        });
    }

    public Uni<Boolean> isReturnUriAllowed(String clientId, String returnUri) {
        if (clientId == null || clientId.isBlank() || returnUri == null || returnUri.isBlank()) {
            return Uni.createFrom().item(false);
        }
        return clientRepository.isRedirectUriValid(clientId.trim(), returnUri.trim());
    }

    private Uni<String> resolveClientId(String state, String clientIdParam) {
        if (state != null && !state.isBlank()) {
            return cacheService.getAuthorizationRequestState(state)
                    .map(requestState -> requestState == null ? null : requestState.getClientId())
                    .flatMap(this::validateClientExists);
        }
        return validateClientExists(blankToNull(clientIdParam));
    }

    private Uni<String> validateClientExists(String clientId) {
        if (clientId == null) {
            return Uni.createFrom().nullItem();
        }
        return clientRepository.findByClientId(clientId).map(opt ->
                opt.filter(client -> client.enabled).map(client -> client.clientId).orElse(null));
    }

    private static String translateCreateError(AdminServiceResult<?> result) {
        String description = result.errorDescription();
        if ("username already exists".equals(description)) {
            return "Имя пользователя уже занято";
        }
        if (description != null && description.startsWith("password must be at least")) {
            return "Пароль должен быть не короче " + AdminUserService.MIN_PASSWORD_LENGTH + " символов";
        }
        if ("username and password are required".equals(description)) {
            return "Укажите имя пользователя и пароль";
        }
        return description != null ? description : "Не удалось зарегистрироваться";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RegistrationResult(boolean success, String userId, String errorMessage) {
        static RegistrationResult success(String userId) {
            return new RegistrationResult(true, userId, null);
        }

        static RegistrationResult failure(String errorMessage) {
            return new RegistrationResult(false, null, errorMessage);
        }
    }
}
