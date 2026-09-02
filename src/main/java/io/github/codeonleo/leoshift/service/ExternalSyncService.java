package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.ics.IcsEvent;
import io.github.codeonleo.leoshift.ics.IcsExpander;
import io.github.codeonleo.leoshift.ics.IcsParser;
import io.github.codeonleo.leoshift.service.ExternalSourceService.SyncTarget;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.SyncResultResponse;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 구독한 피드를 가져와 캐시에 넣는다.
 *
 * <p>가져오기(HTTP) · 읽기(ICS 파싱) · 펼치기(반복 전개)를 <b>트랜잭션 밖에서</b> 하고,
 * DB에 닿는 부분만 {@link ExternalSourceService}에 맡긴다. 두 클래스로 나눈 이유가
 * 그것이다 — 같은 빈 안에서 부르면 프록시를 타지 않아 트랜잭션 경계가 생기지 않는다.
 *
 * <p>실패해도 예외를 밖으로 내보내지 않는다. 피드 하나가 죽었다고 나머지 동기화가
 * 멈추거나 화면이 오류로 덮이면 안 된다. 실패는 {@code last_error}에 남고 설정
 * 화면에 그대로 보인다.
 */
@Service
public class ExternalSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSyncService.class);

    private final ExternalSourceService sourceService;
    private final FeedFetcher fetcher;

    public ExternalSyncService(ExternalSourceService sourceService, FeedFetcher fetcher) {
        this.sourceService = sourceService;
        this.fetcher = fetcher;
    }

    /**
     * 구독 하나를 지금 가져온다.
     *
     * <p>권한 확인은 부르는 쪽이 한다. 스케줄러에는 로그인한 사용자가 없다.
     */
    public SyncResultResponse sync(Long sourceId) {
        SyncTarget target = sourceService.loadForSync(sourceId);

        if (!target.active()) {
            return new SyncResultResponse(sourceService.snapshot(sourceId), 0, null);
        }

        try {
            String body = fetcher.fetch(target.feedUrl());
            List<IcsEvent> events = IcsParser.parse(body, target.zone());

            Instant now = Instant.now();
            List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                    events,
                    ExternalSourceService.windowFrom(now),
                    ExternalSourceService.windowTo(now));

            int imported = sourceService.applySync(sourceId, occurrences);
            log.info("피드를 가져왔다: source={} 일정={} 회차={}", sourceId, events.size(), imported);
            return new SyncResultResponse(sourceService.snapshot(sourceId), imported, null);

        } catch (FeedFetcher.FeedException e) {
            // 사용자에게 그대로 보여줘도 되는 문구다
            return failed(sourceId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("피드 동기화에 실패했다: source={}", sourceId, e);
            return failed(sourceId, "피드를 읽지 못했습니다");
        }
    }

    /**
     * 주기가 된 구독을 전부 가져온다.
     *
     * <p>5분마다 깨어나 <b>각 구독의 주기</b>를 확인한다. 깨어나는 주기와 가져오는
     * 주기는 다른 것이다. 5분마다 전부 가져오면 구글 쪽에서 차단당한다.
     *
     * <p>{@code fixedDelay}라 앞 회차가 끝난 뒤에 다음이 시작된다. 피드가 느려도
     * 작업이 겹쳐 쌓이지 않는다.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void syncDue() {
        List<Long> due;
        try {
            due = sourceService.dueSourceIds(Instant.now());
        } catch (RuntimeException e) {
            log.warn("동기화 대상을 고르지 못했다", e);
            return;
        }
        for (Long sourceId : due) {
            try {
                sync(sourceId);
            } catch (RuntimeException e) {
                // sync()가 이미 삼키지만, 여기서 한 건이 새어 나와도 나머지는 돈다
                log.warn("구독 동기화 중 예외: source={}", sourceId, e);
            }
        }
    }

    private SyncResultResponse failed(Long sourceId, String message) {
        sourceService.failSync(sourceId, message);
        return new SyncResultResponse(sourceService.snapshot(sourceId), 0, message);
    }
}
