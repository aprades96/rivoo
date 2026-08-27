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
        // True if the services list above could not be loaded from staff-service
        // (network error, 5xx, unreadable body...), so an empty `services` list is
        // NOT a reliable signal of "this salon has no services" — the frontend's
        // public-service-step should show a transient-error state instead of an
        // empty-catalogue one. Derived ONLY from the services call: independent of
        // employeesUnavailable below, because the two staff-service calls
        // (services, employees) fail independently and each backs a separate
        // reservation step (public-service-step / public-employee-step). Additive
        // field: absent in older clients parsing this response, so it does not
        // break rivoo-frontend/src/types/salon.ts, which does not declare it yet.
        boolean servicesUnavailable,
        // Mirror of servicesUnavailable, for the employees list and the
        // public-employee-step. Derived ONLY from the employees call.
        boolean employeesUnavailable
) {
}
