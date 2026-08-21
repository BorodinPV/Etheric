package com.etheric.logging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SecurityAuditLogger {

    private static final Logger LOG = Logger.getLogger(SecurityAuditLogger.class);

    public void loginFailed(String username, String ip) {
        LOG.warnf("SECURITY_AUDIT event=login_failed username=%s ip=%s", safe(username), safe(ip));
    }

    public void loginSuccess(String userId, String ip) {
        LOG.warnf("SECURITY_AUDIT event=login_success userId=%s ip=%s", safe(userId), safe(ip));
    }

    public void consentDenied(String userId, String clientId) {
        LOG.warnf("SECURITY_AUDIT event=consent_denied userId=%s clientId=%s", safe(userId), safe(clientId));
    }

    public void accessDenied(String userId, String clientId, String reason) {
        LOG.warnf("SECURITY_AUDIT event=access_denied userId=%s clientId=%s reason=%s",
                safe(userId), safe(clientId), safe(reason));
    }

    public void refreshTokenReuse(String clientId, String ip) {
        LOG.warnf("SECURITY_AUDIT event=refresh_token_reuse clientId=%s ip=%s", safe(clientId), safe(ip));
    }

    public void tokenExchangeFailed(String reason, String clientId, String ip) {
        LOG.warnf("SECURITY_AUDIT event=token_exchange_failed reason=%s clientId=%s ip=%s",
                safe(reason), safe(clientId), safe(ip));
    }

    public static String resolveClientIp(HttpHeaders headers) {
        if (headers == null) {
            return "unknown";
        }
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private static String safe(String value) {
        return value != null ? value : "-";
    }
}
