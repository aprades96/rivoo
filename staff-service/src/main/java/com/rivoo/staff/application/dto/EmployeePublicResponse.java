package com.rivoo.staff.application.dto;

import java.util.List;

/**
 * Employee as seen by an anonymous visitor on the booking page.
 * Does NOT include email or phone: those are the employee's personal data.
 */
public record EmployeePublicResponse(
        String id,
        String firstName,
        String lastName,
        String jobTitle,
        List<String> serviceIds
) {
}
