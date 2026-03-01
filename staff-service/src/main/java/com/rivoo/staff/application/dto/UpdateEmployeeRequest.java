package com.rivoo.staff.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email String email,
        @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$") String phone,
        String role
) {
}
