package io.github.codeonleo.leoshift.dto;

public record ExceptionUpdateRequest(
        String customCode,
        String memo,
        String anniversaryMemo,
        boolean repeatYearly
) {
}
