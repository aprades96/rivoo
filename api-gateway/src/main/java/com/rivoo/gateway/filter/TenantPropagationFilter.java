package com.rivoo.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Gateway GlobalFilter that:
 * 1. Strips incoming tenant/user headers (anti-spoofing)
 * 2. Extracts JWT claims and injects headers for downstream services
 */
@Slf4j
@Component
public class TenantPropagationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_SUBSCRIPTION_PLAN = "X-Subscription-Plan";

    private static final List<String> HEADERS_TO_STRIP = List.of(
            HEADER_TENANT_ID, HEADER_USER_ID, HEADER_USER_ROLE,
            HEADER_USER_EMAIL, HEADER_SUBSCRIPTION_PLAN);

    @Override
    public int getOrder() {
        // Run after security filter (which has order ~0)
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Step 1: Strip all propagated headers from the original request (anti-spoofing)
        ServerWebExchange strippedExchange = exchange.mutate()
                .request(r -> {
                    for (String header : HEADERS_TO_STRIP) {
                        r.headers(h -> h.remove(header));
                    }
                })
                .build();

        // Step 2: Try to extract JWT claims and inject headers
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    Jwt jwt = auth.getToken();
                    return strippedExchange.mutate()
                            .request(r -> r.headers(headers -> {
                                // X-Tenant-Id from custom claim
                                String tenantId = jwt.getClaimAsString("tenant_id");
                                if (tenantId != null) {
                                    headers.set(HEADER_TENANT_ID, tenantId);
                                }

                                // X-User-Id from sub claim
                                String userId = jwt.getSubject();
                                if (userId != null) {
                                    headers.set(HEADER_USER_ID, userId);
                                }

                                // X-User-Email from email claim
                                String email = jwt.getClaimAsString("email");
                                if (email != null) {
                                    headers.set(HEADER_USER_EMAIL, email);
                                }

                                // X-Subscription-Plan from custom claim
                                String plan = jwt.getClaimAsString("subscription_plan");
                                if (plan != null) {
                                    headers.set(HEADER_SUBSCRIPTION_PLAN, plan);
                                }

                                // X-User-Role from realm_access.roles — first ROLE_* match
                                String role = extractRole(jwt);
                                if (role != null) {
                                    headers.set(HEADER_USER_ROLE, role);
                                }
                            }))
                            .build();
                })
                .defaultIfEmpty(strippedExchange)
                .flatMap(chain::filter);
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return null;
        }
        Object rolesObj = realmAccess.get("roles");
        if (rolesObj instanceof List<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(r -> r.startsWith("ROLE_"))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
