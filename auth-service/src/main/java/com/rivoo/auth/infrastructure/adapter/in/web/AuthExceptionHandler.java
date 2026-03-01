package com.rivoo.auth.infrastructure.adapter.in.web;

import com.rivoo.auth.domain.exception.KeycloakOperationException;
import com.rivoo.auth.domain.exception.UserAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
@Order(0)
public class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/user-already-exists"));
        problem.setTitle("User Already Exists");
        addCommonProperties(problem);
        return problem;
    }

    @ExceptionHandler(KeycloakOperationException.class)
    public ProblemDetail handleKeycloakOperation(KeycloakOperationException ex) {
        log.error("Keycloak operation failed: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setType(URI.create("https://rivoo.com/errors/keycloak-operation-failed"));
        problem.setTitle("Keycloak Operation Failed");
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
