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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.GET;
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
        String notFoundBody = performAvailability(unknownSlugMockMvc(slug), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Scenario B: the slug resolves, but the salon is SUSPENDED.
        String suspendedBody = performAvailability(suspendedSalonMockMvc(slug), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
    }

    @Test
    void publicBook_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        String slug = "misteriosa";

        // Scenario A: salon-service genuinely has no salon for this slug.
        String notFoundBody = performBooking(unknownSlugMockMvc(slug), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Scenario B: the slug resolves, but the salon is SUSPENDED.
        String suspendedBody = performBooking(suspendedSalonMockMvc(slug), slug)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
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
    private static MockMvc unknownSlugMockMvc(String slug) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + slug))
                .andExpect(method(GET))
                // The exact body salon-service's own SalonExceptionHandler produces for a
                // genuine SalonNotFoundException — SalonServiceAdapter only trusts a 404 as
                // "unknown salon" when it carries this marker (see Bloque 2).
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                        {"type":"https://rivoo.com/errors/salon-not-found","title":"Salon Not Found",
                         "status":404,"detail":"Salon not found: %s"}
                        """.formatted(slug)));
        return buildMockMvc(builder);
    }

    /**
     * Builds the full real chain backed by a fake salon-service that answers 200 with a
     * salon whose status is {@code SUSPENDED} — mirroring a slug that exists but is not
     * publicly bookable.
     */
    private static MockMvc suspendedSalonMockMvc(String slug) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + slug))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"sal_X","name":"Misteriosa","slug":"%s","status":"SUSPENDED"}
                        """.formatted(slug), MediaType.APPLICATION_JSON));
        return buildMockMvc(builder);
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
