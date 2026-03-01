package com.rivoo.common.security;

import com.rivoo.common.tenant.TenantAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(after = TenantAutoConfiguration.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RivooSecurityProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    public KeycloakJwtConverter keycloakJwtConverter() {
        return new KeycloakJwtConverter();
    }

    @Bean
    public InternalEndpointFilter internalEndpointFilter(RivooSecurityProperties properties) {
        return new InternalEndpointFilter(properties.internalServiceKey());
    }
}
