package com.etheric.endpoint;

import com.etheric.service.ClientAuthService;
import com.etheric.service.TokenIntrospectionService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * OAuth 2.0 Token Introspection Endpoint (RFC 7662) — {@code POST /introspect}.
 */
@Path("/introspect")
public class IntrospectionEndpoint {

    @Inject
    TokenIntrospectionService tokenIntrospectionService;

    @Inject
    ClientAuthService clientAuthService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> introspect(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @Context HttpHeaders headers) {

        return clientAuthService.authenticateRequired(clientId, clientSecret, headers)
                .flatMap(client -> tokenIntrospectionService.introspect(token, tokenTypeHint))
                .map(body -> Response.ok(body).build());
    }
}
