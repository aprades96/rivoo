package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.domain.exception.KeycloakOperationException;
import com.rivoo.auth.infrastructure.adapter.out.keycloak.dto.KeycloakTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class KeycloakTokenManager {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenManager.class);
    private static final int REFRESH_BUFFER_SECONDS = 30;

    private final RestClient restClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public KeycloakTokenManager(RestClient restClient, String serverUrl, String realm,
                                String clientId, String clientSecret) {
        this.restClient = restClient;
        this.tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && !current.isExpired()) {
            return current.token;
        }

        synchronized (this) {
            current = cachedToken.get();
            if (current != null && !current.isExpired()) {
                return current.token;
            }
            return refreshToken();
        }
    }

    private String refreshToken() {
        log.debug("Requesting new Keycloak admin token");

        String body = "grant_type=client_credentials&client_id=%s&client_secret=%s"
                .formatted(clientId, clientSecret);

        try {
            KeycloakTokenResponse response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new KeycloakOperationException("Received null token from Keycloak");
            }

            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn() - REFRESH_BUFFER_SECONDS);
            cachedToken.set(new CachedToken(response.accessToken(), expiresAt));

            log.debug("Keycloak admin token acquired, expires in {}s", response.expiresIn());
            return response.accessToken();

        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakOperationException("Failed to obtain Keycloak admin token", e);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
