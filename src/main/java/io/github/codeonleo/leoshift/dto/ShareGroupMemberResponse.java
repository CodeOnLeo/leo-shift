package io.github.codeonleo.leoshift.dto;

public record ShareGroupMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail
) {
}
