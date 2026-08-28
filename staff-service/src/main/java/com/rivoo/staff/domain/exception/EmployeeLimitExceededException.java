package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.PlanLimitExceededException;

public class EmployeeLimitExceededException extends PlanLimitExceededException {

    public EmployeeLimitExceededException(int limit) {
        super("employees", limit);
    }

    /**
     * Authenticated-only by construction: staff-service has no anonymous surface. The single
     * throw site is EmployeeService#create, behind hasRole('SALON_OWNER') - the plan ceiling in
     * the message is the owner's own, and telling them is the point of a 402.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
