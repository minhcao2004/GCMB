-- Production branch and account data for GenZ Cinema.
-- This migration replaces fixed UC34-37, UC39-40 and UC44 fixtures while preserving operational history.
-- Cinema prices and combos already come from V3__room_price_catalog.sql.
SET LOCAL search_path TO gcmb, public;

-- Remove the known booking/demo fixture rows in dependency order.
-- Temporarily bypass append-only guards only while deleting these exact test fixtures;
-- Flyway runs this migration transactionally and the guards are restored below.
ALTER TABLE gcmb.booking_event DISABLE TRIGGER immutable_booking_event;
ALTER TABLE gcmb.deposit_entry DISABLE TRIGGER immutable_deposit_entry;
ALTER TABLE gcmb.payment DISABLE TRIGGER immutable_payment;
ALTER TABLE gcmb.audit_event DISABLE TRIGGER immutable_audit;

DELETE FROM gcmb.booking_event
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.deposit_entry
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR deposit_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR payment_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.payment
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR request_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR bank_transaction_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.payment_request
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR branch_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR account_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.bank_transaction
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR external_id LIKE 'DEMO-UC3437-%'
   OR transfer_content LIKE 'DEMO-UC3437-%';

DELETE FROM gcmb.deposit
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.booking_charge
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.room_allocation
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR booking_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.booking
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR code LIKE 'DEMO-UC3437-%';

UPDATE gcmb.financial_account
SET active = FALSE
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR code LIKE 'DEMO-UC3437-%';

DELETE FROM gcmb.employee_assignment
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR employee_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR branch_id::text LIKE '34370000-0000-4000-8000-000000000%';

DELETE FROM gcmb.branch_manager_assignment
WHERE branch_id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR user_id::text IN (
       '34370000-0000-4000-8000-000000000003',
       '44440000-0000-4000-8000-000000000001'
   );

DELETE FROM gcmb.room_operating_status_history
WHERE room_id::text LIKE '34370000-0000-4000-8000-000000000%';

UPDATE gcmb.employee
SET status = 'INACTIVE'
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR id = '44440000-0000-4000-8000-000000000002'
   OR code LIKE 'DEMO-UC3437-%'
   OR code = 'DEMO-UC44-ACC';

DELETE FROM gcmb.auth_token
WHERE user_id::text IN (
    '34370000-0000-4000-8000-000000000003',
    '44440000-0000-4000-8000-000000000001'
);

DELETE FROM gcmb.audit_event
WHERE branch_id::text = '34370000-0000-4000-8000-000000000001';

ALTER TABLE gcmb.booking_event ENABLE TRIGGER immutable_booking_event;
ALTER TABLE gcmb.deposit_entry ENABLE TRIGGER immutable_deposit_entry;
ALTER TABLE gcmb.payment ENABLE TRIGGER immutable_payment;
ALTER TABLE gcmb.audit_event ENABLE TRIGGER immutable_audit;

-- Keep old identities for booking and financial foreign keys; revoke their logins and
-- detach them from disposable branches before those branches are suspended below.
UPDATE gcmb.user_account
SET status = 'REVOKED', branch_id = NULL, locked_until = NULL
WHERE id::text IN (
    '34370000-0000-4000-8000-000000000003',
    '44440000-0000-4000-8000-000000000001'
)
   OR username IN ('uc3437.demo', 'uc44.accountant');

-- Keep legacy catalog rows referenced by bookings, allocations, and charges.
-- Disable them instead of deleting rows protected by RESTRICT foreign keys.
UPDATE gcmb.room
SET operating_status = 'INACTIVE'
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR code LIKE 'DEMO-MB-%'
   OR code LIKE 'DEMO-CIN-%';

UPDATE gcmb.price_rule
SET active = FALSE
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR name LIKE 'UC34-37 %'
   OR name LIKE 'UC39-40 %';

UPDATE gcmb.combo
SET active = FALSE
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR code LIKE 'DEMO-UC3437-%'
   OR code LIKE 'DEMO-UC3940-%';

UPDATE gcmb.room_type
SET active = FALSE
WHERE id::text LIKE '34370000-0000-4000-8000-000000000%'
   OR code LIKE 'DEMO-UC3437-%'
   OR code LIKE 'DEMO-UC3940-%';

