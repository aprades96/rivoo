package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

public record EmployeeInternalDto(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        boolean active
) {
    public String fullName() {
        return firstName + " " + lastName;
    }
}
