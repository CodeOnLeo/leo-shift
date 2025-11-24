package io.github.codeonleo.leoshift.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record DaySchedule(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        String memo,
        boolean repeatYearly,
        List<String> yearlyMemos
) {

    public List<String> combinedMemos() {
        List<String> memos = new java.util.ArrayList<>();
        if (memo != null && !memo.isBlank()) {
            memos.add(memo);
        }
        if (yearlyMemos != null) {
            memos.addAll(yearlyMemos);
        }
        return memos;
    }

    public static DaySchedule empty(LocalDate date) {
        return new DaySchedule(date, null, null, null, false, Collections.emptyList());
    }
}
