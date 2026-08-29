package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** 조회 API 응답. */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record CurrentUserResponse(
            Long id, String name, String nickname, String email,
            String colorTag, String timeZone) {
    }

    public record CalendarSummaryResponse(
            Long id, String name, String color, String kind,
            boolean isDefault, boolean ownedByGroup, boolean canEdit, String visibility) {

        public static CalendarSummaryResponse from(CalendarAccessService.Access access) {
            Calendar calendar = access.calendar();
            return new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    calendar.getColor(),
                    calendar.getKind().name(),
                    calendar.isDefault(),
                    calendar.isOwnedByGroup(),
                    access.canEdit(),
                    access.visibility().name());
        }
    }

    public record ScheduleTypeResponse(
            String code, String name, String color, String category,
            LocalTime startTime, LocalTime endTime,
            boolean crossesMidnight, boolean halfDay) {

        public static ScheduleTypeResponse from(ScheduleType type) {
            return new ScheduleTypeResponse(
                    type.getCode(), type.getName(), type.getColor(),
                    type.getCategory().name(),
                    type.getStartTime(), type.getEndTime(),
                    type.isCrossesMidnight(), type.isHalfDay());
        }
    }

    public record DayResponse(
            LocalDate date, String code, String source, Long sourceId, String note) {

        public static DayResponse from(ResolvedDay day) {
            return new DayResponse(
                    day.date(), day.code(), day.source().name(), day.sourceId(), day.note());
        }
    }

    /**
     * @param summary 코드별 일수. 조회 구간 전체가 아니라 요청한 기준 월만 센다.
     */
    public record ScheduleRangeResponse(
            Long calendarId,
            LocalDate from,
            LocalDate to,
            List<DayResponse> days,
            List<ScheduleTypeResponse> scheduleTypes,
            Map<String, Long> summary) {
    }
}
