package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarDayDto;
import io.github.codeonleo.leoshift.dto.CalendarResponse;
import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final SettingsService settingsService;
    private final ShiftCalculationService calculationService;
    private final ShiftExceptionRepository exceptionRepository;

    public CalendarResponse buildMonthlyCalendar(int year, int month) {
        Optional<UserSettings> maybeSettings = settingsService.findSettings();
        if (maybeSettings.isEmpty() || !settingsService.isPatternConfigured(maybeSettings.get())) {
            return new CalendarResponse(false, year, month, Collections.emptyList(), Collections.emptyMap());
        }
        UserSettings settings = maybeSettings.get();
        List<String> pattern = settingsService.extractPattern(settings);
        LocalDate patternStart = settings.getPatternStartDate();
        LocalDate cursor = LocalDate.of(year, month, 1);
        LocalDate end = cursor.withDayOfMonth(cursor.lengthOfMonth());

        Map<LocalDate, ShiftException> exceptionByDate = exceptionRepository.findByDateBetween(cursor, end).stream()
                .collect(Collectors.toMap(ShiftException::getDate, ex -> ex));

        Map<Integer, List<ShiftException>> yearlyByDay = exceptionRepository.findYearlyEntriesForMonth(month).stream()
                .collect(Collectors.groupingBy(ex -> ex.getDate().getDayOfMonth()));

        List<CalendarDayDto> days = new ArrayList<>();
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("D", 0L);
        summary.put("A", 0L);
        summary.put("N", 0L);
        summary.put("O", 0L);

        while (!cursor.isAfter(end)) {
            ShiftException dayException = exceptionByDate.get(cursor);
            String baseCode = calculationService.determineCode(pattern, patternStart, cursor);
            String effectiveCode = baseCode;
            if (dayException != null && StringUtils.hasText(dayException.getCustomCode())) {
                effectiveCode = dayException.getCustomCode().toUpperCase();
            }
            String memo = dayException != null ? dayException.getMemo() : null;
            List<String> memos = memo != null && !memo.isBlank() ? List.of(memo) : Collections.emptyList();
            List<String> yearlyMemos = yearlyByDay.getOrDefault(cursor.getDayOfMonth(), Collections.emptyList()).stream()
                    .filter(entry -> !entry.getDate().equals(cursor))
                    .map(ShiftException::getMemo)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            days.add(new CalendarDayDto(cursor, baseCode, effectiveCode, memos, yearlyMemos, dayException != null));
            if (effectiveCode != null) {
                summary.merge(effectiveCode, 1L, Long::sum);
            }
            cursor = cursor.plusDays(1);
        }

        return new CalendarResponse(true, year, month, days, summary);
    }
}
