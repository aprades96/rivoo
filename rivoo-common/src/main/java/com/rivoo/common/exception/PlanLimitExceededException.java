package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately: it is a shared base class, so publishing
 * here would be inherited by every future subtype. {@code AppointmentLimitExceededException}
 * extends it and IS reachable anonymously, where the plan ceiling in the message reveals the
 * salon's plan tier. Subtypes reachable only from authenticated endpoints (e.g.
 * {@code EmployeeLimitExceededException}) opt in individually.
 */
public class PlanLimitExceededException extends RivooException {

    public PlanLimitExceededException(String message) {
        super(message, "plan-limit-exceeded", "Plan Limit Exceeded", HttpStatus.PAYMENT_REQUIRED);
    }

    public PlanLimitExceededException(String resource, int limit) {
        super("Plan limit exceeded: maximum %d %s allowed".formatted(limit, resource),
                "plan-limit-exceeded", "Plan Limit Exceeded", HttpStatus.PAYMENT_REQUIRED);
    }
}
