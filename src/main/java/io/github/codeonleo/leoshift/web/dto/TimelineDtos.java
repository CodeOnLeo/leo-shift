package io.github.codeonleo.leoshift.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 그룹 타임라인 응답.
 *
 * <p><b>메모는 담지 않는다.</b> 이 화면이 답하는 질문은 "그날 누가 있고 누가
 * 없는가"뿐이다. 근무 캘린더의 메모에도 "오전 반차 · 병원" 같은 사적인 사유가
 * 들어가므로, 화면이 쓰지 않는 값을 응답에 실어 보내지 않는다.
 */
public final class TimelineDtos {

    private TimelineDtos() {
    }

    /**
     * 한 사람의 하루.
     *
     * @param member   그날 이 그룹에 소속돼 있었는가. 아니면 격자를 비운다
     * @param category WORK · OFF · LEAVE. 사람마다 코드가 달라도 이 값으로 집계된다
     */
    public record TimelineDayResponse(
            LocalDate date, String code, String name, String color,
            String category, boolean member) {

        public static TimelineDayResponse outside(LocalDate date) {
            return new TimelineDayResponse(date, null, null, null, null, false);
        }

        public static TimelineDayResponse blank(LocalDate date) {
            return new TimelineDayResponse(date, null, null, null, null, true);
        }
    }

    /**
     * @param self   보는 사람 자신의 줄. 맨 위에 놓는다
     * @param shared 이 사람이 캘린더를 이 그룹에 공유했는가. 아니면 줄이 통째로 비어 있다
     */
    public record TimelineRowResponse(
            Long userId, String displayName, String colorTag,
            boolean self, boolean shared,
            List<TimelineDayResponse> days) {
    }

    /**
     * @param workingCount 날짜별 근무 인원. 프로젝트에서 실제로 보고 싶은 값이다
     * @param absentCount  날짜별 휴가 인원
     * @param viewerShared 내 캘린더가 이 그룹에 공유돼 있는가.
     *                     아니면 남들에게 내 줄은 비어 보인다는 안내를 띄운다
     */
    public record TimelineResponse(
            Long groupId, String groupName, String groupKind,
            LocalDate from, LocalDate to,
            List<LocalDate> dates,
            List<TimelineRowResponse> rows,
            List<Integer> workingCount,
            List<Integer> absentCount,
            boolean viewerShared) {
    }
}
