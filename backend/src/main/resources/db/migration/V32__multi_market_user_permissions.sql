CREATE TABLE sys_user_market (
  user_id bigint NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, market_code)
);

INSERT INTO sys_user_market(user_id, market_code)
SELECT id, market_code
FROM sys_user
WHERE market_code IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX idx_sys_user_market_market ON sys_user_market(market_code, user_id);
