package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarFeedToken;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.event.Event;
import io.github.codeonleo.leoshift.domain.event.EventOccurrence;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.event.EventDefinition;
import io.github.codeonleo.leoshift.event.EventExpander;
import io.github.codeonleo.leoshift.event.EventInstance;
import io.github.codeonleo.leoshift.event.OccurrenceException;
import io.github.codeonleo.leoshift.ics.IcsWriter;
import io.github.codeonleo.leoshift.repository.CalendarFeedTokenRepository;
import io.github.codeonleo.leoshift.repository.EventOccurrenceRepository;
import io.github.codeonleo.leoshift.repository.EventRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 근무표를 읽기 전용 {@code .ics} 주소로 내보낸다.
 *
 * <p>근무표는 이 앱에서 만들고 보기는 각자 익숙한 앱에서 한다. 비용 대비 효과가 가장
 * 큰 기능이라 판단한 자리다 ({@code docs/feature-spec.md} 3.7).
 *
 * <p><b>구독해 온 외부 일정은 다시 내보내지 않는다.</b> 구글에서 받아온 것을 구글로
 * 돌려보내면 같은 일정이 두 번 뜨고, 두 캘린더를 서로 구독하면 무한히 늘어난다.
 * 내보내는 것은 이 앱에서 만든 것 — 근무와 우리 일정 — 뿐이다.
 */
@Service
public class CalendarFeedService {

    /**
     * 내보내는 창.
     *
     * <p>지난 근무도 얼마간 넣는다. 구글 캘린더에서 저번 달을 넘겨 봤을 때 근무만
     * 비어 있으면 구독이 깨진 것처럼 보인다.
     */
    private static final Duration WINDOW_PAST = Duration.ofDays(90);
    private static final Duration WINDOW_FUTURE = Duration.ofDays(400);

    /** 구독하는 쪽에 권하는 갱신 주기. */
    private static final Duration REFRESH = Duration.ofHours(6);

    /** 사용자당 살아 있는 주소 수. 하나면 폐기하고 다시 만들 때 잠깐 끊긴다. */
    private static final int MAX_TOKENS_PER_CALENDAR = 5;

    private final CalendarFeedTokenRepository tokenRepository;
    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final EventRepository eventRepository;
    private final EventOccurrenceRepository occurrenceRepository;
    private final CalendarAccessService accessService;

    public CalendarFeedService(CalendarFeedTokenRepository tokenRepository,
                               ScheduleQueryService scheduleQueryService,
                               ScheduleTypeRepository scheduleTypeRepository,
                               EventRepository eventRepository,
                               EventOccurrenceRepository occurrenceRepository,
                               CalendarAccessService accessService) {
        this.tokenRepository = tokenRepository;
        this.scheduleQueryService = scheduleQueryService;
        this.scheduleTypeRepository = scheduleTypeRepository;
        this.eventRepository = eventRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.accessService = accessService;
    }

    // ---------------------------------------------------------------- 주소 관리

    /**
     * 이 캘린더의 살아 있는 구독 주소.
     *
     * <p>편집 권한을 요구한다. 주소를 아는 사람은 누구나 캘린더를 볼 수 있으므로,
     * 보기 권한만 받은 사람이 주소를 가져가면 공유를 끊어도 계속 볼 수 있게 된다.
     */
    @Transactional(readOnly = true)
    public List<CalendarFeedToken> list(Long calendarId) {
        accessService.requireEdit(calendarId);
        return tokenRepository.findActiveFor(calendarId);
    }

    @Transactional
    public CalendarFeedToken create(Long calendarId, String visibility) {
        Calendar calendar = accessService.requireEdit(calendarId).calendar();

        if (tokenRepository.findActiveFor(calendarId).size() >= MAX_TOKENS_PER_CALENDAR) {
            throw new IllegalArgumentException("주소가 너무 많습니다. 쓰지 않는 것을 먼저 폐기해 주세요");
        }
        return tokenRepository.save(CalendarFeedToken.builder()
                .calendar(calendar)
                .token(UUID.randomUUID())
                .visibility(visibilityOf(visibility))
                .build());
    }

