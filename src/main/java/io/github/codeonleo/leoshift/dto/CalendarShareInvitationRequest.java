package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarShareInvitationRequest(
        @NotBlank @Email String email,
        @NotNull CalendarShare.Permission permission
) {
}
