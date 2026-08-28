package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown from BOTH anonymous and authenticated paths, so the decision "may this message be
 * published?" cannot be made once for the class — it belongs to the throw site.
 * <p>
 * The class default stays RESTRICTIVE ({@code clientSafeDetail()} returns {@code null}) because it
 * is a shared base class: an override here would be inherited by every subtype in every service,
 * present and future, whatever endpoint throws it. {@code AppointmentConflictException} extends
 * this class and its message names an employee — inverting the default would hand that name back
 * to unauthenticated callers, which is the exact leak this hierarchy moved away from.
 * <p>
 * A throw site that is reachable only from an authenticated endpoint, and whose message carries
 * nothing the caller does not already own, opts in explicitly with {@link #clientSafe(String)}.
 * Publishing is therefore a per-site act, visible in the diff, never an inherited default.
 * <p>
 * As of this writing the direct throw sites split like this:
 * <ul>
 *   <li>{@code SalonBusinessHours#validate} and {@code EmployeeWorkingHours#validate} — reached
 *       from {@code PUT /api/v1/salons/me/business-hours} and
 *       {@code PUT /api/v1/staff/employees/{id}/working-hours}, both
 *       {@code hasRole('SALON_OWNER')}: {@link #clientSafe(String)}.</li>
 *   <li>{@code AppointmentService#create} — {@code POST /api/v1/appointments},
 *       {@code hasAnyRole('SALON_OWNER','EMPLOYEE')}: {@link #clientSafe(String)}.</li>
 *   <li>{@code AppointmentService#book} — the ANONYMOUS {@code POST /api/v1/appointments/book}.
 *       The two booking-window rules are {@link #clientSafe(String)} (they describe the caller's
 *       own input and are the only thing telling a visitor what to fix); the two
 *       "employee/service is not active" checks keep the restrictive default, because they
 *       disclose a salon's internal state to an unauthenticated caller.</li>
 * </ul>
 */
public class BusinessValidationException extends RivooException {

    private final boolean publishesMessage;

    /**
     * Restrictive by default: the message goes to the log only. Subtypes reach this constructor
     * through {@code super(message)} and therefore inherit the restrictive default too, unless
     * they override {@link #clientSafeDetail()} for themselves.
     */
    public BusinessValidationException(String message) {
        this(message, false);
    }

    private BusinessValidationException(String message, boolean publishesMessage) {
        super(message, "business-validation", "Business Validation Failed", HttpStatus.UNPROCESSABLE_ENTITY);
        this.publishesMessage = publishesMessage;
    }

    /**
     * An instance that DOES publish {@code message} as the Problem Details {@code detail}.
     * <p>
     * Use it only where the throw site is reachable exclusively from an authenticated endpoint,
     * or where the message describes nothing but the caller's own request. The returned instance
     * carries the decision, so the same class can stay restrictive everywhere else.
     */
    public static BusinessValidationException clientSafe(String message) {
        return new BusinessValidationException(message, true);
    }

    @Override
    public String clientSafeDetail() {
        return publishesMessage ? getMessage() : null;
    }
}
