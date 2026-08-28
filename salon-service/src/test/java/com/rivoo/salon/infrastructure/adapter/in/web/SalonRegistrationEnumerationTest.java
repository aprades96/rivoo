package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.application.OnboardingSagaService;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.AuthServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.BillingServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.NotificationServiceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@code POST /api/v1/salons} is ANONYMOUS, so whatever it returns is readable by anyone. It must
 * therefore return the SAME thing for an address that is free and for an address that already has
 * an account - otherwise it is an oracle answering "does this person have a Rivoo account?" for any
 * address on the internet.
 * <p>
 * Three scenarios, deliberately different in their SETUP (a test whose branches stub the same thing
 * the same way proves nothing, and this project has shipped exactly that mistake before):
 * <ol>
 *   <li><b>FREE</b> - no salon row, Keycloak accepts: the full saga runs.</li>
 *   <li><b>SALON_EXISTS</b> - {@code existsByEmail} is true: the saga must stop before minting
 *       anything, and both dependencies must receive ZERO requests.</li>
 *   <li><b>KEYCLOAK_KNOWS_IT</b> - no salon row, but auth-service answers 409 (an employee address,
 *       or an orphan from a compensated onboarding). Semantically the same fact as (2), reached
 *       down a completely different code path, and it has to end the same way.</li>
 * </ol>
 * Everything below the controller is real - the saga, all three outbound adapters, both advices.
 * The doubles are the persistence ports and the HTTP edge, both strictly BELOW the behaviour under
 * test, so nothing can be stubbed into agreeing.
 */
class SalonRegistrationEnumerationTest {

    private static final String AUTH_URL = "http://auth-internal.rivoo.local:8081";
    private static final String BILLING_URL = "http://billing-internal.rivoo.local:8087";
    private static final String NOTIFICATION_URL = "http://notification-internal.rivoo.local:8086";
    private static final String REGISTER_OWNER_URI = AUTH_URL + "/api/internal/auth/register-owner";
    private static final String SUBSCRIPTIONS_URI = BILLING_URL + "/api/internal/billing/subscriptions";
    private static final String NOTIFY_URI = NOTIFICATION_URL + "/api/internal/notifications/send";
    private static final String KEYCLOAK_USER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";

    private static final String EMAIL = "owner@example.com";

    private static final String REQUEST_BODY = """
            {
              "name": "Demo Salon",
              "email": "owner@example.com",
              "phone": "+34600000000",
              "addressStreet": "Carrer Demo 1",
              "addressPostalCode": "08001",
              "ownerFirstName": "Ana",
              "ownerLastName": "Lopez",
              "ownerPassword": "supersecret"
            }
            """;

    private static final String OWNER_REGISTERED_BODY = """
            {"keycloakUserId":"%s","email":"%s","role":"SALON_OWNER"}
            """.formatted(KEYCLOAK_USER_ID, EMAIL);

    /** One wiring of the whole endpoint, configured per scenario. */
    private static final class Fixture {

        final SalonPersistencePort salons = mock(SalonPersistencePort.class);
        final BusinessHoursPersistencePort businessHours = mock(BusinessHoursPersistencePort.class);
        final MockRestServiceServer auth;
        final MockRestServiceServer billing;
        final MockRestServiceServer notifications;
        final MockMvc mockMvc;

        Fixture(boolean salonRowExistsForEmail) {
            RestClient.Builder authBuilder = RestClient.builder();
            auth = MockRestServiceServer.bindTo(authBuilder).build();
            RestClient.Builder billingBuilder = RestClient.builder();
            billing = MockRestServiceServer.bindTo(billingBuilder).build();
            RestClient.Builder notificationBuilder = RestClient.builder();
            notifications = MockRestServiceServer.bindTo(notificationBuilder).build();

            when(salons.existsByEmail(EMAIL)).thenReturn(salonRowExistsForEmail);
            when(salons.existsBySlug(any())).thenReturn(false);
            when(salons.save(any())).thenAnswer(invocation -> {
                Salon salon = invocation.getArgument(0);
                if (salon.getId() == null) {
                    salon.setId(1L);
                }
                return salon;
            });

            OnboardingSagaService saga = new OnboardingSagaService(
                    salons,
                    businessHours,
                    new AuthServiceAdapter(authBuilder, AUTH_URL),
                    new BillingServiceAdapter(billingBuilder, BILLING_URL),
                    new NotificationServiceAdapter(notificationBuilder, NOTIFICATION_URL));

            SalonController controller = new SalonController(
                    saga,
                    mock(GetSalonUseCase.class),
                    mock(UpdateSalonUseCase.class),
                    mock(ManageBusinessHoursUseCase.class),
                    mock(ManageSalonStatusUseCase.class),
                    mock(ListSalonsUseCase.class));

            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                    .build();
        }

        MockHttpServletResponse register() throws Exception {
            return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/salons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andReturn().getResponse();
        }

        void expectWelcomeNotification() {
            notifications.expect(requestTo(NOTIFY_URI))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.type").value("WELCOME"))
                    .andExpect(jsonPath("$.recipientEmail").value(EMAIL))
                    .andRespond(withSuccess());
        }

        void expectExistingAccountNotification() {
            notifications.expect(requestTo(NOTIFY_URI))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.type").value("REGISTRATION_ATTEMPT_EXISTING_ACCOUNT"))
                    .andExpect(jsonPath("$.recipientEmail").value(EMAIL))
                    .andRespond(withSuccess());
        }

