package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.calendar.CalendarFeedToken;
import java.time.Instant;

/** 내보내기용 구독 주소 API. */
public final class FeedDtos {

    private FeedDtos() {
    }

    /**
     * @param url      구글 캘린더에 그대로 붙여 넣는 주소. 토큰이 들어 있으므로
     *                 <b>이 값을 아는 사람은 누구나 캘린더를 볼 수 있다</b>
     * @param lastUsedAt 마지막으로 누가 읽어간 시각. 구독이 실제로 걸렸는지 아는 신호다
     */
    public record FeedTokenResponse(
            Long id, Long calendarId, String url, String visibility,
            Instant createdAt, Instant lastUsedAt) {

        public static FeedTokenResponse from(CalendarFeedToken token, String baseUrl) {
            return new FeedTokenResponse(
                    token.getId(),
                    token.getCalendar().getId(),
                    baseUrl + "/feed/" + token.getToken() + ".ics",
                    token.getVisibility().name(),
                    token.getCreatedAt(),
                    token.getLastUsedAt());
        }
    }

    /** @param visibility FULL 또는 BUSY_ONLY. 비우면 FULL */
    public record CreateFeedTokenRequest(String visibility) {
    }
}
