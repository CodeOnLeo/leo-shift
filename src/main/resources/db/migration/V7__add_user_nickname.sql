-- 사용자 닉네임 필드 추가
ALTER TABLE users ADD COLUMN nickname VARCHAR(50);

-- 기존 사용자들의 닉네임을 name으로 초기화
UPDATE users SET nickname = name WHERE nickname IS NULL;
