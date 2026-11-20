-- HuyDV: branch rates and employee payroll handoff to accounting.
CREATE TABLE gcmb.branch_employee_pay_rate (
    branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
    employee_id uuid NOT NULL REFERENCES gcmb.employee(id),
    effective_from date NOT NULL,
    hourly_rate numeric(15,0) NOT NULL CHECK (hourly_rate > 0),
    PRIMARY KEY (branch_id, employee_id, effective_from)
);
ALTER TABLE gcmb.payroll_entry
    ADD COLUMN submitted_branch_id uuid REFERENCES gcmb.branch(id);
