package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.staff.domain.exception.AuthServiceException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

// Redundant since AuthServiceException now extends RivooException:
// GlobalExceptionHandler.handleRivooException(RivooException) matches it at
// depth 1 regardless of which advice bean Spring visits first, so the old
// failure mode (falling through to the generic
// @ExceptionHandler(Exception.class) catch-all and returning a 500) can no
// longer happen even without this @Order. Kept anyway as a belt: it
// guarantees this handler's specific logging (atError + stack trace for
// AuthServiceException, valuable to diagnose auth-service outages) is always
// the one produced, deterministically, instead of depending on which advice
// bean Spring's ExceptionHandlerExceptionResolver happens to visit first.
// Verified by StaffExceptionHandlerOrderTest. Matches the convention already
// used in salon-service's SalonExceptionHandler (see commit 39ee0dc) and
// auth-service's AuthExceptionHandler.
@Slf4j
@RestControllerAdvice
@Order(0)
public class StaffExceptionHandler {

    @ExceptionHandler(AuthServiceException.class)
    public ProblemDetail handleAuthServiceError(AuthServiceException ex) {
        log.atError().setCause(ex).log("Auth service error");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/auth-service-error"));
        problem.setTitle("Auth Service Error");
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
