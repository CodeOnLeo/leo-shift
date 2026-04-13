CREATE TABLE IF NOT EXISTS share_groups (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (owner_user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_share_groups_owner
    ON share_groups(owner_user_id);

CREATE TABLE IF NOT EXISTS share_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES share_groups(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_share_group_members_group
    ON share_group_members(group_id);

CREATE INDEX IF NOT EXISTS idx_share_group_members_user
    ON share_group_members(user_id);

CREATE TABLE IF NOT EXISTS calendar_share_grants (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    target_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    target_group_id BIGINT REFERENCES share_groups(id) ON DELETE CASCADE,
    permission VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_calendar_share_grants_target
        CHECK (
            (target_type = 'USER' AND target_user_id IS NOT NULL AND target_group_id IS NULL)
            OR
            (target_type = 'GROUP' AND target_group_id IS NOT NULL AND target_user_id IS NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_calendar_share_grants_user
    ON calendar_share_grants(calendar_id, target_user_id)
    WHERE target_type = 'USER';

CREATE UNIQUE INDEX IF NOT EXISTS uq_calendar_share_grants_group
    ON calendar_share_grants(calendar_id, target_group_id)
    WHERE target_type = 'GROUP';

CREATE INDEX IF NOT EXISTS idx_calendar_share_grants_calendar
    ON calendar_share_grants(calendar_id);

CREATE INDEX IF NOT EXISTS idx_calendar_share_grants_target_user
    ON calendar_share_grants(target_user_id)
    WHERE target_type = 'USER';

CREATE INDEX IF NOT EXISTS idx_calendar_share_grants_target_group
    ON calendar_share_grants(target_group_id)
    WHERE target_type = 'GROUP';
