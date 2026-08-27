package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class BillingServiceException extends RivooException {

    public BillingServiceException(String message) {
        super(message, "billing-service-error", "Billing Service Error", HttpStatus.BAD_GATEWAY);
    }

    public BillingServiceException(String message, Throwable cause) {
        super(message, cause, "billing-service-error", "Billing Service Error", HttpStatus.BAD_GATEWAY);
    }
}
