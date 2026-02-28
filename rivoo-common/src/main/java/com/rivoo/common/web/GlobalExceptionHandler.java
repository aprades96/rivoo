package com.rivoo.common.web;

import com.rivoo.common.exception.BusinessValidationException;
import com.rivoo.common.exception.PlanLimitExceededException;
import com.rivoo.common.exception.ResourceNotFoundException;
import com.rivoo.common.exception.TenantMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/resource-not-found"));
        problem.setTitle("Resource Not Found");
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(TenantMismatchException.class)
    public ProblemDetail handleTenantMismatch(TenantMismatchException ex) {
        log.warn("Tenant mismatch: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/tenant-mismatch"));
        problem.setTitle("Tenant Mismatch");
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ProblemDetail handlePlanLimitExceeded(PlanLimitExceededException ex) {
        log.warn("Plan limit exceeded: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/plan-limit-exceeded"));
        problem.setTitle("Plan Limit Exceeded");
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ProblemDetail handleBusinessValidation(BusinessValidationException ex) {
        log.warn("Business validation failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/business-validation"));
        problem.setTitle("Business Validation Failed");
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
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
