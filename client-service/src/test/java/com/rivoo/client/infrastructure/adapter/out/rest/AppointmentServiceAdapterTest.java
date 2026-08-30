package com.rivoo.client.infrastructure.adapter.out.rest;

import com.rivoo.client.application.dto.ClientAppointmentDto;
import com.rivoo.client.application.dto.ClientAppointmentsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link AppointmentServiceAdapter} against the real JSON shapes appointment-service
 * emits, using {@link MockRestServiceServer} on the real {@code RestClient} — the same technique
 * as {@code StaffServiceAdapterTest} in appointment-service.
 * <p>
 * The two methods on this adapter have deliberately DIFFERENT error-handling contracts (D38,
 * "salvo que"): {@code getClientAppointments} (GDPR export) degrades to an empty list on
 * failure; {@code getClientAppointmentsPage} (the client screen's history, B3) must propagate
 * the failure, because a screen cannot show an empty page and a failed load identically.
 */
class AppointmentServiceAdapterTest {

    private static final String APPOINTMENT_SERVICE_URL = "http://appointment";

    private MockRestServiceServer server;
    private AppointmentServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AppointmentServiceAdapter(builder, APPOINTMENT_SERVICE_URL);
    }

    @Test
    void getClientAppointments_deserializesPayloadAndCarriesPrice() {
        server.expect(requestTo(APPOINTMENT_SERVICE_URL
                        + "/api/internal/admin/appointments/by-client/cli_1?tenantId=sal_A"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [
                          {"id":"apt_1","clientName":"Ana Lopez","employeeName":"Laura Martinez",
                           "serviceName":"Corte + Secado","servicePrice":35.00,
                           "startTime":"2026-08-05T10:00:00Z","endTime":"2026-08-05T10:45:00Z",
                           "status":"COMPLETED","source":"MANUAL"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ClientAppointmentDto> result = adapter.getClientAppointments("cli_1", "sal_A");

        server.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("apt_1");
        assertThat(result.get(0).price()).isEqualByComparingTo("35.00");
        assertThat(result.get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void getClientAppointments_appointmentServiceDown_degradesToEmptyList() {
        // GDPR export behaviour must NOT change (D38, "salvo que").
        server.expect(requestTo(APPOINTMENT_SERVICE_URL
                        + "/api/internal/admin/appointments/by-client/cli_1?tenantId=sal_A"))
                .andExpect(method(GET))
                .andRespond(withServerError());

        List<ClientAppointmentDto> result = adapter.getClientAppointments("cli_1", "sal_A");

        server.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void getClientAppointmentsPage_deserializesTheEnvelopeEmittedByAppointmentHistoryResponse() {
        server.expect(requestTo(APPOINTMENT_SERVICE_URL
                        + "/api/internal/admin/appointments/by-client/cli_1?tenantId=sal_A&page=0&size=7"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "content": [
                            {"id":"apt_1","clientName":"Ana Lopez","employeeName":"Laura Martinez",
                             "serviceName":"Corte + Secado","servicePrice":35.00,
                             "startTime":"2026-08-05T10:00:00Z","endTime":"2026-08-05T10:45:00Z",
                             "status":"COMPLETED","source":"MANUAL"}
                          ],
                          "page":0,"size":7,"totalElements":14,"totalPages":2,
                          "summary":{"totalAppointments":14,"billedAmount":612.00,
                                     "completedCount":11,"lastCompletedAt":"2026-08-05T10:00:00Z"}
                        }
                        """, MediaType.APPLICATION_JSON));

        ClientAppointmentsResponse result = adapter.getClientAppointmentsPage("cli_1", "sal_A", 0, 7);

        server.verify();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).price()).isEqualByComparingTo("35.00");
        assertThat(result.totalElements()).isEqualTo(14);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.summary().totalAppointments()).isEqualTo(14);
        assertThat(result.summary().billedAmount()).isEqualByComparingTo(new BigDecimal("612.00"));
        assertThat(result.summary().completedCount()).isEqualTo(11);
    }

    @Test
    void getClientAppointmentsPage_appointmentServiceDown_propagatesTheFailure() {
        // D38: unlike getClientAppointments (GDPR export), this path must NOT degrade to an
        // empty page — the caller (and eventually the UI) needs a real error.
        server.expect(requestTo(APPOINTMENT_SERVICE_URL
                        + "/api/internal/admin/appointments/by-client/cli_1?tenantId=sal_A&page=0&size=7"))
                .andExpect(method(GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.getClientAppointmentsPage("cli_1", "sal_A", 0, 7))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(ex -> assertThat(((HttpServerErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        server.verify();
    }
}
