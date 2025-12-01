-- Align DB schema with current JPA models (calendar_shares, user_settings, users, calendars, push_subscriptions, roles)
DO $$
BEGIN
    -- ===== calendar_shares =====
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'permission'
    ) THEN
        ALTER TABLE calendar_shares ADD COLUMN permission VARCHAR(20);
    END IF;

    -- Migrate legacy role -> permission then drop role
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'role'
    ) THEN
        UPDATE calendar_shares SET permission = COALESCE(permission, role);
        ALTER TABLE calendar_shares DROP COLUMN role;
    END IF;

    -- Rename invited_at -> shared_at if needed
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'invited_at'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'shared_at'
    ) THEN
        ALTER TABLE calendar_shares RENAME COLUMN invited_at TO shared_at;
    END IF;

    -- Rename accepted_at -> responded_at if needed
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'accepted_at'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'responded_at'
    ) THEN
        ALTER TABLE calendar_shares RENAME COLUMN accepted_at TO responded_at;
    END IF;

    -- Ensure shared_at/responded_at columns exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'shared_at'
    ) THEN
        ALTER TABLE calendar_shares ADD COLUMN shared_at TIMESTAMP NOT NULL DEFAULT NOW();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendar_shares' AND column_name = 'responded_at'
    ) THEN
        ALTER TABLE calendar_shares ADD COLUMN responded_at TIMESTAMP;
    END IF;

    -- Enforce defaults / not nulls
    UPDATE calendar_shares SET permission = 'VIEW' WHERE permission IS NULL;
    ALTER TABLE calendar_shares ALTER COLUMN permission SET NOT NULL;

    ALTER TABLE calendar_shares ALTER COLUMN status SET DEFAULT 'PENDING';
    UPDATE calendar_shares SET status = 'PENDING' WHERE status IS NULL;
    ALTER TABLE calendar_shares ALTER COLUMN status SET NOT NULL;

    -- ===== user_settings =====
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_settings' AND column_name = 'notification_minutes'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_settings' AND column_name = 'default_notification_minutes'
    ) THEN
        ALTER TABLE user_settings RENAME COLUMN notification_minutes TO default_notification_minutes;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_settings' AND column_name = 'default_notification_minutes'
    ) THEN
        ALTER TABLE user_settings ADD COLUMN default_notification_minutes INT;
    END IF;

    UPDATE user_settings SET default_notification_minutes = 60 WHERE default_notification_minutes IS NULL;
    ALTER TABLE user_settings ALTER COLUMN default_notification_minutes SET NOT NULL;

    -- ===== users =====
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'picture_url'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'profile_image_url'
    ) THEN
        ALTER TABLE users RENAME COLUMN picture_url TO profile_image_url;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'password'
    ) THEN
        ALTER TABLE users ADD COLUMN password VARCHAR(255);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'provider'
    ) THEN
        ALTER TABLE users ADD COLUMN provider VARCHAR(50);
    END IF;
    UPDATE users SET provider = COALESCE(provider, 'LOCAL');
    ALTER TABLE users ALTER COLUMN provider SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'provider_id'
    ) THEN
        ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE users ADD COLUMN created_at TIMESTAMP;
    END IF;
    UPDATE users SET created_at = COALESCE(created_at, NOW());
    ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'last_login_at'
    ) THEN
        ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'enabled'
    ) THEN
        ALTER TABLE users ADD COLUMN enabled BOOLEAN;
    END IF;
    UPDATE users SET enabled = COALESCE(enabled, TRUE);
    ALTER TABLE users ALTER COLUMN enabled SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'nickname'
    ) THEN
        ALTER TABLE users ADD COLUMN nickname VARCHAR(50);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'color_tag'
    ) THEN
        ALTER TABLE users ADD COLUMN color_tag VARCHAR(16);
    END IF;

    -- user_roles 테이블 보장 + 기본 USER 롤 채우기
    CREATE TABLE IF NOT EXISTS user_roles (
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        role VARCHAR(50),
        PRIMARY KEY (user_id, role)
    );
    INSERT INTO user_roles (user_id, role)
    SELECT u.id, 'USER'
    FROM users u
    WHERE NOT EXISTS (
        SELECT 1 FROM user_roles r WHERE r.user_id = u.id
    );

    -- ===== calendars =====
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendars' AND column_name = 'description'
    ) THEN
        ALTER TABLE calendars ADD COLUMN description VARCHAR(255);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendars' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE calendars ADD COLUMN created_at TIMESTAMP;
    END IF;
    UPDATE calendars SET created_at = COALESCE(created_at, NOW());
    ALTER TABLE calendars ALTER COLUMN created_at SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendars' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE calendars ADD COLUMN updated_at TIMESTAMP;
    END IF;
    UPDATE calendars SET updated_at = COALESCE(updated_at, created_at, NOW());

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'calendars' AND column_name = 'pattern_enabled'
    ) THEN
        ALTER TABLE calendars ADD COLUMN pattern_enabled BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;

    -- ===== push_subscriptions =====
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'push_subscriptions' AND column_name = 'p256dh_key'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'push_subscriptions' AND column_name = 'p256dh'
    ) THEN
        ALTER TABLE push_subscriptions RENAME COLUMN p256dh_key TO p256dh;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'push_subscriptions' AND column_name = 'auth_key'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'push_subscriptions' AND column_name = 'auth'
    ) THEN
        ALTER TABLE push_subscriptions RENAME COLUMN auth_key TO auth;
    END IF;
END $$;
