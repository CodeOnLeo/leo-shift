package io.github.codeonleo.leoshift.event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 반복 규칙. RFC 5545 RRULE의 실사용 부분집합이다.
 *
 * <p>지원하는 것은 매일 · 매주(요일 지정) · 매월(같은 날짜) · 매년과 간격 · 횟수다.
 * 실사용의 대부분이 여기 들어간다. "매월 셋째 주 화요일" 같은 건 뒤로 미뤘지만,
 * <b>저장은 표준 RRULE 문자열</b>이라 나중에 넓히기 쉽고 ICS 내보내기와도 바로 맞는다.
 *
 * <p>종료 시각은 이 문자열이 아니라 {@code events.recurrence_end} 컬럼에 둔다.
 * "이 기간에 회차가 있을 수 있는 반복 일정"을 DB가 골라내야 하는데, RRULE 문자열
 * 안의 UNTIL은 인덱스로 걸 수 없기 때문이다.
 *
 * <p>근무 규칙과는 다른 원시 타입이다. 근무는 {@code (기준일, 주기, 시퀀스)}로
 * 하루에 하나가 통째로 순환하고, 일정은 하루에 여럿이며 건마다 따로 반복한다.
 * 둘을 한 모델로 합치려 하면 양쪽 다 망가진다.
 */
public record RecurrenceRule(Frequency frequency, int interval, Set<DayOfWeek> byDay, Integer count) {

    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /**
     * 전개 횟수 상한.
     *
     * <p>{@code COUNT}를 지키려면 조회 구간이 아니라 <b>시리즈 시작부터</b> 세야 한다.
     * 10년 전에 시작한 매일 반복이라도 4천 번이면 닿으므로 실사용에는 넉넉하고,
     * 잘못된 규칙이 서버를 붙잡는 것은 막는다.
     */
    private static final int MAX_STEPS = 4000;

    /** BYDAY 약어. RFC 5545 순서다. */
    private static final List<String> DAY_CODES = List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU");

