package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.application.dto.CancelAppointmentRequest;
import com.rivoo.appointment.application.dto.CreateAppointmentRequest;
import com.rivoo.appointment.domain.exception.AppointmentConflictException;
import com.rivoo.appointment.domain.exception.AppointmentLimitExceededException;
import com.rivoo.appointment.domain.exception.InvalidStatusTransitionException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService — use case behaviour")
class AppointmentServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String TENANT_ID = "tenant-001";
    private static final String EMPLOYEE_EXT_ID = "emp_abc123";
    private static final String SERVICE_EXT_ID = "svc_xyz456";
    private static final String EXTERNAL_ID = "apt_test001";

    @Mock private AppointmentPersistencePort appointmentPersistencePort;
    @Mock private StaffServicePort staffServicePort;
    @Mock private ClientServicePort clientServicePort;
    @Mock private BillingServicePort billingServicePort;
    @Mock private NotificationServicePort notificationServicePort;
    @Mock private SalonServicePort salonServicePort;
    @Mock private AppointmentDtoMapper mapper;

    private AppointmentService appointmentService;

    @BeforeEach
    void createServiceUnderTest() {
        // Clock.systemUTC() reproduces exactly what this code did before "now" became an
        // injected collaborator. The boundary cases run on a fixed clock, in
        // BookingLeadTimeConsistencyTest.
        appointmentService = new AppointmentService(appointmentPersistencePort, staffServicePort,
                clientServicePort, billingServicePort, notificationServicePort, salonServicePort,
                mapper, Clock.systemUTC());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StaffServicePort.StaffEmployeeInfo activeEmployee() {
        return new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_EXT_ID, "Carlos", "Lopez", true);
    }

    private StaffServicePort.StaffEmployeeInfo inactiveEmployee() {
        return new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_EXT_ID, "Carlos", "Lopez", false);
    }

    private StaffServicePort.StaffServiceInfo activeService(int durationMinutes) {
        return new StaffServicePort.StaffServiceInfo(SERVICE_EXT_ID, "Haircut",
                BigDecimal.valueOf(25), durationMinutes, true);
    }

    private StaffServicePort.StaffServiceInfo inactiveService() {
        return new StaffServicePort.StaffServiceInfo(SERVICE_EXT_ID, "Haircut",
                BigDecimal.valueOf(25), 30, false);
    }

    private CreateAppointmentRequest createRequest() {
        // Start time well in the future to avoid timezone edge cases
        return new CreateAppointmentRequest(
                EMPLOYEE_EXT_ID,
                SERVICE_EXT_ID,
                null,
                "Ana Garcia",
                "+34 612 345 678",
                "ana@example.com",
                LocalDateTime.of(2099, 6, 15, 10, 0),
                "MANUAL",
                null
        );
    }

    private Appointment savedAppointment() {
        return Appointment.builder()
                .externalId(EXTERNAL_ID)
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_EXT_ID)
                .employeeName("Carlos Lopez")
                .serviceId(SERVICE_EXT_ID)
                .serviceName("Haircut")
                .servicePrice(BigDecimal.valueOf(25))
                .serviceDurationMinutes(30)
                .clientName("Ana Garcia")
                .clientPhone("+34 612 345 678")
                .clientEmail("ana@example.com")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(5400))
                .status(AppointmentStatus.PENDING)
                .source(AppointmentSource.MANUAL)
                .reminderSent(false)
                .build();
    }

    private AppointmentResponse stubResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getExternalId(),
                appointment.getClientId(),
                appointment.getClientName(),
                appointment.getClientPhone(),
                appointment.getClientEmail(),
                appointment.getEmployeeId(),
                appointment.getEmployeeName(),
                appointment.getServiceId(),
                appointment.getServiceName(),
                appointment.getServicePrice(),
                appointment.getServiceDurationMinutes(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus().name(),
                null, null,
                appointment.getSource().name(),
                null, false,
                null, null
        );
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Happy path — returns response with snapshot data from employee and service")
        void happyPath_returnsResponse() {
            Appointment saved = savedAppointment();
            AppointmentResponse expectedResponse = stubResponse(saved);

            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1); // unlimited
            when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
            when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService(30));
            when(appointmentPersistencePort.findOverlappingForUpdate(
                    eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());
            when(appointmentPersistencePort.save(any(Appointment.class))).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(expectedResponse);

            AppointmentResponse result = appointmentService.create(TENANT_ID, createRequest());

            assertNotNull(result);
            assertEquals(EXTERNAL_ID, result.id());
            assertEquals("Haircut", result.serviceName());
            assertEquals("Carlos Lopez", result.employeeName());
            verify(appointmentPersistencePort).save(any(Appointment.class));
            verify(notificationServicePort).scheduleReminder(saved);
        }

        @Test
        @DisplayName("Plan limit reached — throws AppointmentLimitExceededException")
        void planLimitExceeded_throwsException() {
            int maxAppointments = 50;
            YearMonth currentMonth = YearMonth.now(MADRID);
            Instant monthStart = currentMonth.atDay(1).atStartOfDay(MADRID).toInstant();
            Instant monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay(MADRID).toInstant();

            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(maxAppointments);
            when(appointmentPersistencePort.countByTenantAndMonth(
                    eq(TENANT_ID), any(Instant.class), any(Instant.class)))
                    .thenReturn(50L); // already at limit

            assertThrows(AppointmentLimitExceededException.class,
                    () -> appointmentService.create(TENANT_ID, createRequest()));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Overlapping appointment exists — throws AppointmentConflictException")
        void conflict_throwsAppointmentConflictException() {
            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
            when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
            when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService(30));
            when(appointmentPersistencePort.findOverlappingForUpdate(
                    eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(savedAppointment())); // conflict

            assertThrows(AppointmentConflictException.class,
                    () -> appointmentService.create(TENANT_ID, createRequest()));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Inactive employee — throws BusinessValidationException")
        void inactiveEmployee_throwsBusinessValidationException() {
            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
            when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(inactiveEmployee());

            assertThrows(BusinessValidationException.class,
                    () -> appointmentService.create(TENANT_ID, createRequest()));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Inactive service — throws BusinessValidationException")
        void inactiveService_throwsBusinessValidationException() {
            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
            when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
            when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(inactiveService());

            assertThrows(BusinessValidationException.class,
                    () -> appointmentService.create(TENANT_ID, createRequest()));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Notification failure does not prevent appointment creation")
        void notificationFailure_doesNotBlockCreation() {
            Appointment saved = savedAppointment();

            when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
            when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_EXT_ID)).thenReturn(activeEmployee());
            when(staffServicePort.getService(TENANT_ID, SERVICE_EXT_ID)).thenReturn(activeService(30));
            when(appointmentPersistencePort.findOverlappingForUpdate(
                    eq(TENANT_ID), eq(EMPLOYEE_EXT_ID), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());
            when(appointmentPersistencePort.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(stubResponse(saved));
            // Notification throws
            org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                    .when(notificationServicePort).scheduleReminder(any());

            // Should still return successfully
            AppointmentResponse result = appointmentService.create(TENANT_ID, createRequest());

            assertNotNull(result);
            verify(appointmentPersistencePort).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // cancel()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING appointment can be cancelled with a reason")
        void pendingAppointment_cancelledSuccessfully() {
            Appointment pending = savedAppointment(); // status = PENDING
            CancelAppointmentRequest cancelRequest = new CancelAppointmentRequest("Client request", "CLIENT");

            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(pending));
            when(appointmentPersistencePort.save(any(Appointment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any(Appointment.class)))
                    .thenAnswer(inv -> {
                        Appointment a = inv.getArgument(0);
                        return stubResponse(a);
                    });

            appointmentService.cancel(EXTERNAL_ID, cancelRequest);

            // Capture saved appointment to verify state changes
            ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
            verify(appointmentPersistencePort).save(captor.capture());
            Appointment saved = captor.getValue();

            assertEquals(AppointmentStatus.CANCELLED, saved.getStatus());
            assertEquals("Client request", saved.getCancellationReason());
        }

        @Test
        @DisplayName("COMPLETED (terminal) appointment cannot be cancelled — throws InvalidStatusTransitionException")
        void completedAppointment_cannotBeCancelled() {
            Appointment completed = savedAppointment();
            completed.setStatus(AppointmentStatus.COMPLETED);
            CancelAppointmentRequest cancelRequest = new CancelAppointmentRequest("Mistake", "SALON");

            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(completed));

            assertThrows(InvalidStatusTransitionException.class,
                    () -> appointmentService.cancel(EXTERNAL_ID, cancelRequest));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("CANCELLED appointment cannot be cancelled again — throws InvalidStatusTransitionException")
        void alreadyCancelledAppointment_throwsException() {
            Appointment alreadyCancelled = savedAppointment();
            alreadyCancelled.setStatus(AppointmentStatus.CANCELLED);

            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(alreadyCancelled));

            assertThrows(InvalidStatusTransitionException.class,
                    () -> appointmentService.cancel(EXTERNAL_ID, new CancelAppointmentRequest(null, "SYSTEM")));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Cancel reminder failure does not prevent appointment cancellation")
        void reminderCancelFailure_doesNotBlockCancellation() {
            Appointment pending = savedAppointment();
            CancelAppointmentRequest cancelRequest = new CancelAppointmentRequest("Test", "SALON");

            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(pending));
            when(appointmentPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenAnswer(inv -> stubResponse(inv.getArgument(0)));
            org.mockito.Mockito.doThrow(new RuntimeException("Notification service down"))
                    .when(notificationServicePort).cancelReminders(EXTERNAL_ID);

            // Must not throw
            appointmentService.cancel(EXTERNAL_ID, cancelRequest);

            verify(appointmentPersistencePort).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // updateStatus()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("Valid transition PENDING -> CONFIRMED updates and persists")
        void validTransition_updatesStatus() {
            Appointment pending = savedAppointment(); // PENDING
            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(pending));
            when(appointmentPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenAnswer(inv -> stubResponse(inv.getArgument(0)));

            appointmentService.updateStatus(EXTERNAL_ID, "CONFIRMED");

            ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
            verify(appointmentPersistencePort).save(captor.capture());
            assertEquals(AppointmentStatus.CONFIRMED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("Invalid transition PENDING -> COMPLETED throws InvalidStatusTransitionException")
        void invalidTransition_throwsException() {
            Appointment pending = savedAppointment(); // PENDING
            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(pending));

            assertThrows(InvalidStatusTransitionException.class,
                    () -> appointmentService.updateStatus(EXTERNAL_ID, "COMPLETED"));

            verify(appointmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Transition from terminal COMPLETED throws InvalidStatusTransitionException")
        void fromTerminalCompleted_throwsException() {
            Appointment completed = savedAppointment();
            completed.setStatus(AppointmentStatus.COMPLETED);

            when(appointmentPersistencePort.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(completed));

            assertThrows(InvalidStatusTransitionException.class,
                    () -> appointmentService.updateStatus(EXTERNAL_ID, "PENDING"));

            verify(appointmentPersistencePort, never()).save(any());
        }
    }
}
