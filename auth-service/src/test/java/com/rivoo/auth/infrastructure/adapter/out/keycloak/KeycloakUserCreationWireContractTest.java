package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private RestClient restClient;
    private KeycloakTokenManager tokenManager;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        tokenManager = mock(KeycloakTokenManager.class);
        when(tokenManager.getAccessToken()).thenReturn("stub-admin-token");
        restClient = builder.build();
    }

    /**
     * @param ownerEmailVerifiedOnCreation the value of
     *        {@code rivoo.keycloak.owner.email-verified-on-creation}. {@code false} is the
     *        production behaviour and the inline default; {@code true} is the temporary
     *        local/test escape hatch that exists only while there is no SMTP server.
     */
    private KeycloakAdminPort adapterWith(boolean ownerEmailVerifiedOnCreation) {
        return new KeycloakAdminAdapter(restClient, tokenManager, BASE_URL, ownerEmailVerifiedOnCreation);
    }

    private void expectUserCreation(org.springframework.test.web.client.ResponseActions actions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create(BASE_URL + "/users/" + USER_ID));
        actions.andRespond(withStatus(org.springframework.http.HttpStatus.CREATED).headers(headers));
    }

    @Test
    void createUser_whenVerificationRequired_marksTheOwnerAddressUnverifiedAndRequiresVerifyEmail() {
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                // The defect: this used to be true, so Keycloak trusted an address nobody proved.
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.requiredActions.length()").value(1))
                .andExpect(jsonPath("$.requiredActions[0]").value("VERIFY_EMAIL")));

        adapterWith(false).createUser("owner@example.com", "chosen-by-the-owner", "Ana", "Lopez");

        keycloak.verify();
    }

    @Test
    void createUser_whenVerificationRequired_doesNotForceTheOwnerToChangeTheirOwnPassword() {
        // The owner typed this password during registration, so temporary=false and UPDATE_PASSWORD
        // must NOT be required — that is the employee's flow, not this one. Asserted separately
        // from the test above so a fix that simply copied forEmployeeCreation cannot pass.
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andExpect(jsonPath("$.requiredActions.length()").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("UPDATE_PASSWORD")))));

        adapterWith(false).createUser("owner@example.com", "chosen-by-the-owner", "Ana", "Lopez");

        keycloak.verify();
    }

    /**
     * The other direction of the same switch. Only reachable on {@code local} and {@code test},
     * and only because there is no SMTP server yet: neither the application's sender
     * (notification-service's {@code MailStubAdapter}, which just logs) nor Keycloak's realm (no
     * {@code smtpServer} in {@code rivoo-realm.json}) can send the verification link, so requiring
     * it would leave every owner permanently locked out. When SMTP exists this must go back to
     * being unreachable in every profile.
     */
    @Test
    void createUser_whenVerificationSkipped_marksTheOwnerVerifiedAndRequiresNoAction() {
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andExpect(jsonPath("$.requiredActions.length()").value(0))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("VERIFY_EMAIL")))));

        adapterWith(true).createUser("owner@example.com", "chosen-by-the-owner", "Ana", "Lopez");

        keycloak.verify();
    }

    @Test
    void createEmployeeUser_whenOwnerVerificationRequired_keepsItsTemporaryPasswordAndBothRequiredActions() {
        // Regression guard in the other direction: the owner fix must not have been applied by
        // editing the shared shape and silently relaxing the employee's flow.
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.requiredActions.length()").value(2))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andExpect(jsonPath("$.requiredActions[1]").value("VERIFY_EMAIL")));

        adapterWith(false).createEmployeeUser("employee@example.com", "temp-pass", "Luis", "Gomez");

        keycloak.verify();
    }

    @Test
    void createEmployeeUser_whenOwnerVerificationSkipped_isCompletelyUnaffected() {
        // The switch is the OWNER's alone. An employee gets a temporary password from someone else,
        // so nothing about them may change when the owner's verification is relaxed.
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.requiredActions.length()").value(2))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andExpect(jsonPath("$.requiredActions[1]").value("VERIFY_EMAIL")));

        adapterWith(true).createEmployeeUser("employee@example.com", "temp-pass", "Luis", "Gomez");

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

        adapterWith(false).sendRequiredActionsEmail(USER_ID, List.of("VERIFY_EMAIL"));

        keycloak.verify();
    }

    /**
     * Not a wire assertion, but the same switch: the wiring that decides which of the two bodies
     * above is sent. Pinned by reflection because no Spring context is started in this module, and
     * the two things that matter are unobservable otherwise -- the property NAME (a typo silently
     * falls back to the default forever) and the INLINE default. The default must be present, so a
     * profile file that omits the property cannot stop the service booting, and it must be the SAFE
     * value, so that profile gets production behaviour rather than the local/test escape hatch.
     */
    @Test
    void ownerVerificationFlag_isReadFromTheExpectedPropertyAndDefaultsToRequiringVerification()
            throws NoSuchMethodException {
        Constructor<KeycloakAdminAdapter> constructor = KeycloakAdminAdapter.class.getDeclaredConstructor(
                RestClient.class, KeycloakTokenManager.class, String.class, boolean.class);

        Value flag = constructor.getParameters()[3].getAnnotation(Value.class);

        assertThat(flag).as("the flag must come from configuration, not a constant").isNotNull();
        assertThat(flag.value())
                .as("exact property name, and an inline default equal to the safe value")
                .isEqualTo("${rivoo.keycloak.owner.email-verified-on-creation:false}");
    }
}
