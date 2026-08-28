package com.rivoo.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String tenantId = jwt.getClaimAsString("tenant_id");
        String subscriptionPlan = jwt.getClaimAsString("subscription_plan");
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = extractEmailVerified(jwt);

        return new TenantAwareJwtAuthenticationToken(
                jwt, authorities, tenantId, subscriptionPlan, email, emailVerified);
    }

    /**
     * Reads the OIDC {@code email_verified} claim, keeping "absent" distinguishable from "false".
     * <p>
     * Not {@code getClaimAsBoolean}: that one asserts the value is convertible and throws on
     * anything it does not recognise, which would turn a cosmetic claim-type quirk in an identity
     * provider into a failure to authenticate at all. Everything unrecognised degrades to
     * {@code null} — "the token says nothing about it" — which is the truthful reading and the one
     * consumers already have to handle.
     */
    private Boolean extractEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        if (claim instanceof Boolean bool) {
            return bool;
        }
        if (claim instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.valueOf(text);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) {
                for (String role : roles) {
                    if (role.startsWith("ROLE_")) {
                        authorities.add(new SimpleGrantedAuthority(role));
                    } else {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
        }

        return authorities;
    }
}
