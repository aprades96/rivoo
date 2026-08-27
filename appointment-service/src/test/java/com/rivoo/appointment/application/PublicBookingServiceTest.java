package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.PublicBookingRequest;
import com.rivoo.appointment.application.dto.PublicBookingResponse;
import com.rivoo.appointment.domain.exception.AppointmentConflictException;
import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentSource;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.BillingServicePort;
import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.mapper.AppointmentDtoMapper;
import com.rivoo.common.exception.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService — public book() method")
class PublicBookingServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String SALON_SLUG = "barberia-carlos";
    private static final String TENANT_ID = "tenant-001";
    private static final String EMPLOYEE_EXT_ID = "emp_abc123";
    private static final String SERVICE_EXT_ID = "svc_xyz456";

    @Mock private AppointmentPersistencePort appointmentPersistencePort;
    @Mock private StaffServicePort staffServicePort;
    @Mock private ClientServicePort clientServicePort;
    @Mock private BillingServicePort billingServicePort;
    @Mock private NotificationServicePort notificationServicePort;
    @Mock private SalonServicePort salonServicePort;
    @Mock private AppointmentDtoMapper mapper;

    @InjectMocks
    private AppointmentService appointmentService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a request where requestedTime is configurable. */
    private PublicBookingRequest requestAt(LocalDateTime requestedTime, String honeypot) {
        return new PublicBookingRequest(
                SALON_SLUG,
                EMPLOYEE_EXT_ID,
                SERVICE_EXT_ID,
                "Ana",
                "Garcia",
                "ana@example.com",
                "+34 612 345 678",
                requestedTime,
                honeypot
        );
    }

    /** Builds a valid future request (30 days from now) with no honeypot. */
    private PublicBookingRequest validFutureRequest() {
        return requestAt(LocalDateTime.now(MADRID).plusDays(30), null);
    }

    private SalonServicePort.SalonInfo activeSalon() {
        return new SalonServicePort.SalonInfo(TENANT_ID, "Barberia Carlos", "ACTIVE");
    }

    private SalonServicePort.SalonInfo inactiveSalon() {
        return new SalonServicePort.SalonInfo(TENANT_ID, "Barberia Carlos", "INACTIVE");
    }

    private StaffServicePort.StaffEmployeeInfo activeEmployee() {
        return new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_EXT_ID, "Carlos", "Lopez", true);
    }

    private StaffServicePort.StaffServiceInfo activeService() {
        return new StaffServicePort.StaffServiceInfo(SERVICE_EXT_ID, "Haircut",
                BigDecimal.valueOf(25), 30, true);
    }

    private ClientServicePort.ClientInfo clientInfo() {
        return new ClientServicePort.ClientInfo("cli_001", "Ana", "Garcia",
                "ana@example.com", "+34 612 345 678", true);
    }

    private Appointment persistedBooking() {
        Instant start = Instant.now().plusSeconds(86400 * 30);
        return Appointment.builder()
                .externalId("apt_booking001")
                .tenantId(TENANT_ID)
                .clientId("cli_001")
                .clientName("Ana Garcia")
                .clientPhone("+34 612 345 678")
                .clientEmail("ana@example.com")
                .employeeId(EMPLOYEE_EXT_ID)
                .employeeName("Carlos Lopez")
                .serviceId(SERVICE_EXT_ID)
                .serviceName("Haircut")
                .servicePrice(BigDecimal.valueOf(25))
                .serviceDurationMinutes(30)
                .startTime(start)
                .endTime(start.plusSeconds(1800))
                .status(AppointmentStatus.PENDING)
                .source(AppointmentSource.ONLINE)
                .reminderSent(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Honeypot field populated — returns fake success without creating real appointment")
    void honeypot_returnsFakeSuccessWithoutPersisting() {
        PublicBookingRequest request = requestAt(
                LocalDateTime.now(MADRID).plusDays(5), "i-am-a-bot");

        PublicBookingResponse response = appointmentService.book(request);

        assertNotNull(response);
        assertEquals("apt_fake", response.appointmentId());
        assertEquals("PENDING", response.status());
        // No downstream calls whatsoever
        verify(appointmentPersistencePort, never()).save(any());
        verify(salonServicePort, never()).getSalonBySlug(anyString());
        verify(staffServicePort, never()).getEmployee(anyString(), anyString());
    }

    @Test
    @DisplayName("Requested time less than 1 hour in the future — throws BusinessValidationException")
    void bookingWindowTooSoon_throwsBusinessValidationException() {
        // 30 minutes from now — inside the 1-hour guard
        PublicBookingRequest request = requestAt(
                LocalDateTime.now(MADRID).plusMinutes(30), null);

        assertThrows(BusinessValidationException.class,
                () -> appointmentService.book(request));

        verify(appointmentPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Requested time more than 60 days in the future — throws BusinessValidationException")
    void bookingWindowTooFar_throwsBusinessValidationException() {
        // 61 days from now — beyond the 60-day guard
        PublicBookingRequest request = requestAt(
                LocalDateTime.now(MADRID).plusDays(61), null);

        assertThrows(BusinessValidationException.class,
                () -> appointmentService.book(request));

        verify(appointmentPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Salon not ACTIVE — throws SalonNotFoundException, same as an unknown slug")
    void salonNotActive_throwsSalonNotFoundException() {
        PublicBookingRequest request = validFutureRequest();

        when(salonServicePort.getSalonBySlug(SALON_SLUG)).thenReturn(inactiveSalon());

        // Must be SalonNotFoundException — the very same exception SalonServiceAdapter
        // throws for a slug salon-service has never heard of — so an anonymous caller
        // cannot tell "suspended" apart from "never existed" (enumeration oracle).
        assertThrows(SalonNotFoundException.class,
                () -> appointmentService.book(request));

        verify(appointmentPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("same slug: 'does not exist' and 'exists but not ACTIVE' raise field-for-field " +
            "indistinguishable exceptions (the actual enumeration-oracle property)")
    void salonNotFoundAndSalonNotActive_areIndistinguishable() {
        PublicBookingRequest request = validFutureRequest();

        // Scenario A: mirrors what SalonServiceAdapter throws for a slug salon-service has
        // never heard of (a real 404 mapped to SalonNotFoundException).
        SalonServicePort notFoundSalonPort = mock(SalonServicePort.class);
        when(notFoundSalonPort.getSalonBySlug(SALON_SLUG)).thenThrow(new SalonNotFoundException(SALON_SLUG));
        AppointmentService notFoundService = new AppointmentService(appointmentPersistencePort, staffServicePort,
                clientServicePort, billingServicePort, notificationServicePort, notFoundSalonPort, mapper);

        // Scenario B: the slug resolves fine but the salon is not ACTIVE.
        SalonServicePort inactiveSalonPort = mock(SalonServicePort.class);
        when(inactiveSalonPort.getSalonBySlug(SALON_SLUG)).thenReturn(inactiveSalon());
        AppointmentService inactiveService = new AppointmentService(appointmentPersistencePort, staffServicePort,
                clientServicePort, billingServicePort, notificationServicePort, inactiveSalonPort, mapper);

        SalonNotFoundException notFoundException = catchThrowableOfType(
                () -> notFoundService.book(request), SalonNotFoundException.class);
        SalonNotFoundException inactiveException = catchThrowableOfType(
                () -> inactiveService.book(request), SalonNotFoundException.class);

        // Everything that GlobalExceptionHandler.handleRivooException(RivooException) copies
        // into the HTTP response body must match, not just the exception's runtime type.
        assertThat(notFoundException).isNotNull();
        assertThat(inactiveException).isNotNull();
        assertThat(inactiveException.getClass()).isEqualTo(notFoundException.getClass());
        assertThat(inactiveException.getMessage()).isEqualTo(notFoundException.getMessage());
        assertThat(inactiveException.getHttpStatus()).isEqualTo(notFoundException.getHttpStatus());
        assertThat(inactiveException.getErrorType()).isEqualTo(notFoundException.getErrorType());
        assertThat(inactiveException.getErrorTitle()).isEqualTo(notFoundException.getErrorTitle());
    }

    @Test
    @DisplayName("Happy path — appointment created with source=ONLINE and client linked")
    void happyPath_appointmentCreatedWithOnlineSource() {
        PublicBookingRequest request = validFutureRequest();
        Appointment saved = persistedBooking();

        when(salonServicePort.getSalonBySlug(SALON_SLUG)).thenReturn(activeSalon());
        when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
        when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService());
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1); // unlimited
        when(appointmentPersistencePort.findOverlappingForUpdate(
                eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(clientServicePort.findOrCreateClient(
                eq(TENANT_ID), eq("Ana"), eq("Garcia"), eq("ana@example.com"), eq("+34 612 345 678")))
                .thenReturn(clientInfo());
        when(appointmentPersistencePort.save(any(Appointment.class))).thenReturn(saved);

        PublicBookingResponse response = appointmentService.book(request);

        assertNotNull(response);
        assertEquals("apt_booking001", response.appointmentId());
        assertEquals("Barberia Carlos", response.salonName());
        assertEquals("Carlos Lopez", response.employeeName());
        assertEquals("Haircut", response.serviceName());
        assertEquals("PENDING", response.status());

        // Verify the appointment persisted with source=ONLINE
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentPersistencePort).save(captor.capture());
        assertEquals(AppointmentSource.ONLINE, captor.getValue().getSource());
        assertEquals("cli_001", captor.getValue().getClientId());

        // Confirm both notification calls were made
        verify(notificationServicePort).sendConfirmation(saved);
        verify(notificationServicePort).scheduleReminder(saved);
    }

    @Test
    @DisplayName("Conflicting appointment for slot — throws AppointmentConflictException")
    void conflictingSlot_throwsAppointmentConflictException() {
        PublicBookingRequest request = validFutureRequest();

        when(salonServicePort.getSalonBySlug(SALON_SLUG)).thenReturn(activeSalon());
        when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
        when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService());
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
        when(appointmentPersistencePort.findOverlappingForUpdate(
                eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(persistedBooking())); // conflict

        assertThrows(AppointmentConflictException.class,
                () -> appointmentService.book(request));

        verify(appointmentPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Notification failures on book() do not prevent the booking from succeeding")
    void notificationFailures_doNotBlockBooking() {
        PublicBookingRequest request = validFutureRequest();
        Appointment saved = persistedBooking();

        when(salonServicePort.getSalonBySlug(SALON_SLUG)).thenReturn(activeSalon());
        when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
        when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService());
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
        when(appointmentPersistencePort.findOverlappingForUpdate(
                eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(clientServicePort.findOrCreateClient(any(), any(), any(), any(), any()))
                .thenReturn(clientInfo());
        when(appointmentPersistencePort.save(any())).thenReturn(saved);
        org.mockito.Mockito.doThrow(new RuntimeException("Mail server down"))
                .when(notificationServicePort).sendConfirmation(any());
        org.mockito.Mockito.doThrow(new RuntimeException("Mail server down"))
                .when(notificationServicePort).scheduleReminder(any());

        // Must succeed despite notification failures
        PublicBookingResponse response = appointmentService.book(request);

        assertNotNull(response);
        verify(appointmentPersistencePort).save(any());
    }
}
