package com.rivoo.admin.infrastructure.adapter.out.rest.dto;

import java.time.Instant;

public record SalonAdminDto(
        String id,
        String name,
        String slug,
        String email,
        String phone,
        String status,
        Instant createdAt
) {
}
