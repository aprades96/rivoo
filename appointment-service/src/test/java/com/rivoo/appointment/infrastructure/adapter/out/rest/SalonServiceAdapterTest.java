package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies that {@link SalonServiceAdapter#getSalonBySlug(String)} maps a
 * legitimate 404 from salon-service to {@link SalonNotFoundException} (a
 * domain "not found", not an opaque server error) so the two anonymous
 * public flows can turn it into the same response they give for a salon
 * that exists but is not ACTIVE — see {@code AvailabilityService} and
 * {@code AppointmentService#book}.
 */
class SalonServiceAdapterTest {

    private static final String SALON_SERVICE_URL = "http://salon";

    private MockRestServiceServer server;
    private SalonServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SalonServiceAdapter(builder, SALON_SERVICE_URL);
    }

    @Test
    void getSalonBySlug_returnsSalonInfo_whenSalonServiceRespondsWithSuccess() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"sal_A","name":"Bella Vista","slug":"bella-vista","status":"ACTIVE"}
                        """, MediaType.APPLICATION_JSON));

        SalonServicePort.SalonInfo result = adapter.getSalonBySlug("bella-vista");

        assertThat(result.tenantId()).isEqualTo("sal_A");
        assertThat(result.name()).isEqualTo("Bella Vista");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getSalonBySlug_throwsSalonNotFoundException_whenSalonServiceRespondsWith404() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/ghost-slug"))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));

        // Must be the domain "not found" exception, not the generic RuntimeException that
        // used to blanket-wrap every failure (which surfaced as a 500 via the catch-all
        // handler and let anyone distinguish "unknown slug" from "suspended salon").
        assertThatThrownBy(() -> adapter.getSalonBySlug("ghost-slug"))
                .isExactlyInstanceOf(SalonNotFoundException.class);
    }

    @Test
    void getSalonBySlug_stillThrowsRuntimeException_whenSalonServiceIsDown() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        // An actual upstream failure (not a "the slug doesn't exist" case) is still
        // reported as an unexpected error — this must not regress into a silent 404.
        assertThatThrownBy(() -> adapter.getSalonBySlug("bella-vista"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(SalonNotFoundException.class);
    }
}
