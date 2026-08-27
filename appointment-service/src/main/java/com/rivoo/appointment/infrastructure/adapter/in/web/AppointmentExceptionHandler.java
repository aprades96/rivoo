package com.rivoo.appointment.infrastructure.adapter.in.web;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.atWarn().setCause(ex).log("Invalid argument");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/invalid-argument"));
        problem.setTitle("Invalid Argument");
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