UPDATE gcmb.branch
SET status = 'SUSPENDED'
WHERE id::text = '34370000-0000-4000-8000-000000000001'
   OR code = 'DEMO-UC3437';

CREATE TEMP TABLE genz_branch_seed (
    code varchar(60) PRIMARY KEY,
    name varchar(200) NOT NULL,
    address text NOT NULL,
    phone varchar(30) NOT NULL,
    service_mode varchar(20) NOT NULL CHECK (service_mode IN ('CINEMA','MUSIC_BOX'))
) ON COMMIT DROP;

INSERT INTO genz_branch_seed (code, name, address, phone, service_mode) VALUES
('NGUYEN_VAN_LOC', 'GenZ Cinema Nguyễn Văn Lộc', 'Lk13 Ngõ 2 Nguyễn Văn Lộc, P. Mộ Lao, Hà Đông, Hà Nội', '0866521881', 'CINEMA'),
('HN_NGUYEN_LUONG_BANG_03', 'GenZ Cinema Nguyễn Lương Bằng', 'Số 3 Ngõ 180 P. Nguyễn Lương Bằng, Quang Trung, Đống Đa, Hà Nội', '0325186385', 'CINEMA'),
('HN_TAN_KHAI', 'GenZ Cinema Tân Khai', '130 P. Tân Khai, Vĩnh Hưng, Hai Bà Trưng, Hà Nội', '0989838603', 'CINEMA'),
('HN_HOANG_NGAN', 'GenZ Cinema Hoàng Ngân', '103 P. Hoàng Ngân, Nhân Chính, Thanh Xuân, Hà Nội', '0823983881', 'CINEMA'),
('HN_HOA_BANG', 'GenZ Cinema Hoa Bằng', '24 P. Hoa Bằng, Yên Hoà, Cầu Giấy, Hà Nội', '0877155379', 'CINEMA'),
('HO_TAY', 'GenZ Cinema Nhật Chiêu', '135 P. Nhật Chiêu, Nhật Tân, Tây Hồ, Hà Nội', '0838408881', 'CINEMA'),
('HN_NGUYEN_LUONG_BANG_86', 'GenZ Cinema Nguyễn Lương Bằng 86-88', '86-88 P. Nguyễn Lương Bằng, Quang Trung, Đống Đa, Hà Nội', '0816018881', 'CINEMA'),
('HN_HOANG_CONG_CHAT', 'GenZ Cinema Hoàng Công Chất', '462 Đ. Hoàng Công Chất, Cầu Diễn, Bắc Từ Liêm, Hà Nội', '0846298881', 'CINEMA'),
('TRIEU_KHUC', 'GenZ Cinema Triều Khúc', '06-N05 khu tái định cư xóm chùa, Triều Khúc, Thanh Liệt, Hà Nội', '0823660705', 'CINEMA'),
('HCM_NGUYEN_THAI_BINH', 'GenZ Cinema Nguyễn Thái Bình', '419 Nguyễn Thái Bình, TP. Hồ Chí Minh', '0932680419', 'CINEMA'),
('HCM_PHAN_XICH_LONG', 'GenZ Cinema Phan Xích Long', '320 Phan Xích Long, TP. Hồ Chí Minh', '0903857320', 'CINEMA'),
('HCM_AU_CO', 'GenZ Cinema Âu Cơ', '547 Âu Cơ, TP. Hồ Chí Minh', '0793759597', 'CINEMA'),
('HCM_NGUYEN_SUY', 'GenZ Cinema Nguyễn Súy', '54 Nguyễn Súy, TP. Hồ Chí Minh', '0796403054', 'CINEMA'),
('HCM_CACH_MANG_THANG_8', 'GenZ Cinema Cách Mạng Tháng 8', '713 Cách Mạng Tháng Tám, TP. Hồ Chí Minh', '0797397974', 'CINEMA'),
('HN_XUAN_THUY', 'GenZ Music Xuân Thủy', '51 Xuân Thủy, Cầu Giấy, Hà Nội', '0823408881', 'MUSIC_BOX'),
('HCM_TAN_BINH', 'GenZ Box Tân Bình', '412 Trường Chinh, Tân Bình, TP. Hồ Chí Minh', '0982813121', 'MUSIC_BOX'),
('HCM_CUU_LONG', 'Music Box Cửu Long', '50 Cửu Long, TP. Hồ Chí Minh', '0964128112', 'MUSIC_BOX'),
('HCM_QUAN_10', 'Music Box Quận 10', '621 Cách Mạng Tháng Tám, Quận 10, TP. Hồ Chí Minh', '0987359361', 'MUSIC_BOX');

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM genz_branch_seed) <> 18 THEN
        RAISE EXCEPTION 'Danh sách cơ sở phải có đúng 18 địa chỉ đã khử trùng';
    END IF;
    IF EXISTS (
        SELECT regexp_replace(lower(address), '[[:space:][:punct:]]', '', 'g')
        FROM genz_branch_seed
        GROUP BY 1 HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Danh sách seed còn địa chỉ trùng';
    END IF;
END $$;

-- Reuse the three temporary rows from V6 and replace their placeholder details.
INSERT INTO gcmb.branch (code, name, address, phone, timezone, status)
SELECT code, name, address, phone, 'Asia/Ho_Chi_Minh', 'ACTIVE'
FROM genz_branch_seed
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    phone = EXCLUDED.phone,
    timezone = EXCLUDED.timezone,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

-- Queen and King plus their Cinema prices and combos are already in V3.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM gcmb.room_type WHERE code = 'RT-QN' AND mode = 'CINEMA' AND active)
       OR NOT EXISTS (SELECT 1 FROM gcmb.room_type WHERE code = 'RT-KG' AND mode = 'CINEMA' AND active)
       OR NOT EXISTS (SELECT 1 FROM gcmb.combo WHERE code = 'CIN_C1_QN' AND active)
       OR NOT EXISTS (SELECT 1 FROM gcmb.combo WHERE code = 'CIN_NIGHT_KG' AND active) THEN
        RAISE EXCEPTION 'Chạy đầy đủ Flyway migrations trước khi seed cơ sở GenZ';
    END IF;
