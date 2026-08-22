package com.etheric.endpoint;

import com.etheric.repository.ClientRepository;
import com.etheric.service.AuthSessionService;
import com.etheric.util.SessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.*;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.List;

/**
 * Session logout ({@code GET /logout}).
 * <p>
 * Optional {@code redirect_uri} query param (must be a registered client redirect URI).
 * Optional {@code client_id} limits logout to OAuth sessions for that client; otherwise all
 * AS sessions for the authenticated user(s) found in cookies are removed.
 * Clears all known OAuth session cookies and {@code 302} to target URI or {@code /}.
 */
@Path("/logout")
@RequiredArgsConstructor
public class LogoutEndpoint {

    private final AuthSessionService authSessionService;
    private final SessionCookieFactory sessionCookieFactory;
    private final ClientRepository clientRepository;

    @GET
    public Uni<Response> logout(@QueryParam("redirect_uri") String redirectUri,
                                @QueryParam("client_id") String clientId,
                                @Context HttpHeaders headers) {
        return authSessionService.logout(headers, clientId)
                .flatMap(v -> resolveRedirectTarget(redirectUri))
                .flatMap(target -> sessionCookieFactory.clearAllKnown()
                        .map(clearCookies -> buildLogoutResponse(target, clearCookies)));
    }

    private Response buildLogoutResponse(URI target, List<String> clearCookies) {
        Response.ResponseBuilder response = Response.seeOther(target);
        for (String clearCookie : clearCookies) {
            response.header("Set-Cookie", clearCookie);
        }
        return response.build();
    }

    private Uni<URI> resolveRedirectTarget(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Uni.createFrom().item(URI.create("/"));
        }
        return clientRepository.isRegisteredRedirectUri(redirectUri)
                .map(valid -> Boolean.TRUE.equals(valid) ? URI.create(redirectUri) : URI.create("/"));
    }
}
