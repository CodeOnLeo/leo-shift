-- 추가 성능 최적화 인덱스

-- calendars 테이블: owner별 캘린더 조회 최적화
CREATE INDEX IF NOT EXISTS idx_calendars_owner ON calendars(owner_id);

-- user_settings 테이블: user_id는 이미 primary key이므로 인덱스 있음
-- default_calendar_id 조회 최적화
CREATE INDEX IF NOT EXISTS idx_user_settings_default_calendar ON user_settings(default_calendar_id);

-- exceptions 테이블: 월별 조회를 위한 복합 인덱스 (함수 기반 인덱스 대신)
-- repeat_yearly가 true인 경우 calendar_id와 month 조합으로 빠르게 조회
CREATE INDEX IF NOT EXISTS idx_exceptions_calendar_month ON exceptions(calendar_id, repeat_yearly, EXTRACT(MONTH FROM date)) WHERE repeat_yearly = true;
