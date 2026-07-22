package com.etheric.endpoint;

import com.etheric.model.JwksResponse;
import com.etheric.service.JwtService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/.well-known")
public class JwksEndpoint {

    @Inject
    JwtService jwtService;

    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwks() {
        JwksResponse jwks = jwtService.getJwks();
        return Response.ok(jwks).build();
    }
}
