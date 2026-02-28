package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
        @NotBlank String status
) {
}
