package com.rivoo.staff.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * The field must be present but may be an empty list (D16b): this endpoint
 * replaces the employee's whole service assignment set, so an empty list is a
 * legitimate "unassign everything" request, not a malformed one. A
 * {@code @NotEmpty} here previously made it impossible for the UI to remove an
 * employee's last remaining service.
 */
public record AssignServicesRequest(
        @NotNull @Valid List<ServiceAssignment> services
) {
    public record ServiceAssignment(
            @jakarta.validation.constraints.NotBlank String serviceId,
            Integer customDuration,
            BigDecimal customPrice
    ) {
    }
}
