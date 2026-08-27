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

    @ExceptionHandler(RivooException.class)
    public ProblemDetail handleRivooException(RivooException ex) {
        log.atWarn().addKeyValue("errorTitle", ex.getErrorTitle()).addKeyValue("detail", ex.getMessage()).log("Rivoo exception");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
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
