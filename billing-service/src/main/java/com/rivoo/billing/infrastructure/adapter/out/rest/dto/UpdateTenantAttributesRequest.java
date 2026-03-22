package com.rivoo.billing.infrastructure.adapter.out.rest.dto;

import java.util.Map;

public record UpdateTenantAttributesRequest(Map<String, String> attributes) {
}
