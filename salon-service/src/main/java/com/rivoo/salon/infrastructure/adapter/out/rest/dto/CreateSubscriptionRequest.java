package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

public record CreateSubscriptionRequest(
        String tenantId,
        String ownerEmail,
        String salonName
) {
}
