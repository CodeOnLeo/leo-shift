package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.WeeklyRuleResponse;
import io.github.codeonleo.leoshift.dto.WeeklyRuleUpdateItemRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarWeeklyRule;
import io.github.codeonleo.leoshift.repository.CalendarWeeklyRuleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarWeeklyRuleService {

    private final CalendarWeeklyRuleRepository repository;
    private final ScheduleTypeService scheduleTypeService;

    @Transactional(readOnly = true)
    public boolean hasRules(Calendar calendar) {
        return calendar != null && repository.existsByCalendar(calendar);
    }

    @Transactional(readOnly = true)
    public List<WeeklyRuleResponse> list(Calendar calendar) {
        if (calendar == null) {
            return List.of();
        }
        return repository.findByCalendarOrderByDayOfWeekAsc(calendar).stream()
                .map(rule -> new WeeklyRuleResponse(rule.getDayOfWeek(), rule.getScheduleTypeCode()))
                .toList();
    }

    @Transactional
    public void ensureDefaultGeneralRules(Calendar calendar) {
        if (calendar == null || repository.existsByCalendar(calendar)) {
            return;
        }
        List<CalendarWeeklyRule> defaults = List.of(
                build(calendar, 1, "WORK"),
                build(calendar, 2, "WORK"),
                build(calendar, 3, "WORK"),
                build(calendar, 4, "WORK"),
                build(calendar, 5, "WORK"),
                build(calendar, 6, "OFF"),
                build(calendar, 7, "OFF")
        );
        repository.saveAll(defaults);
    }

    @Transactional
    public List<WeeklyRuleResponse> update(Calendar calendar, List<WeeklyRuleUpdateItemRequest> requests) {
        if (calendar == null) {
            throw new IllegalArgumentException("calendar_required");
        }
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("weekly_rules_required");
        }
        Map<Integer, WeeklyRuleUpdateItemRequest> requestByDay = requests.stream()
                .collect(Collectors.toMap(
                        WeeklyRuleUpdateItemRequest::dayOfWeek,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("duplicate_weekly_rule");
                        },
                        LinkedHashMap::new
                ));

        List<CalendarWeeklyRule> existing = repository.findByCalendarOrderByDayOfWeekAsc(calendar);
        Map<Integer, CalendarWeeklyRule> existingByDay = existing.stream()
                .collect(Collectors.toMap(CalendarWeeklyRule::getDayOfWeek, Function.identity()));

        for (int day = 1; day <= 7; day++) {
            WeeklyRuleUpdateItemRequest request = requestByDay.get(day);
            if (request == null) {
                continue;
            }
            String code = request.scheduleTypeCode().trim().toUpperCase();
            if (!scheduleTypeService.supportsCode(calendar, code)) {
                throw new IllegalArgumentException("invalid_shift_code");
            }
            CalendarWeeklyRule rule = existingByDay.get(day);
            if (rule == null) {
                rule = build(calendar, day, code);
                existing.add(rule);
            } else {
                rule.setScheduleTypeCode(code);
            }
        }

        repository.saveAll(existing);
        return list(calendar);
    }

    @Transactional(readOnly = true)
    public String resolveCode(Calendar calendar, LocalDate date) {
        if (calendar == null || date == null) {
            return null;
        }
        int dayOfWeek = mapDayOfWeek(date.getDayOfWeek());
        return repository.findByCalendarAndDayOfWeek(calendar, dayOfWeek)
                .map(CalendarWeeklyRule::getScheduleTypeCode)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .orElse(null);
    }

    @Transactional
    public void deleteByCalendar(Calendar calendar) {
        if (calendar != null) {
            repository.deleteByCalendar(calendar);
        }
    }

    private CalendarWeeklyRule build(Calendar calendar, int dayOfWeek, String code) {
        return CalendarWeeklyRule.builder()
                .calendar(calendar)
                .dayOfWeek(dayOfWeek)
                .scheduleTypeCode(code)
                .build();
    }

    private int mapDayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }
}
