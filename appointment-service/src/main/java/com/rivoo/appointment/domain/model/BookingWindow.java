package com.rivoo.appointment.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How far ahead a booking has to be. Single source of truth for that rule.
 *
 * <p>Two sites have to agree on it exactly: slot calculation ({@code AvailabilityService})
 * decides what is OFFERED, and public booking ({@code AppointmentService#book}) decides what is
 * ACCEPTED. While each carried its own copy, the booking page offered slots that the booking
 * endpoint then refused, after the visitor had already filled in the form. Both now call
 * {@link #isTooSoon(LocalDateTime, LocalDateTime)}, so they share the threshold <em>and</em> the
 * comparison operator: they cannot drift apart without editing this file.
 *
 * <p>Two audiences, two pairs, one comparison. The public pair -
 * {@code AvailabilityService#getPublicAvailableSlots} and {@code AppointmentService#book} - owes
 * {@link #MINIMUM_LEAD_TIME} on both sides. The salon pair -
 * {@code AvailabilityService#getAvailableSlots}, the wizard, and {@code AppointmentService#create},
 * the endpoint it posts to - owes nothing on either side. Only the amount differs between them,
 * and it travels as an argument precisely so that neither site can quietly grow its own copy of
 * the comparison again.
 *
 * <p>Domain class: plain Java, no Spring and no JPA (project dependency rule).
 */
public final class BookingWindow {

    /**
     * Minimum lead time between "now" and the start of the appointment.
     *
     * <p>Deliberately a constant and not a configurable property: the domain layer must not
     * depend on Spring, so a {@code @Value} would have to be injected into two different
     * services and would reopen exactly the divergence this file closes. The value also appears
     * verbatim in the message the visitor reads and in the module documentation, and nothing
     * needs it to vary per environment. If it ever has to vary, it will vary per salon
     * (business data), not per deployment.
     */
    public static final Duration MINIMUM_LEAD_TIME = Duration.ofHours(1);

    private BookingWindow() {
    }

    /**
     * @param startTime start of the appointment, in the salon's local time
     * @param now       current instant, in the salon's local time
     * @return {@code true} when {@code startTime} is closer to {@code now} than
     *         {@link #MINIMUM_LEAD_TIME} and therefore cannot be booked. Exactly
     *         {@code now + MINIMUM_LEAD_TIME} is NOT too soon.
     */
    public static boolean isTooSoon(LocalDateTime startTime, LocalDateTime now) {
        return isTooSoon(startTime, now, MINIMUM_LEAD_TIME);
    }

    /**
     * The same comparison, with the lead time the caller's audience owes.
     *
     * <p>{@link Duration#ZERO} is a lead time like any other here, deliberately: it has to keep
     * meaning "not before now" through this same full date+time comparison rather than through a
     * separate is-it-in-the-past branch. That is what keeps a fully past day offering nothing on
     * the salon side too, and what stops a time-of-day comparison from creeping back in.
     *
     * @param startTime start of the appointment, in the salon's local time
     * @param now       current instant, in the salon's local time
     * @param leadTime  notice this audience owes; {@link Duration#ZERO} means "not before now"
     * @return {@code true} when {@code startTime} is closer to {@code now} than {@code leadTime}
     *         and therefore cannot be booked. Exactly {@code now + leadTime} is NOT too soon.
     */
    public static boolean isTooSoon(LocalDateTime startTime, LocalDateTime now, Duration leadTime) {
        return startTime.isBefore(now.plus(leadTime));
    }
}
