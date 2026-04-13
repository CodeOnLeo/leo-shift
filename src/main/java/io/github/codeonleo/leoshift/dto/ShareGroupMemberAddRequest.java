package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ShareGroupMemberAddRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email
) {
}
