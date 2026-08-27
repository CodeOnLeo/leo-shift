package io.github.codeonleo.leoshift.schedule.preset;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 프리셋이 요구하는 일정 타입 하나.
 *
 * <p>프리셋을 고르면 이 명세대로 {@code schedule_types} 행이 만들어진다.
 * 기존 흐름은 사용자가 코드를 먼저 하나씩 만들고 그다음 패턴을 짜야 해서
 * 첫 설정 장벽이 높았다.
 */
public record ScheduleTypeSpec(
        String code,
        String name,
        String color,
        Category category,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight,
        boolean halfDay
) {

    /** DB의 {@code schedule_types.category}와 같은 값이어야 한다. */
    public enum Category {
        /** 실제로 일하는 시간 */
        WORK,
        /** 쉬는 날. 비번·휴무 */
        OFF,
        /** 휴가. 연차·반차 등 */
        LEAVE
    }

    public ScheduleTypeSpec {
        Objects.requireNonNull(category, "category는 필수다");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code는 필수다");
        }
        if (!code.equals(code.toUpperCase())) {
            throw new IllegalArgumentException("code는 대문자여야 한다: " + code);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수다: " + code);
        }
        // DB의 schedule_types_worktime_chk와 같은 규칙
        if (category == Category.WORK && (startTime == null || endTime == null)) {
            throw new IllegalArgumentException("WORK 타입은 시작·종료 시각이 필요하다: " + code);
        }
        if (halfDay && category != Category.LEAVE) {
            throw new IllegalArgumentException("반차는 LEAVE 타입에만 쓸 수 있다: " + code);
        }
    }

    public boolean countsAsWork() {
        return category == Category.WORK;
    }
}
