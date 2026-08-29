package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.work.DayOverride;
import io.github.codeonleo.leoshift.domain.work.Leave;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.DayOverrideRepository;
import io.github.codeonleo.leoshift.repository.LeaveRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.schedule.ScheduleResolver;
import io.github.codeonleo.leoshift.schedule.WorkRuleSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간의 근무를 해석한다.
 *
 * <p>필요한 데이터를 <b>세 번의 조회로 전부</b> 가져와 해석기에 넘긴다.
 * 이전 구현은 날짜마다 두 번씩 질의해서 한 달 화면에 80여 번의 조회가 나갔고,
 * 그걸 줄이려다 달력 전체에 규칙 하나를 적용해서 정확성을 깼다.
 */
@Service
public class ScheduleQueryService {

    private final WorkRuleRepository workRuleRepository;
    private final LeaveRepository leaveRepository;
    private final DayOverrideRepository dayOverrideRepository;

    public ScheduleQueryService(WorkRuleRepository workRuleRepository,
                                LeaveRepository leaveRepository,
                                DayOverrideRepository dayOverrideRepository) {
        this.workRuleRepository = workRuleRepository;
        this.leaveRepository = leaveRepository;
        this.dayOverrideRepository = dayOverrideRepository;
    }

    @Transactional(readOnly = true)
    public List<ResolvedDay> resolve(Long calendarId, LocalDate from, LocalDate to) {
        List<WorkRule> rules = workRuleRepository.findOverlapping(calendarId, from, to);
        List<Leave> leaves = leaveRepository.findOverlapping(calendarId, from, to);
        List<DayOverride> overrides = dayOverrideRepository.findInRange(calendarId, from, to);

        ScheduleResolver resolver = new ScheduleResolver(
                WorkRuleSet.of(rules.stream().map(WorkRule::toDomain).toList()),
                leaves.stream().map(Leave::toDomain).toList(),
                overrides.stream().map(DayOverride::toDomain).toList());

        return resolver.resolveRange(from, to);
    }
}
