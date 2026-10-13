-- Bind each branch manager to exactly one branch for authentication and authorization.
CREATE TABLE gcmb.branch_manager_assignment (
  branch_id uuid PRIMARY KEY,
  user_id uuid NOT NULL UNIQUE,
  assigned_by uuid NOT NULL,
  assigned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_branch_manager_assignment_branch FOREIGN KEY (branch_id)
    REFERENCES gcmb.branch(id) ON DELETE RESTRICT,
  CONSTRAINT fk_branch_manager_assignment_user FOREIGN KEY (user_id)
    REFERENCES gcmb.user_account(id) ON DELETE RESTRICT,
  CONSTRAINT fk_branch_manager_assignment_assigner FOREIGN KEY (assigned_by)
    REFERENCES gcmb.user_account(id) ON DELETE RESTRICT
);

ALTER TABLE gcmb.room
  ADD COLUMN floor_number integer NOT NULL DEFAULT 1 CHECK (floor_number BETWEEN -5 AND 100),
  ADD COLUMN description text NOT NULL DEFAULT '',
  ADD COLUMN status_reason text NOT NULL DEFAULT '',
  ADD COLUMN expected_ready_at timestamptz;

CREATE TABLE gcmb.room_operating_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  previous_status varchar(25) NOT NULL,
  new_status varchar(25) NOT NULL,
  reason text NOT NULL DEFAULT '',
  expected_ready_at timestamptz,
  changed_by uuid NOT NULL,
  changed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (previous_status IN ('AVAILABLE','CLEANING','UNDER_MAINTENANCE','INACTIVE')),
  CHECK (new_status IN ('AVAILABLE','CLEANING','UNDER_MAINTENANCE','INACTIVE')),
  FOREIGN KEY (room_id, branch_id) REFERENCES gcmb.room(id, branch_id) ON DELETE RESTRICT,
  FOREIGN KEY (changed_by) REFERENCES gcmb.user_account(id) ON DELETE RESTRICT
);

CREATE INDEX ix_room_branch_floor ON gcmb.room(branch_id, floor_number, code);
CREATE INDEX ix_room_status_history_room ON gcmb.room_operating_status_history(room_id, changed_at DESC);
