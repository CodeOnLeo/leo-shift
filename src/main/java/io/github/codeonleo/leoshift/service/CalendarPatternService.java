package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.repository.CalendarPatternRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarPatternService {

    private static final List<String> ALLOWED_CODES = List.of("D", "A", "N", "V", "O");

    private final CalendarPatternRepository repository;

    public Optional<CalendarPattern> findLatest(Calendar calendar) {
        return repository.findTopByCalendarOrderByPatternStartDateDesc(calendar);
    }

    public Optional<CalendarPattern> findEffective(Calendar calendar, LocalDate date) {
        return repository.findTopByCalendarAndPatternStartDateLessThanEqualOrderByPatternStartDateDesc(calendar, date);
    }

    public List<CalendarPattern> findAll(Calendar calendar) {
        return repository.findByCalendarOrderByPatternStartDateAsc(calendar);
    }

    public boolean hasPattern(Calendar calendar) {
        return findLatest(calendar).isPresent();
    }

    @Transactional
    public CalendarPattern savePattern(Calendar calendar, List<String> rawPattern, LocalDate startDate, boolean applyRetroactive) {
        if (calendar == null) {
            throw new IllegalArgumentException("calendar_required");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("pattern_start_required");
        }
        List<String> pattern = normalizePattern(rawPattern);
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern_required");
        }
        if (applyRetroactive) {
            repository.deleteByCalendar(calendar);
        }
        CalendarPattern patternEntity = CalendarPattern.builder()
                .calendar(calendar)
                .patternCodes(String.join(",", pattern))
                .patternStartDate(startDate)
                .build();
        return repository.save(patternEntity);
    }

    @Transactional
    public void deleteByCalendar(Calendar calendar) {
        repository.deleteByCalendar(calendar);
    }

    public List<String> extractPattern(CalendarPattern pattern) {
        if (pattern == null || !StringUtils.hasText(pattern.getPatternCodes())) {
            return Collections.emptyList();
        }
        return normalizePattern(List.of(pattern.getPatternCodes().split(",")));
    }

    private List<String> normalizePattern(List<String> pattern) {
        if (pattern == null) {
            return Collections.emptyList();
        }
        return pattern.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .peek(code -> {
                    if (!ALLOWED_CODES.contains(code)) {
                        throw new IllegalArgumentException("invalid_shift_code");
                    }
                })
                .collect(Collectors.toList());
    }
}