        void expectOwnerRegistrationAccepted() {
            auth.expect(requestTo(REGISTER_OWNER_URI))
                    .andExpect(method(POST))
                    .andRespond(withSuccess(OWNER_REGISTERED_BODY, MediaType.APPLICATION_JSON));
        }

        void expectSubscriptionCreated() {
            billing.expect(requestTo(SUBSCRIPTIONS_URI))
                    .andExpect(method(POST))
                    .andRespond(withSuccess());
        }
    }

    // -- Scenario builders. Each one configures a DIFFERENT world. -----------

    /** (1) Nothing knows this address. The saga runs end to end. */
    private static Fixture free() {
        Fixture fixture = new Fixture(false);
        fixture.expectOwnerRegistrationAccepted();
        fixture.expectSubscriptionCreated();
        fixture.expectWelcomeNotification();
        return fixture;
    }

    /**
     * (2) A salon already carries this address. No expectation is registered on auth or billing:
     * MockRestServiceServer fails the request itself if either is contacted.
     */
    private static Fixture salonExists() {
        Fixture fixture = new Fixture(true);
        fixture.expectExistingAccountNotification();
        return fixture;
    }

    /** (3) No salon row, but Keycloak already has the user, so auth-service answers 409. */
    private static Fixture keycloakKnowsIt() {
        Fixture fixture = new Fixture(false);
        fixture.auth.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        fixture.expectExistingAccountNotification();
        return fixture;
    }

    private static String bodyOf(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    // -- The property --------------------------------------------------------

    @Test
    void allThreeOutcomesAreIndistinguishableToTheCaller() throws Exception {
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Fixture fixture : List.of(free(), salonExists(), keycloakKnowsIt())) {
            responses.add(fixture.register());
        }

        MockHttpServletResponse reference = responses.getFirst();
        assertThat(reference.getStatus())
                .as("a salon is not always created, so the shared status cannot be 201")
                .isEqualTo(HttpStatus.ACCEPTED.value());

        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus()).isEqualTo(reference.getStatus());
            assertThat(response.getContentType()).isEqualTo(reference.getContentType());
            assertThat(response.getContentAsByteArray())
                    .as("byte-identical body, or the difference is the oracle")
                    .isEqualTo(reference.getContentAsByteArray());
            assertThat(response.getHeaderNames()).isEqualTo(reference.getHeaderNames());
            assertThat(response.getHeader("Location"))
                    .as("a Location header would point at the salon only one path created")
                    .isNull();
        }

        assertThat(bodyOf(reference))
                .as("nothing that exists on only one of the three paths may appear in the body")
                .doesNotContain("sal_")
                .doesNotContain("demo-salon")
                .doesNotContain("ONBOARDING")
                .doesNotContain("ACTIVE")
                .doesNotContain(EMAIL);
    }

    @Test
    void existingSalonAddress_createsNothingAndContactsNoDependency() throws Exception {
        Fixture fixture = salonExists();

        fixture.register();

        verify(fixture.salons, never()).save(any());
        verify(fixture.salons, never()).deleteById(anyLong());
        verify(fixture.businessHours, never()).saveAll(any());
        // auth and billing hold EMPTY expectation sets: an unexpected request would already have
        // failed register() above, and verify() confirms nothing was silently skipped.
        fixture.auth.verify();
        fixture.billing.verify();
        fixture.notifications.verify();
    }

    @Test
    void addressKnownOnlyToKeycloak_leavesNoSalonBehindAndNeverReachesBilling() throws Exception {
        Fixture fixture = keycloakKnowsIt();

        fixture.register();

        // This path DOES mint a salon before auth-service can answer; what matters is that it gets
        // undone and that the saga stops there instead of surfacing a distinguishable 422.
        verify(fixture.salons).deleteById(1L);
        fixture.billing.verify();
        fixture.auth.verify();
        fixture.notifications.verify();
    }

    @Test
    void eachPathMailsTheRightThingToTheAddressThatWasSubmitted() throws Exception {
        // The notification expectations assert BOTH the type and the recipient, and verify() fails
        // if the request never arrives at all. The inbox is the only place the two outcomes are
        // allowed to differ, because it is the only place just the address owner can read.
        Fixture newAddress = free();
        newAddress.register();
        newAddress.notifications.verify();

        Fixture taken = salonExists();
        taken.register();
        taken.notifications.verify();
    }

    @Test
    void aBrokenNotificationServiceDoesNotChangeTheAnswerOnEitherPath() throws Exception {
        // If the existing-address path let a notification failure escape, anyone able to take
        // notification-service down would get the oracle back. The free path already swallowed it;
        // both must.
        Fixture taken = new Fixture(true);
        taken.notifications.expect(requestTo(NOTIFY_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Fixture newAddress = new Fixture(false);
        newAddress.expectOwnerRegistrationAccepted();
        newAddress.expectSubscriptionCreated();
        newAddress.notifications.expect(requestTo(NOTIFY_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        MockHttpServletResponse takenResponse = taken.register();
        MockHttpServletResponse newResponse = newAddress.register();

        assertThat(takenResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(takenResponse.getContentAsByteArray())
                .isEqualTo(newResponse.getContentAsByteArray());
    }
}
