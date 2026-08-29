package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.domain.work.DayOverride;
import io.github.codeonleo.leoshift.domain.work.Leave;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.DayOverrideRepository;
import io.github.codeonleo.leoshift.repository.LeaveRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.schedule.ScheduleResolver;
import io.github.codeonleo.leoshift.schedule.WorkRuleSet;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.web.dto.DayDtos.DayDetailResponse;
import io.github.codeonleo.leoshift.web.dto.DayDtos.LeaveResponse;
import io.github.codeonleo.leoshift.web.dto.DayDtos.OverrideResponse;
import io.github.codeonleo.leoshift.web.dto.DayDtos.SaveLeaveRequest;
import io.github.codeonleo.leoshift.web.dto.DayDtos.SaveOverrideRequest;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.ScheduleTypeResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DayDetailService {

    private final WorkRuleRepository workRuleRepository;
    private final LeaveRepository leaveRepository;
    private final DayOverrideRepository dayOverrideRepository;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final CurrentUser currentUser;

    public DayDetailService(WorkRuleRepository workRuleRepository,
                            LeaveRepository leaveRepository,
                            DayOverrideRepository dayOverrideRepository,
                            ScheduleTypeRepository scheduleTypeRepository,
                            CurrentUser currentUser) {
        this.workRuleRepository = workRuleRepository;
        this.leaveRepository = leaveRepository;
        this.dayOverrideRepository = dayOverrideRepository;
        this.scheduleTypeRepository = scheduleTypeRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DayDetailResponse load(CalendarAccessService.Access access, LocalDate date) {
        Long calendarId = access.calendar().getId();

        List<WorkRule> rules = workRuleRepository.findOverlapping(calendarId, date, date);
        List<Leave> leaves = leaveRepository.findOverlapping(calendarId, date, date);
        Optional<DayOverride> override = dayOverrideRepository.findOn(calendarId, date);

        WorkRuleSet ruleSet = WorkRuleSet.of(rules.stream().map(WorkRule::toDomain).toList());
        List<io.github.codeonleo.leoshift.schedule.LeavePeriod> leavePeriods =
                leaves.stream().map(Leave::toDomain).toList();

        ResolvedDay resolved = new ScheduleResolver(ruleSet, leavePeriods,
                override.map(DayOverride::toDomain).map(List::of).orElse(List.of())).resolve(date);

        // 예외를 지우면 무엇이 되는지. "원래 야간입니다"를 보여주기 위해 한 번 더 푼다.
        ResolvedDay base = new ScheduleResolver(ruleSet, leavePeriods, List.of()).resolve(date);

        List<ScheduleTypeResponse> types = scheduleTypeRepository.findByCalendar(calendarId).stream()
                .map(ScheduleTypeResponse::from)
                .toList();

        return new DayDetailResponse(
                date,
                resolved.code(),
                resolved.source().name(),
                resolved.note(),
                base.code(),
                base.source().name(),
                override.map(o -> new OverrideResponse(o.getId(), o.getScheduleTypeCode(),
                        o.getNote(), o.getVersion())).orElse(null),
                leaves.stream().findFirst().map(leave -> new LeaveResponse(leave.getId(),
                        leave.getStartDate(), leave.getEndDate(),
                        leave.getScheduleTypeCode(), leave.getNote())).orElse(null),
                findType(types, resolved.code()),
                types,
                access.canEdit());
    }

    private ScheduleTypeResponse findType(List<ScheduleTypeResponse> types, String code) {
        if (code == null) {
            return null;
        }
        return types.stream().filter(type -> type.code().equals(code)).findFirst().orElse(null);
    }

    @Transactional
    public void saveOverride(Calendar calendar, LocalDate date, SaveOverrideRequest request) {
        String code = normalize(request.code());
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        if (code != null) {
            requireKnownCode(calendar, code);
        }

        Optional<DayOverride> existing = dayOverrideRepository.findOn(calendar.getId(), date);

        // 코드도 메모도 없으면 예외 자체가 필요 없다. 규칙으로 되돌린다.
        if (code == null && note == null) {
            existing.ifPresent(dayOverrideRepository::delete);
            return;
        }

        DayOverride override = existing.orElseGet(() -> DayOverride.builder()
                .calendar(calendar)
                .onDate(date)
                .build());

        // 화면이 보고 있던 판과 다르면 거절한다. 편집 권한을 가진 두 사람이
        // 같은 날을 고칠 때 나중 사람이 앞사람 메모를 지우던 문제를 막는다.
        if (override.getId() != null && request.version() != null
                && request.version() != override.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(DayOverride.class, override.getId());
        }

        override.setScheduleTypeCode(code);
        override.setNote(note);
        override.setAuthor(currentUser.require());
        dayOverrideRepository.save(override);
    }

    @Transactional
    public void clearOverride(Calendar calendar, LocalDate date) {
        dayOverrideRepository.findOn(calendar.getId(), date).ifPresent(dayOverrideRepository::delete);
    }

    @Transactional
    public Leave saveLeave(Calendar calendar, SaveLeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }
        String code = normalize(request.code());
        if (code == null) {
            throw new IllegalArgumentException("휴가 종류를 골라 주세요");
        }
        ScheduleType type = requireKnownCode(calendar, code);
        if (type.getCategory() != ScheduleType.Category.LEAVE) {
            throw new IllegalArgumentException("휴가로 쓸 수 있는 종류가 아닙니다: " + type.getName());
        }

        User author = currentUser.require();
        return leaveRepository.save(Leave.builder()
                .calendar(calendar)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .scheduleTypeCode(code)
                .note(StringUtils.hasText(request.note()) ? request.note().trim() : null)
                .createdBy(author)
                .build());
    }

    @Transactional
    public void deleteLeave(Calendar calendar, Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new CalendarAccessService.NotFoundException("휴가를 찾을 수 없습니다"));
        // 다른 캘린더의 휴가를 이 캘린더 경로로 지우지 못하게 한다
        if (!leave.getCalendar().getId().equals(calendar.getId())) {
            throw new CalendarAccessService.NotFoundException("휴가를 찾을 수 없습니다");
        }
        leaveRepository.delete(leave);
    }

    private ScheduleType requireKnownCode(Calendar calendar, String code) {
        return scheduleTypeRepository.findByCalendarAndCode(calendar.getId(), code)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 근무 코드입니다: " + code));
    }

    private String normalize(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase() : null;
    }
}
