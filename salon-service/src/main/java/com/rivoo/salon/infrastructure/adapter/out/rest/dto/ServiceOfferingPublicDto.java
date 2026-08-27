package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

import java.math.BigDecimal;

public record ServiceOfferingPublicDto(String id, String name, String description,
                                       int durationMinutes, BigDecimal price, String currency) {}
