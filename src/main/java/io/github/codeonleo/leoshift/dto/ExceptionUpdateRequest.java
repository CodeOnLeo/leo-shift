package io.github.codeonleo.leoshift.dto;

public record ExceptionUpdateRequest(
        String customCode,
        String memo,
        boolean repeatYearly
) {
}
