package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What actually goes ON THE WIRE to the Keycloak Admin API when each kind of user is created.
 * <p>
 * Asserted at the HTTP boundary rather than on {@code KeycloakUserRepresentation}: the record's
 * component order is {@code (id, username, email, firstName, lastName, enabled, emailVerified,
 * credentials, requiredActions, attributes)} — two adjacent {@code Boolean}s and two adjacent
 * {@code List}s, so a transposed pair of arguments compiles and a test reading the record back
 * through the same accessors would agree with the bug. The JSON body is the only representation
 * Keycloak actually reads.
 */
class KeycloakUserCreationWireContractTest {

    private static final String BASE_URL = "http://keycloak.test/admin/realms/rivoo";
    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

    private MockRestServiceServer keycloak;
    private KeycloakAdminPort adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        KeycloakTokenManager tokenManager = mock(KeycloakTokenManager.class);
        when(tokenManager.getAccessToken()).thenReturn("stub-admin-token");
        adapter = new KeycloakAdminAdapter(builder.build(), tokenManager, BASE_URL);
    }

    private void expectUserCreation(org.springframework.test.web.client.ResponseActions actions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create(BASE_URL + "/users/" + USER_ID));
        actions.andRespond(withStatus(org.springframework.http.HttpStatus.CREATED).headers(headers));
    }

    @Test
    void createUser_marksTheOwnerAddressUnverifiedAndRequiresVerifyEmail() {
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                // The defect: this used to be true, so Keycloak trusted an address nobody proved.
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.requiredActions.length()").value(1))
                .andExpect(jsonPath("$.requiredActions[0]").value("VERIFY_EMAIL")));

        adapter.createUser("owner@example.com", "chosen-by-the-owner", "Ana", "Lopez");

        keycloak.verify();
    }

    @Test
    void createUser_doesNotForceTheOwnerToChangeTheirOwnPassword() {
        // The owner typed this password during registration, so temporary=false and UPDATE_PASSWORD
        // must NOT be required — that is the employee's flow, not this one. Asserted separately
        // from the test above so a fix that simply copied forEmployeeCreation cannot pass.
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andExpect(jsonPath("$.requiredActions.length()").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("UPDATE_PASSWORD")))));

        adapter.createUser("owner@example.com", "chosen-by-the-owner", "Ana", "Lopez");

        keycloak.verify();
    }

    @Test
    void createEmployeeUser_keepsItsTemporaryPasswordAndBothRequiredActions() {
        // Regression guard in the other direction: the owner fix must not have been applied by
        // editing the shared shape and silently relaxing the employee's flow.
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.requiredActions.length()").value(2))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andExpect(jsonPath("$.requiredActions[1]").value("VERIFY_EMAIL")));

        adapter.createEmployeeUser("employee@example.com", "temp-pass", "Luis", "Gomez");

        keycloak.verify();
    }

    @Test
    void sendRequiredActionsEmail_putsExactlyTheActionsItWasGiven() {
        // The actions used to be hardcoded to ["UPDATE_PASSWORD"] inside the adapter, which would
        // have mailed the owner a "change your password" link instead of a verification link.
        keycloak.expect(requestTo(BASE_URL + "/users/" + USER_ID + "/execute-actions-email"))
                .andExpect(method(PUT))
                .andExpect(content().json("[\"VERIFY_EMAIL\"]", true))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        adapter.sendRequiredActionsEmail(USER_ID, List.of("VERIFY_EMAIL"));

        keycloak.verify();
    }
}
