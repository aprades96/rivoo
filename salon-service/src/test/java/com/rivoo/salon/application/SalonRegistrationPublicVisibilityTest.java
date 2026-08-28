package com.rivoo.salon.application;

import com.rivoo.common.security.KeycloakJwtConverter;
import com.rivoo.common.tenant.TenantContext;
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
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonController;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonExceptionHandler;
import com.rivoo.salon.infrastructure.adapter.out.rest.AuthServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.BillingServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.NotificationServiceAdapter;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
 * The property pinned here is the whole invariant, end to end: <b>a salon is invisible on every
 * anonymous surface until its owner makes an authenticated request, and visible immediately
 * afterwards.</b> This test drives the REAL saga on both paths - real {@link OnboardingSagaService},
 * real outbound adapters, a persistence port that is a genuine in-memory store rather than a stub,
 * so what the saga writes is what the public read sees - and asserts the public surface cannot tell
 * them apart. Then it asserts the salon DOES become visible on the owner's first authenticated call,
 * because a fix that simply never publishes anything would pass the first half and break the
 * product.
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

    @AfterEach
    void clearAmbientState() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

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
        // salon is not publicly visible while its owner has not turned up authenticated.
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
    void ownerOpensTheirDashboard_salonBecomesPubliclyVisibleWithNoManualStep() throws Exception {
        World free = worldWhereAddressIsFree();
        free.expectWelcomeNotification();

        free.register();
        assertThat(free.lookUpProbeSlug().getStatus())
                .as("invisible while nobody has authenticated as its owner")
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        MockHttpServletResponse dashboard = free.openDashboard(Boolean.TRUE);

        assertThat(dashboard.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(bodyOf(dashboard)).contains("\"status\":\"ACTIVE\"");
        MockHttpServletResponse afterDashboard = free.lookUpProbeSlug();
        assertThat(afterDashboard.getStatus())
                .as("the owner's own first authenticated call must publish the salon, with nobody"
                        + " touching anything")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(bodyOf(afterDashboard)).contains(PROBE_SLUG);
        free.auth.verify();
        free.notifications.verify();
    }

    @Test
    void ownerNeverOpensTheirDashboard_salonStaysInvisibleForEverAndNoWelcomeMailGoesOut() throws Exception {
        // Recorded deliberately: this salon waits indefinitely. It is the right outcome — its owner
        // cannot take a booking without first adding services from the very dashboard they never
        // opened — and it keeps its slug and address, which releasing would let the next probe
        // re-create.
        World free = worldWhereAddressIsFree();

        free.register();

        assertThat(free.lookUpProbeSlug().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(free.salons.findBySlug(PROBE_SLUG).orElseThrow().getStatus())
                .isEqualTo(SalonStatus.ONBOARDING);
        // notifications holds no expectation: a welcome mail here would have failed the call itself.
        free.notifications.verify();
        free.auth.verify();
    }

    @Test
    void dashboardTokenWithNoEmailVerifiedClaim_stillPublishesTheSalon() throws Exception {
        // A realm that does not map the claim issues perfectly valid tokens without it. Refusing to
        // publish there would strand every owner on that realm with an invisible salon and no
        // self-service way out — a far worse error than trusting the token, which Keycloak would not
        // have issued while a VERIFY_EMAIL required action was still pending.
        World free = worldWhereAddressIsFree();
        free.expectWelcomeNotification();

        free.register();
        free.openDashboard(null);

        assertThat(free.lookUpProbeSlug().getStatus()).isEqualTo(HttpStatus.OK.value());
        free.notifications.verify();
    }

    @Test
    void dashboardTokenSayingTheAddressIsNotVerified_doesNotPublishTheSalon() throws Exception {
        // The one case where the claim overrides the token's existence. An explicit false is the
        // identity provider actively saying "no", which is not the same as saying nothing.
        World free = worldWhereAddressIsFree();

        free.register();
        MockHttpServletResponse dashboard = free.openDashboard(Boolean.FALSE);

        assertThat(dashboard.getStatus())
                .as("the owner still gets their dashboard; it is the PUBLICATION that is withheld")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(bodyOf(dashboard)).contains("\"status\":\"ONBOARDING\"");
        assertThat(free.lookUpProbeSlug().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(free.salons.activateAttempts.get())
                .as("an explicit denial must not even reach the database")
                .isZero();
        // No welcome expectation registered: sending one would have failed the request outright.
        free.notifications.verify();
    }

    @Test
    void dashboardLoadOnAnAlreadyPublishedSalon_performsNoWriteAtAll() throws Exception {
        // Writing on a GET is only defensible while it is strictly conditional. Every load after the
        // first is a plain read: no conditional update statement, no row touched.
        World free = worldWhereAddressIsFree();
        free.expectWelcomeNotification();

        free.register();
        free.openDashboard(Boolean.TRUE);

        int attemptsAfterPublication = free.salons.activateAttempts.get();
        int writesAfterPublication = free.salons.writes.get();
        assertThat(attemptsAfterPublication).isEqualTo(1);

        free.openDashboard(Boolean.TRUE);
        free.openDashboard(Boolean.TRUE);

        assertThat(free.salons.activateAttempts.get())
                .as("an ACTIVE salon must not send a conditional update on every dashboard load")
                .isEqualTo(attemptsAfterPublication);
        assertThat(free.salons.writes.get())
                .as("nor touch the row by any other route")
                .isEqualTo(writesAfterPublication);
        // A second WELCOME would have hit the notification server with no expectation left, failing
        // the request; verify() then also asserts the first one really did go out.
        free.notifications.verify();
    }

    @Test
    void twoDashboardLoadsAtOnce_publishTheSalonOnceAndSendOneWelcomeMail() throws Exception {
        // Two tabs, a refresh, a retried fetch. A read-decide-write implementation lets both callers
        // observe ONBOARDING and both believe they published the salon, which is two welcome mails.
        // The barrier below forces exactly that interleaving: neither thread may proceed past its
        // read until the other has read too.
        RacingSalonStore salons = new RacingSalonStore();
        salons.seed(onboardingSalon());
        CountingNotificationService notifications = new CountingNotificationService();
        BusinessHoursStore businessHours = new BusinessHoursStore();
        SalonService salonService = new SalonService(
                salons,
                businessHours,
                new EmptyCatalogueStaffService(),
                new SalonDtoMapperImpl(),
                new SalonPublicSnapshotLoader(salons, businessHours),
                notifications);

        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> failures = new ArrayList<>();
        Runnable dashboardLoad = () -> {
            try {
                salonService.getByTenantId("sal_racing", Boolean.TRUE);
            } catch (Throwable t) {
                synchronized (failures) {
                    failures.add(t);
                }
            } finally {
                done.countDown();
            }
        };
        Thread first = new Thread(dashboardLoad, "dashboard-load-1");
        Thread second = new Thread(dashboardLoad, "dashboard-load-2");
        first.start();
        second.start();

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("both dashboard loads must finish; a deadlock here is a failure, not a pass")
                .isTrue();
        assertThat(failures).isEmpty();
        assertThat(salons.bothReadBeforeEitherWrote)
                .as("the race the test claims to exercise must actually have happened")
                .isTrue();
        assertThat(salons.activateAttempts.get())
                .as("both callers must genuinely have tried; one winning by not trying proves nothing")
                .isEqualTo(2);
        assertThat(salons.findByTenantId("sal_racing").orElseThrow().getStatus())
                .isEqualTo(SalonStatus.ACTIVE);
        assertThat(notifications.welcomeMails.get())
                .as("exactly one welcome mail, however many callers arrived together")
                .isEqualTo(1);
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
        // salon is really published. An unexpected POST here fails the request itself.
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

    private static Salon onboardingSalon() {
        return Salon.builder()
                .externalId("sal_racing")
                .tenantId("sal_racing")
                .name("Racing Salon")
                .slug("racing-salon")
                .ownerUserId(KEYCLOAK_USER_ID)
                .email(VICTIM_EMAIL)
                .phone("+34600111222")
                .addressStreet("Carrer Demo 1")
                .addressCity("Barcelona")
                .addressPostalCode("08001")
                .timezone("Europe/Madrid")
                .currency("EUR")
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(SalonStatus.ONBOARDING)
                .build();
    }

    /**
     * One wiring of the whole slice: the real saga, the real publication path, the real public read
     * path, the real outbound adapters, and the real {@link KeycloakJwtConverter} turning a token
     * into the {@code email_verified} claim the controller reads. Only the HTTP edge and the two
     * persistence ports are doubled, and the persistence double is a real store, not a stub - the
     * whole point is that the public read observes what the registration actually wrote.
     */
    private static final class World {

        final SalonStore salons = new SalonStore();
        final BusinessHoursStore businessHours = new BusinessHoursStore();
        final MockRestServiceServer auth;
        final MockRestServiceServer billing;
        final MockRestServiceServer notifications;
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

            SalonService salonService = new SalonService(
                    salons,
                    businessHours,
                    new EmptyCatalogueStaffService(),
                    new SalonDtoMapperImpl(),
                    new SalonPublicSnapshotLoader(salons, businessHours),
                    notificationAdapter);

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

        /**
         * The owner's first authenticated call. The security filter chain does not run under
         * {@code standaloneSetup}, so the two things it would leave behind are placed by hand: the
         * tenant in {@link TenantContext} (the gateway header the interceptor reads) and the
         * authentication in the context. The authentication is built by the REAL
         * {@link KeycloakJwtConverter}, so the claim actually travels the production route rather
         * than being handed to the controller pre-digested.
         *
         * @param emailVerifiedClaim {@code null} to issue a token with no such claim at all
         */
        MockHttpServletResponse openDashboard(Boolean emailVerifiedClaim) throws Exception {
            String tenantId = salons.findBySlug(PROBE_SLUG).orElseThrow().getTenantId();
            Jwt.Builder jwt = Jwt.withTokenValue("header.payload.signature")
                    .header("alg", "RS256")
                    .subject(KEYCLOAK_USER_ID)
                    .claim("tenant_id", tenantId)
                    .claim("email", VICTIM_EMAIL)
                    .claim("realm_access", Map.of("roles", List.of("SALON_OWNER")));
            if (emailVerifiedClaim != null) {
                jwt.claim("email_verified", emailVerifiedClaim);
            }
            SecurityContextHolder.getContext()
                    .setAuthentication(new KeycloakJwtConverter().convert(jwt.build()));
            TenantContext.setCurrentTenantId(tenantId);
            try {
                return mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/salons/me"))
                        .andReturn().getResponse();
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
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
     * an entity and back. {@link #activateIfOnboarding(String)} is a genuine compare-and-set, which
     * is what the JPQL {@code UPDATE ... WHERE status = ONBOARDING} behind it is.
     */
    private static class SalonStore implements SalonPersistencePort {

        final Map<Long, Salon> rows = new LinkedHashMap<>();
        /** How many times the conditional update statement was issued at all. */
        final AtomicInteger activateAttempts = new AtomicInteger();
        /** How many times the onboarding-completion conditional update was issued at all. */
        final AtomicInteger markOnboardingAttempts = new AtomicInteger();
        /** Every row-touching operation: {@code save} plus each successful promotion. */
        final AtomicInteger writes = new AtomicInteger();
        private long sequence = 0L;

        void seed(Salon salon) {
            save(salon);
        }

        @Override
        public synchronized Salon save(Salon salon) {
            writes.incrementAndGet();
            if (salon.getId() == null) {
                salon.setId(++sequence);
                salon.setCreatedAt(Instant.now());
            }
            salon.setUpdatedAt(Instant.now());
            rows.put(salon.getId(), copyOf(salon));
            return copyOf(salon);
        }

        @Override
        public synchronized Optional<Salon> findByTenantId(String tenantId) {
            return rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()))
                    .findFirst()
                    .map(SalonStore::copyOf);
        }

        @Override
        public synchronized Optional<Salon> findBySlug(String slug) {
            return rows.values().stream()
                    .filter(s -> slug.equals(s.getSlug()))
                    .findFirst()
                    .map(SalonStore::copyOf);
        }

        @Override
        public synchronized boolean existsBySlug(String slug) {
            return rows.values().stream().anyMatch(s -> slug.equals(s.getSlug()));
        }

        @Override
        public synchronized boolean existsByEmail(String email) {
            return rows.values().stream().anyMatch(s -> email.equals(s.getEmail()));
        }

        @Override
        public synchronized void deleteById(Long id) {
            rows.remove(id);
        }

        @Override
        public Page<Salon> findAll(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public synchronized List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before) {
            return rows.values().stream()
                    .filter(s -> s.getStatus() == status
                            && s.getCreatedAt() != null && s.getCreatedAt().isBefore(before))
                    .map(SalonStore::copyOf)
                    .toList();
        }

        @Override
        public synchronized int activateIfOnboarding(String tenantId) {
            activateAttempts.incrementAndGet();
            Optional<Salon> match = rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()) && s.getStatus() == SalonStatus.ONBOARDING)
                    .findFirst();
            if (match.isEmpty()) {
                return 0;
            }
            match.get().setStatus(SalonStatus.ACTIVE);
            match.get().setUpdatedAt(Instant.now());
            writes.incrementAndGet();
            return 1;
        }

        @Override
        public synchronized int markOnboardingCompleted(String tenantId) {
            markOnboardingAttempts.incrementAndGet();
            Optional<Salon> match = rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()) && s.getOnboardingCompletedAt() == null)
                    .findFirst();
            if (match.isEmpty()) {
                return 0;
            }
            match.get().setOnboardingCompletedAt(Instant.now());
            match.get().setUpdatedAt(Instant.now());
            writes.incrementAndGet();
            return 1;
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
                    .status(s.getStatus()).onboardingCompletedAt(s.getOnboardingCompletedAt())
                    .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Forces the interleaving the concurrency test is about: the first two reads of the salon both
     * complete before either caller is allowed to act on what it read. Without this the two threads
     * would almost always run one after the other and a read-decide-write bug would survive.
     */
    private static final class RacingSalonStore extends SalonStore {

        private final AtomicInteger reads = new AtomicInteger();
        volatile boolean bothReadBeforeEitherWrote;

        /**
         * The barrier ACTION records the state, not the threads: it runs exactly once, while both
         * threads are still parked, so the observation cannot itself race with the promotion.
         */
        private final CyclicBarrier bothHaveRead =
                new CyclicBarrier(2, () -> bothReadBeforeEitherWrote = activateAttempts.get() == 0);

        @Override
        public Optional<Salon> findByTenantId(String tenantId) {
            Optional<Salon> found = super.findByTenantId(tenantId);
            if (reads.incrementAndGet() <= 2) {
                try {
                    bothHaveRead.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("the two dashboard loads never met at the barrier", e);
                }
            }
            return found;
        }
    }

    private static final class CountingNotificationService implements NotificationServicePort {

        final AtomicInteger welcomeMails = new AtomicInteger();

        @Override
        public void sendWelcomeEmail(String tenantId, String recipientEmail, String salonName) {
            welcomeMails.incrementAndGet();
        }

        @Override
        public void sendExistingAccountRegistrationAttempt(String recipientEmail) {
            throw new AssertionError("the publication path must never send a registration-attempt mail");
        }
    }

    private static final class BusinessHoursStore implements BusinessHoursPersistencePort {

        private final List<SalonBusinessHours> rows = new ArrayList<>();

        @Override
        public synchronized List<SalonBusinessHours> findBySalonId(Long salonId) {
            return rows.stream().filter(h -> salonId.equals(h.getSalonId())).toList();
        }

        @Override
        public synchronized List<SalonBusinessHours> saveAll(List<SalonBusinessHours> hours) {
            rows.addAll(hours);
            return List.copyOf(hours);
        }

        @Override
        public synchronized void deleteBySalonId(Long salonId) {
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
