package com.rivoo.staff.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record AssignServicesRequest(
        @NotEmpty @Valid List<ServiceAssignment> services
) {
    public record ServiceAssignment(
            @jakarta.validation.constraints.NotBlank String serviceId,
            Integer customDuration,
            BigDecimal customPrice
    ) {
    }
}
