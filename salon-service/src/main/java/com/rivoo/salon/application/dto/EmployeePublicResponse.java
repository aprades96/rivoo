package com.rivoo.salon.application.dto;

import java.util.List;

public record EmployeePublicResponse(
        String id,
        String firstName,
        String lastName,
        String jobTitle,
        List<String> serviceIds
) {
}
