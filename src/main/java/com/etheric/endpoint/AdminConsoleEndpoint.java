package com.etheric.endpoint;

import com.etheric.admin.AdminConsoleI18n;
import com.etheric.admin.AdminConsoleI18nService;
import com.etheric.admin.AdminConsoleLocale;
import com.etheric.config.EthericAdminConfig;
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

/**
 * Keycloak-style admin console ({@code /admin/console/**}).
 */
@Path(AdminConsoleEndpoint.CONSOLE_PATH)
public class AdminConsoleEndpoint {

    /**
     * JAX-RS {@code @Path} requires a compile-time constant. Keep in sync with
     * {@code etheric.admin.console-path}.
     */
    static final String CONSOLE_PATH = "/admin/console";

    private static final String SETTINGS = "settings";
    private static final String ERROR = "error";
    private static final String PAGE_CLIENTS = "page.clients";
    private static final String ERROR_INVALID_CSRF = "error.invalidCsrf";
    private static final String DETAILS = "details";
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String PAGE_CREATE_USER = "page.createUser";
    private static final String USERS = "users";
    private static final String CLIENTS = "clients";
    private static final String CREDENTIALS = "credentials";
    private static final String PAGE_USERS = "page.users";
    private static final String PAGE_CREATE_CLIENT = "page.createClient";
    private static final String CSRF_TOKEN = "csrfToken";

    private final Template adminConsoleLogin;
    private final Template adminConsoleLayout;
    private final Template adminConsoleClientsList;
    private final Template adminConsoleClientsCreate;
    private final Template adminConsoleClientsDetailSettings;
    private final Template adminConsoleClientsDetailCredentials;
    private final Template adminConsoleClientsDetailUsers;
    private final Template adminConsoleUsersList;
    private final Template adminConsoleUsersCreate;
    private final Template adminConsoleUsersDetailDetails;
    private final Template adminConsoleUsersDetailCredentials;
    private final Template adminConsoleUsersDetailClients;
    private final AdminConsoleAuthService authService;
    private final AdminClientService adminClientService;
    private final AdminUserService adminUserService;
    private final UserClientMembershipService membershipService;
    private final TokenPolicyService tokenPolicyService;
    private final AdminSessionCookieFactory cookieFactory;
    private final AdminConsoleI18nService i18nService;
    private final AdminLocaleCookieFactory localeCookieFactory;
    private final EthericAdminConfig adminConfig;

    @Inject
    public AdminConsoleEndpoint(
            @Location("admin/console/login") Template adminConsoleLogin,
            @Location("admin/console/layout") Template adminConsoleLayout,
            @Location("admin/console/clients/list") Template adminConsoleClientsList,
            @Location("admin/console/clients/create") Template adminConsoleClientsCreate,
            @Location("admin/console/clients/detail-settings") Template adminConsoleClientsDetailSettings,
            @Location("admin/console/clients/detail-credentials") Template adminConsoleClientsDetailCredentials,
            @Location("admin/console/clients/detail-users") Template adminConsoleClientsDetailUsers,
            @Location("admin/console/users/list") Template adminConsoleUsersList,
            @Location("admin/console/users/create") Template adminConsoleUsersCreate,
            @Location("admin/console/users/detail-details") Template adminConsoleUsersDetailDetails,
            @Location("admin/console/users/detail-credentials") Template adminConsoleUsersDetailCredentials,
            @Location("admin/console/users/detail-clients") Template adminConsoleUsersDetailClients,
            AdminConsoleAuthService authService,
            AdminClientService adminClientService,
            AdminUserService adminUserService,
            UserClientMembershipService membershipService,
            TokenPolicyService tokenPolicyService,
            AdminSessionCookieFactory cookieFactory,
            AdminConsoleI18nService i18nService,
            AdminLocaleCookieFactory localeCookieFactory,
            EthericAdminConfig adminConfig) {
        this.adminConsoleLogin = adminConsoleLogin;
        this.adminConsoleLayout = adminConsoleLayout;
        this.adminConsoleClientsList = adminConsoleClientsList;
        this.adminConsoleClientsCreate = adminConsoleClientsCreate;
        this.adminConsoleClientsDetailSettings = adminConsoleClientsDetailSettings;
        this.adminConsoleClientsDetailCredentials = adminConsoleClientsDetailCredentials;
        this.adminConsoleClientsDetailUsers = adminConsoleClientsDetailUsers;
        this.adminConsoleUsersList = adminConsoleUsersList;
        this.adminConsoleUsersCreate = adminConsoleUsersCreate;
        this.adminConsoleUsersDetailDetails = adminConsoleUsersDetailDetails;
        this.adminConsoleUsersDetailCredentials = adminConsoleUsersDetailCredentials;
        this.adminConsoleUsersDetailClients = adminConsoleUsersDetailClients;
        this.authService = authService;
        this.adminClientService = adminClientService;
        this.adminUserService = adminUserService;
        this.membershipService = membershipService;
        this.tokenPolicyService = tokenPolicyService;
        this.cookieFactory = cookieFactory;
        this.i18nService = i18nService;
        this.localeCookieFactory = localeCookieFactory;
        this.adminConfig = adminConfig;
    }

