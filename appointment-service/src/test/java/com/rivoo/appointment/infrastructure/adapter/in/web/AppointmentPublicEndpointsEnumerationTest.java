package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.AppointmentService;
import com.rivoo.appointment.application.AvailabilityService;
import com.rivoo.appointment.domain.port.in.CancelAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.CreateAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.GetAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.UpdateAppointmentStatusUseCase;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.BillingServicePort;
import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.SalonServiceAdapter;
import com.rivoo.appointment.infrastructure.mapper.AppointmentDtoMapper;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.common.web.RivooErrorTypes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves — at the HTTP contract level, with the real {@link GlobalExceptionHandler} and
 * {@link AppointmentExceptionHandler} wired together as they are at runtime, AND with the real
 * {@link AvailabilityService} / {@link AppointmentService} (the classes that actually implement
 * the ACTIVE-only check) and the real {@link SalonServiceAdapter} (the class that actually
 * converts a salon-service 404 into a domain exception) — that the two anonymous public
 * endpoints ({@code GET /api/v1/appointments/public/availability} and
 * {@code POST /api/v1/appointments/book}) do not let a caller enumerate salons: a slug that
 * does not exist and a slug that exists but is not ACTIVE must produce the exact same
 * response (status, type, title, detail), not just the same HTTP status.
 * <p>
 * The double here is deliberately pushed all the way down to the HTTP edge, via
 * {@link MockRestServiceServer} (the same tool {@code SalonServiceAdapterTest} already uses):
 * scenario A makes the fake salon-service answer with a genuine 404 (what
 * {@link SalonServiceAdapter#getSalonBySlug} turns into a {@code SalonNotFoundException}),
 * scenario B makes it answer with 200 and a salon whose status is {@code SUSPENDED} (what
 * {@link AvailabilityService} / {@link AppointmentService#book} turn into the very same
 * exception via their ACTIVE-only check). Both root causes are real, distinct code paths —
 * not the same mock stubbed twice — so reverting either half of the anti-enumeration fix
 * (the adapter's 404 handling or the services' ACTIVE check) makes these tests fail.
 */
class AppointmentPublicEndpointsEnumerationTest {

    private static final String SALON_SERVICE_URL = "http://salon";

    @Test
    void publicAvailability_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        String slug = "misteriosa";

        // Scenario A: salon-service genuinely has no salon for this slug.
        Fixture unknownSlugFixture = unknownSlugMockMvc(slug);
        String notFoundBody = performAvailability(unknownSlugFixture.mockMvc(), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        // Confirms the HTTP call to salon-service actually happened — without this, a
        // refactor that short-circuited before reaching SalonServiceAdapter would leave
        // the expectation above unmet in silence.
        unknownSlugFixture.server().verify();

        // Scenario B: the slug resolves, but the salon is SUSPENDED.
        Fixture suspendedSalonFixture = suspendedSalonMockMvc(slug);
        String suspendedBody = performAvailability(suspendedSalonFixture.mockMvc(), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        suspendedSalonFixture.server().verify();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
    }

    @Test
    void publicBook_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        String slug = "misteriosa";

        // Scenario A: salon-service genuinely has no salon for this slug.
        Fixture unknownSlugFixture = unknownSlugMockMvc(slug);
        String notFoundBody = performBooking(unknownSlugFixture.mockMvc(), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        unknownSlugFixture.server().verify();

        // Scenario B: the slug resolves, but the salon is SUSPENDED.
        Fixture suspendedSalonFixture = suspendedSalonMockMvc(slug);
        String suspendedBody = performBooking(suspendedSalonFixture.mockMvc(), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        suspendedSalonFixture.server().verify();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
    }

    @Test
    void publicBook_salonServiceDown_answers502WithoutNamingAnyInternalService() throws Exception {
        // Same wiring as the enumeration tests above: real controller, real use cases, real
        // SalonServiceAdapter, both advices registered exactly as at runtime. The double sits at
        // the HTTP edge, so the SalonServiceUnavailableException under test is produced by the
        // real adapter from a real upstream 500 - not stubbed into existence.
        Fixture fixture = salonServiceDownMockMvc("misteriosa");

        String body = performBooking(fixture.mockMvc(), "misteriosa")
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();
        fixture.server().verify();

        assertBodyRevealsNoTopology(body);
    }

    @Test
    void publicAvailability_salonServiceDown_answers502WithoutNamingAnyInternalService() throws Exception {
        // The endpoint next door: fixing only one of the two anonymous entry points is exactly
        // how the equivalent leak survived the previous pass.
        Fixture fixture = salonServiceDownMockMvc("misteriosa");

        String body = performAvailability(fixture.mockMvc(), "misteriosa")
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();
        fixture.server().verify();

        assertBodyRevealsNoTopology(body);
    }

    /**
     * Builds the full real chain backed by a fake salon-service that answers 500 - salon-service
     * itself broken, as opposed to answering "no such salon".
     */
    private static Fixture salonServiceDownMockMvc(String slug) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + slug))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));
        return new Fixture(buildMockMvc(builder), server);
    }

    /**
     * The response an unauthenticated caller receives must not describe the internal topology.
     * <p>
     * {@code detail} was the field that named the dependency ("salon-service returned a server
     * error for slug: X"), and it is pinned here to the fixed string. The body as a whole must
     * additionally contain neither the internal base URL (which the exception's CAUSE carries)
     * nor the requested slug.
     * <p>
     * Deliberately NOT asserted: that the literal "salon-service" is absent from the whole body.
     * The published {@code type} URI is {@code .../errors/salon-service-unavailable} - a stable
     * error-taxonomy identifier that predates this change and that consumers key on. Renaming it
     * is a contract change with no consumer benefit; see the report accompanying this commit.
     */
    private static void assertBodyRevealsNoTopology(String body) {
        assertThat(body)
                .as("the internal URL travels in the exception's cause and must not reach the client")
                .doesNotContain(SALON_SERVICE_URL);
        assertThat(body)
                .as("detail must be the fixed client-safe string, not the adapter's internal message")
                .contains("\"detail\":\"This booking page is temporarily unavailable."
                        + " Please try again in a few minutes.\"")
                .doesNotContain("returned a server error for slug")
                .doesNotContain("misteriosa");
    }

    private record Fixture(MockMvc mockMvc, MockRestServiceServer server) {
    }

    private static org.springframework.test.web.servlet.ResultActions performAvailability(
            MockMvc mockMvc, String slug) throws Exception {
        return mockMvc.perform(get("/api/v1/appointments/public/availability")
                .param("salonSlug", slug)
                .param("employeeId", "emp_1")
                .param("date", "2026-09-01"));
    }

    private static org.springframework.test.web.servlet.ResultActions performBooking(
            MockMvc mockMvc, String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/appointments/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBookingRequestJson(slug)));
    }

    /**
     * Builds the full real chain (controller + both advices + real use cases + real
     * {@link SalonServiceAdapter}) backed by a fake salon-service that answers 404 for the
     * given slug — mirroring a slug salon-service has genuinely never heard of.
     */
    private static Fixture unknownSlugMockMvc(String slug) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + slug))
                .andExpect(method(GET))
                // The exact body salon-service's own SalonExceptionHandler produces for a
                // genuine SalonNotFoundException — SalonServiceAdapter only trusts a 404 as
                // "unknown salon" when it carries this marker (see Bloque 3: RivooErrorTypes).
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                        {"type":"%s","title":"Salon Not Found",
                         "status":404,"detail":"Salon not found: %s"}
                        """.formatted(RivooErrorTypes.SALON_NOT_FOUND, slug)));
        return new Fixture(buildMockMvc(builder), server);
    }

    /**
     * Builds the full real chain backed by a fake salon-service that answers 200 with a
     * salon whose status is {@code SUSPENDED} — mirroring a slug that exists but is not
     * publicly bookable.
     */
    private static Fixture suspendedSalonMockMvc(String slug) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + slug))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"sal_X","name":"Misteriosa","slug":"%s","status":"SUSPENDED"}
                        """.formatted(slug), MediaType.APPLICATION_JSON));
        return new Fixture(buildMockMvc(builder), server);
    }

    private static MockMvc buildMockMvc(RestClient.Builder salonServiceRestClientBuilder) {
        SalonServiceAdapter salonServiceAdapter =
                new SalonServiceAdapter(salonServiceRestClientBuilder, SALON_SERVICE_URL);

        AvailabilityService availabilityService = new AvailabilityService(
                mock(AppointmentPersistencePort.class),
                mock(StaffServicePort.class),
                salonServiceAdapter);

        AppointmentService appointmentService = new AppointmentService(
                mock(AppointmentPersistencePort.class),
                mock(StaffServicePort.class),
                mock(ClientServicePort.class),
                mock(BillingServicePort.class),
                mock(NotificationServicePort.class),
                salonServiceAdapter,
                mock(AppointmentDtoMapper.class));

        AppointmentController controller = new AppointmentController(
                mock(CreateAppointmentUseCase.class),
                mock(GetAppointmentUseCase.class),
                mock(UpdateAppointmentStatusUseCase.class),
                mock(CancelAppointmentUseCase.class),
                availabilityService,
                appointmentService);

        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new AppointmentExceptionHandler())
                .build();
    }

    private static String validBookingRequestJson(String slug) {
        return """
                {
                  "salonSlug": "%s",
                  "employeeExternalId": "emp_abc123",
                  "serviceExternalId": "svc_xyz456",
                  "clientFirstName": "Ana",
                  "clientLastName": "Garcia",
                  "clientEmail": "ana@example.com",
                  "clientPhone": "+34 612 345 678",
                  "requestedTime": "2026-09-15T10:00:00",
                  "honeypot": null
                }
                """.formatted(slug);
    }

    /**
     * The only field allowed to differ between the two responses is {@code timestamp}
     * (set to {@code Instant.now()} independently on each call). Everything else — status,
     * type, title, detail, and the absence of any extra distinguishing property — must be
     * identical, which is what actually closes the enumeration oracle.
     */
    private static void assertBodiesIdenticalExceptTimestamp(String bodyA, String bodyB) {
        String normalizedA = bodyA.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
        String normalizedB = bodyB.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
        assertThat(normalizedA).isEqualTo(normalizedB);
    }
}
