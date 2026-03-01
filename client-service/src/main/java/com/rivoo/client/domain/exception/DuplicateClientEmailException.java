package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class DuplicateClientEmailException extends RivooException {

    public DuplicateClientEmailException(String email) {
        super("A client with email '" + email + "' already exists in this salon",
                "duplicate-client-email", "Duplicate Client Email", HttpStatus.CONFLICT);
    }
}
