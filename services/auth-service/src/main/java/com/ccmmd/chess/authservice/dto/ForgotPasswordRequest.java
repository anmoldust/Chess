package com.ccmmd.chess.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 190) String email
) {
}
