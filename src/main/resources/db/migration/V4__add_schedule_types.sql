CREATE TABLE IF NOT EXISTS schedule_types (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(16),
    start_time TIME,
    end_time TIME,
    counts_as_work BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    is_default_off BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (calendar_id, code)
);

CREATE INDEX IF NOT EXISTS idx_schedule_types_calendar_sort
    ON schedule_types(calendar_id, sort_order, code);

INSERT INTO schedule_types (
    calendar_id,
    code,
    name,
    color,
    start_time,
    end_time,
    counts_as_work,
    sort_order,
    is_default_off
)
SELECT
    c.id,
    defaults.code,
    defaults.name,
    defaults.color,
    defaults.start_time,
    defaults.end_time,
    defaults.counts_as_work,
    defaults.sort_order,
    defaults.is_default_off
FROM calendars c
CROSS JOIN (
    VALUES
        ('D', '주간', '#2563EB', TIME '06:00:00', TIME '14:00:00', TRUE, 10, FALSE),
        ('A', '오후', '#F97316', TIME '14:00:00', TIME '22:00:00', TRUE, 20, FALSE),
        ('N', '야간', '#7C3AED', TIME '22:00:00', TIME '06:00:00', TRUE, 30, FALSE),
        ('V', '연차', '#14B8A6', NULL, NULL, FALSE, 40, FALSE),
        ('O', '휴무', '#94A3B8', NULL, NULL, FALSE, 50, TRUE)
) AS defaults(code, name, color, start_time, end_time, counts_as_work, sort_order, is_default_off)
WHERE NOT EXISTS (
    SELECT 1
    FROM schedule_types st
    WHERE st.calendar_id = c.id
      AND st.code = defaults.code
);
