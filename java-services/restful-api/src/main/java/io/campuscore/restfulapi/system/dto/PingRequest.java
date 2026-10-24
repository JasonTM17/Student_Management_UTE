package io.campuscore.restfulapi.system.dto;

import jakarta.validation.constraints.NotBlank;

public record PingRequest(@NotBlank(message = "message is required") String message) {
}
