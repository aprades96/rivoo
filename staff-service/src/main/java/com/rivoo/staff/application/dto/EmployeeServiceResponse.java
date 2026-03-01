package com.rivoo.staff.application.dto;

import java.math.BigDecimal;

public record EmployeeServiceResponse(
        String serviceId,
        String serviceName,
        int effectiveDuration,
        BigDecimal effectivePrice,
        Integer customDuration,
        BigDecimal customPrice
) {
}
