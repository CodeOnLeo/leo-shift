package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.external.ExternalSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 외부 캘린더 구독 API. */
public final class ExternalDtos {

    private ExternalDtos() {
    }

    /**
     * @param lastError 원격 서버가 준 문구가 섞여 있다. 화면에서 반드시 텍스트로만 넣을 것
     * @param eventCount 가져온 일정 수. 동기화가 실제로 됐는지 사용자가 아는 유일한 신호다
     */
    public record ExternalSourceResponse(
            Long id, Long calendarId, String calendarName, String name, String feedUrl,
            String color, String displayMode, boolean active, int syncIntervalMinutes,
            Instant lastSyncedAt, String lastError, long eventCount) {

        public static ExternalSourceResponse from(ExternalSource source, long eventCount) {
            return new ExternalSourceResponse(
                    source.getId(),
                    source.getCalendar().getId(),
                    source.getCalendar().getName(),
                    source.getName(),
                    source.getFeedUrl(),
                    source.getColor(),
                    source.getDisplayMode().name(),
                    source.isActive(),
                    source.getSyncIntervalMinutes(),
                    source.getLastSyncedAt(),
                    source.getLastError(),
                    eventCount);
        }
    }

    public record SaveExternalSourceRequest(
            @NotBlank(message = "이름을 입력해 주세요")
            @Size(max = 100, message = "이름이 너무 깁니다")
            String name,

            @NotBlank(message = "구독 주소를 입력해 주세요")
            @Size(max = 2000, message = "주소가 너무 깁니다")
            String feedUrl,

            @Size(max = 16)
            String color,

            /** BADGE · INLINE · HIDDEN. 비우면 BADGE */
            String displayMode,

            Boolean active,

            Integer syncIntervalMinutes) {
    }

    /**
     * 달력에 그려질 외부 일정 하나.
     *
     * <p>우리 일정({@code EventInstance})과 일부러 다른 타입으로 둔다. 편집할 수 없고
     * 출처 표시가 붙으며 {@code displayMode}에 따라 그리는 방식이 다르다. 한 타입으로
     * 합치면 화면이 매번 "이건 외부인가"를 묻게 된다.
     */
    public record ExternalEventResponse(
            Long sourceId, String sourceName, Long calendarId, String color, String displayMode,
            Instant startsAt, Instant endsAt, boolean allDay,
            String title, String description, String location) {
    }

    public record ExternalRangeResponse(Instant from, Instant to, List<ExternalEventResponse> events) {
    }

    /**
     * @param imported 이번에 저장한 회차 수
     * @param error    실패했으면 사용자에게 보여줄 문구. 성공이면 null
     */
    public record SyncResultResponse(ExternalSourceResponse source, int imported, String error) {
    }
}
