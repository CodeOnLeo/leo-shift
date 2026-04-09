WITH general_rule_calendars AS (
    SELECT c.id
    FROM calendars c
    JOIN calendar_weekly_rules r ON r.calendar_id = c.id
    LEFT JOIN calendar_patterns p ON p.calendar_id = c.id
    GROUP BY c.id
    HAVING COUNT(p.id) = 0
       AND COUNT(r.id) = 7
       AND SUM(CASE WHEN r.day_of_week BETWEEN 1 AND 5 AND r.schedule_type_code = 'WORK' THEN 1 ELSE 0 END) = 5
       AND SUM(CASE WHEN r.day_of_week IN (6, 7) AND r.schedule_type_code = 'OFF' THEN 1 ELSE 0 END) = 2
)
UPDATE calendars
SET pattern_enabled = FALSE
WHERE id IN (SELECT id FROM general_rule_calendars);
