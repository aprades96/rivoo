package com.rivoo.staff.domain.port.in;

public interface DeactivateEmployeeUseCase {

    void deactivate(String tenantId, String externalId);
}
