package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.application.dto.AvailableSlot;
import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.BookingWindow;
import com.rivoo.appointment.domain.port.in.CheckAvailabilityUseCase;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService implements CheckAvailabilityUseCase {

    private static final ZoneId SALON_TIMEZONE = ZoneId.of("Europe/Madrid");
    private static final int SLOT_GRANULARITY_MINUTES = 15;

    private final AppointmentPersistencePort appointmentPersistencePort;
    private final StaffServicePort staffServicePort;
    private final SalonServicePort salonServicePort;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse getPublicAvailableSlots(String salonSlug, String employeeId,
                                                          LocalDate date, String serviceId) {
        // Resolve tenant from salon slug — same pattern as the public booking flow.
        SalonServicePort.SalonInfo salon = salonServicePort.getSalonBySlug(salonSlug);
        // A non-ACTIVE salon must be indistinguishable from a non-existent one to
        // an anonymous caller: same exception, same response, as a slug that
        // salon-service has never heard of (see SalonServiceAdapter). Otherwise a
        // 422 here vs. a 404 for an unknown slug would let anyone enumerate which
        // businesses exist but are suspended.
        if (!"ACTIVE".equals(salon.status())) {
            throw new SalonNotFoundException(salonSlug);
        }
        String tenantId = salon.tenantId();

        return getAvailableSlots(tenantId, employeeId, date, serviceId);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailableSlots(String tenantId, String employeeId,
                                                   LocalDate date, String serviceId) {
        log.atInfo()
                .addKeyValue("employeeId", employeeId)
                .addKeyValue("date", date)
                .log("Calculating available slots");

        // 1. Get employee working hours from staff-service
        List<EmployeeWorkingHoursDto> allWorkingHours = staffServicePort.getEmployeeWorkingHours(tenantId, employeeId);

        // 2. Find working hours for the requested day
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Monday ... 7=Sunday
        EmployeeWorkingHoursDto dayHours = allWorkingHours.stream()
                .filter(wh -> wh.dayOfWeek() == dayOfWeek && wh.open())
                .findFirst()
                .orElse(null);

        if (dayHours == null) {
            log.atInfo().addKeyValue("dayOfWeek", dayOfWeek).log("Employee does not work on this day");
            return new AvailabilityResponse(date, employeeId, List.of());
        }

        // 3. Get existing appointments for the date (convert local date to UTC range)
        ZonedDateTime startOfDayZoned = date.atStartOfDay(SALON_TIMEZONE);
        ZonedDateTime endOfDayZoned = startOfDayZoned.plusDays(1);
        Instant startOfDayUtc = startOfDayZoned.toInstant();
        Instant endOfDayUtc = endOfDayZoned.toInstant();

        List<Appointment> existingAppointments = appointmentPersistencePort
                .findByEmployeeAndDateRange(tenantId, employeeId, startOfDayUtc, endOfDayUtc);

        // 4. Get service duration if serviceId provided
        int serviceDuration = 0;
        if (serviceId != null && !serviceId.isBlank()) {
            StaffServicePort.StaffServiceInfo serviceInfo = staffServicePort.getService(tenantId, serviceId);
            serviceDuration = serviceInfo.durationMinutes();
        }

        // 5. Calculate free slots
        List<AvailableSlot> slots = calculateFreeSlots(date, dayHours, existingAppointments, serviceDuration);

        log.atInfo()
                .addKeyValue("slotsFound", slots.size())
                .log("Available slots calculated");

        return new AvailabilityResponse(date, employeeId, slots);
    }

    private List<AvailableSlot> calculateFreeSlots(LocalDate date, EmployeeWorkingHoursDto dayHours,
                                                    List<Appointment> existingAppointments, int serviceDuration) {
        // Build busy intervals from existing appointments (convert UTC to local time)
        List<TimeInterval> busyIntervals = existingAppointments.stream()
                .map(apt -> new TimeInterval(
                        apt.getStartTime().atZone(SALON_TIMEZONE).toLocalTime(),
                        apt.getEndTime().atZone(SALON_TIMEZONE).toLocalTime()))
                .sorted(Comparator.comparing(TimeInterval::start))
                .toList();

        // Build work intervals (accounting for break)
        List<TimeInterval> workIntervals = new ArrayList<>();
        if (dayHours.breakStartTime() != null && dayHours.breakEndTime() != null) {
            workIntervals.add(new TimeInterval(dayHours.openTime(), dayHours.breakStartTime()));
            workIntervals.add(new TimeInterval(dayHours.breakEndTime(), dayHours.closeTime()));
        } else {
            workIntervals.add(new TimeInterval(dayHours.openTime(), dayHours.closeTime()));
        }

        // For each work interval, subtract busy intervals to get free intervals
        List<TimeInterval> freeIntervals = new ArrayList<>();
        for (TimeInterval work : workIntervals) {
            freeIntervals.addAll(subtractBusy(work, busyIntervals));
        }

        // If no service duration specified, return raw free intervals
        if (serviceDuration <= 0) {
            return freeIntervals.stream()
                    .map(interval -> new AvailableSlot(interval.start(), interval.end()))
                    .toList();
        }

        // Split free intervals into discrete slots based on service duration.
        // Only the clock's instant is used; the zone is always the salon's.
        LocalDateTime now = LocalDateTime.now(clock.withZone(SALON_TIMEZONE));

        List<AvailableSlot> slots = new ArrayList<>();
        for (TimeInterval free : freeIntervals) {
            LocalTime cursor = free.start();
            while (cursor.plusMinutes(serviceDuration).compareTo(free.end()) <= 0) {
                // Never offer a slot that AppointmentService.book() would refuse: same rule,
                // same object. Compared as a full date+time rather than "if the date is today,
                // compare the time": with a same-day-only guard, tomorrow's 00:30 escaped the
                // check entirely when the page was loaded at 23:50, and was offered although it
                // is only 40 minutes away.
                if (BookingWindow.isTooSoon(LocalDateTime.of(date, cursor), now)) {
                    cursor = cursor.plusMinutes(SLOT_GRANULARITY_MINUTES);
                    continue;
                }
                slots.add(new AvailableSlot(cursor, cursor.plusMinutes(serviceDuration)));
                cursor = cursor.plusMinutes(SLOT_GRANULARITY_MINUTES);
            }
        }

        return slots;
    }

    private List<TimeInterval> subtractBusy(TimeInterval work, List<TimeInterval> busyIntervals) {
        List<TimeInterval> result = new ArrayList<>();
        LocalTime current = work.start();

        for (TimeInterval busy : busyIntervals) {
            // Skip busy intervals that don't overlap with current work interval
            if (busy.end().compareTo(current) <= 0 || busy.start().compareTo(work.end()) >= 0) {
                continue;
            }

            // Add free gap before this busy interval
            if (busy.start().isAfter(current)) {
                LocalTime freeEnd = busy.start().compareTo(work.end()) < 0 ? busy.start() : work.end();
                result.add(new TimeInterval(current, freeEnd));
            }

            // Move cursor past the busy interval
            current = busy.end().compareTo(current) > 0 ? busy.end() : current;
        }

        // Add remaining free time after last busy interval
        if (current.isBefore(work.end())) {
            result.add(new TimeInterval(current, work.end()));
        }

        return result;
    }

    private record TimeInterval(LocalTime start, LocalTime end) {}
}
