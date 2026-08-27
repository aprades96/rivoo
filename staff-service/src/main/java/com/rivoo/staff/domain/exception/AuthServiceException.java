package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class AuthServiceException extends RivooException {

    public AuthServiceException(String message) {
        super(message, "auth-service-error", "Auth Service Error", HttpStatus.BAD_GATEWAY);
    }

    public AuthServiceException(String message, Throwable cause) {
        super(message, cause, "auth-service-error", "Auth Service Error", HttpStatus.BAD_GATEWAY);
    }
}
