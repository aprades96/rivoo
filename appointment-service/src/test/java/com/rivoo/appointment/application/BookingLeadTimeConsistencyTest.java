package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.application.dto.AvailableSlot;
import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.application.dto.PublicBookingRequest;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.BookingWindow;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The booking page offered slots that the booking endpoint then refused.
 *
 * <p>Availability skipped only slots at or before "now" while public booking demanded a full
 * hour of notice, so everything in the window (now, now + 1h] was rendered, chosen, filled in
 * and rejected at the confirm step. Both sides now read
 * {@link com.rivoo.appointment.domain.model.BookingWindow}, and these tests pin the boundary
 * where the two used to disagree.
 *
 * <p>Every test drives a {@link Clock#fixed(Instant, ZoneId)}: nothing here reads the wall
 * clock, so "queried at 23:50" is an ordinary test and not a nightly flake. The fixed clocks
 * are deliberately built on {@link ZoneOffset#UTC} while the salon is in Madrid, so a service
 * that forgot to re-zone would fail these tests rather than pass them by luck.
 */
@DisplayName("Booking lead time - availability and booking apply the same rule")
class BookingLeadTimeConsistencyTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final String SALON_SLUG = "barberia-carlos";
    private static final String TENANT_ID = "tenant-001";
    private static final String EMPLOYEE_ID = "emp_abc123";
    private static final String SERVICE_ID = "svc_xyz456";
    private static final int SERVICE_DURATION_MINUTES = 30;

    /** 2026-06-10, comfortably inside CEST: no DST transition distorts these local times. */
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private AppointmentPersistencePort appointmentPersistencePort;
    private StaffServicePort staffServicePort;
    private ClientServicePort clientServicePort;
    private BillingServicePort billingServicePort;
    private NotificationServicePort notificationServicePort;
    private SalonServicePort salonServicePort;

    @BeforeEach
    void setUp() {
        appointmentPersistencePort = mock(AppointmentPersistencePort.class);
        staffServicePort = mock(StaffServicePort.class);
        clientServicePort = mock(ClientServicePort.class);
        billingServicePort = mock(BillingServicePort.class);
        notificationServicePort = mock(NotificationServicePort.class);
        salonServicePort = mock(SalonServicePort.class);

        when(salonServicePort.getSalonBySlug(SALON_SLUG))
                .thenReturn(new SalonServicePort.SalonInfo(TENANT_ID, "Barberia Carlos", "ACTIVE"));
        when(staffServicePort.getService(TENANT_ID, SERVICE_ID))
                .thenReturn(new StaffServicePort.StaffServiceInfo(
                        SERVICE_ID, "Corte", new BigDecimal("25.00"), SERVICE_DURATION_MINUTES, true));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A clock frozen at the given Madrid local time, carried on a deliberately foreign zone. */
    private static Clock frozenAt(LocalDate day, LocalTime time) {
        return Clock.fixed(LocalDateTime.of(day, time).atZone(MADRID).toInstant(), ZoneOffset.UTC);
    }

    /** Open hours for whichever weekday the day falls on, so no weekday is hard-coded. */
    private static EmployeeWorkingHoursDto openOn(LocalDate day, LocalTime open, LocalTime close) {
        return new EmployeeWorkingHoursDto(day.getDayOfWeek().getValue(), true, open, close, null, null);
    }

    private AvailabilityService availabilityWith(Clock clock, EmployeeWorkingHoursDto... hours) {
        when(staffServicePort.getEmployeeWorkingHours(TENANT_ID, EMPLOYEE_ID)).thenReturn(List.of(hours));
        when(appointmentPersistencePort.findByEmployeeAndDateRange(
                anyString(), anyString(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
        return new AvailabilityService(appointmentPersistencePort, staffServicePort, salonServicePort, clock);
    }

    private AppointmentService bookingWith(Clock clock) {
        return new AppointmentService(appointmentPersistencePort, staffServicePort, clientServicePort,
                billingServicePort, notificationServicePort, salonServicePort,
                mock(AppointmentDtoMapper.class), clock);
    }

    /** Everything public booking needs past the lead-time check, so only that check can reject. */
    private void stubHappyBookingPath() {
        when(staffServicePort.getEmployee(TENANT_ID, EMPLOYEE_ID))
                .thenReturn(new StaffServicePort.StaffEmployeeInfo(EMPLOYEE_ID, "Carlos", "Ruiz", true));
        when(billingServicePort.getMaxAppointmentsPerMonth(TENANT_ID)).thenReturn(-1);
        when(appointmentPersistencePort.findOverlappingForUpdate(
                anyString(), anyString(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
        when(clientServicePort.findOrCreateClient(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClientServicePort.ClientInfo(
                        "cli_def789", "Ana", "Garcia", "ana@example.com", "+34600000000", true));
        when(appointmentPersistencePort.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static PublicBookingRequest bookingAt(LocalDate day, LocalTime time) {
        return new PublicBookingRequest(SALON_SLUG, EMPLOYEE_ID, SERVICE_ID, "Ana", "Garcia",
                "ana@example.com", "+34600000000", LocalDateTime.of(day, time), null);
    }

    private static List<LocalTime> startTimesOf(AvailabilityResponse response) {
        return response.slots().stream().map(AvailableSlot::startTime).toList();
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("the rule itself")
    class Rule {

        private final LocalDateTime now = LocalDateTime.of(TODAY, LocalTime.of(10, 0));

        @Test
        @DisplayName("exactly one hour ahead is not too soon (the boundary is inclusive)")
        void exactlyOneHourAhead_isNotTooSoon() {
            assertThat(BookingWindow.isTooSoon(now.plusHours(1), now)).isFalse();
        }

        @Test
        @DisplayName("fifty-nine minutes ahead is too soon")
        void fiftyNineMinutesAhead_isTooSoon() {
            assertThat(BookingWindow.isTooSoon(now.plusMinutes(59), now)).isTrue();
        }

        @Test
        @DisplayName("one nanosecond short of an hour is still too soon")
        void oneNanoShortOfAnHour_isTooSoon() {
            assertThat(BookingWindow.isTooSoon(now.plusHours(1).minusNanos(1), now)).isTrue();
        }

        @Test
        @DisplayName("a time already past is too soon")
        void alreadyPast_isTooSoon() {
            assertThat(BookingWindow.isTooSoon(now.minusMinutes(1), now)).isTrue();
        }
    }

    @Nested
    @DisplayName("what availability offers")
    class Offering {

        @Test
        @DisplayName("a slot exactly one hour ahead is offered, and nothing closer is")
        void slotExactlyOneHourAhead_isOffered() {
            // 10:00 now, open 09:00-20:00, 15-minute grid: 10:15/10:30/10:45 are inside the
            // hour and must go; 11:00 is exactly the boundary and must stay.
            AvailabilityService availability = availabilityWith(
                    frozenAt(TODAY, LocalTime.of(10, 0)),
                    openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(20, 0)));

            List<LocalTime> starts = startTimesOf(
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, TODAY, SERVICE_ID));

            assertThat(starts).contains(LocalTime.of(11, 0));
            assertThat(starts).first().isEqualTo(LocalTime.of(11, 0));
            assertThat(starts).doesNotContain(
                    LocalTime.of(10, 15), LocalTime.of(10, 30), LocalTime.of(10, 45));
        }

        @Test
        @DisplayName("a slot fifty-nine minutes ahead is not offered")
        void slotFiftyNineMinutesAhead_isNotOffered() {
            // 10:01 now: 11:00 is 59 minutes away and must go, 11:15 is 74 and must stay.
            AvailabilityService availability = availabilityWith(
                    frozenAt(TODAY, LocalTime.of(10, 1)),
                    openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(20, 0)));

            List<LocalTime> starts = startTimesOf(
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, TODAY, SERVICE_ID));

            assertThat(starts).doesNotContain(LocalTime.of(11, 0));
            assertThat(starts).first().isEqualTo(LocalTime.of(11, 15));
        }

        @Test
        @DisplayName("slots earlier today are not offered")
        void slotsAlreadyPast_areNotOffered() {
            AvailabilityService availability = availabilityWith(
                    frozenAt(TODAY, LocalTime.of(10, 0)),
                    openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(20, 0)));

            List<LocalTime> starts = startTimesOf(
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, TODAY, SERVICE_ID));

            assertThat(starts).doesNotContain(
                    LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("a day that is entirely in the past offers nothing at all")
        void dayEntirelyInThePast_offersNothing() {
            // The old same-day guard compared only clock times, so yesterday afternoon still
            // looked "later than now" and was offered.
            AvailabilityService availability = availabilityWith(
                    frozenAt(TODAY, LocalTime.of(10, 0)),
                    openOn(YESTERDAY, LocalTime.of(9, 0), LocalTime.of(20, 0)));

            AvailabilityResponse response =
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, YESTERDAY, SERVICE_ID);

            assertThat(response.slots()).isEmpty();
        }

        @Test
        @DisplayName("queried at 23:50, tomorrow 01:00 is offered but tomorrow 00:30 is not")
        void acrossMidnight_leadTimeIsMeasuredAcrossTheDateBoundary() {
            // The date boundary is where the two encodings differed most: the old guard only
            // ever fired when the requested date was today, so at 23:50 every slot after
            // midnight was offered, including ones minutes away.
            AvailabilityService availability = availabilityWith(
                    frozenAt(TODAY, LocalTime.of(23, 50)),
                    openOn(TOMORROW, LocalTime.of(0, 0), LocalTime.of(6, 0)));

            List<LocalTime> starts = startTimesOf(
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, TOMORROW, SERVICE_ID));

            // 00:50 is the threshold; 00:45 is 55 minutes away, 01:00 is 70.
            assertThat(starts).doesNotContain(
                    LocalTime.of(0, 0), LocalTime.of(0, 30), LocalTime.of(0, 45));
            assertThat(starts).first().isEqualTo(LocalTime.of(1, 0));
            assertThat(starts).contains(LocalTime.of(5, 0));
        }
    }

    @Nested
    @DisplayName("what booking accepts")
    class Accepting {

        @Test
        @DisplayName("a booking exactly one hour ahead is accepted")
        void bookingExactlyOneHourAhead_isAccepted() {
            stubHappyBookingPath();
            AppointmentService booking = bookingWith(frozenAt(TODAY, LocalTime.of(10, 0)));

            assertThatCode(() -> booking.book(bookingAt(TODAY, LocalTime.of(11, 0))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a booking fifty-nine minutes ahead is still rejected when posted directly")
        void bookingFiftyNineMinutesAhead_isRejected() {
            stubHappyBookingPath();
            AppointmentService booking = bookingWith(frozenAt(TODAY, LocalTime.of(10, 0)));

            assertThatThrownBy(() -> booking.book(bookingAt(TODAY, LocalTime.of(10, 59))))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessage("Booking must be at least 1 hour in the future");
        }
    }

    @Nested
    @DisplayName("the invariant that matters")
    class Agreement {

        @Test
        @DisplayName("every slot the availability endpoint offers, the booking endpoint accepts")
        void everyOfferedSlotIsBookable() {
            Clock clock = frozenAt(TODAY, LocalTime.of(10, 0));
            AvailabilityService availability = availabilityWith(
                    clock, openOn(TODAY, LocalTime.of(9, 0), LocalTime.of(20, 0)));
            stubHappyBookingPath();
            AppointmentService booking = bookingWith(clock);

            List<LocalTime> offered = startTimesOf(
                    availability.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, TODAY, SERVICE_ID));

            // 11:00 to 19:30 on a 15-minute grid. Pinned so that a rule which silently offered
            // nothing could not satisfy this test vacuously.
            assertThat(offered).hasSize(35);
            assertThat(offered).startsWith(LocalTime.of(11, 0)).endsWith(LocalTime.of(19, 30));

            for (LocalTime slot : offered) {
                assertThatCode(() -> booking.book(bookingAt(TODAY, slot)))
                        .as("slot %s was offered by availability", slot)
                        .doesNotThrowAnyException();
            }
        }
    }
}
