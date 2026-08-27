package com.rivoo.salon.application.dto;

import java.util.List;

public record SalonPublicResponse(
        String name,
        String slug,
        String phone,
        String description,
        String logoUrl,
        String primaryColor,
        String addressStreet,
        String addressCity,
        String addressPostalCode,
        List<BusinessHoursResponse> businessHours,
        List<ServicePublicResponse> services,
        List<EmployeePublicResponse> employees,
        // True if either the services or the employees catalogue could not be loaded
        // from staff-service (network error, 5xx, unreadable body...), so the empty/
        // partial lists above are NOT a reliable signal of "this salon has no
        // services/employees" — the frontend should show a transient-error state
        // instead of an empty-catalogue one. Additive field: absent in older clients
        // parsing this response, so it does not break rivoo-frontend/src/types/salon.ts,
        // which does not declare it yet.
        boolean degraded
) {
}
