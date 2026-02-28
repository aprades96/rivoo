package com.rivoo.auth.domain.port.in;

import com.rivoo.auth.application.dto.TenantUserResponse;

import java.util.List;

public interface ListTenantUsersUseCase {
    List<TenantUserResponse> listTenantUsers(String tenantId);
}
