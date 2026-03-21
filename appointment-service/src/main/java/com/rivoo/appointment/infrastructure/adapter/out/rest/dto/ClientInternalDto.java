package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

public record ClientInternalDto(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean active
) {
    public String fullName() {
        return firstName + " " + lastName;
    }
}
