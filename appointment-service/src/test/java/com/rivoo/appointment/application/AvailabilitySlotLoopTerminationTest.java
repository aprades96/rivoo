package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.application.dto.AvailableSlot;
import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The slot loop has to terminate for every opening hour a salon can actually store.
 *
 * <p>The cursor used to be a {@link LocalTime}, and {@code LocalTime.plusMinutes()} wraps at
 * midnight. Whenever the free interval ended at 23:45 or later - the only way to say "we close at
 * midnight" with a {@code LocalTime}, and something staff-service stores happily, since it only
 * checks that the closing time is after the opening one - no cursor position ever falsified
 * {@code cursor + duration <= closeTime}: at 23:45 the cursor rolled over to 00:00 and started the
 * day again. One anonymous {@code GET /api/v1/appointments/public/availability} was then enough to
 * pin a request thread and grow the slot list without bound.
 *
 * <p><strong>Why assertTimeoutPreemptively.</strong> A test that merely asserted "09:00-23:59
 * yields N slots" would pass once the loop is fixed while proving nothing about termination:
 * against the broken loop it never reaches its assertion at all, it hangs the surefire fork for
 * ever and the build reports no result. Plain {@code assertTimeout} is no better - it runs the
 * executable on the calling thread and compares the elapsed time only after the call
 * <em>returns</em>. {@code assertTimeoutPreemptively} runs it on a separate thread and fails the
 * test the moment the deadline passes, which is the only JUnit construct that turns "does not
 * terminate" into a red test instead of a hung build. Two seconds against a loop that needs
 * microseconds measures termination, not machine speed.
 *
 * <p><strong>Why both hanging cases offer nothing.</strong> {@code assertTimeoutPreemptively}
 * fails the test at the deadline but cannot stop the thread it abandons: against a broken loop
 * that thread keeps running until the fork exits. Whether it merely spins or fills the heap is
 * decided by the fixture, and the choice is binary. The lead-time filter judges the cursor's
 * time of day against a threshold that does not move, and a wrapped cursor revisits the same 96
 * quarter-hour positions for ever, so a fixture that lets one position through lets it through
 * on every cycle and the slot list grows without bound. Both cases below are therefore frozen
 * so that NO position survives the filter - case A at 23:50 with an hour of lead, case B on a
 * day already past - and the abandoned thread adds not one slot. Measured against a verbatim
 * transcription of the pre-fix loop, each adds 0 slots in 2,000,000 iterations while still
 * failing to terminate. What gets reported is the timeout itself, never a collateral
 * {@code OutOfMemoryError} that would take the rest of the suite down with it.
 *
 * <p>The price is that neither hanging case can also assert which slots a near-midnight day
 * offers: by the argument above, any fixture that emits one slot is a fixture that fills the
 * heap. The slot list is pinned instead on intervals that terminate under both loops (the two
 * controls below), and the lead-time filtering by {@code BookingLeadTimeConsistencyTest}.
 */
