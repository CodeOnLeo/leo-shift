package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.ScheduleTypeResponse;
import io.github.codeonleo.leoshift.dto.ScheduleTypeUpdateItemRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.entity.CalendarWeeklyRule;
import io.github.codeonleo.leoshift.entity.ScheduleType;
import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.repository.CalendarPatternRepository;
import io.github.codeonleo.leoshift.repository.CalendarWeeklyRuleRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScheduleTypeService {

    private static final Map<String, LegacyScheduleType> LEGACY_DEFAULTS = createLegacyDefaults();

    private final ScheduleTypeRepository scheduleTypeRepository;
    private final CalendarPatternRepository calendarPatternRepository;
    private final CalendarWeeklyRuleRepository calendarWeeklyRuleRepository;
    private final ShiftExceptionRepository shiftExceptionRepository;

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

    @Transactional
    public void ensureDefaults(Calendar calendar, String templateType) {
        if (calendar == null || scheduleTypeRepository.existsByCalendar(calendar)) {
            return;
        }
        if ("general".equalsIgnoreCase(templateType) || "empty".equalsIgnoreCase(templateType)) {
            scheduleTypeRepository.saveAll(List.of(
                    ScheduleType.builder()
                            .calendar(calendar)
                            .code("WORK")
                            .name("일정")
                            .color("#2563EB")
                            .startTime(LocalTime.of(9, 0))
                            .endTime(LocalTime.of(18, 0))
                            .countsAsWork(true)
                            .sortOrder(10)
                            .defaultOff(false)
                            .build(),
                    ScheduleType.builder()
                            .calendar(calendar)
                            .code("OFF")
                            .name("휴식")
                            .color("#94A3B8")
                            .countsAsWork(false)
                            .sortOrder(20)
                            .defaultOff(true)
                            .build()
            ));
            return;
        }
        ensureDefaults(calendar);
    }

    @Transactional
    public List<ScheduleTypeResponse> updateTypes(Calendar calendar, List<ScheduleTypeUpdateItemRequest> requests) {
        if (calendar == null) {
            throw new IllegalArgumentException("calendar_required");
        }
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("schedule_types_required");
        }

        ensureDefaults(calendar);

        List<ScheduleType> existing = scheduleTypeRepository.findByCalendarOrderBySortOrderAscCodeAsc(calendar);
        Map<String, ScheduleType> existingByCode = existing.stream()
                .collect(Collectors.toMap(type -> type.getCode().toUpperCase(), Function.identity()));

        Map<String, ScheduleTypeUpdateItemRequest> requestByLookupCode = new LinkedHashMap<>();
        Map<String, String> finalCodeByLookupCode = new LinkedHashMap<>();
        for (ScheduleTypeUpdateItemRequest request : requests) {
            String finalCode = normalizeCode(request.code());
            String lookupCode = normalizeLookupCode(request, existingByCode, finalCode);
            if (requestByLookupCode.containsKey(lookupCode) || finalCodeByLookupCode.containsValue(finalCode)) {
                throw new IllegalArgumentException("duplicate_schedule_type_code");
            }
            requestByLookupCode.put(lookupCode, request);
            finalCodeByLookupCode.put(lookupCode, finalCode);
        }
        if (requestByLookupCode.isEmpty()) {
            throw new IllegalArgumentException("schedule_types_required");
        }

        List<ScheduleType> keptExisting = existing.stream()
                .filter(type -> requestByLookupCode.containsKey(type.getCode().toUpperCase()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ScheduleType> removedTypes = existing.stream()
                .filter(type -> !requestByLookupCode.containsKey(type.getCode().toUpperCase()))
                .toList();
        validateDeletions(calendar, removedTypes);

        List<CodeRename> renames = new ArrayList<>();
        for (Map.Entry<String, ScheduleTypeUpdateItemRequest> entry : requestByLookupCode.entrySet()) {
            ScheduleType type = existingByCode.get(entry.getKey());
            if (type == null) {
                continue;
            }
            ScheduleTypeUpdateItemRequest request = entry.getValue();
            String finalCode = finalCodeByLookupCode.get(entry.getKey());
            String existingCode = type.getCode().toUpperCase();
            applyFieldUpdates(type, request, finalCode, existingCode);
            if (!existingCode.equalsIgnoreCase(finalCode)) {
                renames.add(new CodeRename(type, existingCode, finalCode));
            }
        }

        if (!renames.isEmpty()) {
            for (CodeRename rename : renames) {
                rename.type().setCode(createTemporaryCode(rename.oldCode()));
            }
            scheduleTypeRepository.saveAll(keptExisting);
            scheduleTypeRepository.flush();
        }
        if (!removedTypes.isEmpty()) {
            scheduleTypeRepository.deleteAll(removedTypes);
            scheduleTypeRepository.flush();
        }
        if (!renames.isEmpty()) {
            rewriteCodeReferences(calendar, renames);
            for (CodeRename rename : renames) {
                rename.type().setCode(rename.newCode());
            }
        }

        int nextSortOrder = keptExisting.stream()
                .map(ScheduleType::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 10;
        for (Map.Entry<String, ScheduleTypeUpdateItemRequest> entry : requestByLookupCode.entrySet()) {
            if (existingByCode.containsKey(entry.getKey())) {
                continue;
            }
            ScheduleTypeUpdateItemRequest request = entry.getValue();
            String finalCode = finalCodeByLookupCode.get(entry.getKey());
            boolean defaultOff = request.startTime() == null && request.endTime() == null;
            keptExisting.add(ScheduleType.builder()
                    .calendar(calendar)
                    .code(finalCode)
                    .name(request.name().trim())
                    .color(normalizeColor(request.color(), "#94A3B8"))
                    .startTime(defaultOff ? null : request.startTime())
                    .endTime(defaultOff ? null : request.endTime())
                    .countsAsWork(!defaultOff)
                    .defaultOff(defaultOff)
                    .sortOrder(nextSortOrder)
                    .build());
            nextSortOrder += 10;
        }
        scheduleTypeRepository.saveAll(keptExisting);
        return listForCalendar(calendar);
    }

    private void applyFieldUpdates(ScheduleType type, ScheduleTypeUpdateItemRequest request, String finalCode, String existingCode) {
        type.setCode(finalCode);
        type.setName(request.name().trim());
        type.setColor(normalizeColor(request.color(), legacyFallback(existingCode).color()));
        if (type.isDefaultOff()) {
            type.setStartTime(null);
            type.setEndTime(null);
        } else {
            type.setStartTime(request.startTime());
            type.setEndTime(request.endTime());
        }
    }

    private String normalizeLookupCode(ScheduleTypeUpdateItemRequest request, Map<String, ScheduleType> existingByCode, String finalCode) {
        String originalCode = normalizeNullableCode(request.originalCode());
        if (originalCode != null) {
            return originalCode;
        }
        return existingByCode.containsKey(finalCode) ? finalCode : finalCode;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("schedule_type_code_required");
        }
        return code.trim().toUpperCase();
    }

    private String normalizeNullableCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String createTemporaryCode(String oldCode) {
        String prefix = "__TMP__" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return (prefix + "_" + oldCode).substring(0, Math.min(32, prefix.length() + 1 + oldCode.length()));
    }

    private void rewriteCodeReferences(Calendar calendar, List<CodeRename> renames) {
        Map<String, String> renameMap = renames.stream()
                .collect(Collectors.toMap(CodeRename::oldCode, CodeRename::newCode, (left, right) -> right, LinkedHashMap::new));

        List<CalendarPattern> patterns = calendarPatternRepository.findByCalendarOrderByPatternStartDateAsc(calendar);
        for (CalendarPattern pattern : patterns) {
            pattern.setPatternCodes(rewriteCommaSeparatedCodes(pattern.getPatternCodes(), renameMap));
        }
        calendarPatternRepository.saveAll(patterns);

        List<CalendarWeeklyRule> weeklyRules = calendarWeeklyRuleRepository.findByCalendarOrderByDayOfWeekAsc(calendar);
        for (CalendarWeeklyRule weeklyRule : weeklyRules) {
            String normalized = normalizeNullableCode(weeklyRule.getScheduleTypeCode());
            if (normalized != null && renameMap.containsKey(normalized)) {
                weeklyRule.setScheduleTypeCode(renameMap.get(normalized));
            }
        }
        calendarWeeklyRuleRepository.saveAll(weeklyRules);

        List<ShiftException> exceptions = shiftExceptionRepository.findByCalendar(calendar);
        for (ShiftException exception : exceptions) {
            String normalized = normalizeNullableCode(exception.getCustomCode());
            if (normalized != null && renameMap.containsKey(normalized)) {
                exception.setCustomCode(renameMap.get(normalized));
            }
        }
        shiftExceptionRepository.saveAll(exceptions);
    }

    private void validateDeletions(Calendar calendar, List<ScheduleType> removedTypes) {
        if (removedTypes == null || removedTypes.isEmpty()) {
            return;
        }
        Set<String> referencedCodes = collectReferencedCodes(calendar);
        removedTypes.stream()
                .map(ScheduleType::getCode)
                .map(String::toUpperCase)
                .filter(referencedCodes::contains)
                .findFirst()
                .ifPresent(code -> {
                    throw new IllegalArgumentException("schedule_type_in_use:" + code);
                });
    }

    private Set<String> collectReferencedCodes(Calendar calendar) {
        Set<String> referencedCodes = new HashSet<>();

        calendarPatternRepository.findByCalendarOrderByPatternStartDateAsc(calendar).stream()
                .map(CalendarPattern::getPatternCodes)
                .filter(StringUtils::hasText)
                .forEach(raw -> List.of(raw.split(",")).stream()
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(String::toUpperCase)
                        .forEach(referencedCodes::add));

        calendarWeeklyRuleRepository.findByCalendarOrderByDayOfWeekAsc(calendar).stream()
                .map(CalendarWeeklyRule::getScheduleTypeCode)
                .map(this::normalizeNullableCode)
                .filter(code -> code != null)
                .forEach(referencedCodes::add);

        shiftExceptionRepository.findByCalendar(calendar).stream()
                .map(ShiftException::getCustomCode)
                .map(this::normalizeNullableCode)
                .filter(code -> code != null)
                .forEach(referencedCodes::add);

        return referencedCodes;
    }

    private String rewriteCommaSeparatedCodes(String rawCodes, Map<String, String> renameMap) {
        if (!StringUtils.hasText(rawCodes)) {
            return rawCodes;
        }
        return List.of(rawCodes.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .map(code -> renameMap.getOrDefault(code, code))
                .collect(Collectors.joining(","));
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

    private String normalizeColor(String color, String fallback) {
        if (!StringUtils.hasText(color)) {
            return fallback;
        }
        return color.trim().toUpperCase();
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
        defaults.put("WORK", new LegacyScheduleType("WORK", "일정", "#2563EB", LocalTime.of(9, 0), LocalTime.of(18, 0), true, false, 10));
        defaults.put("OFF", new LegacyScheduleType("OFF", "휴식", "#94A3B8", null, null, false, true, 20));
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

    private record CodeRename(
            ScheduleType type,
            String oldCode,
            String newCode
    ) {
    }
}
