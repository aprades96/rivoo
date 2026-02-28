package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

public record RegisterOwnerRequest(
        String tenantId,
        String email,
        String password,
        String firstName,
        String lastName,
        String salonName,
        String subscriptionPlan
) {
}
