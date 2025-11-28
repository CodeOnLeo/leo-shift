package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemoDto(
        Long id,
        LocalDate date,
        String memo,
        AuthorDto author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isOwn  // 현재 로그인한 사용자의 메모인지 여부
) {
}
