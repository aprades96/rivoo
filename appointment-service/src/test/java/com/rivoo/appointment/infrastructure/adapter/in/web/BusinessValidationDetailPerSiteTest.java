package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.AppointmentService;
import com.rivoo.appointment.application.AvailabilityService;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.BillingServicePort;
import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.SalonServiceAdapter;
import com.rivoo.appointment.infrastructure.mapper.AppointmentDtoMapper;
import com.rivoo.common.exception.BusinessValidationException;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link BusinessValidationException} is thrown from both an anonymous and an authenticated
 * endpoint of the SAME service, with two of the messages ("Employee is not active", "Service is
 * not active") being character-for-character identical on both. A class-level decision is
 * therefore wrong in one direction or the other, and this pins the per-site one:
 * <ul>
 *   <li>{@code POST /api/v1/appointments} ({@code hasAnyRole('SALON_OWNER','EMPLOYEE')}) publishes
 *       its three rejections - the caller is the tenant the message is about;</li>
 *   <li>{@code POST /api/v1/appointments/book} (ANONYMOUS) publishes the two booking-window rules,
 *       which describe only the visitor's own submitted date and are the sole instruction telling
 *       them how to fix the form;</li>
 *   <li>the same anonymous endpoint must NOT publish "employee/service is not active", which
 *       describes the salon's internal state. Those two are the regression guard: a mutation
 *       swapping them to {@code clientSafe} has to fail here.</li>
 * </ul>
 * Wiring follows {@link PublicBookingDetailLeakTest}: real controller, real
 * {@link AppointmentService}, real {@link SalonServiceAdapter} driven at the HTTP edge, real
 * advice. The doubles sit below the exception-to-response mapping under test.
 */
class BusinessValidationDetailPerSiteTest {

    private static final String SALON_SERVICE_URL = "http://salon-internal.rivoo.local:8082";
    private static final String SLUG = "misteriosa";
    private static final String TENANT_ID = "sal_98765432-abcd-ef01-2345-678901234567";
    private static final String EMPLOYEE_ID = "emp_abc123";
    private static final String SERVICE_ID = "svc_xyz456";
    private static final String CLIENT_ID = "cli_def789";

    private static final ZoneId SALON_TIMEZONE = ZoneId.of("Europe/Madrid");

    private StaffServicePort staffServicePort;
    private ClientServicePort clientServicePort;
    private MockRestServiceServer salonServer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        staffServicePort = mock(StaffServicePort.class);
        clientServicePort = mock(ClientServicePort.class);
        BillingServicePort billingServicePort = mock(BillingServicePort.class);
        AppointmentPersistencePort appointmentPersistencePort = mock(AppointmentPersistencePort.class);

        when(billingServicePort.getMaxAppointmentsPerMonth(anyString())).thenReturn(-1);
        activeEmployee();
        activeService();

        RestClient.Builder builder = RestClient.builder();
        salonServer = MockRestServiceServer.bindTo(builder).build();
        SalonServiceAdapter salonServiceAdapter = new SalonServiceAdapter(builder, SALON_SERVICE_URL);

        AppointmentService appointmentService = new AppointmentService(
                appointmentPersistencePort,
                staffServicePort,
                clientServicePort,
                billingServicePort,
                mock(NotificationServicePort.class),
                salonServiceAdapter,
                mock(AppointmentDtoMapper.class));

        AvailabilityService availabilityService = new AvailabilityService(
                appointmentPersistencePort, staffServicePort, salonServiceAdapter);

        AppointmentController controller = new AppointmentController(
                appointmentService, appointmentService, appointmentService, appointmentService,
                availabilityService, appointmentService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new AppointmentExceptionHandler())
                .build();

        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Authenticated POST /api/v1/appointments — publishes ──────────────

    @Test
    void authenticatedCreate_inactiveEmployee_publishesTheReason() throws Exception {
        inactiveEmployee();

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Employee is not active"));
    }

    @Test
    void authenticatedCreate_inactiveService_publishesTheReason() throws Exception {
        inactiveService();

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Service is not active"));
    }

    @Test
    void authenticatedCreate_inactiveClient_publishesTheReason() throws Exception {
        when(clientServicePort.getClient(TENANT_ID, CLIENT_ID))
                .thenReturn(new ClientServicePort.ClientInfo(
                        CLIENT_ID, "Laura", "Puig", "laura@example.com", "+34 612 345 678", false));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(CLIENT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Client is not active"));
    }

    // ── Anonymous POST /api/v1/appointments/book — publishes the window ──

    @Test
    void anonymousBook_tooSoon_publishesTheRuleTheVisitorMustFix() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(LocalDateTime.now(SALON_TIMEZONE).plusMinutes(15))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Booking must be at least 1 hour in the future"));
    }

    @Test
    void anonymousBook_tooFarAhead_publishesTheRuleTheVisitorMustFix() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(LocalDateTime.now(SALON_TIMEZONE).plusDays(90))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Booking cannot be more than 60 days in the future"));
    }

    // ── Anonymous POST /api/v1/appointments/book — stays restrictive ─────

    @Test
    void anonymousBook_inactiveEmployee_doesNotPublishTheSalonsInternalState() throws Exception {
        expectSalonLookupReturnsActiveSalon();
        inactiveEmployee();

        assertBookingIsRejectedGenerically("Employee is not active");
    }

    @Test
    void anonymousBook_inactiveService_doesNotPublishTheSalonsInternalState() throws Exception {
        expectSalonLookupReturnsActiveSalon();
        inactiveService();

        assertBookingIsRejectedGenerically("Service is not active");
    }

    private void assertBookingIsRejectedGenerically(String forbiddenFragment) throws Exception {
        String body = mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(LocalDateTime.now(SALON_TIMEZONE).plusDays(7))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL))
                .andReturn().getResponse().getContentAsString();
        salonServer.verify();

        assertThat(body)
                .as("an unauthenticated caller must not learn which staff or services this salon "
                        + "has deactivated - the identical message IS published on the "
                        + "authenticated POST /api/v1/appointments, which is the whole reason the "
                        + "decision belongs to the throw site and not to the exception class")
                .doesNotContain(forbiddenFragment);
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private void activeEmployee() {
        when(staffServicePort.getEmployee(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_ID, "Ana", "Garcia", true));
    }

    private void inactiveEmployee() {
        when(staffServicePort.getEmployee(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_ID, "Ana", "Garcia", false));
    }

    private void activeService() {
        when(staffServicePort.getService(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffServiceInfo(
                        SERVICE_ID, "Corte", new BigDecimal("25.00"), 60, true));
    }

    private void inactiveService() {
        when(staffServicePort.getService(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffServiceInfo(
                        SERVICE_ID, "Corte", new BigDecimal("25.00"), 60, false));
    }

    private void expectSalonLookupReturnsActiveSalon() {
        salonServer.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + SLUG))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"%s","name":"Misteriosa","slug":"%s","status":"ACTIVE"}
                        """.formatted(TENANT_ID, SLUG), MediaType.APPLICATION_JSON));
    }

    private static String createRequestJson(String clientId) {
        String startTime = LocalDateTime.now(SALON_TIMEZONE)
                .plusDays(3).withMinute(0).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return """
                {
                  "employeeId": "%s",
                  "serviceId": "%s",
                  "clientId": %s,
                  "clientName": "Laura Puig",
                  "clientPhone": "+34 612 345 678",
                  "clientEmail": "laura@example.com",
                  "startTime": "%s"
                }
                """.formatted(EMPLOYEE_ID, SERVICE_ID,
                clientId == null ? "null" : "\"" + clientId + "\"", startTime);
    }

    private static String bookingRequestJson(LocalDateTime requestedTime) {
        return """
                {
                  "salonSlug": "%s",
                  "employeeExternalId": "%s",
                  "serviceExternalId": "%s",
                  "clientFirstName": "Laura",
                  "clientLastName": "Puig",
                  "clientEmail": "laura@example.com",
                  "clientPhone": "+34 612 345 678",
                  "requestedTime": "%s",
                  "honeypot": null
                }
                """.formatted(SLUG, EMPLOYEE_ID, SERVICE_ID,
                requestedTime.withSecond(0).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
