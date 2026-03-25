CREATE TABLE IF NOT EXISTS calendar_weekly_rules (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    schedule_type_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (calendar_id, day_of_week)
);

CREATE INDEX IF NOT EXISTS idx_calendar_weekly_rules_calendar
    ON calendar_weekly_rules(calendar_id, day_of_week);
