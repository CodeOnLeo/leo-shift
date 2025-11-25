ALTER TABLE users
    ADD COLUMN IF NOT EXISTS color_tag VARCHAR(16);

-- 기존 사용자 색상 태그 백필: 이메일/이름 해시 기반 팔레트 선택
WITH palette(idx, color) AS (
    VALUES
        (1, '#0A84FF'),
        (2, '#5E5CE6'),
        (3, '#32D74B'),
        (4, '#FF9F0A'),
        (5, '#FF375F'),
        (6, '#64D2FF'),
        (7, '#FFD60A'),
        (8, '#A2845E')
)
UPDATE users u
SET color_tag = p.color
FROM palette p
WHERE u.color_tag IS NULL
  AND p.idx = ((('x' || substr(md5(coalesce(u.email, u.name, '')), 1, 8))::bit(32)::int % 8) + 1);
