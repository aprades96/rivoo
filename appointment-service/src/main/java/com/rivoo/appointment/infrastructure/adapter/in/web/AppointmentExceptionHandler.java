package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.domain.exception.SalonServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

// Unlike a RivooException subtype (already matched deterministically by
// GlobalExceptionHandler.handleRivooException at depth 1 regardless of visit
// order), IllegalArgumentException is NOT a RivooException, so it is only
// caught here or falls through to GlobalExceptionHandler's generic
// @ExceptionHandler(Exception.class) catch-all. Before this @Order, both
// advices were implicitly Ordered.LOWEST_PRECEDENCE (a tie), and Spring's
// AnnotationAwareOrderComparator does not specify a deterministic tie-break
// between two beans with the same order — so an IllegalArgumentException
// could resolve to this handler's 400 or to the catch-all's 500,
// unpredictably. This @Order(0) makes this handler win deterministically.
// Matches the convention already used in salon-service's SalonExceptionHandler
// and staff-service's StaffExceptionHandler.
@Slf4j
@RestControllerAdvice
@Order(0)
public class AppointmentExceptionHandler {

    // SalonServiceUnavailableException reaches the two ANONYMOUS public endpoints
    // (POST /api/v1/appointments/book, GET /api/v1/appointments/public/availability).
    // SalonServiceAdapter builds its message as "salon-service returned a server error for slug:
    // X" / "salon-service is unreachable for slug: X", and its cause is the ResourceAccessException
    // or HttpServerErrorException that names the full internal URL — host and port included.
    // GlobalExceptionHandler.handleRivooException publishes ex.getMessage() verbatim as `detail`,
    // so without this handler an unauthenticated caller is told which internal service failed.
    // The client now gets a fixed string; the real message and its cause go to the log.
    private static final String SALON_SERVICE_UNAVAILABLE_DETAIL =
            "This booking page is temporarily unavailable. Please try again in a few minutes.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.atWarn().setCause(ex).log("Invalid argument");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/invalid-argument"));
        problem.setTitle("Invalid Argument");
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(SalonServiceUnavailableException.class)
    public ProblemDetail handleSalonServiceUnavailable(SalonServiceUnavailableException ex) {
        // Status, type and title still come from the exception: the 502-vs-503 split (salon-service
        // answered badly vs was never reached) is real information for the caller and for the
        // gateway, and it does not vary with the slug, so it opens no enumeration oracle. Only the
        // free-text detail — the one field that named the internal service — is replaced, which
        // also makes the body strictly MORE uniform across slugs than it was before.
        log.atError()
                .setCause(ex)
                .addKeyValue("dependency", "salon-service")
                .addKeyValue("internalDetail", ex.getMessage())
                .log("Salon service unavailable");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(),
                SALON_SERVICE_UNAVAILABLE_DETAIL);
        problem.setType(URI.create("https://rivoo.com/errors/" + ex.getErrorType()));
        problem.setTitle(ex.getErrorTitle());
        enrichProblemDetail(problem);
        return problem;
    }

    private void enrichProblemDetail(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