@DisplayName("Slot loop termination - a closing time near midnight must not hang the request thread")
class AvailabilitySlotLoopTerminationTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Duration TERMINATION_BUDGET = Duration.ofSeconds(2);

    private static final String SALON_SLUG = "barberia-carlos";
    private static final String TENANT_ID = "tenant-001";
    private static final String EMPLOYEE_ID = "emp_abc123";
    private static final String SERVICE_ID = "svc_xyz456";

    /** 2026-06-10, comfortably inside CEST: no DST transition distorts these local times. */
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private AppointmentPersistencePort appointmentPersistencePort;
    private StaffServicePort staffServicePort;
    private SalonServicePort salonServicePort;

    @BeforeEach
    void setUp() {
        appointmentPersistencePort = mock(AppointmentPersistencePort.class);
        staffServicePort = mock(StaffServicePort.class);
        salonServicePort = mock(SalonServicePort.class);

        when(salonServicePort.getSalonBySlug(SALON_SLUG))
                .thenReturn(new SalonServicePort.SalonInfo(TENANT_ID, "Barberia Carlos", "ACTIVE"));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                anyString(), anyString(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    }

    /** A clock frozen at the given Madrid local time, carried on a deliberately foreign zone. */
    private static Clock frozenAt(LocalDate day, LocalTime time) {
        return Clock.fixed(LocalDateTime.of(day, time).atZone(MADRID).toInstant(), ZoneOffset.UTC);
    }

    private AvailabilityService openOn(LocalDate day, LocalTime open, LocalTime close,
                                       int serviceDurationMinutes, Clock clock) {
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID)).thenReturn(List.of(
                new EmployeeWorkingHoursDto(day.getDayOfWeek().getValue(), true, open, close, null, null)));
        when(staffServicePort.getService(TENANT_ID, SERVICE_ID))
                .thenReturn(new StaffServicePort.StaffServiceInfo(
                        SERVICE_ID, "Corte", new BigDecimal("25.00"), serviceDurationMinutes, true));
        return new AvailabilityService(appointmentPersistencePort, staffServicePort, salonServicePort, clock);
    }

    /** Runs the anonymous availability call under a hard deadline and returns its slots. */
    private List<AvailableSlot> slotsWithin(AvailabilityService availability, LocalDate day) {
        AvailabilityResponse response = assertTimeoutPreemptively(TERMINATION_BUDGET,
                () -> availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, day, SERVICE_ID),
                "the slot loop did not terminate");
        return response.slots();
    }

    @Test
    @DisplayName("a salon open until 23:59 terminates, queried inside the last hour of that day")
    void closingOneMinuteBeforeMidnight_terminates() {
        // 23:50 now, open 09:00-23:59, 15-minute service: an hour of lead puts the earliest
        // bookable start at 00:50 tomorrow, so every position on this date is discarded and the
        // old cursor wrapped past midnight without ever adding a slot. The interval still ends
        // at 23:59, which is what made that cursor roll over to 00:00 and walk the day again.
        AvailabilityService availability =
                openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(23, 59), 15, frozenAt(TODAY, LocalTime.of(23, 50)));

        List<AvailableSlot> slots = slotsWithin(availability, TODAY);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("a past day at a salon open until 23:59 terminates too: the lead-time filter is not an exit")
    void closingOneMinuteBeforeMidnight_onAPastDay_terminates() {
        // Every cursor position is discarded here, so the loop adds nothing at all - and still
        // never ended, because the discarding branch advanced the cursor just like the other one.
        AvailabilityService availability =
                openOn(YESTERDAY, LocalTime.of(9, 0), LocalTime.of(23, 59), 15,
                        frozenAt(TODAY, LocalTime.NOON));

        List<AvailableSlot> slots = slotsWithin(availability, YESTERDAY);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("control: 09:00-23:44, the widest interval that never wrapped, is untouched")
    void widestIntervalThatNeverWrapped_isUnchanged() {
        // One minute earlier than the case above and the old cursor already terminated: 23:30
        // plus 15 minutes is 23:45, past 23:44. The fix must not move this boundary.
        AvailabilityService availability =
                openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(23, 44), 15,
                        frozenAt(YESTERDAY, LocalTime.NOON));

        List<AvailableSlot> slots = slotsWithin(availability, TODAY);

        assertThat(slots).hasSize(58);
        assertThat(slots.getFirst()).isEqualTo(new AvailableSlot(LocalTime.of(9, 0), LocalTime.of(9, 15)));
        assertThat(slots.getLast()).isEqualTo(new AvailableSlot(LocalTime.of(23, 15), LocalTime.of(23, 30)));
    }

    @Test
    @DisplayName("control: an ordinary 09:00-18:00 day still yields the 35 slots it always did")
    void ordinaryClosingTime_isUnchanged() {
        AvailabilityService availability =
                openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(18, 0), 30,
                        frozenAt(YESTERDAY, LocalTime.NOON));

        List<AvailableSlot> slots = slotsWithin(availability, TODAY);

        assertThat(slots).hasSize(35);
        assertThat(slots.getFirst()).isEqualTo(new AvailableSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)));
        assertThat(slots.getLast()).isEqualTo(new AvailableSlot(LocalTime.of(17, 30), LocalTime.of(18, 0)));
    }
}
