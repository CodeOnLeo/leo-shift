package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Pattern;

public record ColorUpdateRequest(
        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "invalid_color")
        String color
) {
}
