package com.etheric.endpoint;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Error page for unrecoverable authorization failures ({@code GET /error}).
 */
@Path("/error")
public class ErrorEndpoint {

    @Inject
    Template error;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> getError(
            @QueryParam("error") String errorCode,
            @QueryParam("description") String description) {
        TemplateInstance template = error.instance();
        template.data("error", errorCode);
        template.data("description", description);
        return Uni.createFrom().item(Response.ok(template.render()).type(MediaType.TEXT_HTML).build());
    }
}
