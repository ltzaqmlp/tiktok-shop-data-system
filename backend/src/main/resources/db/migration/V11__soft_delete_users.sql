ALTER TABLE sys_user ADD COLUMN deleted_at timestamptz;
CREATE INDEX idx_sys_user_active ON sys_user(id) WHERE deleted_at IS NULL;
