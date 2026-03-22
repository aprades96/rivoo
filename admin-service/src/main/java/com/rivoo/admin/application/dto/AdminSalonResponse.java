package com.rivoo.admin.application.dto;

import java.time.Instant;

public record AdminSalonResponse(
        String id,
        String name,
        String slug,
        String email,
        String status,
        Instant createdAt
) {
}
