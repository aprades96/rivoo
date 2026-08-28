package com.rivoo.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public class TenantAwareJwtAuthenticationToken extends JwtAuthenticationToken {

    private final String tenantId;
    private final String subscriptionPlan;
    private final String userEmail;
    private final Boolean emailVerified;

    public TenantAwareJwtAuthenticationToken(Jwt jwt,
                                              Collection<? extends GrantedAuthority> authorities,
                                              String tenantId,
                                              String subscriptionPlan,
                                              String userEmail,
                                              Boolean emailVerified) {
        super(jwt, authorities);
        this.tenantId = tenantId;
        this.subscriptionPlan = subscriptionPlan;
        this.userEmail = userEmail;
        this.emailVerified = emailVerified;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public String getUserEmail() {
        return userEmail;
    }

    /**
     * The OIDC {@code email_verified} claim, or {@code null} when the realm does not map it.
     * <p>
     * Three-valued on purpose. {@code TRUE} and a missing claim are NOT the same fact and the
     * distinction has to survive as far as whoever reads it: a realm whose {@code email} client
     * scope was not made default issues perfectly valid tokens with no such claim, and collapsing
     * that to {@code false} would look like a denial the identity provider never made.
     */
    public Boolean getEmailVerified() {
        return emailVerified;
    }
}
