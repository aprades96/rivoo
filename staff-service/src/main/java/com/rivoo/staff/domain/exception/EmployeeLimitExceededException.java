package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.PlanLimitExceededException;

public class EmployeeLimitExceededException extends PlanLimitExceededException {

    public EmployeeLimitExceededException(int limit) {
        super("employees", limit);
    }
}
