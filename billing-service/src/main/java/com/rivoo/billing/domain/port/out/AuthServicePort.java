package com.rivoo.billing.domain.port.out;

import java.util.Map;

public interface AuthServicePort {

    void updateTenantAttributes(String tenantId, Map<String, String> attributes);
}
