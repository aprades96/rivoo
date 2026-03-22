package com.rivoo.billing.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String planName,
        String successUrl,
        String cancelUrl
) {
}
