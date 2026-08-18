package com.etheric.endpoint;

import com.etheric.model.*;
import com.etheric.service.*;
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

    @GET
    public Uni<Response> index() {
        return Uni.createFrom().item(Response.seeOther(URI.create("/admin/console/clients")).build());
    }

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getLogin(@QueryParam("redirect_uri") String redirectUri,
                                  @QueryParam("error") String error) {
        return authService.createAnonymousSession()
                .map(anon -> buildLoginPage(anon.sessionId(), anon.session(), redirectUri, error, true));
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

        String sessionId = AdminSessionCookieFactory.extractSessionId(headers);
        if (sessionId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid CSRF token").build());
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
                                        .entity("Invalid CSRF token").build());
                            }
                            return Uni.createFrom().item(buildLoginPage(
                                    result.sessionId(), result.session(), redirectUri,
                                    result.errorMessage(), false));
                        }));
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> logout(@FormParam("csrf_token") String csrfToken,
                                @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid CSRF token").build());
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
        return adminClientService.list()
                .map(clients -> {
                    List<ClientRegistrationResponse> filtered = filterClients(clients, search);
                    return renderLayout(session, "clients", "Clients", null,
                            adminConsoleClientsList,
                            Map.of("clients", filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/clients/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createClientForm(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return Uni.createFrom().item(renderLayout(session, "clients", "Create client",
                List.of(breadcrumb("Clients", "/admin/console/clients"),
                        breadcrumb("Create client", null)),
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
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
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
                return Uni.createFrom().item(renderLayout(session, "clients", "Create client",
                        List.of(breadcrumb("Clients", "/admin/console/clients"),
                                breadcrumb("Create client", null)),
                        adminConsoleClientsCreate,
                        contentData("error", errorMessage(result))));
            }
            ClientRegistrationResponse created = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    "Copy the client secret now. You won't be able to see it again.",
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
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound());
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    session, clientId, "settings", result.value(), flash, null, null));
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
            @Context ContainerRequestContext requestContext) {

        AdminSessionData session = session(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
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

        return adminClientService.update(clientId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(session, clientId, "settings",
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result), null));
            }
            return adminClientService.updateTokenLifetimes(clientId, accessLifetime, refreshLifetime, sessionLifetime)
                    .map(lifetimeResult -> {
                        if (!lifetimeResult.isSuccess()) {
                            return renderClientDetail(session, clientId, "settings", lifetimeResult.value(),
                                    null, errorMessage(lifetimeResult), null);
                        }
                        return renderClientDetail(session, clientId, "settings", lifetimeResult.value(),
                                null, null, "Client updated successfully");
                    });
        });
    }

    @GET
    @Path("/settings")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> serverSettings(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return adminServerSettingsService.get().map(result -> {
            if (!result.isSuccess()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to load server settings").build();
            }
            Map<String, Object> data = new HashMap<>();
            data.put("settings", result.value());
            data.put("success", null);
            data.put("error", null);
            return renderLayout(session, "settings", "Server settings", List.of(), adminConsoleServerSettings, data);
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
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
            data.put("error", result.isSuccess() ? null : errorMessage(result));
            data.put("success", result.isSuccess() ? "Server settings updated successfully" : null);
            return renderLayout(session, "settings", "Server settings", List.of(), adminConsoleServerSettings, data);
        });
    }

    @GET
    @Path("/clients/{clientId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientCredentials(@PathParam("clientId") String clientId,
                                           @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound());
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    session, clientId, "credentials", result.value(), flash, null, null));
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
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        return adminClientService.regenerateSecret(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(session, clientId, "credentials",
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result), null));
            }
            ClientRegistrationResponse updated = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    "Copy the new client secret now. You won't be able to see it again.",
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
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound());
            }
            return membershipService.listUsersForClient(clientId)
                    .map(assignments -> renderClientDetail(session, clientId, "users",
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        List<UUID> parsedUserIds = parseUserIds(userIds);
        return membershipService.replaceClientUsers(clientId, parsedUserIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).flatMap(clientResult ->
                        membershipService.listUsersForClient(clientId)
                                .map(assignments -> renderClientDetail(session, clientId, "users",
                                        clientResult.isSuccess() ? clientResult.value() : null,
                                        null, errorMessage(result), null, assignments)));
            }
            return adminClientService.get(clientId).flatMap(clientResult ->
                    membershipService.listUsersForClient(clientId)
                            .map(assignments -> renderClientDetail(session, clientId, "users",
                                    clientResult.isSuccess() ? clientResult.value() : null,
                                    null, null, "User assignments updated successfully", assignments)));
        });
    }

    @GET
    @Path("/users")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> listUsers(@QueryParam("search") String search,
                                   @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return adminUserService.list()
                .map(users -> {
                    List<UserResponse> filtered = filterUsers(users, search);
                    return renderLayout(session, "users", "Users", null,
                            adminConsoleUsersList,
                            Map.of("users", filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/users/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createUserForm(@Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return Uni.createFrom().item(renderLayout(session, "users", "Create user",
                List.of(breadcrumb("Users", "/admin/console/users"),
                        breadcrumb("Create user", null)),
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail(blankToNull(email));
        request.setRoles(splitCsv(roles));
        request.setEnabled(enabled == null || "on".equals(enabled));

        return adminUserService.create(request).map(result -> {
            if (!result.isSuccess()) {
                return renderLayout(session, "users", "Create user",
                        List.of(breadcrumb("Users", "/admin/console/users"),
                                breadcrumb("Create user", null)),
                        adminConsoleUsersCreate,
                        contentData("error", errorMessage(result)));
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
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound();
            }
            return renderUserDetail(session, userId, "details", result.value(), null, null);
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(email);
        request.setRoles(splitCsv(roles));
        request.setEnabled("on".equals(enabled));

        return adminUserService.update(userId, request).map(result -> {
            if (!result.isSuccess()) {
                return renderUserDetail(session, userId, "details", null, errorMessage(result), null);
            }
            return renderUserDetail(session, userId, "details", result.value(),
                    null, "User updated successfully");
        });
    }

    @GET
    @Path("/users/{userId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userCredentials(@PathParam("userId") UUID userId,
                                         @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound();
            }
            return renderUserDetail(session, userId, "credentials", result.value(), null, null);
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword(newPassword);

        return adminUserService.changePassword(userId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).map(userResult ->
                        renderUserDetail(session, userId, "credentials",
                                userResult.isSuccess() ? userResult.value() : null,
                                errorMessage(result), null));
            }
            return adminUserService.get(userId).map(userResult ->
                    renderUserDetail(session, userId, "credentials", userResult.value(),
                            null, "Password updated successfully"));
        });
    }

    @GET
    @Path("/users/{userId}/clients")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userClients(@PathParam("userId") UUID userId,
                                     @Context ContainerRequestContext requestContext) {
        AdminSessionData session = session(requestContext);
        return adminUserService.get(userId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound());
            }
            return membershipService.listClientsForUser(userId)
                    .map(assignments -> renderUserDetail(session, userId, "clients",
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
        if (!authService.validateCsrf(session, csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf());
        }

        return membershipService.replaceUserClients(userId, clientIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).flatMap(userResult ->
                        membershipService.listClientsForUser(userId)
                                .map(assignments -> renderUserDetail(session, userId, "clients",
                                        userResult.isSuccess() ? userResult.value() : null,
                                        errorMessage(result), null, assignments)));
            }
            return adminUserService.get(userId).flatMap(userResult ->
                    membershipService.listClientsForUser(userId)
                            .map(assignments -> renderUserDetail(session, userId, "clients",
                                    userResult.isSuccess() ? userResult.value() : null,
                                    null, "Client assignments updated successfully", assignments)));
        });
    }

    private Uni<AdminFlashData> consumeFlash(String sessionId) {
        return authService.consumeFlash(sessionId);
    }

    private Response renderClientDetail(AdminSessionData session, String clientId, String activeTab,
                                        ClientRegistrationResponse client, AdminFlashData flash,
                                        String error, String success) {
        return renderClientDetail(session, clientId, activeTab, client, flash, error, success, null);
    }

    private Response renderClientDetail(AdminSessionData session, String clientId, String activeTab,
                                        ClientRegistrationResponse client, AdminFlashData flash,
                                        String error, String success,
                                        List<MembershipAssignmentView> assignments) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb("Clients", "/admin/console/clients"),
                breadcrumb(clientId, "/admin/console/clients/" + clientId + "/settings"),
                breadcrumb(capitalize(activeTab), null));

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

        return renderLayout(session, "clients", clientId, breadcrumbs, contentTemplate, data);
    }

    private Response renderUserDetail(AdminSessionData session, UUID userId, String activeTab,
                                      UserResponse user, String error, String success) {
        return renderUserDetail(session, userId, activeTab, user, error, success, null);
    }

    private Response renderUserDetail(AdminSessionData session, UUID userId, String activeTab,
                                      UserResponse user, String error, String success,
                                      List<MembershipAssignmentView> assignments) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb("Users", "/admin/console/users"),
                breadcrumb(user != null ? user.getUsername() : userId.toString(),
                        "/admin/console/users/" + userId + "/details"),
                breadcrumb(capitalize(activeTab), null));

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

        return renderLayout(session, "users",
                user != null ? user.getUsername() : "User", breadcrumbs, contentTemplate, data);
    }

    private Response renderLayout(AdminSessionData session, String navSection, String pageTitle,
                                  List<Map<String, String>> breadcrumbs, Template contentTemplate,
                                  Map<String, Object> contentData) {
        TemplateInstance content = contentTemplate.instance();
        contentData.forEach(content::data);
        content.data("csrfToken", session.getCsrfToken());

        TemplateInstance layout = adminConsoleLayout.instance();
        layout.data("navSection", navSection);
        layout.data("pageTitle", pageTitle);
        layout.data("username", session.getUsername());
        layout.data("csrfToken", session.getCsrfToken());
        layout.data("breadcrumbs", breadcrumbs != null ? breadcrumbs : List.of());
        layout.data("content", new RawString(content.render()));
        return Response.ok(layout.render()).type(MediaType.TEXT_HTML).build();
    }

    private Response buildLoginPage(String sessionId, AdminSessionData session, String redirectUri,
                                    String error, boolean issueCookie) {
        TemplateInstance page = adminConsoleLogin.instance();
        page.data("csrfToken", session.getCsrfToken());
        page.data("redirectUri", redirectUri);
        page.data("error", error);
        Response.ResponseBuilder response = Response.ok(page.render()).type(MediaType.TEXT_HTML);
        if (issueCookie) {
            response.header("Set-Cookie", cookieFactory.create(sessionId));
        } else {
            response.header("Set-Cookie", cookieFactory.create(sessionId));
        }
        return response.build();
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

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Response forbiddenCsrf() {
        return Response.status(Response.Status.FORBIDDEN).entity("Invalid CSRF token").build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).entity("Not found").build();
    }

    @SuppressWarnings("unchecked")
    private static String errorMessage(AdminServiceResult<?> result) {
        Object entity = result.toResponse().getEntity();
        if (entity instanceof Map<?, ?> map && map.get("error_description") != null) {
            return map.get("error_description").toString();
        }
        return "Request failed";
    }
}
