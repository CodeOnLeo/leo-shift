package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.Calendar;
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
import io.github.codeonleo.leoshift.dto.AuthorDto;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final SettingsService settingsService;
    private final ShiftCalculationService calculationService;
    private final ShiftExceptionRepository exceptionRepository;

    @Transactional
    public Optional<DaySchedule> resolveDay(LocalDate date, Calendar calendar) {
        Optional<UserSettings> maybeSettings = settingsService.findSettings(calendar.getOwner());
        boolean patternConfigured = maybeSettings.isPresent() && settingsService.isPatternConfigured(maybeSettings.get());
        boolean usePattern = calendar.isPatternEnabled();
        if (usePattern && !patternConfigured) {
            return Optional.empty();
        }
        UserSettings settings = maybeSettings.orElse(null);
        List<String> pattern = usePattern && settings != null ? settingsService.extractPattern(settings) : List.of();
        String baseCode = usePattern ? calculationService.determineCode(pattern, settings.getPatternStartDate(), date) : null;
        ShiftException exception = exceptionRepository.findByCalendarAndDate(calendar, date).orElse(null);
        String memo = exception != null ? exception.getMemo() : null;
        String anniversaryMemo = exception != null ? exception.getAnniversaryMemo() : null;
        boolean repeatYearly = exception != null && exception.isRepeatYearly();
        String effective = baseCode;
        if (exception != null && StringUtils.hasText(exception.getCustomCode())) {
            effective = exception.getCustomCode().toUpperCase();
        }
        if (!usePattern && exception == null) {
            return Optional.of(DaySchedule.empty(date));
        }
        List<String> yearlyMemos = exceptionRepository.findYearlyEntriesForMonth(calendar, date.getMonthValue()).stream()
                .filter(e -> e.getDate().getDayOfMonth() == date.getDayOfMonth())
                .filter(e -> !e.getDate().equals(date))
                .filter(e -> !e.getDate().isAfter(date)) // 등록 날짜 이후만 표시
                .map(ShiftException::getAnniversaryMemo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        AuthorDto author = exception != null && exception.getAuthor() != null
                ? new AuthorDto(exception.getAuthor().getId(), exception.getAuthor().getName())
                : null;
        return Optional.of(new DaySchedule(date, baseCode, effective, memo, anniversaryMemo, repeatYearly, yearlyMemos, author, exception != null ? exception.getUpdatedAt() : null));
    }

    @Transactional
    public List<DaySchedule> resolveRange(LocalDate start, LocalDate end, Calendar calendar) {
        if (end.isBefore(start)) {
            return Collections.emptyList();
        }
        return start.datesUntil(end.plusDays(1))
                .map(date -> resolveDay(date, calendar))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