END $$;

INSERT INTO gcmb.room_type (code, name, mode, capacity, description, active)
VALUES ('RT-MUSIC', 'Music Box', 'MUSIC_BOX', 4, 'Phòng Music Box', TRUE)
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM gcmb.room_type WHERE code = 'RT-MUSIC' AND mode = 'MUSIC_BOX' AND active) THEN
        RAISE EXCEPTION 'Mã loại phòng RT-MUSIC đã được dùng cho cấu hình khác';
    END IF;
END $$;

-- These six starter rooms are seed data, not a limit on a branch's room count.
-- Existing rooms and their operating status are preserved on a code conflict.
INSERT INTO gcmb.room (branch_id, room_type_id, code, name, capacity, operating_status)
SELECT b.id, rt.id, r.room_code, r.room_name, rt.capacity, 'AVAILABLE'
FROM genz_branch_seed s
JOIN gcmb.branch b ON b.code = s.code
CROSS JOIN (VALUES
    ('RT-QN', 'Q01', 'Queen 01', 'CINEMA'),
    ('RT-QN', 'Q02', 'Queen 02', 'CINEMA'),
    ('RT-QN', 'Q03', 'Queen 03', 'CINEMA'),
    ('RT-KG', 'K01', 'King 01', 'CINEMA'),
    ('RT-KG', 'K02', 'King 02', 'CINEMA'),
    ('RT-KG', 'K03', 'King 03', 'CINEMA'),
    ('RT-MUSIC', 'MB01', 'Music Box 01', 'MUSIC_BOX'),
    ('RT-MUSIC', 'MB02', 'Music Box 02', 'MUSIC_BOX'),
    ('RT-MUSIC', 'MB03', 'Music Box 03', 'MUSIC_BOX'),
    ('RT-MUSIC', 'MB04', 'Music Box 04', 'MUSIC_BOX'),
    ('RT-MUSIC', 'MB05', 'Music Box 05', 'MUSIC_BOX'),
    ('RT-MUSIC', 'MB06', 'Music Box 06', 'MUSIC_BOX')
) AS r(type_code, room_code, room_name, service_mode)
JOIN gcmb.room_type rt ON rt.code = r.type_code AND rt.mode = r.service_mode
WHERE s.service_mode = r.service_mode
ON CONFLICT (branch_id, code) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM genz_branch_seed s
        JOIN gcmb.branch b ON b.code = s.code
        LEFT JOIN gcmb.room r ON r.branch_id = b.id
        LEFT JOIN gcmb.room_type rt ON rt.id = r.room_type_id
        GROUP BY b.id, s.service_mode
        HAVING COUNT(DISTINCT rt.mode) <> 1
            OR MIN(rt.mode) <> s.service_mode
    ) THEN
        RAISE EXCEPTION 'Loại hình phòng của cơ sở không khớp cấu hình seed';
    END IF;
