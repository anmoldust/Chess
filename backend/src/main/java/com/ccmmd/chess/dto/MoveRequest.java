package com.ccmmd.chess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MoveRequest(
        @NotBlank @Size(min = 2, max = 2) String from,
        @NotBlank @Size(min = 2, max = 2) String to,
        @Size(max = 1) String promotion
) {
}
