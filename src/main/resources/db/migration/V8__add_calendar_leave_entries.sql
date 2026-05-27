CREATE TABLE IF NOT EXISTS calendar_leave_entries (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    target_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    leave_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (calendar_id, date, target_user_id)
);

CREATE INDEX IF NOT EXISTS idx_calendar_leave_entries_calendar_date
    ON calendar_leave_entries(calendar_id, date);

CREATE INDEX IF NOT EXISTS idx_calendar_leave_entries_target_user
    ON calendar_leave_entries(target_user_id);
