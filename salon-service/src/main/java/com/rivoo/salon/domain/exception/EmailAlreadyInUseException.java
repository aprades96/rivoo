package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyInUseException extends RivooException {

    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email, "email-already-in-use", "Email Already In Use", HttpStatus.CONFLICT);
    }
}