    /**
     * 주소를 폐기한다.
     *
     * <p>지우지 않고 {@code revoked_at}을 적는다. 누가 언제까지 읽을 수 있었는지가
     * 남아야 "그때 그 주소로 새어 나갔나"를 나중에 확인할 수 있다.
     */
    @Transactional
    public void revoke(Long tokenId) {
        CalendarFeedToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("구독 주소를 찾을 수 없습니다"));
        accessService.requireEdit(token.getCalendar().getId());
        if (!token.isRevoked()) {
            token.setRevokedAt(Instant.now());
        }
    }

    // ---------------------------------------------------------------- 내보내기

    /**
     * 토큰으로 {@code .ics} 본문을 만든다.
     *
     * <p><b>로그인하지 않은 요청이 여기로 온다.</b> 구글 서버가 읽어가기 때문이다.
     * 그래서 권한 판단은 오직 토큰 하나로 하고, 폐기된 토큰은 찾히지 않는다.
     *
     * @throws NotFoundException 없거나 폐기된 토큰. 404로 나가야 한다 — 403은
     *                           "주소는 맞다"는 정보를 주는 셈이라 대입 시도에 힌트가 된다
     */
    @Transactional
    public Feed render(UUID token) {
        CalendarFeedToken feedToken = tokenRepository.findUsable(token)
                .orElseThrow(() -> new NotFoundException("구독 주소를 찾을 수 없습니다"));

        Calendar calendar = feedToken.getCalendar();
        if (calendar.getDeletedAt() != null) {
            throw new NotFoundException("구독 주소를 찾을 수 없습니다");
        }
        boolean busyOnly = feedToken.getVisibility() == CalendarShare.Visibility.BUSY_ONLY;
        ZoneId zone = zoneOf(calendar.getTimeZone());

        Instant now = Instant.now();
        LocalDate fromDate = now.minus(WINDOW_PAST).atZone(zone).toLocalDate();
        LocalDate toDate = now.plus(WINDOW_FUTURE).atZone(zone).toLocalDate();

        IcsWriter writer = IcsWriter.calendar(calendar.getName(), zone, REFRESH);
        writeWorkDays(writer, calendar, zone, fromDate, toDate, busyOnly);
        writeEvents(writer, calendar, fromDate, toDate, zone, busyOnly);

        feedToken.setLastUsedAt(now);
        return new Feed(calendar.getName(), writer.finish());
    }

    /** @param fileName 다운로드했을 때 붙는 이름. 캘린더 이름이 그대로 파일 이름이 된다 */
    public record Feed(String fileName, String body) {
    }

    /**
     * 근무를 날짜마다 한 건씩 내보낸다.
     *
     * <p>시각이 있는 코드는 시각 있는 일정으로, 휴무 · 휴가는 종일 일정으로 나간다.
     * 야간처럼 자정을 넘는 코드는 끝나는 시각이 다음 날이다 — 그러지 않으면 구글에서
     * 22:00에 시작해 06:00에 끝나는, 길이가 음수인 일정이 된다.
     */
    private void writeWorkDays(IcsWriter writer, Calendar calendar, ZoneId zone,
                               LocalDate from, LocalDate to, boolean busyOnly) {

        Map<String, ScheduleType> types = new HashMap<>();
        for (ScheduleType type : scheduleTypeRepository.findByCalendar(calendar.getId())) {
            types.put(type.getCode(), type);
        }
        if (types.isEmpty()) {
            return;
        }

        for (ResolvedDay day : scheduleQueryService.resolve(calendar.getId(), from, to)) {
            ScheduleType type = day.code() == null ? null : types.get(day.code());
            if (type == null) {
                continue;
            }
            // 바쁨만 공유는 "언제 일하는지"만 알린다. 휴무와 휴가는 아예 내보내지 않는다.
            if (busyOnly && type.getCategory() != ScheduleType.Category.WORK) {
                continue;
            }
            String uid = "work-" + calendar.getId() + "-" + day.date() + "@leo-shift";
            String summary = busyOnly ? "바쁨" : type.getName();
            String note = busyOnly ? null : day.note();

            if (type.getStartTime() != null && type.getEndTime() != null) {
                LocalDateTime startsAt = day.date().atTime(type.getStartTime());
                LocalDateTime endsAt = (type.isCrossesMidnight() ? day.date().plusDays(1) : day.date())
                        .atTime(type.getEndTime());
                writer.timed(uid, startsAt.atZone(zone).toInstant(), endsAt.atZone(zone).toInstant(),
                        summary, note, null);
            } else {
                writer.allDay(uid, day.date(), day.date().plusDays(1), summary, note);
            }
        }
    }

    /**
     * 이 캘린더의 일정을 회차로 펼쳐 내보낸다.
     *
     * <p>반복을 RRULE 그대로 내보내지 않는 이유는 {@link IcsWriter}에 적어 뒀다.
     * 펼쳐 내보내면 휴강 · 보강 같은 회차 예외가 저절로 반영되는 이점도 있다.
     */
    private void writeEvents(IcsWriter writer, Calendar calendar, LocalDate from, LocalDate to,
                             ZoneId zone, boolean busyOnly) {

        Instant windowFrom = from.atStartOfDay(zone).toInstant();
        Instant windowTo = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Long> calendarIds = List.of(calendar.getId());

        List<Event> events = new ArrayList<>(
                eventRepository.findSingleOccurrences(calendarIds, windowFrom, windowTo));
        List<Event> recurring = eventRepository.findRecurringCandidates(calendarIds, windowFrom, windowTo);
        events.addAll(recurring);
        if (events.isEmpty()) {
            return;
        }

        List<OccurrenceException> exceptions = recurring.isEmpty()
                ? List.of()
                : occurrenceRepository.findByEventIds(recurring.stream().map(Event::getId).toList())
                        .stream().map(EventOccurrence::toDomain).toList();

        List<EventDefinition> definitions = events.stream().map(Event::toDomain).toList();

        for (EventInstance instance : EventExpander.expand(definitions, exceptions, windowFrom, windowTo)) {
            // 휴강은 내보내지 않는다. 우리 화면에서는 "취소됨"으로 남겨 두지만,
            // 남의 캘린더에서는 취소된 수업이 그냥 수업으로 보인다.
            if (instance.isCancelled()) {
                continue;
            }
            String uid = "event-" + instance.eventId()
                    + "-" + instance.occurrenceStart().getEpochSecond() + "@leo-shift";

            if (instance.allDay()) {
                writer.allDay(uid,
                        instance.startsAt().atZone(zone).toLocalDate(),
                        instance.endsAt().atZone(zone).toLocalDate().plusDays(1),
                        busyOnly ? "바쁨" : instance.title(),
                        busyOnly ? null : instance.description());
            } else {
                writer.timed(uid, instance.startsAt(), instance.endsAt(),
                        busyOnly ? "바쁨" : instance.title(),
                        busyOnly ? null : instance.description(),
                        busyOnly ? null : instance.location());
            }
        }
    }

    // ---------------------------------------------------------------- 보조

    private static CalendarShare.Visibility visibilityOf(String value) {
        if (value == null || value.isBlank()) {
            return CalendarShare.Visibility.FULL;
        }
        try {
            return CalendarShare.Visibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 공개 단계입니다: " + value);
        }
    }

    private static ZoneId zoneOf(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (java.time.DateTimeException e) {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
