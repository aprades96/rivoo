package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every exception this platform maps to a Problem Details response.
 * <p>
 * {@code getMessage()} is a SERVER-SIDE diagnostic and is never published as such: it is written
 * to the log by {@code GlobalExceptionHandler.handleRivooException}, together with the exception
 * as cause. What reaches the caller is {@link #clientSafeDetail()}, which defaults to
 * {@code null} — i.e. "nothing about this exception is safe to publish" — so the handler falls
 * back to a fixed generic string.
 * <p>
 * The default is deliberately the RESTRICTIVE one. It used to be the opposite: every subtype
 * published its message, so each new subtype leaked by default and the anonymous endpoints
 * (public booking, salon registration, the public salon page) had to be patched one site at a
 * time, twice, and still leaked. A subtype now has to opt IN to publishing, and the reviewer of
 * a new subtype sees an explicit decision instead of an implicit one.
 */
public abstract class RivooException extends RuntimeException {

    private final String errorType;
    private final String errorTitle;
    private final HttpStatus httpStatus;

    protected RivooException(String message, String errorType, String errorTitle, HttpStatus httpStatus) {
        super(message);
        this.errorType = errorType;
        this.errorTitle = errorTitle;
        this.httpStatus = httpStatus;
    }

    protected RivooException(String message, Throwable cause, String errorType, String errorTitle, HttpStatus httpStatus) {
        super(message, cause);
        this.errorType = errorType;
        this.errorTitle = errorTitle;
        this.httpStatus = httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorTitle() {
        return errorTitle;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * The {@code detail} this exception may publish to the caller, or {@code null} if none of its
     * message is safe to publish.
     * <p>
     * Override with {@code return getMessage();} ONLY when every throw site of the subtype is
     * reachable exclusively from an authenticated endpoint — i.e. the only caller who can see the
     * message is the tenant the message is about. If any throw site is reachable from an endpoint
     * the gateway AND the owning service both mark {@code permitAll}, leave this default: an
     * unauthenticated caller then gets the generic string and the real message goes to the log.
     */
    public String clientSafeDetail() {
        return null;
    }
}
