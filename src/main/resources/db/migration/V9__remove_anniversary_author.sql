-- 기념일 메모는 작성자를 표시하지 않도록 author_id를 null로 설정
-- memo가 없고 anniversary_memo만 있는 경우 author_id를 null로 변경
UPDATE exceptions
SET author_id = NULL
WHERE (memo IS NULL OR memo = '')
  AND (anniversary_memo IS NOT NULL AND anniversary_memo != '');
