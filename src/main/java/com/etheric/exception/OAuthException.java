package com.etheric.exception;

import lombok.Getter;

/**
 * Application-level OAuth protocol error with optional redirect context.
 */
@Getter
public class OAuthException extends RuntimeException {

    private final OAuthError error;
    private final String redirectUri;
    private final String state;
    private final int httpStatus;

    public OAuthException(OAuthError error, String redirectUri, String state) {
        super(error.getErrorDescription());
        this.error = error;
        this.redirectUri = redirectUri;
        this.state = state;
        this.httpStatus = 400;
    }

    public OAuthException(OAuthError error, int httpStatus) {
        super(error.getErrorDescription());
        this.error = error;
        this.redirectUri = null;
        this.state = null;
        this.httpStatus = httpStatus;
    }

    public OAuthException(OAuthError error, String description, int httpStatus) {
        super(description);
        this.error = error;
        this.redirectUri = null;
        this.state = null;
        this.httpStatus = httpStatus;
    }

    public boolean hasRedirectUri() {
        return redirectUri != null && !redirectUri.isEmpty();
    }
}
