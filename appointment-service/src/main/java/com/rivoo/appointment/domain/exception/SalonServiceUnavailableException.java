package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when salon-service itself is the problem — it answered with a server error, or it
 * could not be reached at all — as opposed to {@link SalonNotFoundException}, which means
 * salon-service answered normally and said "no such salon".
 * <p>
 * Unlike the generic {@code RuntimeException} this replaces, this maps (via
 * {@code GlobalExceptionHandler.handleRivooException}) to 502/503 instead of a blanket 500:
 * a dependency being down or misbehaving is not "our bug", and 502/503 tells the caller it
 * may be worth retrying. This does not reopen the anti-enumeration oracle: the status and
 * body are the same regardless of which slug was requested, only the failure mode of
 * salon-service itself decides between 502 and 503.
 */
public class SalonServiceUnavailableException extends RivooException {

    private SalonServiceUnavailableException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause, "salon-service-unavailable", "Salon Service Unavailable", httpStatus);
    }

    /** salon-service answered, but with a server error (5xx). */
    public static SalonServiceUnavailableException serverError(String message, Throwable cause) {
        return new SalonServiceUnavailableException(message, cause, HttpStatus.BAD_GATEWAY);
    }

    /** salon-service could not be reached at all (connection refused, timeout, DNS, ...). */
    public static SalonServiceUnavailableException unreachable(String message, Throwable cause) {
        return new SalonServiceUnavailableException(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
