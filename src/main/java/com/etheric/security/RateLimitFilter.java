package com.etheric.security;

import com.etheric.config.EthericRateLimitConfig;
import com.etheric.exception.OAuthError;
import com.etheric.model.ErrorResponse;
import com.etheric.service.CacheService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Redis-backed rate limiting for sensitive OAuth endpoints.
 */
@ApplicationScoped
public class RateLimitFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);

    private final CacheService cacheService;
    private final EthericRateLimitConfig rateLimitConfig;

    public RateLimitFilter(CacheService cacheService, EthericRateLimitConfig rateLimitConfig) {
        this.cacheService = cacheService;
        this.rateLimitConfig = rateLimitConfig;
    }

    @ServerRequestFilter(preMatching = true)
    public Uni<Response> filter(ContainerRequestContext ctx) {
        if (!rateLimitConfig.enabled()) {
            return Uni.createFrom().nullItem();
        }

        String path = normalizePath(ctx.getUriInfo().getPath());
        String method = ctx.getMethod();
        Integer limit = resolveLimit(path, method);
        if (limit == null) {
            return Uni.createFrom().nullItem();
        }

        String clientIp = resolveClientIp(ctx);
        String bucket = path + ":" + clientIp;

        return cacheService.checkRateLimit(bucket, limit, rateLimitConfig.windowSeconds())
                .flatMap(allowed -> {
                    if (Boolean.TRUE.equals(allowed)) {
                        return Uni.createFrom().nullItem();
                    }
                    LOG.warnf("Rate limit exceeded for %s from %s", path, clientIp);
                    ErrorResponse body = new ErrorResponse(
                            OAuthError.TEMPORARILY_UNAVAILABLE.getError(),
                            "Rate limit exceeded. Please try again later.");
                    return Uni.createFrom().item(Response.status(429).entity(body).build());
                });
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private Integer resolveLimit(String path, String method) {
        return switch (path) {
            case "authorize" -> rateLimitConfig.authorizeMax();
            case "login" -> rateLimitConfig.loginMax();
            case "register" -> rateLimitConfig.loginMax();
            case "admin/console/login" -> "POST".equalsIgnoreCase(method) ? rateLimitConfig.loginMax() : null;
            case "token" -> rateLimitConfig.tokenMax();
            case "consent" -> "POST".equalsIgnoreCase(method) ? rateLimitConfig.consentMax() : null;
            default -> null;
        };
    }

    private String resolveClientIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }
}
