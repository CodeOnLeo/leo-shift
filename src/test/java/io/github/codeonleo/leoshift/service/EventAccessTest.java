package io.github.codeonleo.leoshift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codeonleo.leoshift.AbstractPostgresTest;
import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.web.dto.CalendarDtos.SaveCalendarRequest;
import io.github.codeonleo.leoshift.web.dto.EventDtos.EventInstanceResponse;
import io.github.codeonleo.leoshift.web.dto.EventDtos.SaveEventRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.AddMemberRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.SaveGroupRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.SetShareRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareLevel;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.TargetType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 일정이 어디까지 나가는가.
 *
 * <p>여기서 지키는 것은 <b>"근무만"이 별도의 권한 값이 아니라 근무 캘린더만
 * 공유하는 것</b>이라는 설계다. 개인 일정이 애초에 나가지 않으므로 조회 경로
 * 어딘가에서 필터를 빠뜨려도 샐 데이터가 없다. 반대로 이 전제가 깨지면 —
 * 개인 일정이 근무 캘린더에 쌓이거나 단계가 캘린더를 안 고르게 되면 —
 * 직장 동료에게 "14:00 병원"이 그대로 간다.
 */
@SpringBootTest
@DisplayName("일정 공개 범위")
class EventAccessTest extends AbstractPostgresTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant FROM = at(2026, 3, 2, 0, 0);
    private static final Instant TO = at(2026, 3, 9, 0, 0);

    @Autowired private UserRepository userRepository;
    @Autowired private CalendarRepository calendarRepository;
    @Autowired private CalendarService calendarService;
    @Autowired private EventService eventService;
    @Autowired private GroupService groupService;
    @Autowired private SharingService sharingService;

    private User owner;
    private User coworker;
    private Long groupId;
    private Long workCalendarId;
    private Long personalCalendarId;

    @BeforeEach
    void setUp() {
        owner = newUser("owner");
        coworker = newUser("coworker");

        workCalendarId = calendarRepository.save(Calendar.builder()
                .ownerUser(owner).name("내 근무").kind(Calendar.Kind.WORK).isDefault(true).build())
                .getId();

        loginAs(owner);
        personalCalendarId = calendarService
                .create(new SaveCalendarRequest("개인 일정", null, null)).id();

        groupId = groupService.create(
                new SaveGroupRequest("직장-" + UUID.randomUUID(), Group.Kind.WORKPLACE, null, null)).id();
        groupService.addMember(groupId, new AddMemberRequest(coworker.getEmail(), null));

        eventService.create(workCalendarId, event("근무 인수인계", at(2026, 3, 3, 9, 0)));
        eventService.create(personalCalendarId, event("14:00 병원", at(2026, 3, 3, 14, 0)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("근무만 공유하면 개인 일정은 애초에 나가지 않는다")
    void workOnlyNeverLeaksPersonalEvents() {
        loginAs(owner);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY));

        loginAs(coworker);
        assertThat(titlesVisibleToViewer()).containsExactly("근무 인수인계");
    }

    @Test
    @DisplayName("전체로 올리면 개인 일정도 보이고, 다시 내리면 사라진다")
    void raisingAndLoweringLevel() {
        loginAs(owner);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.FULL));

        loginAs(coworker);
        assertThat(titlesVisibleToViewer()).containsExactlyInAnyOrder("근무 인수인계", "14:00 병원");

        loginAs(owner);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY));

        // 권한이 더해지기만 하면 안 된다. 내리는 것이 실제로 내려가야 한다.
        loginAs(coworker);
        assertThat(titlesVisibleToViewer()).containsExactly("근무 인수인계");
    }

    @Test
    @DisplayName("공유받은 일정은 볼 수만 있다")
    void sharedEventsAreReadOnly() {
        loginAs(owner);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.FULL));
        Long eventId = eventService.range(List.of(personalCalendarId), FROM, TO).get(0).eventId();

        loginAs(coworker);
        assertThat(eventService.range(null, FROM, TO)).allSatisfy(
                instance -> assertThat(instance.canEdit()).isFalse());

        assertThatThrownBy(() -> eventService.update(eventId, event("몰래 수정", at(2026, 3, 3, 14, 0))))
                .isInstanceOf(CalendarAccessService.AccessDeniedException.class);
    }

    @Test
    @DisplayName("볼 수 없는 캘린더를 지정하면 조용히 빼지 않고 막는다")
    void requestingForeignCalendarIsRejected() {
        loginAs(coworker);
        // 조용히 빈 결과를 주면 권한 문제인지 데이터가 없는 건지 구분할 수 없다.
        assertThatThrownBy(() -> eventService.range(List.of(personalCalendarId), FROM, TO))
                .isInstanceOf(CalendarAccessService.AccessDeniedException.class);
    }

    @Test
    @DisplayName("캘린더를 지우면 그 일정도 함께 보이지 않는다")
    void deletingCalendarHidesItsEvents() {
        loginAs(owner);
        assertThat(titlesVisibleToViewer()).hasSize(2);

        calendarService.delete(personalCalendarId);

        assertThat(titlesVisibleToViewer()).containsExactly("근무 인수인계");
    }

    @Test
    @DisplayName("마지막 캘린더는 지울 수 없다")
    void cannotDeleteLastCalendar() {
        loginAs(owner);
        calendarService.delete(personalCalendarId);

        assertThatThrownBy(() -> calendarService.delete(workCalendarId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마지막");
    }

    @Test
    @DisplayName("기본 캘린더를 지우면 남은 캘린더가 기본이 된다")
    void defaultMovesOnDelete() {
        loginAs(owner);
        // 기본이 없으면 일정을 만들 때 어디에 넣을지 정할 수 없다.
        calendarService.delete(workCalendarId);

        assertThat(calendarService.list())
                .filteredOn(calendar -> calendar.id().equals(personalCalendarId))
                .singleElement()
                .satisfies(calendar -> assertThat(calendar.isDefault()).isTrue());
    }

    // ---------------------------------------------------------------- 보조

    private List<String> titlesVisibleToViewer() {
        return eventService.range(null, FROM, TO).stream()
                .map(EventInstanceResponse::title)
                .toList();
    }

    private static SaveEventRequest event(String title, Instant startsAt) {
        return new SaveEventRequest(title, null, null, startsAt, startsAt.plusSeconds(3600),
                false, "Asia/Seoul", null, null);
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), SEOUL).toInstant();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getId(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private User newUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@test.local")
                .name(prefix)
                .build());
    }
}
