package com.etheric.endpoint;

import com.etheric.logging.SecurityAuditLogger;
import com.etheric.repository.ClientRepository;
import com.etheric.service.AuthorizationCodeService;
import com.etheric.service.CacheService;
import com.etheric.service.ConsentService;
import com.etheric.service.TokenPolicyService;
import com.etheric.service.UserClientMembershipService;
import com.etheric.util.SessionCookieFactory;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * Shared collaborators for the authorize and consent HTTP endpoints.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class OAuthFlowSupport {

    private final ClientRepository clientRepository;
    private final CacheService cacheService;
    private final AuthorizationCodeService authorizationCodeService;
    private final SessionCookieFactory sessionCookieFactory;
    private final TokenPolicyService tokenPolicyService;
    private final ConsentService consentService;
    private final UserClientMembershipService membershipService;
    private final SecurityAuditLogger securityAuditLogger;

    public ClientRepository clients() {
        return clientRepository;
    }

    public CacheService cache() {
        return cacheService;
    }

    public AuthorizationCodeService codes() {
        return authorizationCodeService;
    }

    public SessionCookieFactory cookies() {
        return sessionCookieFactory;
    }

    public TokenPolicyService tokenPolicy() {
        return tokenPolicyService;
    }

    public ConsentService consent() {
        return consentService;
    }

    public UserClientMembershipService membership() {
        return membershipService;
    }

    public SecurityAuditLogger audit() {
        return securityAuditLogger;
    }
}
