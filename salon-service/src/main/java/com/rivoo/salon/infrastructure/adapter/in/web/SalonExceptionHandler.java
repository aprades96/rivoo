package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.exception.EmailAlreadyInUseException;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.exception.SlugAlreadyExistsException;
import com.rivoo.common.web.RivooErrorTypes;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

// Redundant since the five exceptions below now extend RivooException:
// GlobalExceptionHandler.handleRivooException(RivooException) matches them
// at depth 1 regardless of which advice bean Spring visits first, so the
// old failure mode (falling through to the generic
// @ExceptionHandler(Exception.class) catch-all and returning a 500) can no
// longer happen even without this @Order. Kept anyway as a belt: it
// guarantees this handler's specific ProblemDetail bodies/logging (e.g. the
// atError+stack-trace log for AuthServiceException) are always the ones
// produced, deterministically, instead of depending on which advice bean
// Spring's ExceptionHandlerExceptionResolver happens to visit first.
// Verified by SalonExceptionHandlerOrderTest, which stays green even with
// this annotation removed (see the class extending RivooException). Matches
// the convention already used in auth-service's AuthExceptionHandler.
@Slf4j
@RestControllerAdvice
@Order(0)
public class SalonExceptionHandler {

    @ExceptionHandler(SalonNotFoundException.class)
    public ProblemDetail handleSalonNotFound(SalonNotFoundException ex) {
        log.atWarn().addKeyValue("detail", ex.getMessage()).log("Salon not found");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        // Shared with appointment-service's SalonServiceAdapter, which parses this exact value
        // to recognize a genuine "no salon for this slug" 404 (see Bloque 3 / RivooErrorTypes).
        problem.setType(URI.create(RivooErrorTypes.SALON_NOT_FOUND));
        problem.setTitle("Salon Not Found");
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        log.atWarn().addKeyValue("detail", ex.getMessage()).log("Email conflict");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/email-already-in-use"));
        problem.setTitle("Email Already In Use");
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(AuthServiceException.class)
    public ProblemDetail handleAuthServiceError(AuthServiceException ex) {
        log.atError().setCause(ex).log("Auth service error");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/auth-service-error"));
        problem.setTitle("Auth Service Error");
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(BillingServiceException.class)
    public ProblemDetail handleBillingServiceError(BillingServiceException ex) {
        // Same class of failure as AuthServiceException above (an external dependency down
        // during salon onboarding) and it DOES carry a cause (see BillingServiceAdapter,
        // which wraps the original exception) — GlobalExceptionHandler.handleRivooException's
        // generic atWarn (no setCause) would silently drop that stack trace, leaving a
        // billing-service outage diagnosable only from a one-line WARN with no cause chain.
        log.atError().setCause(ex).log("Billing service error");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/billing-service-error"));
        problem.setTitle("Billing Service Error");
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(SlugAlreadyExistsException.class)
    public ProblemDetail handleSlugAlreadyExists(SlugAlreadyExistsException ex) {
        log.atWarn().addKeyValue("detail", ex.getMessage()).log("Slug conflict");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/slug-already-exists"));
        problem.setTitle("Slug Already Exists");
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
