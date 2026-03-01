package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

public class PlanLimitExceededException extends RivooException {

    public PlanLimitExceededException(String message) {
        super(message, "plan-limit-exceeded", "Plan Limit Exceeded", HttpStatus.PAYMENT_REQUIRED);
    }

    public PlanLimitExceededException(String resource, int limit) {
        super("Plan limit exceeded: maximum %d %s allowed".formatted(limit, resource),
                "plan-limit-exceeded", "Plan Limit Exceeded", HttpStatus.PAYMENT_REQUIRED);
    }
}
