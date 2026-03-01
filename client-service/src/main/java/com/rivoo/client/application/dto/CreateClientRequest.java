package com.rivoo.client.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email String email,
        @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$") String phone,
        String gender,
        String source,
        @Size(max = 2000) String notes
) {
}
