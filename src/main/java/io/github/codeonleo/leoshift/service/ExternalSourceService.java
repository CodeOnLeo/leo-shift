package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.external.ExternalEvent;
import io.github.codeonleo.leoshift.domain.external.ExternalSource;
import io.github.codeonleo.leoshift.ics.IcsExpander;
import io.github.codeonleo.leoshift.repository.ExternalEventRepository;
import io.github.codeonleo.leoshift.repository.ExternalSourceRepository;
import io.github.codeonleo.leoshift.service.CalendarAccessService.Access;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.ExternalEventResponse;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.ExternalRangeResponse;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.ExternalSourceResponse;
import io.github.codeonleo.leoshift.web.dto.ExternalDtos.SaveExternalSourceRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 캘린더 구독의 저장과 조회.
 *
 * <p>가져오기 자체는 {@link ExternalSyncService}가 한다. 여기는 <b>트랜잭션 안에서
 * 하는 일</b>만 맡는다. HTTP를 트랜잭션 안에서 부르면 원격 서버가 느린 동안 DB
 * 커넥션이 붙잡힌다 — 피드 하나가 느리면 앱 전체가 멈추는 구조가 된다.
 */
@Service
public class ExternalSourceService {

    /**
     * 회차를 펼쳐 담아 두는 창.
     *
     * <p>지난 것도 얼마간 남긴다 — 달력에서 지난달로 넘겨 봤을 때 구독 일정만
     * 사라져 있으면 데이터가 지워진 것처럼 보인다.
     */
    private static final Duration WINDOW_PAST = Duration.ofDays(90);
    private static final Duration WINDOW_FUTURE = Duration.ofDays(400);

    /** 조회 한 번에 볼 수 있는 최대 기간. {@code EventService}와 같은 한도다. */
    private static final Duration MAX_RANGE = Duration.ofDays(400);

    /** 구독에 허용하는 가장 짧은 주기. 스케줄러의 후보 선정 기준이기도 하다. */
    private static final Duration MIN_SYNC_INTERVAL = Duration.ofMinutes(10);

    private final ExternalSourceRepository sourceRepository;
    private final ExternalEventRepository eventRepository;
    private final CalendarAccessService accessService;
    private final FeedFetcher fetcher;

    public ExternalSourceService(ExternalSourceRepository sourceRepository,
                                 ExternalEventRepository eventRepository,
                                 CalendarAccessService accessService,
                                 FeedFetcher fetcher) {
        this.sourceRepository = sourceRepository;
        this.eventRepository = eventRepository;
        this.accessService = accessService;
        this.fetcher = fetcher;
    }

    // ---------------------------------------------------------------- 구독 관리

    @Transactional(readOnly = true)
    public List<ExternalSourceResponse> list(Long calendarId) {
        accessService.requireView(calendarId);
        return present(sourceRepository.findByCalendar(calendarId));
    }

    @Transactional
    public ExternalSource create(Long calendarId, SaveExternalSourceRequest request) {
        Access access = accessService.requireEdit(calendarId);

        // 저장 전에 주소를 본다. 못 쓰는 주소가 DB에 남으면 스케줄러가 매번 실패한다.
        String feedUrl = fetcher.validate(request.feedUrl()).toString();
        if (sourceRepository.findByCalendarIdAndFeedUrl(calendarId, feedUrl).isPresent()) {
            throw new IllegalArgumentException("이미 구독 중인 주소입니다");
        }

        return sourceRepository.save(ExternalSource.builder()
                .calendar(access.calendar())
                .name(request.name().trim())
                .feedUrl(feedUrl)
                .color(blankToNull(request.color()))
                .displayMode(displayMode(request.displayMode()))
                .active(request.active() == null || request.active())
                .syncIntervalMinutes(syncInterval(request.syncIntervalMinutes()))
                .build());
    }

