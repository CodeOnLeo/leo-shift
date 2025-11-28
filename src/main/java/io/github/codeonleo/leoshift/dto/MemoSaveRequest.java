package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;

public record MemoSaveRequest(
        @NotBlank(message = "메모 내용을 입력해주세요.")
        String memo
) {
}
