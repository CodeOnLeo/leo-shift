package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.event.Event;
import io.github.codeonleo.leoshift.event.EventInstance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 일정 API. */
public final class EventDtos {

    private EventDtos() {
    }

    /**
     * @param rrule         반복 규칙. 단발이면 null.
     *                      {@code FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH} 형태
     * @param recurrenceEnd 반복 종료. 무한이면 null. RRULE의 UNTIL이 아니라 컬럼으로
     *                      두는 이유는 DB가 이 조건으로 후보를 걸러야 하기 때문이다
     * @param timeZone      비우면 캘린더의 시간대. 반복을 벽시계 기준으로 전개하는 기준이다
     */
    public record SaveEventRequest(
            @NotBlank(message = "제목을 입력해 주세요")
            @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다")
            String title,

            @Size(max = 2000, message = "메모는 2000자를 넘을 수 없습니다")
            String description,

            @Size(max = 200, message = "장소는 200자를 넘을 수 없습니다")
            String location,

            @NotNull(message = "시작 시각을 입력해 주세요")
            Instant startsAt,

            @NotNull(message = "종료 시각을 입력해 주세요")
            Instant endsAt,

            boolean allDay,
            String timeZone,
            String rrule,
            Instant recurrenceEnd) {
    }

    /**
     * 반복 중 한 회차만 손대기.
     *
     * @param originalStart 어느 회차인지. 옮겨도 이 값으로 계속 가리킨다
     * @param cancelled     휴강. 참이면 시각은 무시된다
     */
    public record SaveOccurrenceRequest(
            @NotNull(message = "어느 회차인지 알려 주세요")
            Instant originalStart,
            Instant startsAt,
            Instant endsAt,
            @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다")
            String title,
            @Size(max = 2000, message = "메모는 2000자를 넘을 수 없습니다")
            String note,
            boolean cancelled) {
    }

    /** 편집 화면이 쓰는 원본. 회차가 아니라 시리즈 전체다. */
    public record EventResponse(
            Long id, Long calendarId, String title, String description, String location,
            Instant startsAt, Instant endsAt, boolean allDay, String timeZone,
            String rrule, Instant recurrenceEnd, int version) {

        public static EventResponse from(Event event) {
            return new EventResponse(
                    event.getId(), event.getCalendar().getId(), event.getTitle(),
                    event.getDescription(), event.getLocation(),
                    event.getStartsAt(), event.getEndsAt(), event.isAllDay(), event.getTimeZone(),
                    event.getRrule(), event.getRecurrenceEnd(), event.getVersion());
        }
    }

    /**
     * 달력에 그려지는 한 칸.
     *
     * @param change    NONE · CANCELLED · MOVED · MODIFIED. 취소된 회차도 지우지 않고 보낸다
     * @param canEdit   공유받은 캘린더의 일정은 고칠 수 없다
     */
    public record EventInstanceResponse(
            Long eventId, Long calendarId, String calendarName, String color,
            Instant occurrenceStart, Instant startsAt, Instant endsAt,
            boolean allDay, boolean recurring,
            String title, String description, String location,
            String change, boolean canEdit) {
    }

    public record EventRangeResponse(
            Instant from, Instant to, List<EventInstanceResponse> instances) {
    }
}
