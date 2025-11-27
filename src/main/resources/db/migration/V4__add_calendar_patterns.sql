CREATE TABLE IF NOT EXISTS calendar_patterns (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    pattern_codes TEXT NOT NULL,
    pattern_start_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (calendar_id, pattern_start_date)
);

-- 기존 사용자 설정에 패턴이 있으면 각 사용자의 캘린더에 동일 패턴을 백필
INSERT INTO calendar_patterns (calendar_id, pattern_codes, pattern_start_date)
SELECT c.id, us.pattern_codes, us.pattern_start_date
FROM calendars c
JOIN user_settings us ON c.owner_id = us.user_id
WHERE us.pattern_codes IS NOT NULL
  AND us.pattern_start_date IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM calendar_patterns cp
    WHERE cp.calendar_id = c.id
      AND cp.pattern_start_date = us.pattern_start_date
  );
