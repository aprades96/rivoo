package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class SalonNotFoundException extends RivooException {

    public SalonNotFoundException(String identifier) {
        super("Salon not found: " + identifier, "salon-not-found", "Salon Not Found", HttpStatus.NOT_FOUND);
    }
}
