package com.etheric.endpoint;

import com.etheric.admin.AdminConsoleI18n;
import com.etheric.admin.AdminConsoleI18nService;
import com.etheric.admin.AdminConsoleLocale;
import com.etheric.model.*;
import com.etheric.service.*;
import com.etheric.util.AdminLocaleCookieFactory;
import com.etheric.util.AdminSessionCookieFactory;
import io.quarkus.qute.Location;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Keycloak-style admin console ({@code /admin/console/**}).
 */
@Path("/admin/console")
public class AdminConsoleEndpoint {

    @Inject
    @Location("admin/console/login")
    Template adminConsoleLogin;

    @Inject
    @Location("admin/console/layout")
    Template adminConsoleLayout;

    @Inject
    @Location("admin/console/clients/list")
    Template adminConsoleClientsList;

    @Inject
    @Location("admin/console/clients/create")
    Template adminConsoleClientsCreate;

    @Inject
    @Location("admin/console/clients/detail-settings")
    Template adminConsoleClientsDetailSettings;

    @Inject
    @Location("admin/console/clients/detail-credentials")
    Template adminConsoleClientsDetailCredentials;

    @Inject
    @Location("admin/console/clients/detail-users")
    Template adminConsoleClientsDetailUsers;

    @Inject
    @Location("admin/console/users/list")
    Template adminConsoleUsersList;

    @Inject
    @Location("admin/console/users/create")
    Template adminConsoleUsersCreate;

    @Inject
    @Location("admin/console/users/detail-details")
    Template adminConsoleUsersDetailDetails;

    @Inject
    @Location("admin/console/users/detail-credentials")
    Template adminConsoleUsersDetailCredentials;

    @Inject
    @Location("admin/console/users/detail-clients")
    Template adminConsoleUsersDetailClients;

    @Inject
    AdminConsoleAuthService authService;

    @Inject
    AdminClientService adminClientService;

    @Inject
    AdminUserService adminUserService;

    @Inject
    AdminServerSettingsService adminServerSettingsService;

    @Inject
    UserClientMembershipService membershipService;

    @Inject
    @Location("admin/console/server-settings")
    Template adminConsoleServerSettings;

    @Inject
    AdminSessionCookieFactory cookieFactory;

    @Inject
    AdminConsoleI18nService i18nService;

    @Inject
    AdminLocaleCookieFactory localeCookieFactory;

    @GET
    public Uni<Response> index() {
        return Uni.createFrom().item(Response.seeOther(URI.create("/admin/console/clients")).build());
    }

    @GET
    @Path("/locale")
    public Uni<Response> setLocale(@QueryParam("lang") String lang,
                                   @QueryParam("redirect") String redirect,
                                   @Context HttpHeaders headers) {
        AdminConsoleLocale locale = AdminConsoleLocale.parse(lang);
        String target = localeRedirectTarget(redirect, headers);
        return Uni.createFrom().item(Response.seeOther(URI.create(target))
                .header("Set-Cookie", localeCookieFactory.create(locale))
                .build());
    }

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getLogin(@QueryParam("redirect_uri") String redirectUri,
                                  @QueryParam("error") String error,
                                  @Context HttpHeaders headers) {
        AdminConsoleI18n i18n = i18nService.resolve(headers);
        return authService.createAnonymousSession()
                .map(anon -> buildLoginPage(anon.sessionId(), anon.session(), redirectUri, error, true, i18n));
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> postLogin(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("redirect_uri") String redirectUri,
            @Context HttpHeaders headers) {

        AdminConsoleI18n i18n = i18nService.resolve(headers);
        String sessionId = AdminSessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity(i18n.get("error.invalidCsrf")).build());
        }

        return authService.getSession(sessionId).flatMap(session ->
                authService.login(sessionId, session, username, password, csrfToken)
                        .flatMap(result -> {
                            if (result.outcome() == AdminConsoleAuthService.LoginAttemptResult.Outcome.SUCCESS) {
                                String target = safeRedirect(redirectUri, "/admin/console/clients");
                                return Uni.createFrom().item(Response.seeOther(URI.create(target))
                                        .header("Set-Cookie", cookieFactory.create(result.sessionId()))
                                        .build());
                            }
                            if (result.outcome() == AdminConsoleAuthService.LoginAttemptResult.Outcome.CSRF_ERROR) {
                                return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                                        .entity(i18n.get("error.invalidCsrf")).build());
                            }
                            return Uni.createFrom().item(buildLoginPage(
                                    result.sessionId(), result.session(), redirectUri,
                                    result.errorMessage(), false, i18n));
                        }));
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> logout(@FormParam("csrf_token") String csrfToken,
                                @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        String sessionId = sessionId(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity(i18n.get("error.invalidCsrf")).build());
        }
        return authService.logout(sessionId)
                .map(result -> Response.seeOther(URI.create("/admin/console/login"))
                        .header("Set-Cookie", cookieFactory.clear())
                        .build());
    }

    @GET
    @Path("/clients")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> listClients(@QueryParam("search") String search,
                                     @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminClientService.list()
                .map(clients -> {
                    List<ClientRegistrationResponse> filtered = filterClients(clients, search);
                    return renderLayout(session, i18n, "clients", i18n.get("page.clients"), null,
                            adminConsoleClientsList,
                            Map.of("clients", filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/clients/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createClientForm(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return Uni.createFrom().item(renderLayout(session, i18n, "clients", i18n.get("page.createClient"),
                List.of(breadcrumb(i18n.get("page.clients"), "/admin/console/clients"),
                        breadcrumb(i18n.get("page.createClient"), null)),
                adminConsoleClientsCreate, contentData("error", null)));
    }

    @POST
    @Path("/clients/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createClient(
            @FormParam("csrf_token") String csrfToken,
            @FormParam("client_id") String clientId,
            @FormParam("client_name") String clientName,
            @FormParam("redirect_uris") String redirectUris,
            @FormParam("scopes") String scopes,
            @FormParam("grant_types") String grantTypes,
            @FormParam("client_description") String clientDescription,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setClientId(blankToNull(clientId));
        request.setClientName(clientName);
        request.setRedirectUris(splitLines(redirectUris));
        request.setScopes(splitCsv(scopes));
        request.setGrantTypes(splitCsv(grantTypes));
        request.setClientDescription(blankToNull(clientDescription));

        return adminClientService.register(request).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(renderLayout(session, i18n, "clients", i18n.get("page.createClient"),
                        List.of(breadcrumb(i18n.get("page.clients"), "/admin/console/clients"),
                                breadcrumb(i18n.get("page.createClient"), null)),
                        adminConsoleClientsCreate,
                        contentData("error", errorMessage(result, i18n))));
            }
            ClientRegistrationResponse created = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    i18n.get("flash.clientSecretCreated"),
                    created.getClientId(), created.getClientSecret());
            return authService.setFlash(sessionId, flash)
                    .replaceWith(Response.seeOther(
                            URI.create("/admin/console/clients/" + created.getClientId() + "/credentials")).build());
        });
    }

    @GET
    @Path("/clients/{clientId}")
    public Uni<Response> clientRoot(@PathParam("clientId") String clientId) {
        return Uni.createFrom().item(
                Response.seeOther(URI.create("/admin/console/clients/" + clientId + "/settings")).build());
    }

    @GET
    @Path("/clients/{clientId}/settings")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientSettings(@PathParam("clientId") String clientId,
                                        @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(i18n));
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    session, i18n, clientId, "settings", result.value(), flash, null, null));
        });
    }

    @POST
    @Path("/clients/{clientId}/settings")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> updateClientSettings(
            @PathParam("clientId") String clientId,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("client_name") String clientName,
            @FormParam("redirect_uris") String redirectUris,
            @FormParam("scopes") String scopes,
            @FormParam("grant_types") String grantTypes,
            @FormParam("enabled") String enabled,
            @FormParam("client_description") String clientDescription,
            @FormParam("access_token_lifetime_seconds") String accessTokenLifetime,
            @FormParam("refresh_token_lifetime_seconds") String refreshTokenLifetime,
            @FormParam("session_lifetime_seconds") String sessionLifetimeSeconds,
            @FormParam("session_cookie_name") String sessionCookieName,
            @FormParam("session_cookie_secure") String sessionCookieSecure,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setClientName(clientName);
        request.setRedirectUris(splitLines(redirectUris));
        request.setScopes(splitCsv(scopes));
        request.setGrantTypes(splitCsv(grantTypes));
        request.setEnabled("on".equals(enabled));
        request.setClientDescription(clientDescription);

        Integer accessLifetime = parseOptionalPositiveInt(accessTokenLifetime);
        Integer refreshLifetime = parseOptionalPositiveInt(refreshTokenLifetime);
        Integer sessionLifetime = parseOptionalPositiveInt(sessionLifetimeSeconds);
        Boolean cookieSecure = parseOptionalSecureFlag(sessionCookieSecure);

        return adminClientService.update(clientId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(session, i18n, clientId, "settings",
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result, i18n), null));
            }
            return adminClientService.updateOAuthSettings(clientId, accessLifetime, refreshLifetime, sessionLifetime,
                            blankToNull(sessionCookieName), cookieSecure)
                    .map(lifetimeResult -> {
                        if (!lifetimeResult.isSuccess()) {
                            return renderClientDetail(session, i18n, clientId, "settings", lifetimeResult.value(),
                                    null, errorMessage(lifetimeResult, i18n), null);
                        }
                        return renderClientDetail(session, i18n, clientId, "settings", lifetimeResult.value(),
                                null, null, i18n.get("success.clientUpdated"));
                    });
        });
    }

    @GET
    @Path("/settings")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> serverSettings(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminServerSettingsService.get().map(result -> {
            if (!result.isSuccess()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(i18n.get("error.loadServerSettings")).build();
            }
            Map<String, Object> data = new HashMap<>();
            data.put("settings", result.value());
            data.put("success", null);
            data.put("error", null);
            return renderLayout(session, i18n, "settings", i18n.get("page.serverSettings"),
                    List.of(), adminConsoleServerSettings, data);
        });
    }

    @POST
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> updateServerSettings(
            @FormParam("csrf_token") String csrfToken,
            @FormParam("oauth_session_cookie_name") String oauthSessionCookieName,
            @FormParam("oauth_session_lifetime_seconds") int oauthSessionLifetimeSeconds,
            @FormParam("default_access_token_lifetime_seconds") int defaultAccessTokenLifetimeSeconds,
            @FormParam("default_refresh_token_lifetime_seconds") int defaultRefreshTokenLifetimeSeconds,
            @FormParam("session_cookie_secure") String sessionCookieSecure,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        ServerSettingsView view = new ServerSettingsView(
                oauthSessionCookieName,
                oauthSessionLifetimeSeconds,
                defaultAccessTokenLifetimeSeconds,
                defaultRefreshTokenLifetimeSeconds,
                "on".equals(sessionCookieSecure));

        return adminServerSettingsService.update(view).map(result -> {
            Map<String, Object> data = new HashMap<>();
            data.put("settings", result.isSuccess() ? result.value() : view);
            data.put("error", result.isSuccess() ? null : errorMessage(result, i18n));
            data.put("success", result.isSuccess() ? i18n.get("success.serverSettingsUpdated") : null);
            return renderLayout(session, i18n, "settings", i18n.get("page.serverSettings"),
                    List.of(), adminConsoleServerSettings, data);
        });
    }

    @GET
    @Path("/clients/{clientId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientCredentials(@PathParam("clientId") String clientId,
                                           @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(i18n));
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    session, i18n, clientId, "credentials", result.value(), flash, null, null));
        });
    }

    @POST
    @Path("/clients/{clientId}/credentials")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> regenerateClientSecret(
            @PathParam("clientId") String clientId,
            @FormParam("csrf_token") String csrfToken,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        return adminClientService.regenerateSecret(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(session, i18n, clientId, "credentials",
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result, i18n), null));
            }
            ClientRegistrationResponse updated = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    i18n.get("flash.clientSecretRegenerated"),
                    updated.getClientId(), updated.getClientSecret());
            return authService.setFlash(sessionId, flash)
                    .replaceWith(Response.seeOther(
                            URI.create("/admin/console/clients/" + clientId + "/credentials")).build());
        });
    }

    @GET
    @Path("/clients/{clientId}/users")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientUsers(@PathParam("clientId") String clientId,
                                     @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(i18n));
            }
            return membershipService.listUsersForClient(clientId)
                    .map(assignments -> renderClientDetail(session, i18n, clientId, "users",
                            result.value(), null, null, null, assignments));
        });
    }

    @POST
    @Path("/clients/{clientId}/users")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> updateClientUsers(
            @PathParam("clientId") String clientId,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("user_ids") List<String> userIds,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        List<UUID> parsedUserIds = parseUserIds(userIds);
        return membershipService.replaceClientUsers(clientId, parsedUserIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).flatMap(clientResult ->
                        membershipService.listUsersForClient(clientId)
                                .map(assignments -> renderClientDetail(session, i18n, clientId, "users",
                                        clientResult.isSuccess() ? clientResult.value() : null,
                                        null, errorMessage(result, i18n), null, assignments)));
            }
            return adminClientService.get(clientId).flatMap(clientResult ->
                    membershipService.listUsersForClient(clientId)
                            .map(assignments -> renderClientDetail(session, i18n, clientId, "users",
                                    clientResult.isSuccess() ? clientResult.value() : null,
                                    null, null, i18n.get("success.userAssignmentsUpdated"), assignments)));
        });
    }

    @GET
    @Path("/users")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> listUsers(@QueryParam("search") String search,
                                   @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminUserService.list()
                .map(users -> {
                    List<UserResponse> filtered = filterUsers(users, search);
                    return renderLayout(session, i18n, "users", i18n.get("page.users"), null,
                            adminConsoleUsersList,
                            Map.of("users", filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/users/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createUserForm(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return Uni.createFrom().item(renderLayout(session, i18n, "users", i18n.get("page.createUser"),
                List.of(breadcrumb(i18n.get("page.users"), "/admin/console/users"),
                        breadcrumb(i18n.get("page.createUser"), null)),
                adminConsoleUsersCreate, contentData("error", null)));
    }

    @POST
    @Path("/users/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createUser(
            @FormParam("csrf_token") String csrfToken,
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("email") String email,
            @FormParam("roles") String roles,
            @FormParam("enabled") String enabled,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail(blankToNull(email));
        request.setRoles(splitCsv(roles));
        request.setEnabled(enabled == null || "on".equals(enabled));

        return adminUserService.create(request).map(result -> {
            if (!result.isSuccess()) {
                return renderLayout(session, i18n, "users", i18n.get("page.createUser"),
                        List.of(breadcrumb(i18n.get("page.users"), "/admin/console/users"),
                                breadcrumb(i18n.get("page.createUser"), null)),
                        adminConsoleUsersCreate,
                        contentData("error", errorMessage(result, i18n)));
            }
            UserResponse created = result.value();
            return Response.seeOther(URI.create("/admin/console/users/" + created.getId() + "/details")).build();
        });
    }

    @GET
    @Path("/users/{userId}")
    public Uni<Response> userRoot(@PathParam("userId") UUID userId) {
        return Uni.createFrom().item(
                Response.seeOther(URI.create("/admin/console/users/" + userId + "/details")).build());
    }

    @GET
    @Path("/users/{userId}/details")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userDetails(@PathParam("userId") UUID userId,
                                     @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound(i18n);
            }
            return renderUserDetail(session, i18n, userId, "details", result.value(), null, null);
        });
    }

    @POST
    @Path("/users/{userId}/details")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> updateUserDetails(
            @PathParam("userId") UUID userId,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("email") String email,
            @FormParam("roles") String roles,
            @FormParam("enabled") String enabled,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(email);
        request.setRoles(splitCsv(roles));
        request.setEnabled("on".equals(enabled));

        return adminUserService.update(userId, request).map(result -> {
            if (!result.isSuccess()) {
                return renderUserDetail(session, i18n, userId, "details", null, errorMessage(result, i18n), null);
            }
            return renderUserDetail(session, i18n, userId, "details", result.value(),
                    null, i18n.get("success.userUpdated"));
        });
    }

    @GET
    @Path("/users/{userId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userCredentials(@PathParam("userId") UUID userId,
                                         @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound(i18n);
            }
            return renderUserDetail(session, i18n, userId, "credentials", result.value(), null, null);
        });
    }

    @POST
    @Path("/users/{userId}/credentials")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> resetUserPassword(
            @PathParam("userId") UUID userId,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("new_password") String newPassword,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword(newPassword);

        return adminUserService.changePassword(userId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).map(userResult ->
                        renderUserDetail(session, i18n, userId, "credentials",
                                userResult.isSuccess() ? userResult.value() : null,
                                errorMessage(result, i18n), null));
            }
            return adminUserService.get(userId).map(userResult ->
                    renderUserDetail(session, i18n, userId, "credentials", userResult.value(),
                            null, i18n.get("success.passwordUpdated")));
        });
    }

    @GET
    @Path("/users/{userId}/clients")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userClients(@PathParam("userId") UUID userId,
                                     @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        return adminUserService.get(userId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(i18n));
            }
            return membershipService.listClientsForUser(userId)
                    .map(assignments -> renderUserDetail(session, i18n, userId, "clients",
                            result.value(), null, null, assignments));
        });
    }

    @POST
    @Path("/users/{userId}/clients")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> updateUserClients(
            @PathParam("userId") UUID userId,
            @FormParam("csrf_token") String csrfToken,
            @FormParam("client_ids") List<String> clientIds,
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        AdminConsoleI18n i18n = i18n(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(i18n));
        }

        return membershipService.replaceUserClients(userId, clientIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).flatMap(userResult ->
                        membershipService.listClientsForUser(userId)
                                .map(assignments -> renderUserDetail(session, i18n, userId, "clients",
                                        userResult.isSuccess() ? userResult.value() : null,
                                        errorMessage(result, i18n), null, assignments)));
            }
            return adminUserService.get(userId).flatMap(userResult ->
                    membershipService.listClientsForUser(userId)
                            .map(assignments -> renderUserDetail(session, i18n, userId, "clients",
                                    userResult.isSuccess() ? userResult.value() : null,
                                    null, i18n.get("success.clientAssignmentsUpdated"), assignments)));
        });
    }

    private Uni<AdminFlashData> consumeFlash(String sessionId) {
        return authService.consumeFlash(sessionId);
    }

    private Response renderClientDetail(AdminSessionData session, AdminConsoleI18n i18n, String clientId,
                                        String activeTab, ClientRegistrationResponse client, AdminFlashData flash,
                                        String error, String success) {
        return renderClientDetail(session, i18n, clientId, activeTab, client, flash, error, success, null);
    }

    private Response renderClientDetail(AdminSessionData session, AdminConsoleI18n i18n, String clientId,
                                        String activeTab, ClientRegistrationResponse client, AdminFlashData flash,
                                        String error, String success,
                                        List<MembershipAssignmentView> assignments) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb(i18n.get("page.clients"), "/admin/console/clients"),
                breadcrumb(clientId, "/admin/console/clients/" + clientId + "/settings"),
                breadcrumb(i18n.tabLabel(activeTab), null));

        Template contentTemplate = switch (activeTab) {
            case "settings" -> adminConsoleClientsDetailSettings;
            case "users" -> adminConsoleClientsDetailUsers;
            default -> adminConsoleClientsDetailCredentials;
        };

        Map<String, Object> data = new HashMap<>();
        data.put("client", client);
        data.put("clientId", clientId);
        data.put("activeTab", activeTab);
        data.put("flash", flash);
        data.put("error", error);
        data.put("success", success);
        if (assignments != null) {
            data.put("assignments", assignments);
        }

        return renderLayout(session, i18n, "clients", clientId, breadcrumbs, contentTemplate, data);
    }

    private Response renderUserDetail(AdminSessionData session, AdminConsoleI18n i18n, UUID userId,
                                      String activeTab, UserResponse user, String error, String success) {
        return renderUserDetail(session, i18n, userId, activeTab, user, error, success, null);
    }

    private Response renderUserDetail(AdminSessionData session, AdminConsoleI18n i18n, UUID userId,
                                      String activeTab, UserResponse user, String error, String success,
                                      List<MembershipAssignmentView> assignments) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb(i18n.get("page.users"), "/admin/console/users"),
                breadcrumb(user != null ? user.getUsername() : userId.toString(),
                        "/admin/console/users/" + userId + "/details"),
                breadcrumb(i18n.tabLabel(activeTab), null));

        Template contentTemplate = switch (activeTab) {
            case "details" -> adminConsoleUsersDetailDetails;
            case "clients" -> adminConsoleUsersDetailClients;
            default -> adminConsoleUsersDetailCredentials;
        };

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("userId", userId);
        data.put("activeTab", activeTab);
        data.put("error", error);
        data.put("success", success);
        if (assignments != null) {
            data.put("assignments", assignments);
        }

        return renderLayout(session, i18n, "users",
                user != null ? user.getUsername() : i18n.get("page.user"), breadcrumbs, contentTemplate, data);
    }

    private Response renderLayout(AdminSessionData session, AdminConsoleI18n i18n, String navSection,
                                  String pageTitle, List<Map<String, String>> breadcrumbs,
                                  Template contentTemplate, Map<String, Object> contentData) {
        TemplateInstance content = contentTemplate.instance();
        contentData.forEach(content::data);
        content.data("csrfToken", session.getCsrfToken());
        content.data("i18n", i18n);

        TemplateInstance layout = adminConsoleLayout.instance();
        layout.data("navSection", navSection);
        layout.data("pageTitle", pageTitle);
        layout.data("username", session.getUsername());
        layout.data("csrfToken", session.getCsrfToken());
        layout.data("breadcrumbs", breadcrumbs != null ? breadcrumbs : List.of());
        layout.data("content", new RawString(content.render()));
        layout.data("i18n", i18n);
        layout.data("locale", i18n.locale().code());
        layout.data("htmlLang", i18n.locale().code());
        return Response.ok(layout.render()).type(MediaType.TEXT_HTML).build();
    }

    private Response buildLoginPage(String sessionId, AdminSessionData session, String redirectUri,
                                    String error, boolean issueCookie, AdminConsoleI18n i18n) {
        TemplateInstance page = adminConsoleLogin.instance();
        page.data("csrfToken", session.getCsrfToken());
        page.data("redirectUri", redirectUri);
        page.data("error", translateLoginError(error, i18n));
        page.data("i18n", i18n);
        page.data("locale", i18n.locale().code());
        page.data("htmlLang", i18n.locale().code());
        Response.ResponseBuilder response = Response.ok(page.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", cookieFactory.create(sessionId));
        } else {
            response.header("Set-Cookie", cookieFactory.create(sessionId));
        }
        return response.build();
    }

    private AdminConsoleI18n i18n(ContainerRequestContext ctx) {
        return i18nService.resolve(ctx.getHeaders());
    }

    private static String translateLoginError(String error, AdminConsoleI18n i18n) {
        if (error == null) {
            return null;
        }
        return switch (error) {
            case "invalid_credentials" -> i18n.get("error.invalidCredentials");
            case "access_denied" -> i18n.get("error.accessDenied");
            case "invalid_csrf" -> i18n.get("error.invalidCsrf");
            default -> error;
        };
    }

    private static String localeRedirectTarget(String redirect, HttpHeaders headers) {
        if (redirect != null && redirect.startsWith("/admin/console")
                && !redirect.startsWith("/admin/console/login")
                && !redirect.startsWith("/admin/console/locale")) {
            return redirect;
        }
        String referer = headers.getHeaderString("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getPath();
                if (path != null && path.startsWith("/admin/console")
                        && !path.equals("/admin/console/locale")) {
                    String query = uri.getQuery();
                    return query != null ? path + "?" + query : path;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return "/admin/console/clients";
    }

    private static AdminSessionData session(ContainerRequestContext ctx) {
        return (AdminSessionData) ctx.getProperty(AdminConsoleAuthService.SESSION_PROPERTY);
    }

    private static String sessionId(ContainerRequestContext ctx) {
        return (String) ctx.getProperty(AdminConsoleAuthService.SESSION_ID_PROPERTY);
    }

    private static String safeRedirect(String redirectUri, String fallback) {
        if (redirectUri != null && redirectUri.startsWith("/admin/console")
                && !redirectUri.startsWith("/admin/console/login")) {
            return redirectUri;
        }
        return fallback;
    }

    private static List<ClientRegistrationResponse> filterClients(List<ClientRegistrationResponse> clients,
                                                                  String search) {
        if (search == null || search.isBlank()) {
            return clients;
        }
        String q = search.toLowerCase(Locale.ROOT);
        return clients.stream()
                .filter(c -> c.getClientId().toLowerCase(Locale.ROOT).contains(q)
                        || c.getClientName().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toList());
    }

    private static List<UserResponse> filterUsers(List<UserResponse> users, String search) {
        if (search == null || search.isBlank()) {
            return users;
        }
        String q = search.toLowerCase(Locale.ROOT);
        return users.stream()
                .filter(u -> u.getUsername().toLowerCase(Locale.ROOT).contains(q)
                        || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(q)))
                .collect(Collectors.toList());
    }

    private static Integer parseOptionalPositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private static Boolean parseOptionalSecureFlag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "true".equals(value);
    }

    private static List<UUID> parseUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> parsed = new ArrayList<>();
        for (String userId : userIds) {
            if (userId != null && !userId.isBlank()) {
                parsed.add(UUID.fromString(userId.trim()));
            }
        }
        return parsed.stream().distinct().toList();
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> breadcrumb(String label, String href) {
        Map<String, String> crumb = new HashMap<>();
        crumb.put("label", label);
        crumb.put("href", href);
        return crumb;
    }

    private static Map<String, Object> contentData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }

    private static Response forbiddenCsrf(AdminConsoleI18n i18n) {
        return Response.status(Response.Status.FORBIDDEN).entity(i18n.get("error.invalidCsrf")).build();
    }

    private static Response notFound(AdminConsoleI18n i18n) {
        return Response.status(Response.Status.NOT_FOUND).entity(i18n.get("error.notFound")).build();
    }

    @SuppressWarnings("unchecked")
    private static String errorMessage(AdminServiceResult<?> result, AdminConsoleI18n i18n) {
        Object entity = result.toResponse().getEntity();
        if (entity instanceof Map<?, ?> map && map.get("error_description") != null) {
            return map.get("error_description").toString();
        }
        return i18n.get("error.requestFailed");
    }
}
