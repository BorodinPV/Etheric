package com.etheric.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OAuth 2.0 / OIDC error codes with standard descriptions.
 */
@Getter
@RequiredArgsConstructor
public enum OAuthError {

    INVALID_REQUEST("invalid_request", "The request is missing a required parameter or is otherwise malformed."),
    UNAUTHORIZED_CLIENT("unauthorized_client", "The client is not authorized for this request."),
    ACCESS_DENIED("access_denied", "The resource owner denied the request."),
    UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type", "The authorization server does not support this response type."),
    INVALID_SCOPE("invalid_scope", "The requested scope is invalid or unknown."),
    SERVER_ERROR("server_error", "The authorization server encountered an unexpected condition."),
    TEMPORARILY_UNAVAILABLE("temporarily_unavailable", "The authorization server is currently unable to handle the request."),
    INVALID_GRANT("invalid_grant", "The provided authorization grant is invalid, expired, or revoked."),
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type", "The authorization grant type is not supported."),
    INVALID_CLIENT("invalid_client", "Client authentication failed.");

    private final String error;
    private final String errorDescription;
}
