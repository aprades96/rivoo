package com.rivoo.appointment.infrastructure.config;

import com.rivoo.common.security.InternalEndpointFilter;
import com.rivoo.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AppointmentSecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final InternalEndpointFilter internalEndpointFilter;

    public AppointmentSecurityConfig(KeycloakJwtConverter keycloakJwtConverter,
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
                auth.requestMatchers("/api/internal/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/appointments/book").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/v1/appointments/public/**").permitAll();
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(internalEndpointFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter)));

        return http.build();
    }
}
