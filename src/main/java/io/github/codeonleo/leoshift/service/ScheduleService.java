package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final SettingsService settingsService;
    private final ShiftCalculationService calculationService;
    private final ShiftExceptionRepository exceptionRepository;

    @Transactional
    public Optional<DaySchedule> resolveDay(LocalDate date) {
        Optional<UserSettings> maybeSettings = settingsService.findSettings();
        if (maybeSettings.isEmpty() || !settingsService.isPatternConfigured(maybeSettings.get())) {
            return Optional.empty();
        }
        UserSettings settings = maybeSettings.get();
        List<String> pattern = settingsService.extractPattern(settings);
        String baseCode = calculationService.determineCode(pattern, settings.getPatternStartDate(), date);
        ShiftException exception = exceptionRepository.findByDate(date).orElse(null);
        String memo = exception != null ? exception.getMemo() : null;
        String anniversaryMemo = exception != null ? exception.getAnniversaryMemo() : null;
        boolean repeatYearly = exception != null && exception.isRepeatYearly();
        String effective = baseCode;
        if (exception != null && StringUtils.hasText(exception.getCustomCode())) {
            effective = exception.getCustomCode().toUpperCase();
        }
        List<String> yearlyMemos = exceptionRepository.findYearlyEntriesForMonth(date.getMonthValue()).stream()
                .filter(e -> e.getDate().getDayOfMonth() == date.getDayOfMonth())
                .filter(e -> !e.getDate().equals(date))
                .filter(e -> !e.getDate().isAfter(date)) // 등록 날짜 이후만 표시
                .map(ShiftException::getAnniversaryMemo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return Optional.of(new DaySchedule(date, baseCode, effective, memo, anniversaryMemo, repeatYearly, yearlyMemos));
    }

    @Transactional
    public List<DaySchedule> resolveRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return Collections.emptyList();
        }
        return start.datesUntil(end.plusDays(1))
                .map(this::resolveDay)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
