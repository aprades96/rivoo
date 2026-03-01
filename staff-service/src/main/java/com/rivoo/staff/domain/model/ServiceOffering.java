package com.rivoo.staff.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOffering {

    private Long id;
    private String externalId;
    private String tenantId;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
