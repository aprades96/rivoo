package com.rivoo.billing.infrastructure.config;

import com.rivoo.common.security.InternalEndpointFilter;
import com.rivoo.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BillingSecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final InternalEndpointFilter internalEndpointFilter;

    public BillingSecurityConfig(KeycloakJwtConverter keycloakJwtConverter,
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
                // Stripe webhook — ANONYMOUS AND UNVERIFIED as of today. The comment here used to
                // claim the signature was checked; nothing checks it. WebhookController accepts
                // Stripe-Signature as required=false and StripeStubAdapter.constructEvent ignores
                // the header entirely, extracting the event fields from the raw body instead, so
                // anyone able to reach this path can forge a subscription event. Implementing
                // Webhook.constructEvent with the endpoint secret is a separate security ticket;
                // this comment states the real state until then.
                auth.requestMatchers("/api/webhooks/stripe").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll(); // Public — plan listing
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(internalEndpointFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter)));

        return http.build();
    }
}
