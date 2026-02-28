package com.rivoo.auth.infrastructure.config;

import com.rivoo.auth.infrastructure.adapter.out.keycloak.KeycloakTokenManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${rivoo.keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${rivoo.keycloak.admin.realm}")
    private String realm;

    @Value("${rivoo.keycloak.admin.client-id}")
    private String clientId;

    @Value("${rivoo.keycloak.admin.client-secret}")
    private String clientSecret;

    @Bean
    public RestClient keycloakRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public KeycloakTokenManager keycloakTokenManager(RestClient keycloakRestClient) {
        return new KeycloakTokenManager(keycloakRestClient, serverUrl, realm, clientId, clientSecret);
    }

    @Bean
    public String keycloakAdminBaseUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
