package com.etheric.service;

import com.etheric.config.EthericTtlConfig;
import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.AuthorizationRequestState;
import com.etheric.repository.AuthorizationCodeRepository;
import com.etheric.util.OAuthRedirectBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Issues authorization codes (Redis primary, PostgreSQL backup).
 */
@ApplicationScoped
public class AuthorizationCodeService {

    private static final Logger LOG = Logger.getLogger(AuthorizationCodeService.class);

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @Inject
    EthericTtlConfig ttlConfig;

    @Inject
    AuthorizationCodeRepository authorizationCodeRepository;

    public Uni<Response> issueCodeAndRedirect(String userId, AuthorizationRequestState requestState,
                                              String state) {
        String code = jwtService.generateAuthorizationCode();
        long ttl = ttlConfig.authorizationCodeLifetime();
        AuthorizationCodeData codeData = new AuthorizationCodeData(
                requestState.getClientId(), userId, requestState.getRedirectUri(),
                requestState.getScope(), requestState.getCodeChallenge(),
                requestState.getCodeChallengeMethod(), requestState.getNonce());

        Instant expiresAt = Instant.now().plusSeconds(ttl);

        return cacheService.saveAuthorizationCode(code, codeData, ttl)
                .flatMap(v -> authorizationCodeRepository.saveBackup(code, codeData, expiresAt)
                        .onFailure().invoke(e -> LOG.warnf("PG auth code backup failed for code: %s", e.getMessage()))
                        .onFailure().recoverWithNull())
                .flatMap(v -> cacheService.deleteAuthorizationRequestState(state))
                .replaceWith(Response.seeOther(OAuthRedirectBuilder.authorizationSuccess(
                        requestState.getRedirectUri(), code, requestState.getState())).build());
    }

    public Uni<Void> markCodeUsed(String code) {
        return cacheService.deleteAuthorizationCode(code)
                .flatMap(v -> authorizationCodeRepository.markUsed(code)
                        .onFailure().invoke(e -> LOG.warnf("PG auth code mark-used failed: %s", e.getMessage()))
                        .onFailure().recoverWithNull())
                .replaceWithVoid();
    }
}
