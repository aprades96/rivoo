package com.rivoo.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public class TenantAwareJwtAuthenticationToken extends JwtAuthenticationToken {

    private final String tenantId;
    private final String subscriptionPlan;
    private final String userEmail;

    public TenantAwareJwtAuthenticationToken(Jwt jwt,
                                              Collection<? extends GrantedAuthority> authorities,
                                              String tenantId,
                                              String subscriptionPlan,
                                              String userEmail) {
        super(jwt, authorities);
        this.tenantId = tenantId;
        this.subscriptionPlan = subscriptionPlan;
        this.userEmail = userEmail;
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
}
