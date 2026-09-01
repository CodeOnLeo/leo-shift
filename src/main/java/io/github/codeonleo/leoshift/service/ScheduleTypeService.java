package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.DayOverrideRepository;
import io.github.codeonleo.leoshift.repository.LeaveRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.web.dto.ScheduleTypeDtos.SaveScheduleTypeRequest;
import io.github.codeonleo.leoshift.web.dto.ScheduleTypeDtos.ScheduleTypeUsageResponse;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 근무 코드 관리.
 *
 * <p>이름을 바꿀 때 참조를 어떻게 따라가는지가 이 클래스의 핵심이다.
 * <ul>
 *   <li>{@code leaves}, {@code day_overrides} — 복합 FK의 ON UPDATE CASCADE로 DB가 처리한다</li>
 *   <li>{@code work_rules.code_sequence} — JSONB라 FK가 없으므로 여기서 직접 고친다</li>
 * </ul>
 *
 * <p>이전 구현은 이 일을 전부 애플리케이션이 했고, 유니크 충돌을 피하려고 임시 코드를
 * 만들어 두 번 저장하는 400줄짜리 로직이 있었다.
 */
@Service
public class ScheduleTypeService {

    private final ScheduleTypeRepository scheduleTypeRepository;
    private final WorkRuleRepository workRuleRepository;
    private final LeaveRepository leaveRepository;
    private final DayOverrideRepository dayOverrideRepository;

