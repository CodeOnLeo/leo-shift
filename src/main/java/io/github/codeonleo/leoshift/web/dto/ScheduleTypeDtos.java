package io.github.codeonleo.leoshift.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public final class ScheduleTypeDtos {

    private ScheduleTypeDtos() {
    }

    /**
     * 근무 코드 저장 요청.
     *
     * <p>{@code crossesMidnight}는 받지 않는다. 시작·종료 시각에서 서버가 계산한다.
     * 클라이언트가 잘못 보내면 야간 근무가 자정을 넘지 않는 것으로 저장될 수 있다.
     */
    public record SaveScheduleTypeRequest(
            @NotBlank(message = "코드를 입력해 주세요")
            @Size(max = 32, message = "코드는 32자를 넘을 수 없습니다")
            @Pattern(regexp = "[A-Za-z0-9_]+", message = "코드는 영문·숫자·밑줄만 쓸 수 있습니다")
            String code,

            @NotBlank(message = "이름을 입력해 주세요")
            @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다")
            String name,

            @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "색은 #RRGGBB 형식이어야 합니다")
            String color,

            @NotNull(message = "종류를 골라 주세요") String category,

            LocalTime startTime,
            LocalTime endTime,
            boolean halfDay,
            Integer sortOrder) {
    }

    /** 코드를 지울 수 있는지, 없다면 왜 안 되는지. */
    public record ScheduleTypeUsageResponse(
            String code, boolean inUse, boolean usedByRule,
            boolean usedByLeave, boolean usedByOverride) {
    }
}
