package com.rivoo.auth.domain.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("User with email '%s' already exists in Keycloak".formatted(email));
    }
}
