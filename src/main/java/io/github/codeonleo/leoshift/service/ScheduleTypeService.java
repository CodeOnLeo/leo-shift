package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.ScheduleTypeResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ScheduleType;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScheduleTypeService {

    private static final Map<String, LegacyScheduleType> LEGACY_DEFAULTS = createLegacyDefaults();

    private final ScheduleTypeRepository scheduleTypeRepository;

    @Transactional
    public void ensureDefaults(Calendar calendar) {
        if (calendar == null || scheduleTypeRepository.existsByCalendar(calendar)) {
            return;
        }
        List<ScheduleType> defaults = LEGACY_DEFAULTS.values().stream()
                .map(definition -> ScheduleType.builder()
                        .calendar(calendar)
                        .code(definition.code())
                        .name(definition.name())
                        .color(definition.color())
                        .startTime(definition.startTime())
                        .endTime(definition.endTime())
                        .countsAsWork(definition.countsAsWork())
                        .sortOrder(definition.sortOrder())
                        .defaultOff(definition.defaultOff())
                        .build())
                .toList();
        scheduleTypeRepository.saveAll(defaults);
    }

    @Transactional(readOnly = true)
    public List<ScheduleTypeResponse> listForCalendar(Calendar calendar) {
        if (calendar == null) {
            return List.of();
        }
        List<ScheduleType> types = scheduleTypeRepository.findByCalendarOrderBySortOrderAscCodeAsc(calendar);
        if (types.isEmpty()) {
            return LEGACY_DEFAULTS.values().stream()
                    .map(this::toResponse)
                    .toList();
        }
        return types.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ScheduleTypeResponse> findByCode(Calendar calendar, String code) {
        if (calendar == null || !StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return scheduleTypeRepository.findByCalendarAndCodeIgnoreCase(calendar, code.trim())
                .map(this::toResponse)
                .or(() -> Optional.ofNullable(LEGACY_DEFAULTS.get(code.trim().toUpperCase())).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public boolean supportsCode(Calendar calendar, String code) {
        return findByCode(calendar, code).isPresent();
    }

    @Transactional
    public void deleteByCalendar(Calendar calendar) {
        if (calendar != null) {
            scheduleTypeRepository.deleteByCalendar(calendar);
        }
    }

    public String resolveLabel(Calendar calendar, String code) {
        return findByCode(calendar, code)
                .map(ScheduleTypeResponse::name)
                .orElseGet(() -> legacyFallback(code).name());
    }

    public String resolveTimeRange(Calendar calendar, String code) {
        return findByCode(calendar, code)
                .map(ScheduleTypeResponse::timeRangeLabel)
                .orElseGet(() -> legacyFallback(code).timeRangeLabel());
    }

    private ScheduleTypeResponse toResponse(ScheduleType type) {
        return new ScheduleTypeResponse(
                type.getCode(),
                type.getName(),
                type.getColor(),
                type.getStartTime(),
                type.getEndTime(),
                formatTimeRange(type.getName(), type.getStartTime(), type.getEndTime(), type.isDefaultOff()),
                type.isCountsAsWork(),
                type.isDefaultOff()
        );
    }

    private ScheduleTypeResponse toResponse(LegacyScheduleType type) {
        return new ScheduleTypeResponse(
                type.code(),
                type.name(),
                type.color(),
                type.startTime(),
                type.endTime(),
                type.timeRangeLabel(),
                type.countsAsWork(),
                type.defaultOff()
        );
    }

    private LegacyScheduleType legacyFallback(String code) {
        if (!StringUtils.hasText(code)) {
            return LEGACY_DEFAULTS.get("O");
        }
        return LEGACY_DEFAULTS.getOrDefault(code.trim().toUpperCase(), LEGACY_DEFAULTS.get("O"));
    }

    private String formatTimeRange(String name, LocalTime startTime, LocalTime endTime, boolean defaultOff) {
        if (defaultOff) {
            return name;
        }
        if (startTime == null || endTime == null) {
            return name != null ? name : "";
        }
        return startTime + "–" + endTime;
    }

    private static Map<String, LegacyScheduleType> createLegacyDefaults() {
        Map<String, LegacyScheduleType> defaults = new LinkedHashMap<>();
        defaults.put("D", new LegacyScheduleType("D", "주간", "#2563EB", LocalTime.of(6, 0), LocalTime.of(14, 0), true, false, 10));
        defaults.put("A", new LegacyScheduleType("A", "오후", "#F97316", LocalTime.of(14, 0), LocalTime.of(22, 0), true, false, 20));
        defaults.put("N", new LegacyScheduleType("N", "야간", "#7C3AED", LocalTime.of(22, 0), LocalTime.of(6, 0), true, false, 30));
        defaults.put("V", new LegacyScheduleType("V", "연차", "#14B8A6", null, null, false, false, 40));
        defaults.put("O", new LegacyScheduleType("O", "휴무", "#94A3B8", null, null, false, true, 50));
        return defaults;
    }

    private record LegacyScheduleType(
            String code,
            String name,
            String color,
            LocalTime startTime,
            LocalTime endTime,
            boolean countsAsWork,
            boolean defaultOff,
            int sortOrder
    ) {
        private String timeRangeLabel() {
            if (defaultOff) {
                return name;
            }
            if (startTime == null || endTime == null) {
                return name;
            }
            return startTime + "–" + endTime;
        }
    }
}
