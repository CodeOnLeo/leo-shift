CREATE TABLE IF NOT EXISTS external_calendar_sources (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    feed_url TEXT NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#5E5CE6',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS external_calendar_events (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES external_calendar_sources(id) ON DELETE CASCADE,
    uid VARCHAR(512) NOT NULL,
    title VARCHAR(500) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT TRUE,
    location VARCHAR(500),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (source_id, uid, start_date)
);

CREATE INDEX IF NOT EXISTS idx_external_calendar_sources_calendar
    ON external_calendar_sources(calendar_id);

CREATE INDEX IF NOT EXISTS idx_external_calendar_events_source_range
    ON external_calendar_events(source_id, start_date, end_date);
