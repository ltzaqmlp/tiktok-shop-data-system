ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS sys_user_username_key;
CREATE UNIQUE INDEX uq_sys_user_active_username ON sys_user(username) WHERE deleted_at IS NULL;
