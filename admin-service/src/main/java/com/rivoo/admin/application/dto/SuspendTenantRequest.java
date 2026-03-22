package com.rivoo.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SuspendTenantRequest(
        @NotBlank
        @Pattern(regexp = "SUSPENDED|ACTIVE", message = "status must be SUSPENDED or ACTIVE")
        String status
) {
}
