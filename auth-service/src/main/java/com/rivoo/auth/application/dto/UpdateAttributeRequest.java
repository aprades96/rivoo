package com.rivoo.auth.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record UpdateAttributeRequest(
        @NotEmpty Map<String, String> attributes
) {
}
