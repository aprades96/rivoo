package com.rivoo.billing.application.dto;

public record CheckoutResponse(
        String checkoutUrl,
        String sessionId
) {
}
