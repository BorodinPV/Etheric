package com.etheric.service;

import com.etheric.entity.UserConsent;
import com.etheric.model.ConsentData;
import com.etheric.repository.ConsentRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ConsentService {

    private final CacheService cacheService;
    private final ConsentRepository consentRepository;
    private final TokenPolicyService tokenPolicyService;

    public Uni<ConsentData> getConsent(String userId, String clientId) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return cacheService.getConsent(userId, clientId)
                .flatMap(cached -> {
                    if (cached != null) {
                        return Uni.createFrom().item(cached);
                    }
                    UUID userUuid;
                    try {
                        userUuid = UUID.fromString(userId);
                    } catch (IllegalArgumentException e) {
                        return Uni.createFrom().nullItem();
                    }
                    return consentRepository.find(userUuid, clientId)
                            .flatMap(entity -> {
                                if (entity == null) {
                                    return Uni.createFrom().nullItem();
                                }
                                ConsentData data = toConsentData(entity);
                                return cacheConsent(userId, clientId, data).replaceWith(data);
                            });
                });
    }

    public Uni<Void> saveConsent(String userId, String clientId, List<String> scopes) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().voidItem();
        }
        return consentRepository.upsertMergeScopes(userUuid, clientId, scopes)
                .flatMap(entity -> {
                    if (entity == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return cacheConsent(userId, clientId, toConsentData(entity));
                });
    }

    private Uni<Void> cacheConsent(String userId, String clientId, ConsentData data) {
        return tokenPolicyService.resolveSessionLifetimeForClient(clientId)
                .flatMap(ttl -> cacheService.saveConsent(userId, clientId, data, ttl));
    }

    private ConsentData toConsentData(UserConsent entity) {
        return new ConsentData(entity.getScopes(), entity.grantedAt.toInstant().toEpochMilli());
    }
}
