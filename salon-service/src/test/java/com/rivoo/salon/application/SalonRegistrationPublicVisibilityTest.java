package com.rivoo.salon.application;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonController;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonExceptionHandler;
import com.rivoo.salon.infrastructure.adapter.out.rest.AuthServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.BillingServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.NotificationServiceAdapter;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The response of {@code POST /api/v1/salons} is identical for a free address and a taken one -
 * {@code SalonRegistrationEnumerationTest} pins that. It was not enough. The registration also left
 * a SIDE EFFECT that differed, and the side effect was readable by the same anonymous attacker in
 * one more request:
 * <ol>
 *   <li>{@code POST /api/v1/salons} with the victim's address and an attacker-chosen {@code name} -
 *       always 202, always the same body;</li>
 *   <li>{@code GET /api/v1/salons/public/<slug derived from that name>} - 200 meant the address was
 *       FREE (the saga had created and ACTIVATED a salon under the attacker's own slug), 404 meant
 *       it was TAKEN (nothing was created).</li>
 * </ol>
 * The same yes/no, one request later, with no timing analysis at all.
 * <p>
 * This test drives the REAL saga on both paths - real {@link OnboardingSagaService}, real outbound
 * adapters, a persistence port that is a genuine in-memory store rather than a stub, so what the
 * saga writes is what the public read sees - and asserts the public surface cannot tell them apart.
 * Then it asserts the salon DOES become visible once the owner confirms the address, because a fix
 * that simply never publishes anything would pass the first half and break the product.
 * <p>
 * The two worlds are set up differently on purpose (empty store + auth-service accepting, versus a
 * store that already holds a salon for that address and an auth-service that is never contacted): a
 * test whose branches stub the same thing the same way proves nothing, and this project has shipped
 * exactly that mistake before. {@link #freePathReallyDidCreateASalonUnderTheAttackersSlug()} is the
 * guard against the other cheap pass - a saga that stopped creating anything at all would make the
 * two paths identical for the wrong reason.
 */
class SalonRegistrationPublicVisibilityTest {

    private static final String AUTH_URL = "http://auth-internal.rivoo.local:8081";
    private static final String BILLING_URL = "http://billing-internal.rivoo.local:8087";
    private static final String NOTIFICATION_URL = "http://notification-internal.rivoo.local:8086";
    private static final String REGISTER_OWNER_URI = AUTH_URL + "/api/internal/auth/register-owner";
    private static final String SUBSCRIPTIONS_URI = BILLING_URL + "/api/internal/billing/subscriptions";
    private static final String NOTIFY_URI = NOTIFICATION_URL + "/api/internal/notifications/send";

    private static final String KEYCLOAK_USER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";
    private static final String EMAIL_VERIFIED_URI =
            AUTH_URL + "/api/internal/auth/users/" + KEYCLOAK_USER_ID + "/email-verified";

    /** The address being probed. In one world it already has a salon, in the other it does not. */
    private static final String VICTIM_EMAIL = "victim@x.com";

    /** Chosen by the ATTACKER, so they know the slug to poll without being told it. */
    private static final String PROBE_NAME = "probe-aaa-111";
    private static final String PROBE_SLUG = "probe-aaa-111";

    /** Deliberately NOT the probe slug: the probe slug must be free in both worlds. */
    private static final String EXISTING_SLUG = "already-registered-salon";

    private static final String REQUEST_BODY = """
            {
              "name": "%s",
              "email": "%s",
              "phone": "+34600000000",
              "addressStreet": "Carrer Demo 1",
              "addressPostalCode": "08001",
              "ownerFirstName": "Ana",
              "ownerLastName": "Lopez",
              "ownerPassword": "supersecret"
            }
            """.formatted(PROBE_NAME, VICTIM_EMAIL);

    private static final String OWNER_REGISTERED_BODY = """
            {"keycloakUserId":"%s","email":"%s","role":"SALON_OWNER"}
            """.formatted(KEYCLOAK_USER_ID, VICTIM_EMAIL);

    // -- The property --------------------------------------------------------

    @Test
    void publicSurfaceCannotTellAFreeAddressFromATakenOneAfterRegistration() throws Exception {
        World free = worldWhereAddressIsFree();
        World taken = worldWhereAddressAlreadyHasASalon();

        MockHttpServletResponse freeRegistration = free.register();
        MockHttpServletResponse takenRegistration = taken.register();

        assertThat(freeRegistration.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(freeRegistration.getContentAsByteArray())
                .as("the response was already uniform; it must stay that way")
                .isEqualTo(takenRegistration.getContentAsByteArray());

        MockHttpServletResponse freeLookup = free.lookUpProbeSlug();
        MockHttpServletResponse takenLookup = taken.lookUpProbeSlug();

        assertThat(freeLookup.getStatus())
                .as("200 here is the oracle: it would mean the address was free")
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(freeLookup.getStatus()).isEqualTo(takenLookup.getStatus());
        assertThat(normalizeTimestamp(bodyOf(freeLookup)))
                .as("the two bodies must not differ either - the status is not the only channel")
                .isEqualTo(normalizeTimestamp(bodyOf(takenLookup)));
        assertThat(freeLookup.getContentType()).isEqualTo(takenLookup.getContentType());
    }

    @Test
    void freePathReallyDidCreateASalonUnderTheAttackersSlug() throws Exception {
        // Guards the test above against passing for the wrong reason. The free path DOES persist a
        // salon carrying the attacker's slug and the victim's address - what changed is that the
        // salon is not publicly visible while its owner has not confirmed the address.
        World free = worldWhereAddressIsFree();

        free.register();

        Salon persisted = free.salons.findBySlug(PROBE_SLUG).orElseThrow();
        assertThat(persisted.getEmail()).isEqualTo(VICTIM_EMAIL);
        assertThat(persisted.getOwnerUserId()).isEqualTo(KEYCLOAK_USER_ID);
        assertThat(persisted.getStatus())
                .as("registration must not publish a salon nobody has proved they own")
                .isEqualTo(SalonStatus.ONBOARDING);
    }

    @Test
    void ownerConfirmsTheAddress_salonBecomesPubliclyVisibleWithNoManualStep() throws Exception {
        World free = worldWhereTheOwnerWillVerify();

        free.register();
        assertThat(free.lookUpProbeSlug().getStatus())
                .as("invisible while the address is unconfirmed")
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        int activated = free.activation.activateVerifiedOwners();

        assertThat(activated).isEqualTo(1);
        MockHttpServletResponse afterVerification = free.lookUpProbeSlug();
        assertThat(afterVerification.getStatus())
                .as("confirming the address must publish the salon with nobody touching anything")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(bodyOf(afterVerification)).contains(PROBE_SLUG);
        free.auth.verify();
        free.notifications.verify();
    }

    @Test
    void ownerHasNotConfirmedYet_salonStaysInvisibleAndNoWelcomeMailGoesOut() throws Exception {
        World free = worldWhereTheOwnerWillNotVerify();

        free.register();

        int activated = free.activation.activateVerifiedOwners();

        assertThat(activated).isZero();
        assertThat(free.lookUpProbeSlug().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        // notifications holds no expectation: a welcome mail here would have failed the call itself.
        free.notifications.verify();
        free.auth.verify();
    }

    // -- Worlds --------------------------------------------------------------

    /** Nothing anywhere knows this address. The saga runs end to end. */
    private static World worldWhereAddressIsFree() {
        World world = new World();
        world.auth.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess(OWNER_REGISTERED_BODY, MediaType.APPLICATION_JSON));
        world.billing.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess());
        // No notification expectation: at REGISTRATION time this path sends none. The mail the
        // owner receives is Keycloak's VERIFY_EMAIL, and the WELCOME one only goes out once the
        // salon is really active. An unexpected POST here fails the request itself.
        return world;
    }

    /**
     * As above, plus the activation pass finding the address confirmed.
     * <p>
     * Every expectation is registered before the first request on purpose: {@code
     * MockRestServiceServer} refuses to accept new ones once traffic has started, and registering
     * them up front also pins the ORDER the two auth-service calls happen in (register the owner,
     * then ask about them).
     */
    private static World worldWhereTheOwnerWillVerify() {
        World world = worldWhereAddressIsFree();
        world.expectEmailVerifiedQuery(true);
        world.expectWelcomeNotification();
        return world;
    }

    /** As above, but Keycloak still reports the address unconfirmed. */
    private static World worldWhereTheOwnerWillNotVerify() {
        World world = worldWhereAddressIsFree();
        world.expectEmailVerifiedQuery(false);
        return world;
    }

    /** A salon already carries this address, under a different slug. The saga stops at step 0. */
    private static World worldWhereAddressAlreadyHasASalon() {
        World world = new World();
        world.salons.seed(Salon.builder()
                .externalId("sal_existing")
                .tenantId("sal_existing")
                .name("Already Registered Salon")
                .slug(EXISTING_SLUG)
                .ownerUserId("11111111-2222-3333-4444-555555555555")
                .email(VICTIM_EMAIL)
                .phone("+34600111222")
                .addressStreet("Carrer Vell 9")
                .addressCity("Barcelona")
                .addressPostalCode("08002")
                .timezone("Europe/Madrid")
                .currency("EUR")
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(SalonStatus.ACTIVE)
                .build());
        world.notifications.expect(requestTo(NOTIFY_URI))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.type").value("REGISTRATION_ATTEMPT_EXISTING_ACCOUNT"))
                .andRespond(withSuccess());
        // auth and billing hold no expectations: this path must not contact them at all.
        return world;
    }

    /**
     * One wiring of the whole slice: the real saga, the real activation pass, the real public read
     * path, the real outbound adapters. Only the HTTP edge and the two persistence ports are
     * doubled, and the persistence double is a real store, not a stub - the whole point is that the
     * public read observes what the registration actually wrote.
     */
    private static final class World {

        final SalonStore salons = new SalonStore();
        final BusinessHoursStore businessHours = new BusinessHoursStore();
        final MockRestServiceServer auth;
        final MockRestServiceServer billing;
        final MockRestServiceServer notifications;
        final OwnerVerificationActivationService activation;
        final MockMvc mockMvc;

        World() {
            RestClient.Builder authBuilder = RestClient.builder();
            auth = MockRestServiceServer.bindTo(authBuilder).build();
            RestClient.Builder billingBuilder = RestClient.builder();
            billing = MockRestServiceServer.bindTo(billingBuilder).build();
            RestClient.Builder notificationBuilder = RestClient.builder();
            notifications = MockRestServiceServer.bindTo(notificationBuilder).build();

            AuthServiceAdapter authAdapter = new AuthServiceAdapter(authBuilder, AUTH_URL);
            NotificationServiceAdapter notificationAdapter =
                    new NotificationServiceAdapter(notificationBuilder, NOTIFICATION_URL);

            OnboardingSagaService saga = new OnboardingSagaService(
                    salons,
                    businessHours,
                    authAdapter,
                    new BillingServiceAdapter(billingBuilder, BILLING_URL),
                    notificationAdapter);

            activation = new OwnerVerificationActivationService(salons, authAdapter, notificationAdapter);

            SalonService salonService = new SalonService(
                    salons,
                    businessHours,
                    new EmptyCatalogueStaffService(),
                    new SalonDtoMapperImpl(),
                    new SalonPublicSnapshotLoader(salons, businessHours));

            SalonController controller = new SalonController(
                    saga,
                    salonService,
                    (UpdateSalonUseCase) null,
                    (ManageBusinessHoursUseCase) null,
                    (ManageSalonStatusUseCase) null,
                    (ListSalonsUseCase) null);

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

        MockHttpServletResponse lookUpProbeSlug() throws Exception {
            return mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/salons/public/" + PROBE_SLUG))
                    .andReturn().getResponse();
        }

        void expectEmailVerifiedQuery(boolean verified) {
            auth.expect(requestTo(EMAIL_VERIFIED_URI))
                    .andExpect(method(GET))
                    .andRespond(withSuccess(
                            "{\"keycloakUserId\":\"%s\",\"emailVerified\":%s}"
                                    .formatted(KEYCLOAK_USER_ID, verified),
                            MediaType.APPLICATION_JSON));
        }

        void expectWelcomeNotification() {
            notifications.expect(requestTo(NOTIFY_URI))
                    .andExpect(method(POST))
                    .andExpect(jsonPath("$.type").value("WELCOME"))
                    .andExpect(jsonPath("$.recipientEmail").value(VICTIM_EMAIL))
                    .andRespond(withSuccess());
        }
    }

    // -- Doubles that behave like the real thing -----------------------------

    /**
     * A real in-memory store, not a stub. Rows go in and come out through copies, so nothing passes
     * between the saga and the public read by object aliasing - the same way a JPA adapter maps to
     * an entity and back.
     */
    private static final class SalonStore implements SalonPersistencePort {

        private final Map<Long, Salon> rows = new LinkedHashMap<>();
        private long sequence = 0L;

        void seed(Salon salon) {
            save(salon);
        }

        @Override
        public Salon save(Salon salon) {
            if (salon.getId() == null) {
                salon.setId(++sequence);
                salon.setCreatedAt(Instant.now());
            }
            salon.setUpdatedAt(Instant.now());
            rows.put(salon.getId(), copyOf(salon));
            return copyOf(salon);
        }

        @Override
        public Optional<Salon> findByTenantId(String tenantId) {
            return rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()))
                    .findFirst()
                    .map(SalonStore::copyOf);
        }

        @Override
        public Optional<Salon> findBySlug(String slug) {
            return rows.values().stream()
                    .filter(s -> slug.equals(s.getSlug()))
                    .findFirst()
                    .map(SalonStore::copyOf);
        }

        @Override
        public boolean existsBySlug(String slug) {
            return rows.values().stream().anyMatch(s -> slug.equals(s.getSlug()));
        }

        @Override
        public boolean existsByEmail(String email) {
            return rows.values().stream().anyMatch(s -> email.equals(s.getEmail()));
        }

        @Override
        public void deleteById(Long id) {
            rows.remove(id);
        }

        @Override
        public Page<Salon> findAll(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before) {
            return rows.values().stream()
                    .filter(s -> s.getStatus() == status
                            && s.getCreatedAt() != null && s.getCreatedAt().isBefore(before))
                    .map(SalonStore::copyOf)
                    .toList();
        }

        @Override
        public List<Salon> findByStatus(SalonStatus status) {
            return rows.values().stream()
                    .filter(s -> s.getStatus() == status)
                    .map(SalonStore::copyOf)
                    .toList();
        }

        private static Salon copyOf(Salon s) {
            return Salon.builder()
                    .id(s.getId()).externalId(s.getExternalId()).tenantId(s.getTenantId())
                    .name(s.getName()).slug(s.getSlug()).ownerUserId(s.getOwnerUserId())
                    .email(s.getEmail()).phone(s.getPhone()).description(s.getDescription())
                    .logoUrl(s.getLogoUrl()).primaryColor(s.getPrimaryColor())
                    .addressStreet(s.getAddressStreet()).addressCity(s.getAddressCity())
                    .addressPostalCode(s.getAddressPostalCode()).timezone(s.getTimezone())
                    .currency(s.getCurrency()).subscriptionPlan(s.getSubscriptionPlan())
                    .status(s.getStatus()).createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                    .build();
        }
    }

    private static final class BusinessHoursStore implements BusinessHoursPersistencePort {

        private final List<SalonBusinessHours> rows = new ArrayList<>();

        @Override
        public List<SalonBusinessHours> findBySalonId(Long salonId) {
            return rows.stream().filter(h -> salonId.equals(h.getSalonId())).toList();
        }

        @Override
        public List<SalonBusinessHours> saveAll(List<SalonBusinessHours> hours) {
            rows.addAll(hours);
            return List.copyOf(hours);
        }

        @Override
        public void deleteBySalonId(Long salonId) {
            rows.removeIf(h -> salonId.equals(h.getSalonId()));
        }
    }

    /** staff-service reachable and answering, with an empty catalogue - a legitimate state. */
    private static final class EmptyCatalogueStaffService implements StaffServicePort {

        @Override
        public Optional<List<EmployeePublicInfo>> getPublicEmployees(String tenantId) {
            return Optional.of(List.of());
        }

        @Override
        public Optional<List<ServicePublicInfo>> getPublicServices(String tenantId) {
            return Optional.of(List.of());
        }
    }

    private static String bodyOf(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String normalizeTimestamp(String body) {
        return body.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
    }
}
