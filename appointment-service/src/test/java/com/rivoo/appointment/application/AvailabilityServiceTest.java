package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentSource;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService — slot calculation")
class AvailabilityServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // A future Wednesday (dayOfWeek = 3) that avoids "today" slot-clipping logic.
    // 2099-06-10 is a Wednesday — verified via jshell.
    private static final LocalDate FUTURE_WEDNESDAY = LocalDate.of(2099, 6, 10);

    private static final String TENANT_ID = "tenant-001";
    private static final String EMPLOYEE_ID = "emp_abc";

    @Mock
    private AppointmentPersistencePort appointmentPersistencePort;

    @Mock
    private StaffServicePort staffServicePort;

    private AvailabilityService availabilityService;

    @BeforeEach
    void createServiceUnderTest() {
        // Clock.systemUTC() reproduces exactly what this code did before "now" became an
        // injected collaborator. The boundary cases run on a fixed clock, in
        // BookingLeadTimeConsistencyTest.
        availabilityService = new AvailabilityService(appointmentPersistencePort, staffServicePort,
                mock(SalonServicePort.class), Clock.systemUTC());
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    /** Working hours for a given dayOfWeek, no break. */
    private EmployeeWorkingHoursDto workDay(int dayOfWeek, LocalTime open, LocalTime close) {
        return new EmployeeWorkingHoursDto(dayOfWeek, true, open, close, null, null);
    }

    /** Working hours for a given dayOfWeek with a break. */
    private EmployeeWorkingHoursDto workDayWithBreak(int dayOfWeek, LocalTime open, LocalTime close,
                                                      LocalTime breakStart, LocalTime breakEnd) {
        return new EmployeeWorkingHoursDto(dayOfWeek, true, open, close, breakStart, breakEnd);
    }

    /** Closed day entry. */
    private EmployeeWorkingHoursDto closedDay(int dayOfWeek) {
        return new EmployeeWorkingHoursDto(dayOfWeek, false, null, null, null, null);
    }

    /** Build a saved appointment with UTC start/end derived from Madrid local times on FUTURE_WEDNESDAY. */
    private Appointment existingAppointment(LocalTime localStart, LocalTime localEnd) {
        Instant start = FUTURE_WEDNESDAY.atTime(localStart).atZone(MADRID).toInstant();
        Instant end   = FUTURE_WEDNESDAY.atTime(localEnd).atZone(MADRID).toInstant();
        return Appointment.builder()
                .externalId("apt_test")
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .status(AppointmentStatus.CONFIRMED)
                .source(AppointmentSource.MANUAL)
                .startTime(start)
                .endTime(end)
                .serviceDurationMinutes(60)
                .servicePrice(BigDecimal.TEN)
                .clientName("Test Client")
                .employeeName("Test Employee")
                .serviceName("Haircut")
                .reminderSent(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Day off (employee doesn't work on requested day) returns empty slots")
    void dayOff_returnsEmptySlots() {
        // FUTURE_WEDNESDAY = dayOfWeek 3; return only Monday (1) as open — so Wednesday is a day off
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDay(1, LocalTime.of(9, 0), LocalTime.of(18, 0))));

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, null);

        assertTrue(response.slots().isEmpty(), "Expected no slots on employee day off");
        verify(staffServicePort).getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID);
        // No appointments query needed when employee doesn't work that day
        verifyNoInteractions(appointmentPersistencePort);
    }

    @Test
    @DisplayName("Full working day with no existing appointments returns many slots")
    void fullWorkingDay_noAppointments_returnsManySlots() {
        // 9:00 to 18:00, Wednesday (dayOfWeek=3)
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDay(3, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                eq(TENANT_ID), eq(EMPLOYEE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, null);

        // No serviceId → raw free intervals, we expect exactly 1 interval covering 9:00–18:00
        assertFalse(response.slots().isEmpty(), "Expected slots for a full working day");
        assertEquals(1, response.slots().size());
        assertEquals(LocalTime.of(9, 0), response.slots().get(0).startTime());
        assertEquals(LocalTime.of(18, 0), response.slots().get(0).endTime());
    }

    @Test
    @DisplayName("Day with one existing appointment creates a gap in available slots")
    void existingAppointment_reducesAvailableSlots() {
        // Work: 9:00–18:00. Existing appointment: 10:00–11:00
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDay(3, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                eq(TENANT_ID), eq(EMPLOYEE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(existingAppointment(LocalTime.of(10, 0), LocalTime.of(11, 0))));

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, null);

        // Expecting 2 free intervals: 9:00–10:00 and 11:00–18:00
        assertEquals(2, response.slots().size(), "Expected two free intervals around the existing appointment");
        assertEquals(LocalTime.of(9, 0), response.slots().get(0).startTime());
        assertEquals(LocalTime.of(10, 0), response.slots().get(0).endTime());
        assertEquals(LocalTime.of(11, 0), response.slots().get(1).startTime());
        assertEquals(LocalTime.of(18, 0), response.slots().get(1).endTime());
    }

    @Test
    @DisplayName("Break period is excluded from available slots")
    void breakPeriod_isExcludedFromSlots() {
        // Work: 9:00–18:00, break: 12:00–13:00
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDayWithBreak(3,
                        LocalTime.of(9, 0), LocalTime.of(18, 0),
                        LocalTime.of(12, 0), LocalTime.of(13, 0))));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                eq(TENANT_ID), eq(EMPLOYEE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, null);

        // No serviceId → raw intervals: 9:00–12:00 and 13:00–18:00
        assertEquals(2, response.slots().size(), "Expected two work intervals split by break");
        assertEquals(LocalTime.of(9, 0), response.slots().get(0).startTime());
        assertEquals(LocalTime.of(12, 0), response.slots().get(0).endTime());
        assertEquals(LocalTime.of(13, 0), response.slots().get(1).startTime());
        assertEquals(LocalTime.of(18, 0), response.slots().get(1).endTime());
    }

    @Test
    @DisplayName("Service duration filtering returns only slots that fit the service")
    void serviceDuration_filtersSlots() {
        // Work: 9:00–10:00 (60 min window). Service duration = 45 min.
        // Slots of 45 min with 15-min granularity: 9:00–9:45 only (9:15 end = 10:00 which fits, 9:30 end = 10:15 does not)
        // 9:00 -> 9:45 fits (end 09:45 <= 10:00)
        // 9:15 -> 10:00 fits (end 10:00 <= 10:00)
        // 9:30 -> 10:15 does NOT fit
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDay(3, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                eq(TENANT_ID), eq(EMPLOYEE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(staffServicePort.getService(TENANT_ID, "svc_haircut"))
                .thenReturn(new StaffServicePort.StaffServiceInfo("svc_haircut", "Haircut",
                        BigDecimal.valueOf(25), 45, true));

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, "svc_haircut");

        // 9:00 and 9:15 start times fit a 45-min service within the 9:00–10:00 window
        assertEquals(2, response.slots().size(),
                "Expected exactly 2 slots for a 45-min service in a 60-min window");
        assertEquals(LocalTime.of(9, 0), response.slots().get(0).startTime());
        assertEquals(LocalTime.of(9, 45), response.slots().get(0).endTime());
        assertEquals(LocalTime.of(9, 15), response.slots().get(1).startTime());
        assertEquals(LocalTime.of(10, 0), response.slots().get(1).endTime());
        verify(staffServicePort).getService(TENANT_ID, "svc_haircut");
    }

    @Test
    @DisplayName("Service longer than available window produces no slots")
    void serviceLongerThanWindow_producesNoSlots() {
        // Work window: 9:00–9:30 (30 min). Service: 60 min.
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of(workDay(3, LocalTime.of(9, 0), LocalTime.of(9, 30))));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                eq(TENANT_ID), eq(EMPLOYEE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(staffServicePort.getService(TENANT_ID, "svc_long"))
                .thenReturn(new StaffServicePort.StaffServiceInfo("svc_long", "Long Treatment",
                        BigDecimal.valueOf(80), 60, true));

        AvailabilityResponse response = availabilityService.getAvailableSlots(
                TENANT_ID, EMPLOYEE_ID, FUTURE_WEDNESDAY, "svc_long");

        assertTrue(response.slots().isEmpty(),
                "Expected no slots when service duration exceeds the available window");
    }
}
