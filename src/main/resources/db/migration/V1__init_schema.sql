-- LeoShift 초기 데이터베이스 스키마
-- 모든 테이블, 인덱스, 제약조건을 포함한 통합 스키마

-- users 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    picture_url TEXT,
    nickname VARCHAR(50),
    color_tag VARCHAR(16),
    UNIQUE(email)
);

-- calendars 테이블
CREATE TABLE IF NOT EXISTS calendars (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    pattern_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- user_settings 테이블
CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    default_calendar_id BIGINT REFERENCES calendars(id),
    pattern_codes TEXT,
    pattern_start_date DATE,
    notification_minutes INT
);

-- exceptions 테이블 (기념일 및 근무 예외)
CREATE TABLE IF NOT EXISTS exceptions (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    author_id BIGINT REFERENCES users(id),
    date DATE NOT NULL,
    custom_code VARCHAR(10),
    memo TEXT,
    anniversary_memo TEXT,
    repeat_yearly BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(calendar_id, date)
);

-- calendar_patterns 테이블
CREATE TABLE IF NOT EXISTS calendar_patterns (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    pattern_codes TEXT NOT NULL,
    pattern_start_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (calendar_id, pattern_start_date)
);

-- calendar_shares 테이블
CREATE TABLE IF NOT EXISTS calendar_shares (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_at TIMESTAMP NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMP,
    UNIQUE(calendar_id, user_id)
);

-- push_subscriptions 테이블
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL,
    p256dh_key TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, endpoint)
);

-- day_memos 테이블 (다중 사용자 메모)
CREATE TABLE IF NOT EXISTS day_memos (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    memo TEXT,
    memo_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- ===== 인덱스 생성 =====

-- calendars 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calendars_owner ON calendars(owner_id);

-- user_settings 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_user_settings_default_calendar ON user_settings(default_calendar_id);

-- exceptions 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_exceptions_calendar_date ON exceptions(calendar_id, date);
CREATE INDEX IF NOT EXISTS idx_exceptions_yearly ON exceptions(calendar_id, repeat_yearly) WHERE repeat_yearly = true;
CREATE INDEX IF NOT EXISTS idx_exceptions_calendar_month ON exceptions(calendar_id, repeat_yearly, EXTRACT(MONTH FROM date)) WHERE repeat_yearly = true;

-- calendar_patterns 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calendar_patterns_effective ON calendar_patterns(calendar_id, pattern_start_date DESC);

-- calendar_shares 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calendar_shares_user ON calendar_shares(user_id, status);
CREATE INDEX IF NOT EXISTS idx_calendar_shares_calendar ON calendar_shares(calendar_id);

-- day_memos 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calendar_date ON day_memos(calendar_id, date);
CREATE INDEX IF NOT EXISTS idx_author ON day_memos(author_id);
