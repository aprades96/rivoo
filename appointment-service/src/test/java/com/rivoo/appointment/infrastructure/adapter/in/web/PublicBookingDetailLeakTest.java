package com.rivoo.appointment.infrastructure.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.rivoo.appointment.application.AppointmentService;
import com.rivoo.appointment.application.AvailabilityService;
import com.rivoo.appointment.domain.exception.AppointmentConflictException;
import com.rivoo.appointment.domain.exception.AppointmentLimitExceededException;
import com.rivoo.appointment.domain.exception.AppointmentNotFoundException;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.BillingServicePort;
import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.SalonServiceAdapter;
import com.rivoo.appointment.infrastructure.mapper.AppointmentDtoMapper;
import com.rivoo.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins what {@code ProblemDetail.detail} may contain on the ANONYMOUS
 * {@code POST /api/v1/appointments/book}, and what must still reach the log instead.
 * <p>
 * Wiring: the real {@link AppointmentController}, the real {@link AppointmentService} (the class
 * that actually builds {@link AppointmentConflictException} and
 * {@link AppointmentLimitExceededException} from real state), the real {@link SalonServiceAdapter}
 * driven through {@link MockRestServiceServer} at the HTTP edge, and the real
 * {@link GlobalExceptionHandler} + {@link AppointmentExceptionHandler} registered exactly as at
 * runtime. Every double sits strictly BELOW the layer implementing the property under test (the
 * exception → response mapping), so no stub can make these pass on its own.
 * <p>
 * The two leak scenarios are produced by two DIFFERENT real code paths — an overlapping
 * appointment found by the persistence port, and a monthly count that has reached the plan
 * ceiling — not by the same mock stubbed twice with the same exception.
 */
class PublicBookingDetailLeakTest {

    private static final String SALON_SERVICE_URL = "http://salon-internal.rivoo.local:8082";
    private static final String SLUG = "misteriosa";
    private static final String TENANT_ID = "sal_98765432-abcd-ef01-2345-678901234567";
    private static final String EMPLOYEE_ID = "emp_abc123";
    private static final String SERVICE_ID = "svc_xyz456";
    private static final String EMPLOYEE_FIRST_NAME = "Ana";
    private static final String EMPLOYEE_LAST_NAME = "Garcia";
    private static final int MONTHLY_LIMIT = 200;

    private static final ZoneId SALON_TIMEZONE = ZoneId.of("Europe/Madrid");

    private AppointmentPersistencePort appointmentPersistencePort;
    private StaffServicePort staffServicePort;
    private BillingServicePort billingServicePort;
    private MockRestServiceServer salonServer;
    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        appointmentPersistencePort = mock(AppointmentPersistencePort.class);
        staffServicePort = mock(StaffServicePort.class);
        billingServicePort = mock(BillingServicePort.class);

        when(staffServicePort.getEmployee(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffEmployeeInfo(
                        EMPLOYEE_ID, EMPLOYEE_FIRST_NAME, EMPLOYEE_LAST_NAME, true));
        when(staffServicePort.getService(anyString(), anyString()))
                .thenReturn(new StaffServicePort.StaffServiceInfo(
                        SERVICE_ID, "Corte", new BigDecimal("25.00"), 60, true));

        RestClient.Builder builder = RestClient.builder();
        salonServer = MockRestServiceServer.bindTo(builder).build();
        SalonServiceAdapter salonServiceAdapter = new SalonServiceAdapter(builder, SALON_SERVICE_URL);

        AppointmentService appointmentService = new AppointmentService(
                appointmentPersistencePort,
                staffServicePort,
                mock(ClientServicePort.class),
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

        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger().detachAppender(logAppender);
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    }

    @Test
    void publicBook_slotAlreadyTaken_doesNotPublishTheEmployeeNameOrTheBookedSlot() throws Exception {
        expectSalonLookupReturnsActiveSalon();
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
        when(appointmentPersistencePort.findOverlappingForUpdate(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(Appointment.builder()
                        .externalId("apt_existing")
                        .tenantId(TENANT_ID)
                        .status(AppointmentStatus.CONFIRMED)
                        .build()));

        String body = mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL))
                .andReturn().getResponse().getContentAsString();
        salonServer.verify();

        assertThat(body)
                .as("an unauthenticated caller must not learn who works at this salon")
                .doesNotContain(EMPLOYEE_FIRST_NAME + " " + EMPLOYEE_LAST_NAME)
                .doesNotContain(EMPLOYEE_LAST_NAME)
                .as("nor which slot is already booked")
                .doesNotContain("already has an appointment");

        assertDiagnosticReachedTheLogWithCause(
                AppointmentConflictException.class, EMPLOYEE_FIRST_NAME + " " + EMPLOYEE_LAST_NAME);
    }

