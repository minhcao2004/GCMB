-- Sample profiles only: no accounts or passwords are created.
BEGIN;
INSERT INTO gcmb.employee(code,full_name,hired_on,status)
VALUES ('DEMO-NV-001','Phạm Thu Hà',CURRENT_DATE,'ACTIVE'),
       ('DEMO-NV-002','Nguyễn Minh An',CURRENT_DATE,'ACTIVE'),
       ('DEMO-NV-003','Trần Gia Huy',CURRENT_DATE,'ACTIVE')
ON CONFLICT (code) DO NOTHING;
COMMIT;
