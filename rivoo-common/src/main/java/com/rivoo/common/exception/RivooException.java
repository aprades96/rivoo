package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

public abstract class RivooException extends RuntimeException {

    private final String errorType;
    private final String errorTitle;
    private final HttpStatus httpStatus;

    protected RivooException(String message, String errorType, String errorTitle, HttpStatus httpStatus) {
        super(message);
        this.errorType = errorType;
        this.errorTitle = errorTitle;
        this.httpStatus = httpStatus;
    }

    protected RivooException(String message, Throwable cause, String errorType, String errorTitle, HttpStatus httpStatus) {
        super(message, cause);
        this.errorType = errorType;
        this.errorTitle = errorTitle;
        this.httpStatus = httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorTitle() {
        return errorTitle;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
