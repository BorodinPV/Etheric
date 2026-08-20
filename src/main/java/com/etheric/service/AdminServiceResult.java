package com.etheric.service;

import jakarta.ws.rs.core.Response;

/**
 * Result wrapper for admin service operations used by JSON endpoints.
 */
public final class AdminServiceResult<T> {

    private final T value;
    private final String errorCode;
    private final String errorDescription;
    private final Response.Status status;

    private AdminServiceResult(T value, String errorCode, String errorDescription, Response.Status status) {
        this.value = value;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
        this.status = status;
    }

    public static <T> AdminServiceResult<T> ok(T value) {
        return new AdminServiceResult<>(value, null, null, null);
    }

    public static <T> AdminServiceResult<T> badRequest(String code, String description) {
        return error(code, description, Response.Status.BAD_REQUEST);
    }

    public static <T> AdminServiceResult<T> notFound(String code, String description) {
        return error(code, description, Response.Status.NOT_FOUND);
    }

    public static <T> AdminServiceResult<T> conflict(String code, String description) {
        return error(code, description, Response.Status.CONFLICT);
    }

    public static <T> AdminServiceResult<T> error(String code, String description, Response.Status status) {
        return new AdminServiceResult<>(null, code, description, status);
    }

    public boolean isSuccess() {
        return errorCode == null;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorDescription() {
        return errorDescription;
    }

    public T value() {
        return value;
    }

    public Response toResponse() {
        if (isSuccess()) {
            return Response.ok(value).build();
        }
        return Response.status(status)
                .entity(java.util.Map.of("error", errorCode, "error_description", errorDescription))
                .build();
    }

    public Response toCreatedResponse() {
        if (isSuccess()) {
            return Response.status(Response.Status.CREATED).entity(value).build();
        }
        return toResponse();
    }

    public Response toNoContentResponse() {
        if (isSuccess()) {
            return Response.noContent().build();
        }
        return toResponse();
    }
}
