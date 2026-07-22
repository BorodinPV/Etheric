package com.etheric.endpoint;

import com.etheric.service.CacheService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.*;

@Path("/logout")
public class LogoutEndpoint {

    @Inject
    CacheService cacheService;

    @GET
    public Response logout(@QueryParam("redirect_uri") String redirectUri, @Context HttpHeaders headers) {
        String sessionId = extractSessionId(headers);

        if (sessionId != null) {
            cacheService.deleteSession(sessionId);
        }

        return Response.seeOther(java.net.URI.create(redirectUri != null ? redirectUri : "/"))
                .header("Set-Cookie", "SESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
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
