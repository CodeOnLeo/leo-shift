package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.AuthorDto;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import io.github.codeonleo.leoshift.util.ColorTagUtil;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final CalendarPatternService calendarPatternService;
    private final ShiftCalculationService calculationService;
    private final ShiftExceptionRepository exceptionRepository;

    @Transactional
    public Optional<DaySchedule> resolveDay(LocalDate date, Calendar calendar) {
        boolean usePattern = calendar.isPatternEnabled();
        boolean patternConfigured = usePattern && calendarPatternService.hasPattern(calendar);
        if (usePattern && !patternConfigured) {
            return Optional.empty();
        }
        String baseCode = null;
        if (usePattern) {
            Optional<CalendarPatternService.ResolvedPattern> effective = calendarPatternService.findEffective(calendar, date);
            if (effective.isPresent()) {
                var pattern = effective.get();
                baseCode = calculationService.determineCode(
                        pattern.codes(),
                        pattern.startDate(),
                        date
                );
            }
        }
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
        AuthorDto author = null;
        if (exception != null && exception.getAuthor() != null) {
            author = new AuthorDto(
                    exception.getAuthor().getId(),
                    exception.getAuthor().getName(),
                    exception.getAuthor().getNickname(),
                    ColorTagUtil.resolve(exception.getAuthor())
            );
        }
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