    public RecurrenceRule {
        if (frequency == null) {
            throw new IllegalArgumentException("반복 주기가 없습니다");
        }
        if (interval < 1) {
            throw new IllegalArgumentException("반복 간격은 1 이상이어야 합니다");
        }
        if (count != null && count < 1) {
            throw new IllegalArgumentException("반복 횟수는 1 이상이어야 합니다");
        }
        byDay = byDay == null || byDay.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(byDay));
        if (!byDay.isEmpty() && frequency != Frequency.WEEKLY) {
            // 조용히 무시하면 "화·목 매월"처럼 저장은 되는데 다르게 도는 규칙이 생긴다.
            throw new IllegalArgumentException("요일 지정은 매주 반복에서만 쓸 수 있습니다");
        }
    }

    public static RecurrenceRule weekly(int interval, Set<DayOfWeek> days) {
        return new RecurrenceRule(Frequency.WEEKLY, interval, days, null);
    }

    // ---------------------------------------------------------------- 파싱

    /** {@code FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=10} */
    public static RecurrenceRule parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("반복 규칙이 비어 있습니다");
        }
        Frequency frequency = null;
        int interval = 1;
        Integer count = null;
        Set<DayOfWeek> days = new LinkedHashSet<>();

        for (String part : text.trim().toUpperCase().split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int equals = part.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("반복 규칙 형식이 아닙니다: " + part);
            }
            String key = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();

            switch (key) {
                case "FREQ" -> frequency = frequencyOf(value);
                case "INTERVAL" -> interval = positiveInt(value, "반복 간격");
                case "COUNT" -> count = positiveInt(value, "반복 횟수");
                case "BYDAY" -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(day -> !day.isEmpty())
                        .forEach(day -> days.add(dayOf(day)));
                // UNTIL은 recurrence_end 컬럼이 담당한다. 문자열에 있으면 무시한다.
                case "UNTIL", "WKST" -> { }
                default -> throw new IllegalArgumentException("지원하지 않는 반복 규칙입니다: " + key);
            }
        }
        return new RecurrenceRule(frequency, interval, days, count);
    }

    public String toRRule() {
        StringBuilder text = new StringBuilder("FREQ=").append(frequency.name());
        if (interval != 1) {
            text.append(";INTERVAL=").append(interval);
        }
        if (!byDay.isEmpty()) {
            text.append(";BYDAY=").append(String.join(",", DAY_CODES.stream()
                    .filter(code -> byDay.contains(dayOf(code)))
                    .toList()));
        }
        if (count != null) {
            text.append(";COUNT=").append(count);
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- 전개

    /**
     * 창 안에 들어오는 회차의 <b>현지 시각</b>을 시간순으로.
     *
     * <p>Instant가 아니라 {@link LocalDateTime}으로 계산하는 것이 중요하다.
     * "매주 화 20:30"은 서머타임이 들어와도 20:30이어야 하는데, 절대 시각에
     * 일주일을 더하면 시계가 한 시간 밀린다. 시간대 변환은 부르는 쪽이 맡는다.
     *
     * @param seriesStart 첫 회차. COUNT는 창이 아니라 여기서부터 센다
     * @param until       마지막 회차 시각. 무한이면 null
     */
    public List<LocalDateTime> occurrences(LocalDateTime seriesStart,
                                           LocalDateTime windowFrom,
                                           LocalDateTime windowTo,
                                           LocalDateTime until) {
        List<LocalDateTime> found = new ArrayList<>();
        LocalTime time = seriesStart.toLocalTime();
        LocalDate windowLast = windowTo.toLocalDate();
        int emitted = 0;

        for (int step = 0; step < MAX_STEPS; step++) {
            LocalDate base = baseDate(seriesStart.toLocalDate(), step);
            if (base != null && base.isAfter(windowLast)) {
                return found;
            }
            for (LocalDate date : datesIn(seriesStart.toLocalDate(), base, step)) {
                LocalDateTime at = date.atTime(time);
                if (at.isBefore(seriesStart)) {
                    continue;
                }
                if (until != null && at.isAfter(until)) {
                    return found;
                }
                emitted++;
                if (count != null && emitted > count) {
                    return found;
                }
                if (!at.isBefore(windowFrom) && !at.isAfter(windowTo)) {
                    found.add(at);
                }
            }
        }
        return found;
    }

    /** 이번 걸음의 기준 날짜. 창을 지났는지 판단하는 데만 쓴다. */
    private LocalDate baseDate(LocalDate seriesDate, int step) {
        long steps = (long) step * interval;
        return switch (frequency) {
            case DAILY -> seriesDate.plusDays(steps);
            case WEEKLY -> seriesDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(steps);
            case MONTHLY -> seriesDate.withDayOfMonth(1).plusMonths(steps);
            case YEARLY -> seriesDate.withDayOfYear(1).plusYears(steps);
        };
    }

    /** 이번 걸음에 실제로 생기는 날짜들. 매주는 한 주에 여러 개가 나올 수 있다. */
    private List<LocalDate> datesIn(LocalDate seriesDate, LocalDate base, int step) {
        return switch (frequency) {
            case DAILY -> List.of(base);
            case WEEKLY -> {
                Set<DayOfWeek> days = byDay.isEmpty() ? Set.of(seriesDate.getDayOfWeek()) : byDay;
                List<LocalDate> week = new ArrayList<>();
                for (int offset = 0; offset < 7; offset++) {
                    LocalDate date = base.plusDays(offset);
                    if (days.contains(date.getDayOfWeek())) {
                        week.add(date);
                    }
                }
                yield week;
            }
            // 31일에 시작한 매월 반복은 31일이 없는 달을 건너뛴다(RFC 5545의 BYMONTHDAY).
            // 30일로 당기면 사용자가 정하지 않은 날에 일정이 생긴다.
            case MONTHLY -> dayInMonth(base, seriesDate.getDayOfMonth());
            case YEARLY -> {
                LocalDate month = base.withMonth(seriesDate.getMonthValue());
                yield dayInMonth(month, seriesDate.getDayOfMonth());
            }
        };
    }

    private static List<LocalDate> dayInMonth(LocalDate month, int dayOfMonth) {
        return dayOfMonth > month.lengthOfMonth()
                ? List.of()
                : List.of(month.withDayOfMonth(dayOfMonth));
    }

    // ---------------------------------------------------------------- 보조

    private static Frequency frequencyOf(String value) {
        try {
            return Frequency.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 반복 주기입니다: " + value);
        }
    }

    private static DayOfWeek dayOf(String code) {
        int index = DAY_CODES.indexOf(code);
        if (index < 0) {
            throw new IllegalArgumentException("요일 형식이 아닙니다: " + code);
        }
        return DayOfWeek.of(index + 1);
    }

    private static int positiveInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "이(가) 숫자가 아닙니다: " + value);
        }
    }
}
