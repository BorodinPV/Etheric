package com.etheric.exception;

import com.etheric.model.ErrorResponse;
import com.etheric.util.OAuthRedirectBuilder;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * Maps {@link OAuthException} to redirect or JSON error responses.
 */
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
        URI redirectUri = OAuthRedirectBuilder.oauthError(
                exception.getRedirectUri(),
                exception.getError().getError(),
                exception.getError().getErrorDescription(),
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
}
