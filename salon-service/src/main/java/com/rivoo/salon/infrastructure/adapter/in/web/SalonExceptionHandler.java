package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.exception.SlugAlreadyExistsException;
import com.rivoo.common.exception.RivooException;
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

// Redundant since the four exceptions below now extend RivooException:
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

    // Both handlers below serve the ANONYMOUS POST /api/v1/salons. ex.getMessage() is built by
    // the outbound adapters and names the dependency and the tenant, and its cause chain carries
    // the full request URL — internal host and port included. Putting it in `detail` published
    // that internal topology to an unauthenticated caller. The message the client gets is now a
    // fixed string per failure class; the real one moves to the log, where it belongs, together
    // with the cause (stack trace + the ResourceAccessException that names the URL).
    private static final String DEPENDENCY_UNAVAILABLE_DETAIL =
            "Salon registration is temporarily unavailable. Please try again in a few minutes.";
    private static final String DEPENDENCY_REJECTED_DETAIL =
            "Salon registration could not be completed. Please review the details provided and try again.";

    @ExceptionHandler(AuthServiceException.class)
    public ProblemDetail handleAuthServiceError(AuthServiceException ex) {
        return handleOnboardingDependencyError(ex, "auth-service", "Auth service error");
    }

    @ExceptionHandler(BillingServiceException.class)
    public ProblemDetail handleBillingServiceError(BillingServiceException ex) {
        // Dedicated handler rather than GlobalExceptionHandler.handleRivooException for the same
        // reason AuthServiceException has one: these exceptions DO carry a cause (see the two
        // adapters, which wrap the original failure), and that handler's generic atWarn (no
        // setCause) would silently drop the stack trace, leaving an outage diagnosable only from
        // a one-line WARN with no cause chain. It would also publish ex.getMessage() as `detail`.
        return handleOnboardingDependencyError(ex, "billing-service", "Billing service error");
    }

    /**
     * Status, type and title come from the exception itself: the adapters classify a dependency
     * 4xx ("it answered and said no") as 422 and only a 5xx / unreachable / unusable response as
     * 502, so hardcoding BAD_GATEWAY here — as this method used to — would silently undo that
     * classification and re-flatten every business rejection into a false "upstream is broken".
     */
    private ProblemDetail handleOnboardingDependencyError(RivooException ex, String dependency, String logMessage) {
        boolean rejectedByDependency = ex.getHttpStatus().is4xxClientError();

        if (rejectedByDependency) {
            // A dependency refusing a request is not an infrastructure incident: logging it at
            // ERROR is the false alarm this whole change exists to remove. The adapter already
            // logged the upstream status at ERROR at the point where it could still see it.
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("dependency", dependency)
                    .addKeyValue("upstreamOutcome", "rejected")
                    .addKeyValue("internalDetail", ex.getMessage())
                    .log(logMessage);
        } else {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("dependency", dependency)
                    .addKeyValue("upstreamOutcome", "unavailable")
                    .addKeyValue("internalDetail", ex.getMessage())
                    .log(logMessage);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(),
                rejectedByDependency ? DEPENDENCY_REJECTED_DETAIL : DEPENDENCY_UNAVAILABLE_DETAIL);
        problem.setType(URI.create("https://rivoo.com/errors/" + ex.getErrorType()));
        problem.setTitle(ex.getErrorTitle());
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
