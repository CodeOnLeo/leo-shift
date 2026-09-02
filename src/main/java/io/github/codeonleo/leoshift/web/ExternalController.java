package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.domain.calendar.CalendarFeedToken;
import io.github.codeonleo.leoshift.domain.external.ExternalSource;
import io.github.codeonleo.leoshift.service.CalendarFeedService;
import io.github.codeonleo.leoshift.service.ExternalSourceService;
import io.github.codeonleo.leoshift.service.ExternalSyncService;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.ExternalRangeResponse;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.ExternalSourceResponse;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.SaveExternalSourceRequest;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.SyncResultResponse;
import io.github.codeonleo.leoshift.web.dto.FeedDtos.CreateFeedTokenRequest;
import io.github.codeonleo.leoshift.web.dto.FeedDtos.FeedTokenResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 다른 캘린더 연동 — 가져오기(구독)와 내보내기(구독 주소).
 *
 * <p>사용자에게는 설정의 한 화면이라 컨트롤러도 하나로 둔다. 방향은 반대지만 둘 다
 * "이 캘린더와 바깥을 잇는다"는 같은 일이다.
 */
@RestController
@RequestMapping("/api")
public class ExternalController {

    private final ExternalSourceService sourceService;
    private final ExternalSyncService syncService;
    private final CalendarFeedService feedService;

    public ExternalController(ExternalSourceService sourceService,
                              ExternalSyncService syncService,
                              CalendarFeedService feedService) {
        this.sourceService = sourceService;
        this.syncService = syncService;
        this.feedService = feedService;
    }

    // ---------------------------------------------------------------- 가져오기

    @GetMapping("/calendars/{calendarId}/external-sources")
    public List<ExternalSourceResponse> sources(@PathVariable Long calendarId) {
        return sourceService.list(calendarId);
    }

    /**
     * 구독을 만들고 <b>곧바로 한 번 가져온다.</b>
     *
     * <p>등록만 하고 스케줄러를 기다리게 하면, 사용자는 주소를 제대로 넣었는지
     * 몇 시간 뒤에야 알게 된다. 첫 동기화 결과를 그 자리에서 보여줘야 한다.
     */
    @PostMapping("/calendars/{calendarId}/external-sources")
    public SyncResultResponse createSource(@PathVariable Long calendarId,
                                           @Valid @RequestBody SaveExternalSourceRequest request) {
        ExternalSource source = sourceService.create(calendarId, request);
        return syncService.sync(source.getId());
    }

    @PutMapping("/external-sources/{sourceId}")
    public SyncResultResponse updateSource(@PathVariable Long sourceId,
                                           @Valid @RequestBody SaveExternalSourceRequest request) {
        sourceService.update(sourceId, request);
        return syncService.sync(sourceId);
    }

    @DeleteMapping("/external-sources/{sourceId}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long sourceId) {
        sourceService.delete(sourceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 지금 가져오기.
     *
     * <p>권한은 여기서 본다 — {@link ExternalSyncService}는 스케줄러도 쓰는 자리라
     * 로그인한 사용자를 전제하지 않는다.
     */
    @PostMapping("/external-sources/{sourceId}/sync")
    public SyncResultResponse sync(@PathVariable Long sourceId) {
        sourceService.requireEditable(sourceId);
        return syncService.sync(sourceId);
    }

    /**
     * 기간에 걸치는 외부 일정. 월 · 주 · 일 화면이 전부 이 하나를 쓴다.
     *
     * @param calendarId 여러 번 줄 수 있다. 비우면 볼 수 있는 캘린더 전부
     */
    @GetMapping("/external/events")
    public ExternalRangeResponse events(@RequestParam Instant from,
                                        @RequestParam Instant to,
                                        @RequestParam(required = false) List<Long> calendarId) {
        return sourceService.range(calendarId, from, to);
    }

    // ---------------------------------------------------------------- 내보내기

    @GetMapping("/calendars/{calendarId}/feed-tokens")
    public List<FeedTokenResponse> feedTokens(@PathVariable Long calendarId) {
        String baseUrl = baseUrl();
        return feedService.list(calendarId).stream()
                .map(token -> FeedTokenResponse.from(token, baseUrl))
                .toList();
    }

    @PostMapping("/calendars/{calendarId}/feed-tokens")
    public FeedTokenResponse createFeedToken(@PathVariable Long calendarId,
                                             @RequestBody(required = false) CreateFeedTokenRequest request) {
        CalendarFeedToken token = feedService.create(
                calendarId, request == null ? null : request.visibility());
        return FeedTokenResponse.from(token, baseUrl());
    }

    @DeleteMapping("/feed-tokens/{tokenId}")
    public ResponseEntity<Void> revokeFeedToken(@PathVariable Long tokenId) {
        feedService.revoke(tokenId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 구독 주소의 앞부분.
     *
     * <p>요청에서 뽑는다. 홈서버는 접속 경로가 여럿이라(집 안 주소 · 터널 도메인)
     * 설정에 하나로 박아 두면 다른 경로로 들어온 사람에게 안 되는 주소를 준다.
     * 리버스 프록시 뒤에서도 원본 호스트가 잡히도록 {@code forward-headers-strategy}가
     * 켜져 있다.
     */
    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
