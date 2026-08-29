package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.ScheduleTypeResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class DayDtos {

    private DayDtos() {
    }

    public record OverrideResponse(Long id, String code, String note, int version) {
    }

    public record LeaveResponse(Long id, LocalDate startDate, LocalDate endDate,
                                String code, String note) {

        /** 하루짜리가 아니면 화면에서 "8/15~8/20 중 하루"라고 알려줘야 한다. */
        public boolean multiDay() {
            return !startDate.equals(endDate);
        }
    }

    /**
     * 하루의 상세.
     *
     * @param code       최종 근무 코드
     * @param source     그 코드가 어디서 왔는지. RULE · LEAVE · OVERRIDE · NONE
     * @param baseCode   예외를 지웠을 때 돌아갈 코드. "원래 야간입니다"를 보여준다
     * @param baseSource 그 기본값의 출처
     */
    public record DayDetailResponse(
            LocalDate date,
            String code,
            String source,
            String note,
            String baseCode,
            String baseSource,
            OverrideResponse override,
            LeaveResponse leave,
            ScheduleTypeResponse scheduleType,
            List<ScheduleTypeResponse> scheduleTypes,
            boolean canEdit) {
    }

    /**
     * 날짜별 예외 저장.
     *
     * @param code    null이면 근무는 그대로 두고 메모만 남긴다
     * @param version 화면이 보고 있던 판. 그 사이 누가 고쳤으면 409로 거절한다.
     *                새로 만드는 경우 null
     */
    public record SaveOverrideRequest(
            String code,
            @Size(max = 2000) String note,
            Integer version) {
    }

    public record SaveLeaveRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull String code,
            @Size(max = 2000) String note) {
    }
}
