package com.rivoo.billing.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSubscriptionStatusRequest(
        @NotBlank String status
) {
}
