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
// default to an advice bean that declares no @Order) so that any per-service
// @RestControllerAdvice is guaranteed to be considered before this one,
// without relying on the unspecified tie-break Spring's
// AnnotationAwareOrderComparator applies between two beans that are both
// implicitly LOWEST_PRECEDENCE. This declaration does not change today's
// runtime behavior in any service (the value is identical to the previous
// default); it only makes the guarantee explicit instead of implicit.
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
