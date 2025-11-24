package io.github.codeonleo.leoshift.service;

import java.time.LocalTime;

public enum ShiftCodeDefinition {
    D("주간", LocalTime.of(6, 0), LocalTime.of(14, 0)),
    A("오후", LocalTime.of(14, 0), LocalTime.of(22, 0)),
    N("야간", LocalTime.of(22, 0), LocalTime.of(6, 0)),
    O("휴무", null, null);

    private final String label;
    private final LocalTime start;
    private final LocalTime end;

    ShiftCodeDefinition(String label, LocalTime start, LocalTime end) {
        this.label = label;
        this.start = start;
        this.end = end;
    }

    public String label() {
        return label;
    }

    public LocalTime startTime() {
        return start;
    }

    public LocalTime endTime() {
        return end;
    }

    public String timeRangeLabel() {
        if (this == O || start == null || end == null) {
            return "휴무";
        }
        return start + "–" + end;
    }

    public boolean isWorkingShift() {
        return this != O;
    }

    public static ShiftCodeDefinition fromCode(String code) {
        if (code == null) {
            return O;
        }
        for (ShiftCodeDefinition definition : values()) {
            if (definition.name().equalsIgnoreCase(code)) {
                return definition;
            }
        }
        return O;
    }
}
