package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.ScheduleQueryService;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.CalendarSummaryResponse;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.CurrentUserResponse;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.DayResponse;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.ScheduleRangeResponse;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.ScheduleTypeResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CalendarQueryController {

    /** 한 번에 조회할 수 있는 최대 기간. 무제한 조회로 서버를 밀어내지 못하게 한다. */
    private static final long MAX_RANGE_DAYS = 400;

    private final CurrentUser currentUser;
    private final CalendarAccessService accessService;
    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleTypeRepository scheduleTypeRepository;

    public CalendarQueryController(CurrentUser currentUser,
                                   CalendarAccessService accessService,
                                   ScheduleQueryService scheduleQueryService,
                                   ScheduleTypeRepository scheduleTypeRepository) {
        this.currentUser = currentUser;
        this.accessService = accessService;
        this.scheduleQueryService = scheduleQueryService;
        this.scheduleTypeRepository = scheduleTypeRepository;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        User user = currentUser.require();
        return new CurrentUserResponse(
                user.getId(), user.getName(), user.getNickname(), user.getEmail(),
                user.getColorTag(), user.getTimeZone());
    }

    @GetMapping("/calendars")
    public List<CalendarSummaryResponse> calendars() {
        return accessService.listVisible().stream()
                .map(CalendarSummaryResponse::from)
                .toList();
    }

    /**
     * 기간의 근무를 해석해서 돌려준다.
     *
     * <p>월 · 주 · 일 화면이 전부 이 하나를 쓴다. 화면마다 다른 경로를 두면
     * 해석이 갈라지고, 그게 이전 구현에서 월 뷰와 일 상세가 서로 다른 근무를
     * 보여준 원인이었다.
     *
     * @param month 지정하면 요약을 그 달 기준으로 계산한다
     */
    @GetMapping("/schedule")
    public ScheduleRangeResponse schedule(
            @RequestParam Long calendarId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 기간을 넘었습니다");
        }

        CalendarAccessService.Access access = accessService.requireView(calendarId);

        List<ResolvedDay> days = scheduleQueryService.resolve(calendarId, from, to);
        List<ScheduleTypeResponse> types = scheduleTypeRepository.findByCalendar(calendarId).stream()
                .map(ScheduleTypeResponse::from)
                .toList();

        return new ScheduleRangeResponse(
                access.calendar().getId(),
                from,
                to,
                days.stream().map(DayResponse::from).toList(),
                types,
                summarize(days, types, year, month));
    }

    /** 코드별 일수. 기준 월이 주어지면 그 달 날짜만 센다. */
    private Map<String, Long> summarize(List<ResolvedDay> days,
                                        List<ScheduleTypeResponse> types,
                                        Integer year,
                                        Integer month) {
        Map<String, Long> summary = new LinkedHashMap<>();
        types.forEach(type -> summary.put(type.code(), 0L));

        for (ResolvedDay day : days) {
            if (day.code() == null) {
                continue;
            }
            if (year != null && month != null
                    && (day.date().getYear() != year || day.date().getMonthValue() != month)) {
                continue;
            }
            summary.merge(day.code(), 1L, Long::sum);
        }
        return summary;
    }
}
