package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService.AccessDeniedException;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.web.dto.CalendarDtos.MyCalendarResponse;
import io.github.codeonleo.leoshift.web.dto.CalendarDtos.SaveCalendarRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 캘린더 관리.
 *
 * <p>캘린더를 여러 개 두는 이유는 정리가 아니라 <b>공개 범위</b>다. 근무 캘린더만
 * 직장에 공유하면 개인 일정은 애초에 나가지 않는다. 세밀한 권한 제어 없이 공개
 * 범위가 나뉘는 것이 이 구조의 값이고, 그래서 개인 일정은 근무 캘린더가 아니라
 * 별도 캘린더에 쌓여야 한다.
 *
 * <p><b>새로 만드는 캘린더는 전부 일반(GENERAL)이다.</b> 근무 캘린더가 여럿이면
 * "내 근무 패턴"이 어느 것인지 정할 수 없고, 패턴 설정 · 근무 코드 · 그룹 타임라인이
 * 전부 첫 번째를 고르는 임시방편으로 돌아간다. 직장을 두 개 다니는 경우는 그때
 * 제대로 다루는 게 맞다.
 */
@Service
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final CurrentUser currentUser;

    public CalendarService(CalendarRepository calendarRepository, CurrentUser currentUser) {
        this.calendarRepository = calendarRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<MyCalendarResponse> list() {
        List<Calendar> mine = calendarRepository.findOwnedBy(currentUser.id());
        boolean removable = mine.size() > 1;
        return mine.stream().map(calendar -> MyCalendarResponse.from(calendar, removable)).toList();
    }

    @Transactional
    public MyCalendarResponse create(SaveCalendarRequest request) {
        User me = currentUser.require();

        Calendar calendar = calendarRepository.save(Calendar.builder()
                .ownerUser(me)
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .color(request.color())
                .kind(Calendar.Kind.GENERAL)
                .timeZone(me.getTimeZone())
                // 기본 캘린더는 사용자당 하나다(부분 유니크 인덱스). 첫 캘린더일 때만 세운다.
                .isDefault(calendarRepository.findDefaultOf(me.getId()).isEmpty())
                .build());

        return MyCalendarResponse.from(calendar, true);
    }

    @Transactional
    public MyCalendarResponse update(Long calendarId, SaveCalendarRequest request) {
        Calendar calendar = requireOwned(calendarId);

        calendar.setName(request.name().trim());
        calendar.setDescription(blankToNull(request.description()));
        calendar.setColor(request.color());

        return MyCalendarResponse.from(calendar, calendarRepository.findOwnedBy(currentUser.id()).size() > 1);
    }

    /** 기본 캘린더를 옮긴다. 일정을 만들 때 미리 골라져 있는 캘린더다. */
    @Transactional
    public void setDefault(Long calendarId) {
        Calendar target = requireOwned(calendarId);
        if (target.isDefault()) {
            return;
        }
        // 부분 유니크 인덱스가 있으므로 먼저 내리고 세워야 한다.
        calendarRepository.findDefaultOf(currentUser.id()).ifPresent(current -> {
            current.setDefault(false);
            calendarRepository.flush();
        });
        target.setDefault(true);
    }

    /**
     * 캘린더를 지운다.
     *
     * <p>실제로 지우지 않고 {@code deleted_at}만 적는다. 캘린더 하나에 몇 년치 근무와
     * 일정이 매달려 있고, 잘못 눌렀을 때 되돌릴 길이 없으면 안 된다.
     * 접근 판정과 목록은 전부 {@code deleted_at}을 거르므로 즉시 사라진 것처럼 보인다.
     */
    @Transactional
    public void delete(Long calendarId) {
        Calendar calendar = requireOwned(calendarId);

        List<Calendar> mine = calendarRepository.findOwnedBy(currentUser.id());
        if (mine.size() <= 1) {
            throw new IllegalArgumentException("마지막 캘린더는 지울 수 없습니다");
        }

        calendar.setDeletedAt(Instant.now());

        // 기본 캘린더를 지웠으면 남은 것 중 하나가 기본이 돼야 한다.
        // 기본이 없으면 일정을 만들 때 어디에 넣을지 정할 수 없다.
        if (calendar.isDefault()) {
            calendar.setDefault(false);
            calendarRepository.flush();
            mine.stream()
                    .filter(other -> !other.getId().equals(calendarId))
                    .findFirst()
                    .ifPresent(next -> next.setDefault(true));
        }
    }

    /** 내 것이 아닌 캘린더는 관리할 수 없다. 공유받은 캘린더도 마찬가지다. */
    @Transactional(readOnly = true)
    public Calendar requireOwned(Long calendarId) {
        Calendar calendar = calendarRepository.findActiveById(calendarId)
                .orElseThrow(() -> new NotFoundException("캘린더를 찾을 수 없습니다"));

        if (calendar.getOwnerUser() == null
                || !calendar.getOwnerUser().getId().equals(currentUser.id())) {
            throw new AccessDeniedException("이 캘린더를 관리할 권한이 없습니다");
        }
        return calendar;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
