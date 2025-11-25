package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.AuthorDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record DaySchedule(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        String memo,
        String anniversaryMemo,
        boolean repeatYearly,
        List<String> yearlyMemos,
        AuthorDto author,
        LocalDateTime updatedAt
) {

    public List<String> combinedMemos() {
        List<String> memos = new java.util.ArrayList<>();
        if (memo != null && !memo.isBlank()) {
            memos.add(memo);
        }
        if (anniversaryMemo != null && !anniversaryMemo.isBlank()) {
            memos.add(anniversaryMemo);
        }
        if (yearlyMemos != null) {
            memos.addAll(yearlyMemos);
        }
        return memos;
    }

    public static DaySchedule empty(LocalDate date) {
        return new DaySchedule(date, null, null, null, null, false, Collections.emptyList(), null, null);
    }
}
