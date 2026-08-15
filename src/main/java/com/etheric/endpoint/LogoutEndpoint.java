package com.etheric.endpoint;

import com.etheric.repository.ClientRepository;
import com.etheric.service.CacheService;
import com.etheric.util.SessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.*;

import java.net.URI;

/**
 * Session logout ({@code GET /logout}).
 * <p>
 * Optional {@code redirect_uri} query param (must be a registered client redirect URI).
 * Deletes session and clears {@code SESSIONID} cookie; {@code 302} to target URI or {@code /}.
 */
@Path("/logout")
public class LogoutEndpoint {

    @Inject
    CacheService cacheService;

    @Inject
    SessionCookieFactory sessionCookieFactory;

    @Inject
    ClientRepository clientRepository;

    @GET
    public Uni<Response> logout(@QueryParam("redirect_uri") String redirectUri, @Context HttpHeaders headers) {
        String sessionId = SessionCookieFactory.extractSessionId(headers);

        Uni<Void> deleteSession = sessionId != null
                ? cacheService.deleteSession(sessionId)
                : Uni.createFrom().voidItem();

        return deleteSession.flatMap(v -> resolveRedirectTarget(redirectUri))
                .map(target -> Response.seeOther(target)
                        .header("Set-Cookie", sessionCookieFactory.clear())
                        .build());
    }

    private Uni<URI> resolveRedirectTarget(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Uni.createFrom().item(URI.create("/"));
        }
        return clientRepository.isRegisteredRedirectUri(redirectUri)
                .map(valid -> valid ? URI.create(redirectUri) : URI.create("/"));
    }
}
