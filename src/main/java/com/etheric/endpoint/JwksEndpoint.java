package com.etheric.endpoint;

import com.etheric.model.JwksResponse;
import com.etheric.service.JwtService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

/**
 * JSON Web Key Set endpoint ({@code GET /.well-known/jwks.json}).
 * <p>
 * Returns the server's public RSA key(s) for verifying JWT access/refresh tokens.
 * Success: {@code 200} JSON {@code {"keys":[…]}}.
 */
@Path("/.well-known")
@RequiredArgsConstructor
public class JwksEndpoint {

    private final JwtService jwtService;

    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getJwks() {
        JwksResponse jwks = jwtService.getJwks();
        return Uni.createFrom().item(Response.ok(jwks).build());
    }
}
