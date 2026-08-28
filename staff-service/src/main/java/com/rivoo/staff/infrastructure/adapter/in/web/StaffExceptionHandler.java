package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.staff.domain.exception.AuthServiceException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
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

    /**
     * Status, type and title come from the exception itself. {@code AuthServiceAdapter}
     * classifies a dependency 4xx ("auth-service answered and said no") as 422 and only a 5xx /
     * unreachable / unusable response as 502, so hardcoding {@code BAD_GATEWAY} here — as this
     * method used to — would silently undo that classification and re-flatten every business
     * rejection into a false "the upstream is broken". Fixing only the adapter would have
     * achieved nothing while this handler stayed.
     * <p>
     * {@code detail} stays {@code ex.getMessage()}: unlike salon-service's anonymous
     * {@code POST /api/v1/salons}, every route that can raise this exception here is behind
     * {@code hasRole('SALON_OWNER')}, so the tenant named in the message is the caller's own.
     */
    @ExceptionHandler(AuthServiceException.class)
    public ProblemDetail handleAuthServiceError(AuthServiceException ex) {
        if (ex.getHttpStatus().is4xxClientError()) {
            // auth-service refusing a request is not an infrastructure incident: logging it at
            // ERROR is the false alarm this handler used to raise on every 4xx. The adapter
            // already logged the upstream status at ERROR where it could still see it.
            log.atWarn().setCause(ex).addKeyValue("upstreamOutcome", "rejected").log("Auth service error");
        } else {
            log.atError().setCause(ex).addKeyValue("upstreamOutcome", "unavailable").log("Auth service error");
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
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