    @GET
    public Uni<Response> index() {
        return Uni.createFrom().item(Response.seeOther(clientsUri()).build());
    }

    @GET
    @Path("/locale")
    public Uni<Response> setLocale(@QueryParam("lang") String lang,
                                   @QueryParam("redirect") String redirect,
                                   @Context HttpHeaders headers) {
        AdminConsoleLocale locale = AdminConsoleLocale.parse(lang);
        String target = localeRedirectTarget(redirect, headers);
        return Uni.createFrom().item(Response.seeOther(toUri(target))
                .header(SET_COOKIE, localeCookieFactory.create(locale))
                .build());
    }

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getLogin(@QueryParam("redirect_uri") String redirectUri,
                                  @QueryParam(ERROR) String error,
                                  @Context HttpHeaders headers) {
        AdminConsoleI18n i18n = i18nService.resolve(headers);
        return authService.createAnonymousSession()
                .map(anon -> buildLoginPage(anon.sessionId(), anon.session(), redirectUri, error, i18n));
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
                    .entity(i18n.get(ERROR_INVALID_CSRF)).build());
        }

        return authService.getSession(sessionId).flatMap(session ->
                authService.login(sessionId, session, username, password, csrfToken)
                        .flatMap(result -> {
                            if (result.outcome() == AdminConsoleAuthService.LoginAttemptResult.Outcome.SUCCESS) {
                                String target = safeRedirect(redirectUri, adminConfig.clientsPath());
                                return Uni.createFrom().item(Response.seeOther(toUri(target))
                                        .header(SET_COOKIE, cookieFactory.create(result.sessionId()))
                                        .build());
                            }
                            if (result.outcome() == AdminConsoleAuthService.LoginAttemptResult.Outcome.CSRF_ERROR) {
                                return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN)
                                        .entity(i18n.get(ERROR_INVALID_CSRF)).build());
                            }
                            return Uni.createFrom().item(buildLoginPage(
                                    result.sessionId(), result.session(), redirectUri,
                                    result.errorMessage(), i18n));
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
                    .entity(i18n.get(ERROR_INVALID_CSRF)).build());
        }
        return authService.logout(sessionId)
                .map(result -> Response.seeOther(loginUri())
                        .header(SET_COOKIE, cookieFactory.clear())
                        .build());
    }

    @GET
    @Path("/clients")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> listClients(@QueryParam("search") String search,
                                     @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminClientService.list()
                .map(clients -> {
                    List<ClientRegistrationResponse> filtered = filterClients(clients, search);
                    return renderLayout(ctx, CLIENTS, ctx.i18n().get(PAGE_CLIENTS), null,
                            adminConsoleClientsList,
                            Map.of(CLIENTS, filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/clients/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createClientForm(@Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        Map<String, Object> data = contentData(ERROR, null);
        data.put("defaults", tokenPolicyService.defaultOAuthPolicy());
        return Uni.createFrom().item(renderCreateClientPage(ctx, data));
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
            @FormParam("access_token_lifetime_seconds") String accessTokenLifetime,
            @FormParam("refresh_token_lifetime_seconds") String refreshTokenLifetime,
            @FormParam("session_lifetime_seconds") String sessionLifetimeSeconds,
            @FormParam("session_cookie_name") String sessionCookieName,
            @FormParam("session_cookie_secure") String sessionCookieSecure,
            @Context ContainerRequestContext requestContext) {

        PageRenderContext ctx = pageContext(requestContext);
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setClientId(blankToNull(clientId));
        request.setClientName(clientName);
        request.setRedirectUris(splitLines(redirectUris));
        request.setScopes(splitCsv(scopes));
        request.setGrantTypes(splitCsv(grantTypes));
        request.setClientDescription(blankToNull(clientDescription));
        request.setAccessTokenLifetimeSeconds(parseRequiredPositiveInt(accessTokenLifetime));
        request.setRefreshTokenLifetimeSeconds(parseRequiredPositiveInt(refreshTokenLifetime));
        request.setSessionLifetimeSeconds(parseRequiredPositiveInt(sessionLifetimeSeconds));
        request.setSessionCookieName(blankToNull(sessionCookieName));
        request.setSessionCookieSecure("on".equals(sessionCookieSecure));

        return adminClientService.register(request).flatMap(result -> {
            if (!result.isSuccess()) {
                Map<String, Object> data = contentData(ERROR, errorMessage(result, ctx.i18n()));
                data.put("defaults", tokenPolicyService.defaultOAuthPolicy());
                return Uni.createFrom().item(renderCreateClientPage(ctx, data));
            }
            ClientRegistrationResponse created = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    ctx.i18n().get("flash.clientSecretCreated"),
                    created.getClientId(), created.getClientSecret());
            return authService.setFlash(sessionId, flash)
                    .replaceWith(Response.seeOther(clientTabUri(created.getClientId(), CREDENTIALS)).build());
        });
    }

    @GET
    @Path("/clients/{clientId}")
    public Uni<Response> clientRoot(@PathParam("clientId") String clientId) {
        return Uni.createFrom().item(
                Response.seeOther(clientTabUri(clientId, SETTINGS)).build());
    }

    @GET
    @Path("/clients/{clientId}/settings")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientSettings(@PathParam("clientId") String clientId,
                                        @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(ctx.i18n()));
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    ctx, ClientDetailView.of(clientId, SETTINGS, result.value(), flash, null, null)));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setClientName(clientName);
        request.setRedirectUris(splitLines(redirectUris));
        request.setScopes(splitCsv(scopes));
        request.setGrantTypes(splitCsv(grantTypes));
        request.setEnabled("on".equals(enabled));
        request.setClientDescription(clientDescription);
        request.setAccessTokenLifetimeSeconds(parseRequiredPositiveInt(accessTokenLifetime));
        request.setRefreshTokenLifetimeSeconds(parseRequiredPositiveInt(refreshTokenLifetime));
        request.setSessionLifetimeSeconds(parseRequiredPositiveInt(sessionLifetimeSeconds));
        request.setSessionCookieName(blankToNull(sessionCookieName));
        request.setSessionCookieSecure("on".equals(sessionCookieSecure));

        return adminClientService.update(clientId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(ctx, ClientDetailView.of(clientId, SETTINGS,
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result, ctx.i18n()), null)));
            }
            return Uni.createFrom().item(renderClientDetail(ctx, ClientDetailView.of(clientId, SETTINGS,
                    result.value(), null, null, ctx.i18n().get("success.clientUpdated"))));
        });
    }

    @GET
    @Path("/clients/{clientId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientCredentials(@PathParam("clientId") String clientId,
                                           @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        String sessionId = sessionId(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(ctx.i18n()));
            }
            return consumeFlash(sessionId).map(flash -> renderClientDetail(
                    ctx, ClientDetailView.of(clientId, CREDENTIALS, result.value(), flash, null, null)));
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

        PageRenderContext ctx = pageContext(requestContext);
        String sessionId = sessionId(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        return adminClientService.regenerateSecret(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).map(clientResult ->
                        renderClientDetail(ctx, ClientDetailView.of(clientId, CREDENTIALS,
                                clientResult.isSuccess() ? clientResult.value() : null,
                                null, errorMessage(result, ctx.i18n()), null)));
            }
            ClientRegistrationResponse updated = result.value();
            AdminFlashData flash = new AdminFlashData("client_secret",
                    ctx.i18n().get("flash.clientSecretRegenerated"),
                    updated.getClientId(), updated.getClientSecret());
            return authService.setFlash(sessionId, flash)
                    .replaceWith(Response.seeOther(clientTabUri(clientId, CREDENTIALS)).build());
        });
    }

    @GET
    @Path("/clients/{clientId}/users")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> clientUsers(@PathParam("clientId") String clientId,
                                     @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminClientService.get(clientId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(ctx.i18n()));
            }
            return membershipService.listUsersForClient(clientId)
                    .map(assignments -> renderClientDetail(ctx, new ClientDetailView(clientId, USERS,
                            result.value(), null, null, null, assignments)));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        List<UUID> parsedUserIds = parseUserIds(userIds);
        return membershipService.replaceClientUsers(clientId, parsedUserIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminClientService.get(clientId).flatMap(clientResult ->
                        membershipService.listUsersForClient(clientId)
                                .map(assignments -> renderClientDetail(ctx, new ClientDetailView(clientId, USERS,
                                        clientResult.isSuccess() ? clientResult.value() : null,
                                        null, errorMessage(result, ctx.i18n()), null, assignments))));
            }
            return adminClientService.get(clientId).flatMap(clientResult ->
                    membershipService.listUsersForClient(clientId)
                            .map(assignments -> renderClientDetail(ctx, new ClientDetailView(clientId, USERS,
                                    clientResult.isSuccess() ? clientResult.value() : null,
                                    null, null, ctx.i18n().get("success.userAssignmentsUpdated"), assignments))));
        });
    }

    @GET
    @Path("/users")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> listUsers(@QueryParam("search") String search,
                                   @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminUserService.list()
                .map(users -> {
                    List<UserResponse> filtered = filterUsers(users, search);
                    return renderLayout(ctx, USERS, ctx.i18n().get(PAGE_USERS), null,
                            adminConsoleUsersList,
                            Map.of(USERS, filtered, "search", search != null ? search : ""));
                });
    }

    @GET
    @Path("/users/create")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> createUserForm(@Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return Uni.createFrom().item(renderCreateUserPage(ctx, contentData(ERROR, null)));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail(blankToNull(email));
        request.setRoles(splitCsv(roles));
        request.setEnabled(enabled == null || "on".equals(enabled));

        return adminUserService.create(request).map(result -> {
            if (!result.isSuccess()) {
                return renderCreateUserPage(ctx, contentData(ERROR, errorMessage(result, ctx.i18n())));
            }
            UserResponse created = result.value();
            return Response.seeOther(userDetailsUri(created.getId())).build();
        });
    }

    @GET
    @Path("/users/{userId}")
    public Uni<Response> userRoot(@PathParam("userId") UUID userId) {
        return Uni.createFrom().item(
                Response.seeOther(userDetailsUri(userId)).build());
    }

    @GET
    @Path("/users/{userId}/details")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userDetails(@PathParam("userId") UUID userId,
                                     @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound(ctx.i18n());
            }
            return renderUserDetail(ctx, UserDetailView.of(userId, DETAILS, result.value(), null, null));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(email);
        request.setRoles(splitCsv(roles));
        request.setEnabled("on".equals(enabled));

        return adminUserService.update(userId, request).map(result -> {
            if (!result.isSuccess()) {
                return renderUserDetail(ctx, UserDetailView.of(userId, DETAILS, null,
                        errorMessage(result, ctx.i18n()), null));
            }
            return renderUserDetail(ctx, UserDetailView.of(userId, DETAILS, result.value(),
                    null, ctx.i18n().get("success.userUpdated")));
        });
    }

    @GET
    @Path("/users/{userId}/credentials")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userCredentials(@PathParam("userId") UUID userId,
                                         @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminUserService.get(userId).map(result -> {
            if (!result.isSuccess()) {
                return notFound(ctx.i18n());
            }
            return renderUserDetail(ctx, UserDetailView.of(userId, CREDENTIALS, result.value(), null, null));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword(newPassword);

        return adminUserService.changePassword(userId, request).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).map(userResult ->
                        renderUserDetail(ctx, UserDetailView.of(userId, CREDENTIALS,
                                userResult.isSuccess() ? userResult.value() : null,
                                errorMessage(result, ctx.i18n()), null)));
            }
            return adminUserService.get(userId).map(userResult ->
                    renderUserDetail(ctx, UserDetailView.of(userId, CREDENTIALS, userResult.value(),
                            null, ctx.i18n().get("success.passwordUpdated"))));
        });
    }

    @GET
    @Path("/users/{userId}/clients")
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> userClients(@PathParam("userId") UUID userId,
                                     @Context ContainerRequestContext requestContext) {
        PageRenderContext ctx = pageContext(requestContext);
        return adminUserService.get(userId).flatMap(result -> {
            if (!result.isSuccess()) {
                return Uni.createFrom().item(notFound(ctx.i18n()));
            }
            return membershipService.listClientsForUser(userId)
                    .map(assignments -> renderUserDetail(ctx, new UserDetailView(userId, CLIENTS,
                            result.value(), null, null, assignments)));
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

        PageRenderContext ctx = pageContext(requestContext);
        if (!authService.validateCsrf(ctx.session(), csrfToken)) {
            return Uni.createFrom().item(forbiddenCsrf(ctx.i18n()));
        }

        return membershipService.replaceUserClients(userId, clientIds).flatMap(result -> {
            if (!result.isSuccess()) {
                return adminUserService.get(userId).flatMap(userResult ->
                        membershipService.listClientsForUser(userId)
                                .map(assignments -> renderUserDetail(ctx, new UserDetailView(userId, CLIENTS,
                                        userResult.isSuccess() ? userResult.value() : null,
                                        errorMessage(result, ctx.i18n()), null, assignments))));
            }
            return adminUserService.get(userId).flatMap(userResult ->
                    membershipService.listClientsForUser(userId)
                            .map(assignments -> renderUserDetail(ctx, new UserDetailView(userId, CLIENTS,
                                    userResult.isSuccess() ? userResult.value() : null,
                                    null, ctx.i18n().get("success.clientAssignmentsUpdated"), assignments))));
        });
    }

    private Uni<AdminFlashData> consumeFlash(String sessionId) {
        return authService.consumeFlash(sessionId);
    }

    private PageRenderContext pageContext(ContainerRequestContext requestContext) {
        return new PageRenderContext(session(requestContext), i18n(requestContext));
    }

    private Response renderCreateClientPage(PageRenderContext ctx, Map<String, Object> data) {
        AdminConsoleI18n i18n = ctx.i18n();
        return renderLayout(ctx, CLIENTS, i18n.get(PAGE_CREATE_CLIENT),
                List.of(breadcrumb(i18n.get(PAGE_CLIENTS), adminConfig.clientsPath()),
                        breadcrumb(i18n.get(PAGE_CREATE_CLIENT), null)),
                adminConsoleClientsCreate, data);
    }

    private Response renderCreateUserPage(PageRenderContext ctx, Map<String, Object> data) {
        AdminConsoleI18n i18n = ctx.i18n();
        return renderLayout(ctx, USERS, i18n.get(PAGE_CREATE_USER),
                List.of(breadcrumb(i18n.get(PAGE_USERS), adminConfig.usersPath()),
                        breadcrumb(i18n.get(PAGE_CREATE_USER), null)),
                adminConsoleUsersCreate, data);
    }

    private Response renderClientDetail(PageRenderContext ctx, ClientDetailView view) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb(ctx.i18n().get(PAGE_CLIENTS), adminConfig.clientsPath()),
                breadcrumb(view.clientId(), adminConfig.clientTabPath(view.clientId(), SETTINGS)),
                breadcrumb(ctx.i18n().tabLabel(view.activeTab()), null));

        Template contentTemplate = switch (view.activeTab()) {
            case SETTINGS -> adminConsoleClientsDetailSettings;
            case USERS -> adminConsoleClientsDetailUsers;
            default -> adminConsoleClientsDetailCredentials;
        };

        Map<String, Object> data = new HashMap<>();
        data.put("client", view.client());
        data.put("clientId", view.clientId());
        data.put("activeTab", view.activeTab());
        data.put("flash", view.flash());
        data.put(ERROR, view.error());
        data.put("success", view.success());
        if (view.assignments() != null) {
            data.put("assignments", view.assignments());
        }

        return renderLayout(ctx, CLIENTS, view.clientId(), breadcrumbs, contentTemplate, data);
    }

    private Response renderUserDetail(PageRenderContext ctx, UserDetailView view) {
        List<Map<String, String>> breadcrumbs = List.of(
                breadcrumb(ctx.i18n().get(PAGE_USERS), adminConfig.usersPath()),
                breadcrumb(view.user() != null ? view.user().getUsername() : view.userId().toString(),
                        adminConfig.userTabPath(view.userId().toString(), DETAILS)),
                breadcrumb(ctx.i18n().tabLabel(view.activeTab()), null));

        Template contentTemplate = switch (view.activeTab()) {
            case DETAILS -> adminConsoleUsersDetailDetails;
            case CLIENTS -> adminConsoleUsersDetailClients;
            default -> adminConsoleUsersDetailCredentials;
        };

        Map<String, Object> data = new HashMap<>();
        data.put("user", view.user());
        data.put("userId", view.userId());
        data.put("activeTab", view.activeTab());
        data.put(ERROR, view.error());
        data.put("success", view.success());
        if (view.assignments() != null) {
            data.put("assignments", view.assignments());
        }

        return renderLayout(ctx, USERS,
                view.user() != null ? view.user().getUsername() : ctx.i18n().get("page.user"),
                breadcrumbs, contentTemplate, data);
    }

    private Response renderLayout(PageRenderContext ctx, String navSection,
                                  String pageTitle, List<Map<String, String>> breadcrumbs,
                                  Template contentTemplate, Map<String, Object> contentData) {
        TemplateInstance content = contentTemplate.instance();
        contentData.forEach(content::data);
        bindConsolePath(content);
        content.data(CSRF_TOKEN, ctx.session().getCsrfToken());
        content.data("i18n", ctx.i18n());

        TemplateInstance layout = adminConsoleLayout.instance();
        bindConsolePath(layout);
        layout.data("navSection", navSection);
        layout.data("pageTitle", pageTitle);
        layout.data("username", ctx.session().getUsername());
        layout.data(CSRF_TOKEN, ctx.session().getCsrfToken());
        layout.data("breadcrumbs", breadcrumbs != null ? breadcrumbs : List.of());
        layout.data("content", new RawString(content.render()));
        layout.data("i18n", ctx.i18n());
        layout.data("locale", ctx.i18n().locale().code());
        layout.data("htmlLang", ctx.i18n().locale().code());
        return Response.ok(layout.render()).type(MediaType.TEXT_HTML).build();
    }

    private Response buildLoginPage(String sessionId, AdminSessionData session, String redirectUri,
                                    String error, AdminConsoleI18n i18n) {
        TemplateInstance page = adminConsoleLogin.instance();
        bindConsolePath(page);
        page.data(CSRF_TOKEN, session.getCsrfToken());
        page.data("redirectUri", redirectUri);
        page.data(ERROR, translateLoginError(error, i18n));
        page.data("i18n", i18n);
        page.data("locale", i18n.locale().code());
        page.data("htmlLang", i18n.locale().code());
        Response.ResponseBuilder response = Response.ok(page.render()).type(MediaType.TEXT_HTML);
        response.header(SET_COOKIE, cookieFactory.create(sessionId));
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
            case "invalid_csrf" -> i18n.get(ERROR_INVALID_CSRF);
            default -> error;
        };
    }

    private void bindConsolePath(TemplateInstance template) {
        template.data("consolePath", adminConfig.consolePath());
    }

    private URI clientsUri() {
        return toUri(adminConfig.clientsPath());
    }

    private URI loginUri() {
        return toUri(adminConfig.loginPath());
    }

    private URI clientTabUri(String clientId, String tab) {
        return toUri(adminConfig.clientTabPath(clientId, tab));
    }

    private URI userDetailsUri(UUID userId) {
        return toUri(adminConfig.userTabPath(userId.toString(), DETAILS));
    }

    private URI toUri(String path) {
        return UriBuilder.fromPath(path).build();
    }

    private String localeRedirectTarget(String redirect, HttpHeaders headers) {
        if (redirect != null && redirect.startsWith(adminConfig.consolePath())
                && !redirect.startsWith(adminConfig.loginPath())
                && !redirect.startsWith(adminConfig.localePath())) {
            return redirect;
        }
        String referer = headers.getHeaderString("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getPath();
                if (path != null && path.startsWith(adminConfig.consolePath())
                        && !path.equals(adminConfig.localePath())) {
                    String query = uri.getQuery();
                    return query != null ? path + "?" + query : path;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return adminConfig.clientsPath();
    }

    private static AdminSessionData session(ContainerRequestContext ctx) {
        return (AdminSessionData) ctx.getProperty(AdminConsoleAuthService.SESSION_PROPERTY);
    }

    private static String sessionId(ContainerRequestContext ctx) {
        return (String) ctx.getProperty(AdminConsoleAuthService.SESSION_ID_PROPERTY);
    }

    private String safeRedirect(String redirectUri, String fallback) {
        if (redirectUri != null && redirectUri.startsWith(adminConfig.consolePath())
                && !redirectUri.startsWith(adminConfig.loginPath())) {
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
                .toList();
    }

    private static List<UserResponse> filterUsers(List<UserResponse> users, String search) {
        if (search == null || search.isBlank()) {
            return users;
        }
        String q = search.toLowerCase(Locale.ROOT);
        return users.stream()
                .filter(u -> u.getUsername().toLowerCase(Locale.ROOT).contains(q)
                        || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }

    private static Integer parseRequiredPositiveInt(String value) {
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

    private static Response forbiddenCsrf(AdminConsoleI18n i18n) {
        return Response.status(Response.Status.FORBIDDEN).entity(i18n.get(ERROR_INVALID_CSRF)).build();
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

    private record PageRenderContext(AdminSessionData session, AdminConsoleI18n i18n) {
    }

    private record ClientDetailView(String clientId, String activeTab, ClientRegistrationResponse client,
                                    AdminFlashData flash, String error, String success,
                                    List<MembershipAssignmentView> assignments) {
        static ClientDetailView of(String clientId, String activeTab, ClientRegistrationResponse client,
                                   AdminFlashData flash, String error, String success) {
            return new ClientDetailView(clientId, activeTab, client, flash, error, success, null);
        }
    }

    private record UserDetailView(UUID userId, String activeTab, UserResponse user,
                                  String error, String success, List<MembershipAssignmentView> assignments) {
        static UserDetailView of(UUID userId, String activeTab, UserResponse user, String error, String success) {
            return new UserDetailView(userId, activeTab, user, error, success, null);
        }
    }
}
