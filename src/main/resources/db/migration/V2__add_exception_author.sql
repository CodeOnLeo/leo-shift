-- 예외(메모) 작성자 및 타임스탬프 컬럼 추가
ALTER TABLE exceptions
    ADD COLUMN IF NOT EXISTS author_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- 기존 데이터 백필: 작성자를 캘린더 소유자로 설정
UPDATE exceptions e
SET author_id = c.owner_id
FROM calendars c
WHERE e.calendar_id = c.id
  AND e.author_id IS NULL;

-- 타임스탬프 백필
UPDATE exceptions
SET updated_at = created_at
WHERE updated_at IS NULL;

-- 조회 성능 및 유니크 제약 보조 인덱스
CREATE INDEX IF NOT EXISTS idx_exceptions_calendar_date ON exceptions(calendar_id, date);
