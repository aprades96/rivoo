package com.rivoo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(Customizer.withDefaults())
            .authorizeExchange(exchanges -> {
                exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                exchanges.pathMatchers("/actuator/**").permitAll();
                exchanges.pathMatchers(HttpMethod.POST, "/api/v1/salons").permitAll();
                exchanges.pathMatchers("/api/v1/salons/public/**").permitAll();
                exchanges.pathMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll();
                exchanges.pathMatchers(HttpMethod.POST, "/api/v1/appointments/book").permitAll();
                exchanges.pathMatchers(HttpMethod.GET, "/api/v1/appointments/public/**").permitAll();
                exchanges.pathMatchers("/api/webhooks/stripe").permitAll();
                exchanges.pathMatchers("/realms/**").permitAll();
                exchanges.anyExchange().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
