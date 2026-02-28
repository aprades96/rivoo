package com.rivoo.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final InternalEndpointFilter internalEndpointFilter;

    public SecurityConfig(KeycloakJwtConverter keycloakJwtConverter,
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
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers("/api/internal/**").permitAll(); // authenticated by InternalEndpointFilter (PSK)
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(internalEndpointFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter)));

        return http.build();
    }
}
