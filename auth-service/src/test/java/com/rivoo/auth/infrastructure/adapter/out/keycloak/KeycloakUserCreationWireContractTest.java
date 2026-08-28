package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.application.AuthService;
import com.rivoo.auth.application.dto.RegisterEmployeeRequest;
import com.rivoo.auth.application.dto.RegisterOwnerRequest;
import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import com.rivoo.auth.domain.port.out.OnboardingEventPort;
import com.rivoo.auth.domain.port.out.TenantUserMappingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
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
    private static final String TENANT_ID = "sal_00000000-1111-2222-3333-444444444444";
    private static final String EXECUTE_ACTIONS_URL =
            BASE_URL + "/users/" + USER_ID + "/execute-actions-email";
    private static final String EXISTING_USER_JSON = """
            {"id":"%s","username":"owner@example.com","email":"owner@example.com",
             "firstName":"Ana","lastName":"Lopez","enabled":true,"emailVerified":true}""".formatted(USER_ID);

    private MockRestServiceServer keycloak;
    private RestClient restClient;
    private KeycloakTokenManager tokenManager;

    /**
     * Every request that actually left, recorded independently of the expectations. Needed to
     * assert an ABSENCE: {@code MockRestServiceServer} can only fail on a request it did not
     * expect, and the caller under test swallows failures from this particular call by design, so
     * "no execute-actions-email happened" is asserted positively here rather than inferred from
     * the absence of a complaint.
     */
    private final List<String> wireCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        wireCalls.clear();
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    wireCalls.add(request.getMethod() + " " + request.getURI());
                    return execution.execute(request, body);
                });
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

    // -- Registration, end to end, judged at the wire --------------------

    private AuthService authServiceWith(boolean ownerEmailVerifiedOnCreation) {
        return new AuthService(adapterWith(ownerEmailVerifiedOnCreation),
                mock(OnboardingEventPort.class), mock(TenantUserMappingPort.class));
    }

    /**
     * Every Keycloak call a registration makes EXCEPT {@code execute-actions-email}, in order.
     * That last call is left out deliberately so each test has to declare for itself whether it
     * expects one -- which is the entire difference between the two directions below.
     */
    private void expectEverythingButTheActionEmail(String roleName) {
        expectUserCreation(keycloak.expect(requestTo(BASE_URL + "/users")).andExpect(method(POST)));
        keycloak.expect(requestTo(BASE_URL + "/users/" + USER_ID)).andExpect(method(GET))
                .andRespond(withSuccess(EXISTING_USER_JSON, MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(BASE_URL + "/users/" + USER_ID)).andExpect(method(PUT))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(BASE_URL + "/roles/" + roleName)).andExpect(method(GET))
                .andRespond(withSuccess("{\"id\":\"role-uuid\",\"name\":\"" + roleName + "\"}",
                        MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(BASE_URL + "/users/" + USER_ID + "/role-mappings/realm"))
                .andExpect(method(POST)).andRespond(withNoContent());
    }

    private void registerOwnerWith(boolean ownerEmailVerifiedOnCreation) {
        authServiceWith(ownerEmailVerifiedOnCreation).registerOwner(new RegisterOwnerRequest(
                TENANT_ID, "owner@example.com", "chosen-by-the-owner", "Ana", "Lopez",
                "Demo Salon", "FREE_TRIAL"));
    }

    private void registerEmployeeWith(boolean ownerEmailVerifiedOnCreation) {
        authServiceWith(ownerEmailVerifiedOnCreation).registerEmployee(new RegisterEmployeeRequest(
                TENANT_ID, "employee@example.com", "temp-pass", "Luis", "Gomez"));
    }

    @Test
    void registerOwner_whenVerificationRequired_putsExactlyVerifyEmailToExecuteActionsEmail() {
        expectEverythingButTheActionEmail("ROLE_SALON_OWNER");
        keycloak.expect(requestTo(EXECUTE_ACTIONS_URL))
                .andExpect(method(PUT))
                .andExpect(content().json("[\"VERIFY_EMAIL\"]", true))
                .andRespond(withNoContent());

        registerOwnerWith(false);

        keycloak.verify();
        assertThat(wireCalls)
                .as("the owner was created unverified, so the link must actually be requested")
                .contains("PUT " + EXECUTE_ACTIONS_URL);
    }

    /**
     * The direction the switch exists for, and the one that was broken. Keycloak's
     * {@code execute-actions-email} SETS the required actions on the user as part of sending, so an
     * owner created with {@code emailVerified=true} and no required action was handed
     * {@code VERIFY_EMAIL} straight back and locked out -- on precisely the profiles that turned
     * the switch on because they have no SMTP server to satisfy it with. Nothing may reach that
     * endpoint here.
     */
    @Test
    void registerOwner_whenVerificationSkipped_neverTouchesTheExecuteActionsEmailEndpoint() {
        expectEverythingButTheActionEmail("ROLE_SALON_OWNER");

        registerOwnerWith(true);

        keycloak.verify();
        assertThat(wireCalls)
                .as("nothing is pending, so Keycloak must not be asked to execute anything")
                .noneMatch(call -> call.contains("execute-actions-email"))
                .hasSize(5);
    }

    @Test
    void registerEmployee_whenOwnerVerificationRequired_putsExactlyUpdatePasswordToExecuteActions() {
        expectEverythingButTheActionEmail("ROLE_EMPLOYEE");
        keycloak.expect(requestTo(EXECUTE_ACTIONS_URL))
                .andExpect(method(PUT))
                .andExpect(content().json("[\"UPDATE_PASSWORD\"]", true))
                .andRespond(withNoContent());

        registerEmployeeWith(false);

        keycloak.verify();
        assertThat(wireCalls).contains("PUT " + EXECUTE_ACTIONS_URL);
    }

    /**
     * The employee flow is not the owner's and must not move with it: a temporary password chosen
     * by somebody else still has to be replaced, whatever the owner's switch says.
     */
    @Test
    void registerEmployee_whenOwnerVerificationSkipped_stillPutsExactlyUpdatePassword() {
        expectEverythingButTheActionEmail("ROLE_EMPLOYEE");
        keycloak.expect(requestTo(EXECUTE_ACTIONS_URL))
                .andExpect(method(PUT))
                .andExpect(content().json("[\"UPDATE_PASSWORD\"]", true))
                .andRespond(withNoContent());

        registerEmployeeWith(true);

        keycloak.verify();
        assertThat(wireCalls).contains("PUT " + EXECUTE_ACTIONS_URL);
    }

    @Test
    void sendRequiredActionsEmail_whenGivenAnEmptyActionList_makesNoRequestAtAll() {
        // No expectation is declared, so any request at all would be an unexpected one; the
        // recorded calls assert the same thing positively.
        adapterWith(false).sendRequiredActionsEmail(USER_ID, List.of());

        keycloak.verify();
        assertThat(wireCalls).isEmpty();
    }

    /**
     * The single source of truth, checked from outside: what the adapter reports as still pending
     * is exactly what it put into the creation body for that same flag value.
     */
    @Test
    void pendingActionsForNewOwner_mirrorsTheRequiredActionsPutIntoTheCreationBody() {
        assertThat(adapterWith(false).pendingActionsForNewOwner())
                .as("created unverified: VERIFY_EMAIL is still owed")
                .containsExactly("VERIFY_EMAIL");
        assertThat(adapterWith(true).pendingActionsForNewOwner())
                .as("created verified: nothing is owed, so nothing may be mailed")
                .isEmpty();
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
