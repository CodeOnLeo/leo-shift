package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.service.CalendarFeedService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 읽기 전용 {@code .ics} 구독 주소.
 *
 * <p><b>인증 없이 열리는 유일한 API다.</b> 구글 캘린더 서버가 읽어가기 때문에 쿠키도
 * 토큰 헤더도 붙일 수 없다. 권한은 주소에 들어 있는 토큰 하나로만 판단한다.
 *
 * <p>그래서 {@code /api} 아래에 두지 않았다. 보안 설정에서 "인증이 필요한 API"와
 * "토큰으로 여는 피드"가 경로만 봐도 갈리는 편이 안전하다.
 */
@RestController
public class FeedController {

    private final CalendarFeedService feedService;

    public FeedController(CalendarFeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feed/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        UUID parsed;
        try {
            parsed = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            // 형식이 틀려도 404다. 400을 주면 "형식은 맞다"는 신호가 되어 대입에 힌트가 된다.
            throw new NotFoundException("구독 주소를 찾을 수 없습니다");
        }
        CalendarFeedService.Feed feed = feedService.render(parsed);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                // 이 주소는 검색엔진에도 남으면 안 된다
                .header("X-Robots-Tag", "noindex, nofollow")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(feed.fileName()))
                // 구글은 제 주기로 읽지만, 브라우저로 열어 본 사람이 매번 다시 받게 두지는 않는다
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .body(feed.body());
    }

    /** 한글 캘린더 이름이 파일 이름으로 나갈 수 있게 RFC 5987 형식으로 적는다. */
    private static String disposition(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"calendar.ics\"; filename*=UTF-8''" + encoded + ".ics";
    }
}