END $$;

CREATE TEMP TABLE genz_account_seed (
    user_id uuid PRIMARY KEY,
    employee_id uuid NOT NULL,
    assignment_id uuid,
    username varchar(100) NOT NULL,
    email varchar(254) NOT NULL,
    password_hash text NOT NULL,
    role_code varchar(60) NOT NULL,
    full_name varchar(200) NOT NULL,
    employee_code varchar(60) NOT NULL,
    account_branch_code varchar(60),
    assignment_branch_code varchar(60),
    is_branch_manager boolean NOT NULL
) ON COMMIT DROP;

-- example.invalid emails are placeholders until staff recovery emails are supplied.
-- The accountant has a Hoa Bằng employee assignment because the current refund API is branch-scoped;
-- the accountant account itself remains chain-level with a NULL user_account.branch_id.
INSERT INTO genz_account_seed VALUES
('47570000-0000-4000-8000-000000000001', '47570000-0000-4000-8000-000000000011', '47570000-0000-4000-8000-000000000021', 'letan.hoabang', 'letan.hoabang@example.invalid', '{pbkdf2-gcmb}6Xg5zmUE2RgWjPaMrtMsFC4BbgqwA7ZXF4GdWFrVm8XlwFSLSmFABaUJGBgBUUek', 'RECEPTIONIST', 'Lễ tân - Hoa Bằng', 'GENZ-REC-HOA-BANG', 'HN_HOA_BANG', 'HN_HOA_BANG', FALSE),
('47570000-0000-4000-8000-000000000002', '47570000-0000-4000-8000-000000000012', '47570000-0000-4000-8000-000000000022', 'letan.nguyenluongbang', 'letan.nguyenluongbang@example.invalid', '{pbkdf2-gcmb}f1uGSUSfIUuaMwIWoqXty7N9J7LV5jOsuQha6SjFI4SoE2Ln3RhXyLAaXFmXcNhU', 'RECEPTIONIST', 'Lễ tân - Nguyễn Lương Bằng 86-88', 'GENZ-REC-NLB-86', 'HN_NGUYEN_LUONG_BANG_86', 'HN_NGUYEN_LUONG_BANG_86', FALSE),
('47570000-0000-4000-8000-000000000003', '47570000-0000-4000-8000-000000000013', '47570000-0000-4000-8000-000000000023', 'qlcs.hoabang', 'qlcs.hoabang@example.invalid', '{pbkdf2-gcmb}yaf8Yzm7NUA5nOG2gPrnQqjYQ2NXF9SXjNgdqJ+WR/vtbvFNQQnHBEhrSjKevy0x', 'BRANCH_MANAGER', 'Quản lý cơ sở - Hoa Bằng', 'GENZ-MGR-HOA-BANG', 'HN_HOA_BANG', 'HN_HOA_BANG', TRUE),
('47570000-0000-4000-8000-000000000004', '47570000-0000-4000-8000-000000000014', '47570000-0000-4000-8000-000000000024', 'qlcs.nguyenluongbang', 'qlcs.nguyenluongbang@example.invalid', '{pbkdf2-gcmb}XpmBHUFNibO1jS6XvW+HNP1hlJffPjApVKQLteAJZSwWSrxe5ionCpu4YKihKCEq', 'BRANCH_MANAGER', 'Quản lý cơ sở - Nguyễn Lương Bằng 86-88', 'GENZ-MGR-NLB-86', 'HN_NGUYEN_LUONG_BANG_86', 'HN_NGUYEN_LUONG_BANG_86', TRUE),
('47570000-0000-4000-8000-000000000005', '47570000-0000-4000-8000-000000000015', NULL, 'qlcc', 'qlcc@example.invalid', '{pbkdf2-gcmb}mFBd/VOhO5HPsBtlzyto8KyFNFfhEKEuwTylJcMc5oJtz7pd50T3o3umWb8K+fqY', 'HEAD_OFFICE_MANAGER', 'Quản lý cấp cao', 'GENZ-HOM', NULL, NULL, FALSE),
('47570000-0000-4000-8000-000000000006', '47570000-0000-4000-8000-000000000016', '47570000-0000-4000-8000-000000000026', 'ketoan', 'ketoan@example.invalid', '{pbkdf2-gcmb}Q4cChieYPkOvOK0bLha1nx9SIfrg86wZS0KUoLTu5IfOzaY7KNVZbLFxP0cLSa84', 'ACCOUNTANT', 'Kế toán', 'GENZ-ACC', NULL, 'HN_HOA_BANG', FALSE);

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM genz_account_seed) <> 6
       OR (SELECT COUNT(*) FROM genz_account_seed WHERE role_code = 'RECEPTIONIST') <> 2
       OR (SELECT COUNT(*) FROM genz_account_seed WHERE role_code = 'BRANCH_MANAGER') <> 2
       OR (SELECT COUNT(*) FROM genz_account_seed WHERE role_code = 'HEAD_OFFICE_MANAGER') <> 1
       OR (SELECT COUNT(*) FROM genz_account_seed WHERE role_code = 'ACCOUNTANT') <> 1 THEN
        RAISE EXCEPTION 'Danh sách tài khoản phải gồm 2 lễ tân, 2 quản lý cơ sở, 1 quản lý cấp cao và 1 kế toán';
    END IF;
    IF EXISTS (
        SELECT 1 FROM gcmb.user_account u
        JOIN genz_account_seed s ON lower(u.username) = lower(s.username)
        WHERE u.id <> s.user_id
    ) OR EXISTS (
        SELECT 1 FROM gcmb.user_account u
        JOIN genz_account_seed s ON lower(u.email) = lower(s.email)
        WHERE u.id <> s.user_id
    ) THEN
        RAISE EXCEPTION 'Một username/email seed đã được dùng bởi tài khoản khác';
    END IF;
    IF EXISTS (
        SELECT 1 FROM gcmb.employee e
        JOIN genz_account_seed s ON e.code = s.employee_code
        WHERE e.id <> s.employee_id
    ) THEN
        RAISE EXCEPTION 'Một mã nhân viên seed đã được dùng';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM genz_account_seed s
        JOIN gcmb.branch b ON b.code = s.assignment_branch_code
        JOIN gcmb.branch_manager_assignment a ON a.branch_id = b.id
        WHERE s.is_branch_manager AND a.user_id <> s.user_id
    ) OR EXISTS (
        SELECT 1
        FROM genz_account_seed s
        JOIN gcmb.branch b ON b.code = s.assignment_branch_code
        JOIN gcmb.branch_manager_assignment a ON a.user_id = s.user_id
        WHERE s.is_branch_manager AND a.branch_id <> b.id
    ) THEN
        RAISE EXCEPTION 'Cơ sở đã được gán cho một quản lý khác';
    END IF;
