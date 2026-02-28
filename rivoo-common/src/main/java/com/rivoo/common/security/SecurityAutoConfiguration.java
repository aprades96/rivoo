package com.rivoo.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(SecurityConfig.class)
public class SecurityAutoConfiguration {

    @Bean
    public KeycloakJwtConverter keycloakJwtConverter() {
        return new KeycloakJwtConverter();
    }

    @Bean
    public InternalEndpointFilter internalEndpointFilter(
            @Value("${rivoo.security.internal-service-key:}") String internalServiceKey) {
        return new InternalEndpointFilter(internalServiceKey);
    }
}