    @Test
    void publicBook_monthlyPlanLimitReached_doesNotPublishThePlanCeiling() throws Exception {
        expectSalonLookupReturnsActiveSalon();
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(MONTHLY_LIMIT);
        when(appointmentPersistencePort.countByTenantAndMonth(anyString(), any(), any()))
                .thenReturn((long) MONTHLY_LIMIT);

        String detail = JsonPath.read(mockMvc.perform(post("/api/v1/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson()))
                .andExpect(status().isPaymentRequired())
                .andReturn().getResponse().getContentAsString(), "$.detail");
        salonServer.verify();

        // Scoped to `detail`, not to the whole body, on purpose: the body also carries a
        // `timestamp` whose nanosecond fraction can contain the digits "200" by chance, which
        // would make a whole-body assertion fail a few times a day for no reason.
        assertThat(detail)
                .as("an unauthenticated caller must not learn the salon's plan tier")
                .isEqualTo(GlobalExceptionHandler.GENERIC_DETAIL)
                .doesNotContain(String.valueOf(MONTHLY_LIMIT))
                .doesNotContain("Monthly appointment limit");

        assertDiagnosticReachedTheLogWithCause(
                AppointmentLimitExceededException.class, "Monthly appointment limit of " + MONTHLY_LIMIT);
    }

    /**
     * The mirror image, on an AUTHENTICATED route: a subtype that is reachable only behind
     * {@code hasAnyRole('SALON_OWNER', 'EMPLOYEE')} must keep publishing its message, otherwise
     * this change would silently degrade every error a salon owner sees. Same real service, same
     * real advice — only the reachability of the throw site differs.
     */
    @Test
    void getById_unknownAppointment_stillPublishesTheUsefulMessageToAnAuthenticatedCaller() throws Exception {
        when(appointmentPersistencePort.findByExternalId("apt_unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/appointments/apt_unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No appointment found with identifier 'apt_unknown'"));

        assertDiagnosticReachedTheLogWithCause(
                AppointmentNotFoundException.class, "No appointment found with identifier 'apt_unknown'");
    }

    private void expectSalonLookupReturnsActiveSalon() {
        salonServer.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/" + SLUG))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"%s","name":"Misteriosa","slug":"%s","status":"ACTIVE"}
                        """.formatted(TENANT_ID, SLUG), MediaType.APPLICATION_JSON));
    }

    /**
     * Deleting the leaked detail is only half the fix: the operator must still be able to
     * diagnose it. This asserts the information MOVED — same run, same failure, absent from the
     * body (above) and present in the log WITH the exception attached via {@code setCause}.
     */
    private void assertDiagnosticReachedTheLogWithCause(Class<?> expectedExceptionType, String expectedFragment) {
        assertThat(logAppender.list)
                .as("GlobalExceptionHandler must log the real message with the fluent API")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isIn(Level.WARN, Level.ERROR);
                    assertThat(event.getKeyValuePairs())
                            .as("the real message must survive as a log field")
                            .anySatisfy(pair -> assertThat(String.valueOf(pair.value)).contains(expectedFragment));
                    assertThat(event.getThrowableProxy())
                            .as("setCause(ex) must attach the exception so the stack trace survives")
                            .isNotNull();
                    assertThat(event.getThrowableProxy().getClassName())
                            .isEqualTo(expectedExceptionType.getName());
                });
    }

    /**
     * Built relative to "now" on purpose: {@code AppointmentService.book} rejects anything outside
     * [now + 1h, now + 60d], so a hardcoded date would silently turn these tests into assertions
     * about the booking window instead of about the leak.
     */
    private static String bookingRequestJson() {
        String requestedTime = LocalDateTime.now(SALON_TIMEZONE)
                .plusDays(7)
                .withMinute(0).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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
                """.formatted(SLUG, EMPLOYEE_ID, SERVICE_ID, requestedTime);
    }
}