END $$;

INSERT INTO gcmb.user_account
    (id, username, email, password_hash, role_id, status, failed_login_count, password_changed_at, branch_id)
SELECT s.user_id, s.username, s.email, s.password_hash, r.id, 'ACTIVE', 0, CURRENT_TIMESTAMP, b.id
FROM genz_account_seed s
JOIN gcmb.role r ON r.code = s.role_code
LEFT JOIN gcmb.branch b ON b.code = s.account_branch_code
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.employee (id, code, user_id, full_name, hired_on, status)
SELECT employee_id, employee_code, user_id, full_name, CURRENT_DATE, 'ACTIVE'
FROM genz_account_seed
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.employee_assignment
    (id, employee_id, branch_id, valid_from, is_branch_manager, assigned_by, reason)
SELECT s.assignment_id, s.employee_id, b.id, CURRENT_TIMESTAMP - INTERVAL '1 minute',
       s.is_branch_manager, '47570000-0000-4000-8000-000000000005',
       'Phân công khởi tạo dữ liệu GenZ'
FROM genz_account_seed s
JOIN gcmb.branch b ON b.code = s.assignment_branch_code
WHERE s.assignment_id IS NOT NULL
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.branch_manager_assignment (branch_id, user_id, assigned_by)
SELECT b.id, s.user_id, '47570000-0000-4000-8000-000000000005'
FROM genz_account_seed s
JOIN gcmb.branch b ON b.code = s.assignment_branch_code
WHERE s.is_branch_manager
ON CONFLICT (branch_id) DO NOTHING;

