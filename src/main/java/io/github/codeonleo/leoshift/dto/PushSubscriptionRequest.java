package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;

public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        Keys keys
) {

    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }
}
