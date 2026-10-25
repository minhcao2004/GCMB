-- Preserve existing assignments and the three-manager limit from V18.
ALTER TABLE gcmb.branch_manager_assignment DROP CONSTRAINT branch_manager_assignment_user_id_key;
CREATE INDEX ix_branch_manager_assignment_user ON gcmb.branch_manager_assignment(user_id);

ALTER TABLE gcmb.auth_token ADD COLUMN selected_branch_id uuid
    REFERENCES gcmb.branch(id) ON DELETE RESTRICT;
