package com.etheric.endpoint;

import com.etheric.model.AuthorizationCodeData;
import com.etheric.model.AuthorizationRequestState;
import com.etheric.repository.ClientRepository;
import com.etheric.service.CacheService;
import com.etheric.service.JwtService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.UUID;

@Path("/consent")
public class ConsentEndpoint {

    private static final Logger LOG = Logger.getLogger(ConsentEndpoint.class);
    private static final long CODE_TTL = 600;

    @Inject
    Template consent;

    @Inject
    ClientRepository clientRepository;

    @Inject
    CacheService cacheService;

    @Inject
    JwtService jwtService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getConsent(@QueryParam("state") String state, @Context HttpHeaders headers) {
        if (state == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String sessionId = extractSessionId(headers);
        if (sessionId == null) {
            return Response.seeOther(URI.create("/login?state=" + state)).build();
        }

        var session = cacheService.getSession(sessionId);
        if (session == null) {
            return Response.seeOther(URI.create("/login?state=" + state)).build();
        }

        var requestState = cacheService.getAuthorizationRequestState(state);
        if (requestState == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid or expired authorization request").build();
        }

        var clientOpt = clientRepository.findByClientId(requestState.getClientId());
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Client not found").build();
        }

        String csrfToken = UUID.randomUUID().toString();
        session.setCsrfToken(csrfToken);
        cacheService.saveSession(sessionId, session, 1800);

        TemplateInstance template = consent.instance();
        template.data("client", clientOpt.get());
        template.data("scopes", requestState.getScope());
        template.data("state", state);
        template.data("csrfToken", csrfToken);
        return Response.ok(template.render()).type(MediaType.TEXT_HTML).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postConsent(
            @FormParam("action") String action,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        if (state == null || action == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String sessionId = extractSessionId(headers);
        if (sessionId == null) {
            return Response.seeOther(URI.create("/login?state=" + state)).build();
        }

        var session = cacheService.getSession(sessionId);
        if (session == null) {
            return Response.seeOther(URI.create("/login?state=" + state)).build();
        }

        if (csrfToken == null || !csrfToken.equals(session.getCsrfToken())) {
            return Response.status(Response.Status.FORBIDDEN).entity("Invalid CSRF token").build();
        }

        var requestState = cacheService.getAuthorizationRequestState(state);
        if (requestState == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid or expired authorization request").build();
        }

        if ("approve".equals(action)) {
            String code = jwtService.generateAuthorizationCode();
            cacheService.saveAuthorizationCode(code, new AuthorizationCodeData(
                    requestState.getClientId(),
                    session.getUserId(),
                    requestState.getRedirectUri(),
                    requestState.getScope()
            ), CODE_TTL);
            cacheService.deleteAuthorizationRequestState(state);

            return Response.seeOther(URI.create(
                    requestState.getRedirectUri() + "?code=" + code + "&state=" + requestState.getState()
            )).build();
        } else {
            cacheService.deleteAuthorizationRequestState(state);

            return Response.seeOther(URI.create(
                    requestState.getRedirectUri() + "?error=access_denied&state=" + requestState.getState()
            )).build();
        }
    }

    private String extractSessionId(HttpHeaders headers) {
        String cookie = headers.getHeaderString("Cookie");
        if (cookie != null && cookie.contains("SESSIONID=")) {
            String[] parts = cookie.split("SESSIONID=");
            if (parts.length > 1) {
                String value = parts[1].split(";")[0].trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
