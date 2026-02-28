package com.rivoo.common.exception;

public class PlanLimitExceededException extends RuntimeException {

    public PlanLimitExceededException(String message) {
        super(message);
    }

    public PlanLimitExceededException(String resource, int limit) {
        super("Plan limit exceeded: maximum %d %s allowed".formatted(limit, resource));
    }
}
