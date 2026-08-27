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
        List<ServicePublicDto> services,
        List<EmployeePublicDto> employees
) {
}