    public ScheduleTypeService(ScheduleTypeRepository scheduleTypeRepository,
                               WorkRuleRepository workRuleRepository,
                               LeaveRepository leaveRepository,
                               DayOverrideRepository dayOverrideRepository) {
        this.scheduleTypeRepository = scheduleTypeRepository;
        this.workRuleRepository = workRuleRepository;
        this.leaveRepository = leaveRepository;
        this.dayOverrideRepository = dayOverrideRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduleType> list(Long calendarId) {
        return scheduleTypeRepository.findByCalendar(calendarId);
    }

    @Transactional
    public ScheduleType create(Calendar calendar, SaveScheduleTypeRequest request) {
        String code = normalizeCode(request.code());
        if (scheduleTypeRepository.findByCalendarAndCode(calendar.getId(), code).isPresent()) {
            throw new IllegalArgumentException("이미 있는 코드입니다: " + code);
        }
        ScheduleType.Category category = parseCategory(request.category());
        validate(category, request);

        int nextOrder = request.sortOrder() != null
                ? request.sortOrder()
                : scheduleTypeRepository.findByCalendar(calendar.getId()).stream()
                        .mapToInt(ScheduleType::getSortOrder).max().orElse(0) + 10;

        return scheduleTypeRepository.save(ScheduleType.builder()
                .calendar(calendar)
                .code(code)
                .name(request.name().trim())
                .color(StringUtils.hasText(request.color()) ? request.color().toUpperCase() : "#94A3B8")
                .category(category)
                .startTime(category == ScheduleType.Category.WORK ? request.startTime() : null)
                .endTime(category == ScheduleType.Category.WORK ? request.endTime() : null)
                .crossesMidnight(crossesMidnight(category, request.startTime(), request.endTime()))
                .halfDay(category == ScheduleType.Category.LEAVE && request.halfDay())
                .sortOrder(nextOrder)
                .build());
    }

    @Transactional
    public ScheduleType update(Calendar calendar, String currentCode, SaveScheduleTypeRequest request) {
        ScheduleType type = require(calendar.getId(), currentCode);
        String newCode = normalizeCode(request.code());
        ScheduleType.Category category = parseCategory(request.category());
        validate(category, request);

        if (!newCode.equals(type.getCode())) {
            if (scheduleTypeRepository.findByCalendarAndCode(calendar.getId(), newCode).isPresent()) {
                throw new IllegalArgumentException("이미 있는 코드입니다: " + newCode);
            }
            // 반복 규칙의 시퀀스는 JSONB라 FK가 걸리지 않는다. 직접 고쳐야 한다.
            // leaves와 day_overrides는 DB가 ON UPDATE CASCADE로 따라온다.
            rewriteRuleSequences(calendar.getId(), type.getCode(), newCode);
            type.setCode(newCode);
        }

        type.setName(request.name().trim());
        if (StringUtils.hasText(request.color())) {
            type.setColor(request.color().toUpperCase());
        }
        type.setCategory(category);
        type.setStartTime(category == ScheduleType.Category.WORK ? request.startTime() : null);
        type.setEndTime(category == ScheduleType.Category.WORK ? request.endTime() : null);
        type.setCrossesMidnight(crossesMidnight(category, request.startTime(), request.endTime()));
        type.setHalfDay(category == ScheduleType.Category.LEAVE && request.halfDay());
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        return scheduleTypeRepository.save(type);
    }

    @Transactional
    public void delete(Calendar calendar, String code) {
        ScheduleType type = require(calendar.getId(), code);
        ScheduleTypeUsageResponse usage = usage(calendar.getId(), type.getCode());
        if (usage.inUse()) {
            throw new IllegalArgumentException(describeUsage(type.getName(), usage));
        }
        scheduleTypeRepository.delete(type);
    }

    /**
     * 이 코드가 쓰이고 있는지.
     *
     * <p>{@code leaves}와 {@code day_overrides}는 DB의 ON DELETE RESTRICT가 막아주지만,
     * 반복 규칙은 FK가 없으므로 여기서 확인해야 한다. 확인하지 않으면 코드가 사라진 뒤
     * 근무표에 정체불명 문자열이 남는다.
     */
    @Transactional(readOnly = true)
    public ScheduleTypeUsageResponse usage(Long calendarId, String code) {
        boolean usedByRule = workRuleRepository.findAllByCalendar(calendarId).stream()
                .anyMatch(rule -> rule.getCodeSequence().contains(code));
        boolean usedByLeave = leaveRepository.existsByCalendarIdAndScheduleTypeCode(calendarId, code);
        boolean usedByOverride =
                dayOverrideRepository.existsByCalendarIdAndScheduleTypeCode(calendarId, code);

        return new ScheduleTypeUsageResponse(code,
                usedByRule || usedByLeave || usedByOverride,
                usedByRule, usedByLeave, usedByOverride);
    }

    private String describeUsage(String name, ScheduleTypeUsageResponse usage) {
        StringBuilder where = new StringBuilder();
        if (usage.usedByRule()) {
            where.append("반복 근무");
        }
        if (usage.usedByLeave()) {
            where.append(where.isEmpty() ? "" : ", ").append("휴가");
        }
        if (usage.usedByOverride()) {
            where.append(where.isEmpty() ? "" : ", ").append("날짜별 변경");
        }
        return name + " 코드가 " + where + "에서 쓰이고 있어 지울 수 없습니다";
    }

    private void rewriteRuleSequences(Long calendarId, String oldCode, String newCode) {
        for (WorkRule rule : workRuleRepository.findAllByCalendar(calendarId)) {
            if (!rule.getCodeSequence().contains(oldCode)) {
                continue;
            }
            rule.setCodeSequence(rule.getCodeSequence().stream()
                    .map(code -> code.equals(oldCode) ? newCode : code)
                    .toList());
            workRuleRepository.save(rule);
        }
        workRuleRepository.flush();
    }

    private ScheduleType require(Long calendarId, String code) {
        return scheduleTypeRepository.findByCalendarAndCode(calendarId, normalizeCode(code))
                .orElseThrow(() -> new CalendarAccessService.NotFoundException("없는 근무 코드입니다"));
    }

    private void validate(ScheduleType.Category category, SaveScheduleTypeRequest request) {
        if (category == ScheduleType.Category.WORK
                && (request.startTime() == null || request.endTime() == null)) {
            throw new IllegalArgumentException("근무 종류는 시작·종료 시각이 필요합니다");
        }
        if (request.halfDay() && category != ScheduleType.Category.LEAVE) {
            throw new IllegalArgumentException("반차는 휴가 종류에만 쓸 수 있습니다");
        }
    }

    /** 야간 22:00~06:00처럼 종료가 시작보다 이르거나 같으면 자정을 넘는다. */
    private boolean crossesMidnight(ScheduleType.Category category, LocalTime start, LocalTime end) {
        if (category != ScheduleType.Category.WORK || start == null || end == null) {
            return false;
        }
        return !end.isAfter(start);
    }

    private ScheduleType.Category parseCategory(String value) {
        try {
            return ScheduleType.Category.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("알 수 없는 종류입니다: " + value);
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
