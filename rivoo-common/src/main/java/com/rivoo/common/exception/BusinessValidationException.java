package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessValidationException extends RivooException {

    public BusinessValidationException(String message) {
        super(message, "business-validation", "Business Validation Failed", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
