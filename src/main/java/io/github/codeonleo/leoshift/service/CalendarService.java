package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarDayDto;
import io.github.codeonleo.leoshift.dto.CalendarResponse;
import io.github.codeonleo.leoshift.dto.AuthorDto;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import io.github.codeonleo.leoshift.util.ColorTagUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarPatternService calendarPatternService;
    private final ShiftCalculationService calculationService;
    private final ShiftExceptionRepository exceptionRepository;

    public CalendarResponse buildMonthlyCalendar(Calendar calendar, int year, int month) {
        boolean usePattern = calendar.isPatternEnabled();
        boolean patternConfigured = usePattern && calendarPatternService.hasPattern(calendar);
        if (usePattern && !patternConfigured) {
            return new CalendarResponse(false, year, month, Collections.emptyList(), Collections.emptyMap());
        }
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        // 달력 시작: 이전 달의 날짜들 포함 (6주 = 42일 고정)
        int firstDayOfWeek = monthStart.getDayOfWeek().getValue() % 7; // 0=Sun, 1=Mon, ...
        LocalDate calendarStart = monthStart.minusDays(firstDayOfWeek);
        LocalDate calendarEnd = calendarStart.plusDays(41); // 6주 = 42일

        // Exception 데이터 조회 범위 확장
        Map<LocalDate, ShiftException> exceptionByDate = exceptionRepository.findByCalendarAndDateBetween(calendar, calendarStart, calendarEnd).stream()
                .collect(Collectors.toMap(ShiftException::getDate, ex -> ex));

        Map<Integer, List<ShiftException>> yearlyByDay = exceptionRepository.findYearlyEntriesForMonth(calendar, month).stream()
                .collect(Collectors.groupingBy(ex -> ex.getDate().getDayOfMonth()));

        // 패턴을 한 번만 조회 (N+1 문제 해결)
        List<String> patternCodes = null;
        LocalDate patternStartDate = null;
        if (usePattern) {
            var resolved = calendarPatternService.findEffective(calendar, calendarEnd);
            if (resolved.isPresent()) {
                var pattern = resolved.get();
                patternCodes = pattern.codes();
                patternStartDate = pattern.startDate();
            }
        }

        List<CalendarDayDto> days = new ArrayList<>();
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("D", 0L);
        summary.put("A", 0L);
        summary.put("N", 0L);
        summary.put("O", 0L);

        LocalDate cursor = calendarStart;
        while (!cursor.isAfter(calendarEnd)) {
            ShiftException dayException = exceptionByDate.get(cursor);
            String baseCode = null;
            if (usePattern && patternCodes != null && patternStartDate != null) {
                baseCode = calculationService.determineCode(
                        patternCodes,
                        patternStartDate,
                        cursor
                );
            }
            String effectiveCode = baseCode;
            if (dayException != null && StringUtils.hasText(dayException.getCustomCode())) {
                effectiveCode = dayException.getCustomCode().toUpperCase();
            }
            String memo = dayException != null ? dayException.getMemo() : null;
            String anniversaryMemo = dayException != null ? dayException.getAnniversaryMemo() : null;

            // 일반 메모
            List<String> memos = memo != null && !memo.isBlank() ? List.of(memo) : Collections.emptyList();

            // 기념일 메모 (당일)
            List<String> anniversaryMemos = anniversaryMemo != null && !anniversaryMemo.isBlank() ? List.of(anniversaryMemo) : Collections.emptyList();

            final LocalDate cursorDate = cursor;

            // yearlyMemos는 현재 조회 월에 속한 날짜에만 표시 (anniversaryMemo 사용)
            // 등록 날짜 이후만 표시
            List<String> yearlyMemos = Collections.emptyList();
            if (cursor.getMonthValue() == month) {
                yearlyMemos = yearlyByDay.getOrDefault(cursor.getDayOfMonth(), Collections.emptyList()).stream()
                        .filter(entry -> !entry.getDate().equals(cursorDate))
                        .filter(entry -> !entry.getDate().isAfter(cursorDate)) // 등록 날짜 이후만 표시
                        .map(ShiftException::getAnniversaryMemo)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList();
            }
            AuthorDto author = null;
            if (dayException != null && dayException.getAuthor() != null) {
                author = new AuthorDto(
                        dayException.getAuthor().getId(),
                        dayException.getAuthor().getName(),
                        ColorTagUtil.resolve(dayException.getAuthor())
                );
            }
            days.add(new CalendarDayDto(cursor, baseCode, effectiveCode, memos, anniversaryMemos, yearlyMemos, dayException != null, author, dayException != null ? dayException.getUpdatedAt() : null));
            // summary는 현재 월의 날짜만 카운트
            if (effectiveCode != null && !cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd)) {
                summary.merge(effectiveCode, 1L, Long::sum);
            }
            cursor = cursor.plusDays(1);
        }

        return new CalendarResponse(true, year, month, days, summary);
    }
}
