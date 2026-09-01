package io.github.codeonleo.leoshift.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 공유 관리 API의 요청 · 응답. */
public final class ShareDtos {

    private ShareDtos() {
    }

    /**
     * 화면이 다루는 공개 단계.
     *
     * <p>저장은 두 축(어느 캘린더를 공유하는가 × {@code visibility})으로 나뉘는데,
     * 사용자가 생각하는 단위는 "저 사람에게 어디까지"라는 한 덩어리다.
     * 그 번역을 서비스가 맡고, 화면과 API는 이 단계만 다룬다.
     *
     * <p>설계 문서의 세 번째 단계인 {@code 바쁨만}(BUSY_ONLY)은 아직 없다.
     * 스키마와 해석 경로는 이미 그 값을 처리하므로, 여기에 상수를 하나 늘리고
     * {@code SharingService.calendarsFor}에 분기를 더하면 붙는다.
     */
    public enum ShareLevel {
        /** 근무 캘린더만. 직장 · 프로젝트용. 개인 일정은 애초에 나가지 않는다. */
        WORK_ONLY,
        /** 전부. 배우자 · 가족용. */
        FULL
    }

    public enum TargetType { GROUP, USER }

    public record SetShareRequest(
            @NotNull(message = "공유 대상을 골라 주세요")
            TargetType targetType,

            /** targetType이 GROUP일 때. */
            Long groupId,

            /** targetType이 USER일 때. */
            @Email(message = "이메일 형식이 아닙니다")
            String email,

            @NotNull(message = "공개 단계를 골라 주세요")
            ShareLevel level) {
    }

    /**
     * 공유 관리 화면의 한 줄.
     *
     * <p>"직장 · 근무만 · 5명"처럼 대상 하나가 한 줄이다. 실제 저장은 캘린더마다
     * 한 행이지만 그건 화면이 알 필요 없다.
     *
     * @param status  개인 공유는 상대가 수락해야 유효하다. 그룹 공유는 항상 ACCEPTED
     * @param pending 아직 수락되지 않은 개인 공유. 화면에서 "대기 중"으로 표시한다
     */
    public record ShareTargetResponse(
            String targetType, Long targetId, String name, String email,
            String level, String status, boolean pending,
            long memberCount, int calendarCount) {
    }

    /** 남이 나에게 보낸 공유. 수락해야 내 캘린더 목록에 들어온다. */
    public record IncomingShareResponse(
            Long id, Long calendarId, String calendarName,
            String ownerName, String ownerEmail,
            String permission, String visibility) {
    }

    /**
     * @param personalCalendarCount 근무 캘린더가 아닌 내 캘린더 수.
     *                              <b>0이면 "근무만"과 "전체"가 같은 것을 공유한다.</b>
     *                              화면은 그때 단계 선택을 내보내지 않고 무엇이 나가는지만
     *                              알린다 — 골라도 아무것도 달라지지 않는 선택지를
     *                              보여주면 설정이 안 먹은 것처럼 읽힌다
     */
    public record ShareOverviewResponse(
            List<ShareTargetResponse> targets,
            List<IncomingShareResponse> incoming,
            int workCalendarCount,
            int personalCalendarCount) {
    }
}
