CREATE SEQUENCE IF NOT EXISTS shooting_ticket_no_seq;

CREATE TABLE shooting_ticket (
  id bigserial PRIMARY KEY,
  ticket_no varchar(40) NOT NULL UNIQUE DEFAULT concat('PS', to_char(current_date, 'YYYYMMDD'), '-', lpad(nextval('shooting_ticket_no_seq')::text, 4, '0')),
  created_by bigint NOT NULL REFERENCES sys_user(id),
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  task_type varchar(32) NOT NULL CHECK(task_type IN ('SCRIPT_SHOOT','LIBRARY','RESHOOT')),
  shot_requirement text NOT NULL,
  planned_valid_shot_count bigint NOT NULL CHECK(planned_valid_shot_count >= 0),
  deadline timestamptz NOT NULL,
  shooter_id bigint REFERENCES sys_user(id),
  sku varchar(500) NOT NULL DEFAULT '',
  actual_valid_shot_count bigint CHECK(actual_valid_shot_count IS NULL OR actual_valid_shot_count >= 0),
  material_notes text NOT NULL DEFAULT '',
  actual_delivered_at timestamptz,
  status varchar(32) NOT NULL DEFAULT 'PENDING_SHOOT' CHECK(status IN ('PENDING_SHOOT','PENDING_EDITOR_REVIEW','PENDING_DEPT_REVIEW','APPROVED')),
  rejection_stage varchar(20) CHECK(rejection_stage IN ('EDITOR','DEPT')),
  rejection_reason text NOT NULL DEFAULT '',
  editor_reviewer_id bigint REFERENCES sys_user(id),
  editor_reviewed_at timestamptz,
  dept_reviewer_id bigint REFERENCES sys_user(id),
  dept_reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_shooting_ticket_status ON shooting_ticket(status, created_at DESC);
CREATE INDEX idx_shooting_ticket_creator ON shooting_ticket(created_by, created_at DESC);
