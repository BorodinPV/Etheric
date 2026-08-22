package com.etheric.endpoint;

import com.etheric.service.ClientAuthService;
import com.etheric.service.TokenRevocationService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

/**
 * OAuth 2.0 Token Revocation Endpoint (RFC 7009) — {@code POST /revoke}.
 */
@Path("/revoke")
@RequiredArgsConstructor
public class RevocationEndpoint {

    private final TokenRevocationService tokenRevocationService;
    private final ClientAuthService clientAuthService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> revoke(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @Context HttpHeaders headers) {

        return clientAuthService.authenticateRequired(clientId, clientSecret, headers)
                .flatMap(client -> tokenRevocationService.revoke(token, tokenTypeHint))
                .replaceWith(Response.ok().build());
    }
}
