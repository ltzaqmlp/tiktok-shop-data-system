CREATE TABLE sys_remember_token (
  selector varchar(64) PRIMARY KEY,
  token_hash varchar(64) NOT NULL,
  user_id bigint NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  session_version int NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_sys_remember_token_expires ON sys_remember_token(expires_at);