    @Transactional
    public ExternalSource update(Long sourceId, SaveExternalSourceRequest request) {
        ExternalSource source = loadEditable(sourceId);
        String feedUrl = fetcher.validate(request.feedUrl()).toString();

        if (!feedUrl.equals(source.getFeedUrl())) {
            sourceRepository.findByCalendarIdAndFeedUrl(source.getCalendar().getId(), feedUrl)
                    .filter(other -> !other.getId().equals(sourceId))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("이미 구독 중인 주소입니다");
                    });
            // 주소가 바뀌면 예전 피드에서 온 일정은 더 이상 이 구독의 것이 아니다.
            eventRepository.deleteBySourceId(sourceId);
            source.setLastSyncedAt(null);
            source.setLastError(null);
        }

        source.setName(request.name().trim());
        source.setFeedUrl(feedUrl);
        source.setColor(blankToNull(request.color()));
        source.setDisplayMode(displayMode(request.displayMode()));
        source.setActive(request.active() == null || request.active());
        source.setSyncIntervalMinutes(syncInterval(request.syncIntervalMinutes()));
        return source;
    }

    @Transactional
    public void delete(Long sourceId) {
        ExternalSource source = loadEditable(sourceId);
        // external_events는 ON DELETE CASCADE지만, 명시적으로 지워 두면
        // 나중에 소프트 삭제로 바꿔도 이 자리만 보면 된다.
        eventRepository.deleteBySourceId(sourceId);
        sourceRepository.delete(source);
    }

    /**
     * 구독 한 건의 현재 상태.
     *
     * <p><b>권한을 보지 않는다.</b> 부르는 쪽이 이미 확인했거나, 스케줄러처럼 로그인한
     * 사용자가 아예 없는 자리에서 쓴다. 컨트롤러에서 직접 부르지 말 것.
     */
    @Transactional(readOnly = true)
    public ExternalSourceResponse snapshot(Long sourceId) {
        ExternalSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("구독을 찾을 수 없습니다"));
        return present(List.of(source)).get(0);
    }

    // ---------------------------------------------------------------- 동기화 지원

    /** 가져오기에 필요한 값만. 엔티티를 트랜잭션 밖으로 들고 나가지 않는다. */
    public record SyncTarget(Long id, Long calendarId, String feedUrl, ZoneId zone, boolean active) {
    }

    @Transactional(readOnly = true)
    public SyncTarget loadForSync(Long sourceId) {
        ExternalSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("구독을 찾을 수 없습니다"));
        Calendar calendar = source.getCalendar();
        return new SyncTarget(source.getId(), calendar.getId(), source.getFeedUrl(),
                zoneOf(calendar.getTimeZone()), source.isActive());
    }

    /**
     * 가져온 회차로 캐시를 통째로 바꾼다.
     *
     * <p>지운 뒤 넣는다. 차이를 계산해 넣으려면 원격 쪽에서 무엇이 사라졌는지 알아야
     * 하는데, 그건 결국 전부 비교하는 것과 같다. 캐시라서 통째로 갈아도 잃는 게 없다.
     *
     * @return 저장한 회차 수
     */
    @Transactional
    public int applySync(Long sourceId, List<IcsExpander.Occurrence> occurrences) {
        ExternalSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("구독을 찾을 수 없습니다"));

        eventRepository.deleteBySourceId(sourceId);
        // 위 삭제가 DB에 닿기 전에 아래 저장이 나가면 유니크 제약에 걸린다.
        eventRepository.flush();

        // 같은 (uid, 시작시각)이 두 번 오는 피드가 있다. 유니크 제약에 걸리므로 먼저 접는다.
        Map<String, IcsExpander.Occurrence> unique = new LinkedHashMap<>();
        for (IcsExpander.Occurrence occurrence : occurrences) {
            unique.putIfAbsent(occurrence.uid() + " " + occurrence.startsAt(), occurrence);
        }

        List<ExternalEvent> rows = unique.values().stream()
                .map(occurrence -> ExternalEvent.builder()
                        .source(source)
                        .uid(truncate(occurrence.uid(), 512))
                        .title(truncate(occurrence.summary(), 500))
                        .startsAt(occurrence.startsAt())
                        .endsAt(occurrence.endsAt())
                        .allDay(occurrence.allDay())
                        .location(truncate(occurrence.location(), 500))
                        .description(occurrence.description())
                        .build())
                .toList();

        eventRepository.saveAll(rows);
        source.setLastSyncedAt(Instant.now());
        source.setLastError(null);
        return rows.size();
    }

    /**
     * 실패를 기록한다.
     *
     * <p><b>{@code lastSyncedAt}도 함께 올린다.</b> 그러지 않으면 스케줄러가 매 주기마다
     * 같은 실패를 무한히 되풀이한다 — 대상 선정이 "마지막 동기화가 오래된 것"이기 때문이다.
     * 이미 가져와 둔 일정은 지우지 않는다. 오늘 연결이 안 된다고 지난주에 받아둔 일정을
     * 지울 이유가 없다.
     */
    @Transactional
    public void failSync(Long sourceId, String message) {
        sourceRepository.findById(sourceId).ifPresent(source -> {
            source.setLastSyncedAt(Instant.now());
            source.setLastError(truncate(message, 500));
        });
    }

    /**
     * 지금 가져올 차례인 구독.
     *
     * <p>주기가 구독마다 다르므로 DB에서 한 번에 고를 수 없다. 가장 짧은 주기로 후보를
     * 넉넉히 뽑고 각자의 주기는 여기서 본다. 후보 수가 활성 구독 수를 넘지 않으므로
     * 이 정도는 메모리에서 걸러도 된다.
     */
    @Transactional(readOnly = true)
    public List<Long> dueSourceIds(Instant now) {
        return sourceRepository.findDueForSync(now.minus(MIN_SYNC_INTERVAL)).stream()
                .filter(source -> source.getLastSyncedAt() == null
                        || !source.getLastSyncedAt()
                                .isAfter(now.minus(Duration.ofMinutes(source.getSyncIntervalMinutes()))))
                .map(ExternalSource::getId)
                .toList();
    }

    /** 회차를 펼쳐 담는 창의 시작. 동기화 시점 기준이라 매번 조금씩 앞으로 밀린다. */
    public static Instant windowFrom(Instant now) {
        return now.minus(WINDOW_PAST);
    }

    public static Instant windowTo(Instant now) {
        return now.plus(WINDOW_FUTURE);
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 기간에 걸치는 외부 일정 전부. 달력이 이걸 겹쳐 그린다.
     *
     * @param calendarIds 비우면 내가 볼 수 있는 캘린더 전부
     */
    @Transactional(readOnly = true)
    public ExternalRangeResponse range(List<Long> calendarIds, Instant from, Instant to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("종료가 시작보다 빠릅니다");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 기간을 넘었습니다");
        }

        Map<Long, Access> visible = new LinkedHashMap<>();
        for (Access access : accessService.listVisible()) {
            visible.put(access.calendar().getId(), access);
        }
        if (calendarIds != null && !calendarIds.isEmpty()) {
            for (Long calendarId : calendarIds) {
                if (!visible.containsKey(calendarId)) {
                    accessService.requireView(calendarId);
                }
            }
            visible.keySet().retainAll(calendarIds);
        }
        if (visible.isEmpty()) {
            return new ExternalRangeResponse(from, to, List.of());
        }

        List<ExternalSource> sources = sourceRepository.findVisibleByCalendars(List.copyOf(visible.keySet()));
        if (sources.isEmpty()) {
            return new ExternalRangeResponse(from, to, List.of());
        }

        Map<Long, ExternalSource> byId = new HashMap<>();
        sources.forEach(source -> byId.put(source.getId(), source));

        List<ExternalEventResponse> events = eventRepository
                .findInRange(List.copyOf(byId.keySet()), from, to).stream()
                .map(event -> present(event, byId.get(event.getSource().getId()), visible))
                .filter(Objects::nonNull)
                .toList();

        return new ExternalRangeResponse(from, to, events);
    }

    /**
     * 외부 일정 하나를 화면용으로.
     *
     * <p>바쁨만 공유는 여기서도 제목을 지운다. 구독해 둔 개인 캘린더의 제목이
     * 근무만 보기로 공유한 상대에게 새어 나가면 안 된다.
     */
    private ExternalEventResponse present(ExternalEvent event, ExternalSource source, Map<Long, Access> visible) {
        if (source == null) {
            return null;
        }
        Access access = visible.get(source.getCalendar().getId());
        if (access == null) {
            return null;
        }
        boolean busyOnly = access.visibility() == CalendarShare.Visibility.BUSY_ONLY;
        String color = source.getColor() != null ? source.getColor() : source.getCalendar().getColor();

        return new ExternalEventResponse(
                source.getId(), source.getName(), source.getCalendar().getId(),
                color, source.getDisplayMode().name(),
                event.getStartsAt(), event.getEndsAt(), event.isAllDay(),
                busyOnly ? "바쁨" : event.getTitle(),
                busyOnly ? null : event.getDescription(),
                busyOnly ? null : event.getLocation());
    }

    // ---------------------------------------------------------------- 보조

    private List<ExternalSourceResponse> present(List<ExternalSource> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : eventRepository.countBySources(
                sources.stream().map(ExternalSource::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        List<ExternalSourceResponse> responses = new ArrayList<>(sources.size());
        for (ExternalSource source : sources) {
            responses.add(ExternalSourceResponse.from(source, counts.getOrDefault(source.getId(), 0L)));
        }
        return List.copyOf(responses);
    }

    /**
     * 이 구독을 고칠 수 있는지 확인만 한다.
     *
     * <p>{@link ExternalSyncService}는 스케줄러도 쓰는 자리라 권한을 보지 않는다.
     * 사람이 부르는 경로에서는 컨트롤러가 이걸 먼저 부른다.
     */
    @Transactional(readOnly = true)
    public void requireEditable(Long sourceId) {
        loadEditable(sourceId);
    }

    private ExternalSource loadEditable(Long sourceId) {
        ExternalSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("구독을 찾을 수 없습니다"));
        accessService.requireEdit(source.getCalendar().getId());
        return source;
    }

    private static ExternalSource.DisplayMode displayMode(String value) {
        if (value == null || value.isBlank()) {
            return ExternalSource.DisplayMode.BADGE;
        }
        try {
            return ExternalSource.DisplayMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 표시 방식입니다: " + value);
        }
    }

    /** 10분보다 자주 부르면 남의 서버를 두드리는 것이고, 하루보다 뜸하면 쓸모가 없다. */
    private static int syncInterval(Integer minutes) {
        return minutes == null ? 360 : Math.clamp(minutes, 10, 1440);
    }

    private static ZoneId zoneOf(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (java.time.DateTimeException e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
