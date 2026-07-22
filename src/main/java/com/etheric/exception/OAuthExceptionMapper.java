package com.etheric.exception;

import com.etheric.model.ErrorResponse;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Provider
public class OAuthExceptionMapper implements ExceptionMapper<OAuthException> {

    private static final Logger LOG = Logger.getLogger(OAuthExceptionMapper.class);

    @Context
    HttpServerResponse response;

    @Override
    public Response toResponse(OAuthException exception) {
        LOG.warnf("OAuth error: %s - %s", exception.getError().getError(), exception.getMessage());

        if (exception.hasRedirectUri()) {
            return handleRedirect(exception);
        }

        return handleJsonResponse(exception);
    }

    private Response handleRedirect(OAuthException exception) {
        URI redirectUri = buildRedirectUri(
                exception.getRedirectUri(),
                exception.getError().getError(),
                exception.getError(),
                exception.getState()
        );

        return Response.status(Response.Status.FOUND)
                .location(redirectUri)
                .build();
    }

    private Response handleJsonResponse(OAuthException exception) {
        ErrorResponse errorResponse = new ErrorResponse(
                exception.getError().getError(),
                exception.getError().getErrorDescription()
        );

        return Response.status(exception.getHttpStatus())
                .entity(errorResponse)
                .build();
    }

    private URI buildRedirectUri(String baseUri, String error, OAuthError oauthError, String state) {
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        uriBuilder.append(baseUri.contains("?") ? "&" : "?");
        uriBuilder.append("error=").append(error);

        if (oauthError.getErrorDescription() != null) {
            uriBuilder.append("&error_description=")
                    .append(URLEncoder.encode(oauthError.getErrorDescription(), StandardCharsets.UTF_8));
        }

        if (state != null) {
            uriBuilder.append("&state=").append(state);
        }

        return URI.create(uriBuilder.toString());
    }
}
