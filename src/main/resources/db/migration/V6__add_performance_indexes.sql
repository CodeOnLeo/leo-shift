-- 성능 최적화를 위한 인덱스 추가

-- exceptions 테이블: 날짜 범위 조회 최적화
CREATE INDEX IF NOT EXISTS idx_exceptions_calendar_date ON exceptions(calendar_id, date);

-- exceptions 테이블: 연간 반복 메모 조회 최적화
CREATE INDEX IF NOT EXISTS idx_exceptions_yearly ON exceptions(calendar_id, repeat_yearly) WHERE repeat_yearly = true;

-- calendar_shares 테이블: 사용자별 공유 캘린더 조회 최적화
CREATE INDEX IF NOT EXISTS idx_calendar_shares_user ON calendar_shares(user_id, status);

-- calendar_shares 테이블: 캘린더별 공유 목록 조회 최적화
CREATE INDEX IF NOT EXISTS idx_calendar_shares_calendar ON calendar_shares(calendar_id);

-- calendar_patterns 테이블: 효과적인 패턴 조회 최적화
CREATE INDEX IF NOT EXISTS idx_calendar_patterns_effective ON calendar_patterns(calendar_id, pattern_start_date DESC);
