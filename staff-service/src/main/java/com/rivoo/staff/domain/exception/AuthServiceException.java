package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * Raised when the call to auth-service while registering an employee's Keycloak account did not
 * produce a usable result.
 * <p>
 * The two factories below encode the distinction the HTTP status has to carry: {@link
 * #unavailable} means auth-service itself is the problem (it answered 5xx, was unreachable, or
 * its response was unusable) and maps to 502 — a genuine "the upstream is broken" signal that
 * should page an operator and that the caller may retry. {@link #rejected} means the exact
 * opposite: auth-service answered correctly and refused the request for a business reason (any
 * 4xx), which is not an infrastructure failure and must not be flattened into 502 — a 502 there
 * both hides the real reason from the salon owner and raises a false infrastructure alarm.
 * <p>
 * The rejection status is 422 and not 409, even though the only caller ({@code POST
 * /api/v1/staff/employees}, {@code hasRole('SALON_OWNER')}) is authenticated and therefore free
 * of the topology-hiding concern that forced a single shared rejection identity on salon-service's
 * anonymous registration path (see {@code OnboardingRejection}: that mapping hides WHICH
 * dependency refused; it does not close account enumeration, which salon-service's own 409 leaves
 * open by a separate, deliberate product decision). The reason here is simply truthfulness:
 * {@code HttpClientErrorException} is the WHOLE
 * 4xx family, so one status has to hold for all of it — 409 would misreport a Keycloak
 * password-policy 400 as a conflict, while 422 ("we understood the request, the dependency
 * refused it") is correct for 400, 404, 409 and 415 alike.
 * <p>
 * {@link #REJECTED_ERROR_TYPE} is not in {@code com.rivoo.common.web.RivooErrorTypes}: per that
 * class's own javadoc, only values a DIFFERENT service parses belong there, and no consumer
 * branches on this one.
 */
public class AuthServiceException extends RivooException {

    private static final String UNAVAILABLE_ERROR_TYPE = "auth-service-error";
    private static final String UNAVAILABLE_ERROR_TITLE = "Auth Service Error";
    private static final String REJECTED_ERROR_TYPE = "employee-registration-rejected";
    private static final String REJECTED_ERROR_TITLE = "Employee Registration Rejected";

    private AuthServiceException(String message, Throwable cause, String errorType,
                                 String errorTitle, HttpStatus httpStatus) {
        super(message, cause, errorType, errorTitle, httpStatus);
    }

    /** auth-service is the problem: 5xx, unreachable, or an unusable response. */
    public static AuthServiceException unavailable(String message, Throwable cause) {
        return new AuthServiceException(message, cause, UNAVAILABLE_ERROR_TYPE,
                UNAVAILABLE_ERROR_TITLE, HttpStatus.BAD_GATEWAY);
    }

    /** auth-service worked and said no: it answered 4xx for a business reason. */
    public static AuthServiceException rejected(String message, Throwable cause) {
        return new AuthServiceException(message, cause, REJECTED_ERROR_TYPE,
                REJECTED_ERROR_TITLE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Authenticated-only: the only caller is POST /api/v1/staff/employees, hasRole('SALON_OWNER'),
     * so the tenant named in the message is the caller's own. This mirrors what
     * StaffExceptionHandler already does for this exception today; the override keeps that
     * behaviour if the dedicated handler is ever removed, instead of silently falling back to
     * the generic string. Contrast salon-service's AuthServiceException, whose caller is the
     * ANONYMOUS POST /api/v1/salons and which therefore keeps the restrictive default.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
