package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.domain.exception.KeycloakOperationException;
import com.rivoo.auth.domain.exception.UserAlreadyExistsException;
import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import com.rivoo.auth.infrastructure.adapter.out.keycloak.dto.KeycloakRoleRepresentation;
import com.rivoo.auth.infrastructure.adapter.out.keycloak.dto.KeycloakUserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminAdapter implements KeycloakAdminPort {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

    private final RestClient restClient;
    private final KeycloakTokenManager tokenManager;
    private final String baseUrl;

    public KeycloakAdminAdapter(RestClient keycloakRestClient,
                                KeycloakTokenManager tokenManager,
                                @Qualifier("keycloakAdminBaseUrl") String baseUrl) {
        this.restClient = keycloakRestClient;
        this.tokenManager = tokenManager;
        this.baseUrl = baseUrl;
    }

    @Override
    public String createUser(String email, String password, String firstName, String lastName) {
        log.debug("Creating Keycloak user: {}", email);

        KeycloakUserRepresentation user = KeycloakUserRepresentation.forCreation(
                email, password, firstName, lastName);

        try {
            URI location = restClient.post()
                    .uri(baseUrl + "/users")
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();

            if (location == null) {
                throw new KeycloakOperationException("Keycloak did not return Location header after user creation");
            }

            String path = location.getPath();
            if (path == null || !path.contains("/users/")) {
                throw new KeycloakOperationException("Unexpected Location header format: " + location);
            }
            String userId = path.substring(path.lastIndexOf('/') + 1);
            if (userId.isBlank()) {
                throw new KeycloakOperationException("Empty userId in Location header: " + location);
            }

            log.info("Keycloak user created: email={}, userId={}", email, userId);
            return userId;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new UserAlreadyExistsException(email);
            }
            throw new KeycloakOperationException("Failed to create user in Keycloak: " + e.getMessage(), e);
        } catch (KeycloakOperationException | UserAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakOperationException("Failed to create user in Keycloak", e);
        }
    }

    @Override
    public void setUserAttributes(String keycloakUserId, Map<String, List<String>> attributes) {
        log.debug("Setting attributes for Keycloak user {}: {}", keycloakUserId, attributes.keySet());

        executeKeycloakOperation("set user attributes", () -> {
            KeycloakUserRepresentation current = getUser(keycloakUserId);

            Map<String, List<String>> merged = new HashMap<>();
            if (current.attributes() != null) {
                merged.putAll(current.attributes());
            }
            merged.putAll(attributes);

            Map<String, Object> updateBody = buildUserUpdateBody(current);
            updateBody.put("attributes", merged);

            restClient.put()
                    .uri(baseUrl + "/users/" + keycloakUserId)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void assignRealmRole(String keycloakUserId, String roleName) {
        log.debug("Assigning role {} to Keycloak user {}", roleName, keycloakUserId);

        executeKeycloakOperation("assign role " + roleName, () -> {
            KeycloakRoleRepresentation role = restClient.get()
                    .uri(baseUrl + "/roles/" + roleName)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .retrieve()
                    .body(KeycloakRoleRepresentation.class);

            if (role == null) {
                throw new KeycloakOperationException("Role not found in Keycloak: " + roleName);
            }

            restClient.post()
                    .uri(baseUrl + "/users/" + keycloakUserId + "/role-mappings/realm")
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Role {} assigned to user {}", roleName, keycloakUserId);
            return null;
        });
    }

    @Override
    public List<String> searchUserIdsByAttribute(String attributeName, String attributeValue) {
        log.debug("Searching Keycloak users by {}={}", attributeName, attributeValue);

        return executeKeycloakOperation("search users by attribute", () -> {
            List<KeycloakUserRepresentation> users = restClient.get()
                    .uri(baseUrl + "/users?q={attr}:{value}&max=100",
                            attributeName, attributeValue)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (users == null) {
                return List.of();
            }

            List<String> ids = new ArrayList<>();
            for (KeycloakUserRepresentation user : users) {
                ids.add(user.id());
            }
            return ids;
        });
    }

    @Override
    public void setUserEnabled(String keycloakUserId, boolean enabled) {
        log.debug("Setting Keycloak user {} enabled={}", keycloakUserId, enabled);

        executeKeycloakOperation("set user enabled status", () -> {
            KeycloakUserRepresentation current = getUser(keycloakUserId);

            Map<String, Object> updateBody = buildUserUpdateBody(current);
            updateBody.put("enabled", enabled);

            restClient.put()
                    .uri(baseUrl + "/users/" + keycloakUserId)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void updateUserAttribute(String keycloakUserId, String key, String value) {
        setUserAttributes(keycloakUserId, Map.of(key, List.of(value)));
    }

    @Override
    public void deleteUser(String keycloakUserId) {
        log.warn("Deleting Keycloak user {} (compensation)", keycloakUserId);

        executeKeycloakOperation("delete user from Keycloak", () -> {
            restClient.delete()
                    .uri(baseUrl + "/users/" + keycloakUserId)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak user {} deleted", keycloakUserId);
            return null;
        });
    }

    // ── Private helpers ─────────────────────────────────────────────────

    @FunctionalInterface
    private interface KeycloakOperation<T> {
        T execute();
    }

    private <T> T executeKeycloakOperation(String operationName, KeycloakOperation<T> operation) {
        try {
            return operation.execute();
        } catch (KeycloakOperationException | UserAlreadyExistsException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new UserAlreadyExistsException("User already exists");
            }
            throw new KeycloakOperationException("Failed to " + operationName + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new KeycloakOperationException("Failed to " + operationName, e);
        }
    }

    private Map<String, Object> buildUserUpdateBody(KeycloakUserRepresentation current) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", current.id());
        body.put("username", current.username());
        body.put("email", current.email());
        body.put("firstName", current.firstName());
        body.put("lastName", current.lastName());
        body.put("enabled", current.enabled());
        body.put("emailVerified", current.emailVerified());
        if (current.attributes() != null) {
            body.put("attributes", current.attributes());
        }
        return body;
    }

    private KeycloakUserRepresentation getUser(String keycloakUserId) {
        return executeKeycloakOperation("get user from Keycloak: " + keycloakUserId, () -> {
            KeycloakUserRepresentation user = restClient.get()
                    .uri(baseUrl + "/users/" + keycloakUserId)
                    .headers(h -> h.setBearerAuth(tokenManager.getAccessToken()))
                    .retrieve()
                    .body(KeycloakUserRepresentation.class);

            if (user == null) {
                throw new KeycloakOperationException("User not found in Keycloak: " + keycloakUserId);
            }
            return user;
        });
    }
}
