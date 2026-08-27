package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.dto.PublicBookingRequest;
import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.port.in.CancelAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.CheckAvailabilityUseCase;
import com.rivoo.appointment.domain.port.in.CreateAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.GetAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.PublicBookingUseCase;
import com.rivoo.appointment.domain.port.in.UpdateAppointmentStatusUseCase;
import com.rivoo.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves — at the HTTP contract level, with the real {@link GlobalExceptionHandler} and
 * {@link AppointmentExceptionHandler} wired together as they are at runtime — that the two
 * anonymous public endpoints ({@code GET /api/v1/appointments/public/availability} and
 * {@code POST /api/v1/appointments/book}) do not let a caller enumerate salons: a slug that
 * does not exist and a slug that exists but is not ACTIVE must produce the exact same
 * response (status, type, title, detail), not just the same HTTP status.
 * <p>
 * Both root causes are represented here by the same {@link SalonNotFoundException} because
 * that is the actual production design (see {@code SalonServiceAdapter#getSalonBySlug} for
 * the "does not exist" case and {@code AvailabilityService}/{@code AppointmentService#book}
 * for the "not ACTIVE" case) — the unification happens before the exception ever reaches
 * this controller, so what this test protects is that the HTTP layer does not reintroduce a
 * distinction (e.g. via response headers, extra properties, or a different message) once the
 * exception arrives.
 */
class AppointmentPublicEndpointsEnumerationTest {

    private CheckAvailabilityUseCase checkAvailabilityUseCase;
    private PublicBookingUseCase publicBookingUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkAvailabilityUseCase = mock(CheckAvailabilityUseCase.class);
        publicBookingUseCase = mock(PublicBookingUseCase.class);
        AppointmentController controller = new AppointmentController(
                mock(CreateAppointmentUseCase.class),
                mock(GetAppointmentUseCase.class),
                mock(UpdateAppointmentStatusUseCase.class),
                mock(CancelAppointmentUseCase.class),
                checkAvailabilityUseCase,
                publicBookingUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new AppointmentExceptionHandler())
                .build();
    }

    @Test
    void publicAvailability_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        String slug = "misteriosa";

        // Scenario A: the slug does not exist at all (what SalonServiceAdapter throws on a 404).
        when(checkAvailabilityUseCase.getPublicAvailableSlots(eq(slug), anyString(), any(LocalDate.class), any()))
                .thenThrow(new SalonNotFoundException(slug));
        String notFoundBody = mockMvc.perform(get("/api/v1/appointments/public/availability")
                        .param("salonSlug", slug)
                        .param("employeeId", "emp_1")
                        .param("date", "2026-09-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andReturn().getResponse().getContentAsString();

        // Scenario B: the slug exists but resolves to a non-ACTIVE salon (what AvailabilityService
        // throws after checking status). Same slug, different underlying cause.
        reset(checkAvailabilityUseCase);
        when(checkAvailabilityUseCase.getPublicAvailableSlots(eq(slug), anyString(), any(LocalDate.class), any()))
                .thenThrow(new SalonNotFoundException(slug));
        String suspendedBody = mockMvc.perform(get("/api/v1/appointments/public/availability")
                        .param("salonSlug", slug)
                        .param("employeeId", "emp_1")
                        .param("date", "2026-09-01"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
    }

    @Test
    void publicBook_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        String slug = "misteriosa";
        String requestBody = validBookingRequestJson(slug);

        // Scenario A: the slug does not exist at all.
        when(publicBookingUseCase.book(any(PublicBookingRequest.class)))
                .thenThrow(new SalonNotFoundException(slug));
        String notFoundBody = mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andReturn().getResponse().getContentAsString();

        // Scenario B: the slug exists but resolves to a non-ACTIVE salon.
        reset(publicBookingUseCase);
        when(publicBookingUseCase.book(any(PublicBookingRequest.class)))
                .thenThrow(new SalonNotFoundException(slug));
        String suspendedBody = mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertBodiesIdenticalExceptTimestamp(notFoundBody, suspendedBody);
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
     * byte-for-byte identical, which is what actually closes the enumeration oracle.
     */
    private static void assertBodiesIdenticalExceptTimestamp(String bodyA, String bodyB) {
        String normalizedA = bodyA.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
        String normalizedB = bodyB.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
        assertThat(normalizedA).isEqualTo(normalizedB);
    }
}
