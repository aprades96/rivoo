package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.exception.EmailAlreadyInUseException;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.exception.SlugAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class SalonExceptionHandler {

    @ExceptionHandler(SalonNotFoundException.class)
    public ProblemDetail handleSalonNotFound(SalonNotFoundException ex) {
        log.atWarn().addKeyValue("detail", ex.getMessage()).log("Salon not found");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/salon-not-found"));
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
