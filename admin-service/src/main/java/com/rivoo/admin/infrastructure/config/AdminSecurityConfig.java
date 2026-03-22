package com.rivoo.admin.infrastructure.config;

import com.rivoo.common.security.InternalEndpointFilter;
import com.rivoo.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for admin-service.
 *
 * All /api/v1/admin/** endpoints require a valid JWT with ROLE_PLATFORM_ADMIN.
 * Role enforcement happens at method level via @PreAuthorize on each controller handler.
 *
 * There are no public endpoints in this service (except actuator/health and internal PSK).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AdminSecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final InternalEndpointFilter internalEndpointFilter;

    public AdminSecurityConfig(KeycloakJwtConverter keycloakJwtConverter,
                               InternalEndpointFilter internalEndpointFilter) {
        this.keycloakJwtConverter = keycloakJwtConverter;
        this.internalEndpointFilter = internalEndpointFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Health/info probes — no auth required
                    auth.requestMatchers("/actuator/**").permitAll();
                    // Internal endpoints are permit-all at the HTTP level;
                    // InternalEndpointFilter enforces the PSK header (X-Internal-Service-Key)
                    auth.requestMatchers("/api/internal/**").permitAll();
                    // Everything else (all /api/v1/admin/**) requires a valid JWT
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(internalEndpointFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter)));

        return http.build();
    }
}
