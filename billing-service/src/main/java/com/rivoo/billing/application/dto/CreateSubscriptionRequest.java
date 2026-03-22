package com.rivoo.billing.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSubscriptionRequest(
        @NotBlank String tenantId,
        @NotBlank String ownerEmail,
        @NotBlank String salonName
) {
}
