package com.etheric.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Protects /admin/** endpoints with a shared API key header.
 */
@ApplicationScoped
public class AdminAuthFilter {

    public static final String HEADER_NAME = "X-Admin-Api-Key";

    @ConfigProperty(name = "etheric.admin.api-key")
    String apiKey;

    @ServerRequestFilter
    public Response filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path == null) {
            return null;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (!normalized.startsWith("admin")) {
            return null;
        }

        String provided = requestContext.getHeaderString(HEADER_NAME);
        if (provided == null || provided.isBlank() || !secureEquals(provided, apiKey)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", "unauthorized",
                            "error_description", "Missing or invalid " + HEADER_NAME
                    ))
                    .build();
        }
        return null;
    }

    private static boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
