package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 캘린더 관리 API. */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    public record SaveCalendarRequest(
            @NotBlank(message = "캘린더 이름을 입력해 주세요")
            @Size(max = 100, message = "캘린더 이름은 100자를 넘을 수 없습니다")
            String name,

            @Size(max = 500, message = "설명은 500자를 넘을 수 없습니다")
            String description,

            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색은 #RRGGBB 형식이어야 합니다")
            String color) {
    }

    /**
     * 내 캘린더 한 줄.
     *
     * @param removable 지울 수 있는가. 마지막 캘린더는 지울 수 없다
     */
    public record MyCalendarResponse(
            Long id, String name, String description, String color,
            String kind, boolean isDefault, boolean removable) {

        public static MyCalendarResponse from(Calendar calendar, boolean removable) {
            return new MyCalendarResponse(
                    calendar.getId(), calendar.getName(), calendar.getDescription(),
                    calendar.getColor(), calendar.getKind().name(),
                    calendar.isDefault(), removable);
        }
    }
}
