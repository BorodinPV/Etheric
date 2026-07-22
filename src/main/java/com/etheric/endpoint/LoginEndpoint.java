package com.etheric.endpoint;

import com.etheric.entity.User;
import com.etheric.model.SessionData;
import com.etheric.repository.UserRepository;
import com.etheric.service.CacheService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.UUID;

@Path("/login")
public class LoginEndpoint {

    private static final Logger LOG = Logger.getLogger(LoginEndpoint.class);
    private static final long SESSION_TTL = 1800;

    @Inject
    Template login;

    @Inject
    UserRepository userRepository;

    @Inject
    CacheService cacheService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getLogin(@QueryParam("state") String state) {
        TemplateInstance template = login.instance();
        template.data("error", null);
        template.data("state", state);
        return Response.ok(template.render()).type(MediaType.TEXT_HTML).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postLogin(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("state") String state,
            @FormParam("csrf_token") String csrfToken,
            @Context HttpHeaders headers) {

        String sessionId = extractSessionId(headers);

        if (sessionId != null) {
            var session = cacheService.getSession(sessionId);
            if (session != null && csrfToken != null && csrfToken.equals(session.getCsrfToken())) {
                User user = userRepository.authenticate(username, password);
                if (user != null) {
                    return handleSuccessfulLogin(user.getId().toString(), state);
                }
            }
        }

        User user = userRepository.authenticate(username, password);
        if (user != null) {
            return handleSuccessfulLogin(user.getId().toString(), state);
        }

        TemplateInstance template = login.instance();
        template.data("error", "Неверное имя пользователя или пароль");
        template.data("state", state);
        return Response.ok(template.render()).type(MediaType.TEXT_HTML).build();
    }

    private Response handleSuccessfulLogin(String userId, String state) {
        String newSessionId = UUID.randomUUID().toString();
        cacheService.saveSession(newSessionId, new SessionData(userId, null, System.currentTimeMillis()), SESSION_TTL);

        if (state != null) {
            var requestState = cacheService.getAuthorizationRequestState(state);
            if (requestState != null) {
                requestState.setUserId(userId);
                cacheService.saveAuthorizationRequestState(state, requestState, 600);
            }
        }

        URI redirectUri = (state != null) ? URI.create("/consent?state=" + state) : URI.create("/");
        return Response.seeOther(redirectUri)
                .header("Set-Cookie", "SESSIONID=" + newSessionId + "; Path=/; HttpOnly; SameSite=Lax")
                .build();
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
