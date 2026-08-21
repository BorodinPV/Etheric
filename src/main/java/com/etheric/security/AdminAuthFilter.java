package com.etheric.security;

import com.etheric.config.EthericAdminConfig;
import com.etheric.service.AdminConsoleAuthService;
import com.etheric.service.CacheService;
import com.etheric.util.AdminSessionCookieFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Protects admin routes: JSON API via API key, console via ADMIN_SESSION cookie.
 */
@ApplicationScoped
public class AdminAuthFilter {

    public static final String HEADER_NAME = "X-Admin-Api-Key";

    private static final Pattern STATIC_ASSET_PATH = Pattern.compile("^admin/[^/]+\\.(css|js)$");

    @Inject
    EthericAdminConfig adminConfig;

    @Inject
    CacheService cacheService;

    @ServerRequestFilter(preMatching = true)
    public Uni<Response> filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!path.startsWith("admin")) {
            return Uni.createFrom().nullItem();
        }

        if (isPublicPath(path)) {
            return Uni.createFrom().nullItem();
        }

        if (isJsonAdminApi(path)) {
            return validateApiKey(requestContext);
        }

        if (path.startsWith("admin/console")) {
            return validateConsoleSession(requestContext, path);
        }

        return validateApiKey(requestContext);
    }

    private Uni<Response> validateApiKey(ContainerRequestContext requestContext) {
        String provided = requestContext.getHeaderString(HEADER_NAME);
        if (provided == null || provided.isBlank() || !secureEquals(provided, adminConfig.apiKey())) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", "unauthorized",
                            "error_description", "Missing or invalid " + HEADER_NAME
                    ))
                    .build());
        }
        return Uni.createFrom().nullItem();
    }

    private Uni<Response> validateConsoleSession(ContainerRequestContext requestContext, String path) {
        String sessionId = AdminSessionCookieFactory.extractSessionIdFromCookie(
                requestContext.getHeaders().getFirst("Cookie"));
        if (sessionId == null) {
            return Uni.createFrom().item(redirectToLogin(path));
        }

        return cacheService.getAdminSession(sessionId).map(session -> {
            if (session == null || session.getUserId() == null) {
                return redirectToLogin(path);
            }
            requestContext.setProperty(AdminConsoleAuthService.SESSION_PROPERTY, session);
            requestContext.setProperty(AdminConsoleAuthService.SESSION_ID_PROPERTY, sessionId);
            return null;
        });
    }

    private static Response redirectToLogin(String path) {
        String target = "/admin/console/login";
        if (!"admin/console/login".equals(path)) {
            target += "?redirect_uri="
                    + java.net.URLEncoder.encode("/" + path, StandardCharsets.UTF_8);
        }
        return Response.seeOther(URI.create(target)).build();
    }

    private static boolean isPublicPath(String path) {
        if ("admin/console/login".equals(path) || "admin/console/locale".equals(path)) {
            return true;
        }
        return STATIC_ASSET_PATH.matcher(path).matches();
    }

    private static boolean isJsonAdminApi(String path) {
        return path.equals("admin/clients")
                || path.startsWith("admin/clients/")
                || path.equals("admin/users")
                || path.startsWith("admin/users/");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
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
