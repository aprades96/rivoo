package com.rivoo.common.web;

import com.rivoo.common.exception.RivooException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

// Explicit LOWEST_PRECEDENCE (same numeric value Spring already assigns by
// default to an advice bean that declares no @Order): this pins this handler
// to the floor, so any per-service @RestControllerAdvice that DOES declare a
// lower @Order value is guaranteed to be considered first. It does NOT by
// itself guarantee anything about a per-service advice that declares no
// @Order of its own — two beans both implicitly/explicitly at
// LOWEST_PRECEDENCE are still a tie, and Spring's
// AnnotationAwareOrderComparator does not specify a deterministic tie-break
// in that case. As of this comment, auth-service's AuthExceptionHandler,
// salon-service's SalonExceptionHandler, staff-service's
// StaffExceptionHandler, appointment-service's AppointmentExceptionHandler,
// billing-service's BillingExceptionHandler and client-service's
// ClientExceptionHandler all declare @Order(0) precisely to win that tie
// deterministically against this handler's generic
// @ExceptionHandler(Exception.class) catch-all. This declaration does not
// change today's runtime behavior in any service (the value is identical to
// the previous default); it only makes the floor explicit instead of
// implicit.
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Published when a RivooException declares no clientSafeDetail(). Deliberately says nothing
    // about the failure beyond "we understood you and did not do it": the class of error is
    // already carried by `status` and by `title` (RFC 9457), which are per-exception and safe.
    // The information removed from here is not lost — it goes to the log below, with the cause.
    public static final String GENERIC_DETAIL =
            "The request could not be completed. Please review the request and try again.";

    @ExceptionHandler(RivooException.class)
    public ProblemDetail handleRivooException(RivooException ex) {
        // Always logged, whatever is published: ex.getMessage() is the diagnostic, and moving it
        // out of the response body must not mean deleting it. setCause(ex) is what keeps the
        // stack trace and the cause chain (e.g. the ResourceAccessException naming an internal
        // URL) — the previous version passed neither, so an outage left only a one-line WARN.
        var logBuilder = ex.getHttpStatus().is5xxServerError() ? log.atError() : log.atWarn();
        logBuilder.setCause(ex)
                .addKeyValue("errorType", ex.getErrorType())
                .addKeyValue("errorTitle", ex.getErrorTitle())
                .addKeyValue("internalDetail", ex.getMessage())
                .log("Rivoo exception");

        String clientSafeDetail = ex.clientSafeDetail();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(),
                clientSafeDetail != null ? clientSafeDetail : GENERIC_DETAIL);
        problem.setType(URI.create("https://rivoo.com/errors/" + ex.getErrorType()));
        problem.setTitle(ex.getErrorTitle());
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.atError().setCause(ex).log("Unexpected error");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setType(URI.create("https://rivoo.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        addCommonProperties(problem);
        return problem;
    }

    private void addCommonProperties(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
