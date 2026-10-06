-- GCMB proposed physical schema, 2026-10-05. PostgreSQL 15+.

-- New empty database only. Service transaction rules are documented in GCMB_Design.md.


CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SCHEMA gcmb;

SET search_path TO gcmb, public;

CREATE TABLE branch (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  address text NOT NULL,
  phone varchar(30),
  timezone varchar(60) NOT NULL,
  status varchar(20) NOT NULL,
  opened_on date,
  closed_on date,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
  CHECK (closed_on IS NULL OR opened_on IS NULL OR closed_on >= opened_on)
);

COMMENT ON TABLE branch IS 'Cơ sở kinh doanh; ngừng hoạt động thay vì xóa.';

COMMENT ON COLUMN branch.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN branch.code IS 'Mã cơ sở duy nhất';

COMMENT ON COLUMN branch.name IS 'Tên cơ sở';

COMMENT ON COLUMN branch.address IS 'Địa chỉ';

COMMENT ON COLUMN branch.phone IS 'Điện thoại';

COMMENT ON COLUMN branch.timezone IS 'Múi giờ, khởi tạo Asia/Ho_Chi_Minh';

COMMENT ON COLUMN branch.status IS 'ACTIVE/SUSPENDED/CLOSED';

COMMENT ON COLUMN branch.opened_on IS 'Ngày mở';

COMMENT ON COLUMN branch.closed_on IS 'Ngày đóng';

COMMENT ON COLUMN branch.created_at IS 'Thời điểm tạo';

CREATE TABLE role (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (code IN ('HEAD_OFFICE_MANAGER','BRANCH_MANAGER','RECEPTIONIST','ACCOUNTANT'))
);

COMMENT ON TABLE role IS 'Bốn vai trò nội bộ.';

COMMENT ON COLUMN role.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN role.code IS 'HEAD_OFFICE_MANAGER/BRANCH_MANAGER/RECEPTIONIST/ACCOUNTANT';

COMMENT ON COLUMN role.name IS 'Tên hiển thị';

COMMENT ON COLUMN role.created_at IS 'Thời điểm tạo';

CREATE TABLE permission (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE permission IS 'Quyền thao tác theo use case.';

COMMENT ON COLUMN permission.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN permission.code IS 'Ví dụ BOOKING_CREATE';

COMMENT ON COLUMN permission.name IS 'Tên quyền';

COMMENT ON COLUMN permission.created_at IS 'Thời điểm tạo';

CREATE TABLE role_permission (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  role_id uuid NOT NULL,
  permission_id uuid NOT NULL,
  scope varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (role_id, permission_id),
  CHECK (scope IN ('CHAIN','BRANCH','SELF'))
);

COMMENT ON TABLE role_permission IS 'Ánh xạ vai trò với quyền và phạm vi.';

COMMENT ON COLUMN role_permission.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN role_permission.role_id IS 'Vai trò';

COMMENT ON COLUMN role_permission.permission_id IS 'Quyền';

COMMENT ON COLUMN role_permission.scope IS 'CHAIN/BRANCH/SELF';

COMMENT ON COLUMN role_permission.created_at IS 'Thời điểm tạo';

CREATE TABLE user_account (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  username varchar(100) NOT NULL,
  email varchar(254) NOT NULL,
  password_hash text NOT NULL,
  role_id uuid NOT NULL,
  status varchar(20) NOT NULL,
  failed_login_count integer NOT NULL,
  locked_until timestamptz,
  last_login_at timestamptz,
  password_changed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('ACTIVE','LOCKED','REVOKED')),
  CHECK (failed_login_count >= 0)
);

COMMENT ON TABLE user_account IS 'Tài khoản nhân sự nội bộ; một vai trò hiện hành theo SRS.';

COMMENT ON COLUMN user_account.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN user_account.username IS 'Tên đăng nhập, unique không phân biệt hoa thường';

COMMENT ON COLUMN user_account.email IS 'Email khôi phục';

COMMENT ON COLUMN user_account.password_hash IS 'Chỉ lưu mật khẩu đã băm';

COMMENT ON COLUMN user_account.role_id IS 'Vai trò hiện hành';

COMMENT ON COLUMN user_account.status IS 'ACTIVE/LOCKED/REVOKED';

COMMENT ON COLUMN user_account.failed_login_count IS 'Số lần đăng nhập sai';

COMMENT ON COLUMN user_account.locked_until IS 'Thời điểm hết khóa tạm';

COMMENT ON COLUMN user_account.last_login_at IS 'Lần đăng nhập cuối';

COMMENT ON COLUMN user_account.password_changed_at IS 'Lần đổi mật khẩu cuối';

COMMENT ON COLUMN user_account.created_at IS 'Thời điểm tạo';

CREATE TABLE auth_token (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  token_hash text NOT NULL UNIQUE,
  kind varchar(20) NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (kind IN ('SESSION','PASSWORD_RESET')),
  CHECK (expires_at > created_at)
);

COMMENT ON TABLE auth_token IS 'Phiên đăng nhập và yêu cầu đặt lại mật khẩu có thể thu hồi.';

COMMENT ON COLUMN auth_token.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN auth_token.user_id IS 'Chủ tài khoản';

COMMENT ON COLUMN auth_token.token_hash IS 'Hash token; không lưu token gốc';

COMMENT ON COLUMN auth_token.kind IS 'SESSION/PASSWORD_RESET';

COMMENT ON COLUMN auth_token.expires_at IS 'Hạn dùng';

COMMENT ON COLUMN auth_token.used_at IS 'Reset chỉ dùng một lần';

COMMENT ON COLUMN auth_token.revoked_at IS 'Thu hồi phiên';

COMMENT ON COLUMN auth_token.created_at IS 'Thời điểm tạo';

CREATE TABLE audit_event (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  actor_id uuid,
  branch_id uuid,
  action varchar(60) NOT NULL,
  entity_type varchar(60) NOT NULL,
  entity_id uuid,
  before_data jsonb,
  after_data jsonb,
  reason text,
  request_id varchar(100),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit_event IS 'Nhật ký append-only; đối tượng dạng text chỉ dùng cho audit, không thay FK nghiệp vụ.';

COMMENT ON COLUMN audit_event.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN audit_event.actor_id IS 'Null nếu sự kiện hệ thống';

COMMENT ON COLUMN audit_event.branch_id IS 'Phạm vi cơ sở nếu có';

COMMENT ON COLUMN audit_event.action IS 'Hành động';

COMMENT ON COLUMN audit_event.entity_type IS 'Tên loại đối tượng';

COMMENT ON COLUMN audit_event.entity_id IS 'ID được ghi nhận, không là FK đa hình';

COMMENT ON COLUMN audit_event.before_data IS 'Giá trị trước, đã loại bỏ mật khẩu/token';

COMMENT ON COLUMN audit_event.after_data IS 'Giá trị sau';

COMMENT ON COLUMN audit_event.reason IS 'Lý do';

COMMENT ON COLUMN audit_event.request_id IS 'Mã request phục vụ truy vết';

COMMENT ON COLUMN audit_event.created_at IS 'Thời điểm tạo';

CREATE TABLE attachment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  object_key text NOT NULL UNIQUE,
  original_name varchar(200) NOT NULL,
  mime_type varchar(100) NOT NULL,
  byte_size bigint NOT NULL,
  sha256 varchar(64) NOT NULL,
  uploaded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (byte_size >= 0)
);

COMMENT ON TABLE attachment IS 'Metadata file; nội dung ở kho file riêng có kiểm soát quyền.';

COMMENT ON COLUMN attachment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN attachment.object_key IS 'Khóa file ổn định, không lưu URL ký tạm';

COMMENT ON COLUMN attachment.original_name IS 'Tên file';

COMMENT ON COLUMN attachment.mime_type IS 'Loại file';

COMMENT ON COLUMN attachment.byte_size IS 'Dung lượng';

COMMENT ON COLUMN attachment.sha256 IS 'Checksum';

COMMENT ON COLUMN attachment.uploaded_by IS 'Người tải';

COMMENT ON COLUMN attachment.created_at IS 'Thời điểm tạo';

CREATE TABLE room_type (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  mode varchar(20) NOT NULL,
  capacity integer NOT NULL,
  description text,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (mode IN ('CINEMA','MUSIC_BOX')),
  CHECK (capacity > 0)
);

COMMENT ON TABLE room_type IS 'Loại phòng Cinema/Music Box.';

COMMENT ON COLUMN room_type.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN room_type.code IS 'Mã loại phòng';

COMMENT ON COLUMN room_type.name IS 'Tên';

COMMENT ON COLUMN room_type.mode IS 'CINEMA/MUSIC_BOX';

COMMENT ON COLUMN room_type.capacity IS 'Sức chứa mặc định';

COMMENT ON COLUMN room_type.description IS 'Tiện nghi/mô tả';

COMMENT ON COLUMN room_type.active IS 'Còn áp dụng';

COMMENT ON COLUMN room_type.created_at IS 'Thời điểm tạo';

CREATE TABLE room (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  room_type_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(200) NOT NULL,
  capacity integer NOT NULL,
  operating_status varchar(25) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, code),
  UNIQUE (id, branch_id),
  CHECK (capacity > 0),
  CHECK (operating_status IN ('AVAILABLE','CLEANING','UNDER_MAINTENANCE','INACTIVE'))
);

COMMENT ON TABLE room IS 'Phòng vật lý; trạng thái vận hành độc lập lịch đặt.';

COMMENT ON COLUMN room.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN room.branch_id IS 'Cơ sở';

COMMENT ON COLUMN room.room_type_id IS 'Loại phòng';

COMMENT ON COLUMN room.code IS 'Mã trong cơ sở';

COMMENT ON COLUMN room.name IS 'Tên phòng';

COMMENT ON COLUMN room.capacity IS 'Sức chứa';

COMMENT ON COLUMN room.operating_status IS 'AVAILABLE/CLEANING/UNDER_MAINTENANCE/INACTIVE';

COMMENT ON COLUMN room.created_at IS 'Thời điểm tạo';

CREATE TABLE price_rule (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  room_type_id uuid NOT NULL,
  branch_id uuid,
  name varchar(200) NOT NULL,
  valid_from date NOT NULL,
  valid_to date,
  day_kind varchar(20) NOT NULL,
  specific_date date,
  minute_from integer NOT NULL,
  minute_to integer NOT NULL,
  hourly_price numeric(18,0) NOT NULL,
  overtime_hourly_price numeric(18,0) NOT NULL,
  billing_step_minutes integer NOT NULL,
  priority integer NOT NULL,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (valid_to IS NULL OR valid_to > valid_from),
  CHECK (minute_from >= 0 AND minute_to <= 1440 AND minute_to > minute_from),
  CHECK (hourly_price >= 0 AND overtime_hourly_price >= 0 AND billing_step_minutes > 0),
  CHECK (day_kind IN ('ALL','WEEKDAY','WEEKEND','HOLIDAY','SPECIFIC')),
  CHECK ((day_kind = 'SPECIFIC') = (specific_date IS NOT NULL))
);

COMMENT ON TABLE price_rule IS 'Phiên bản giá theo loại phòng, cơ sở, ngày và khung giờ; không sửa bản đã dùng.';

COMMENT ON COLUMN price_rule.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN price_rule.room_type_id IS 'Loại phòng';

COMMENT ON COLUMN price_rule.branch_id IS 'Null là giá chung';

COMMENT ON COLUMN price_rule.name IS 'Tên quy tắc';

COMMENT ON COLUMN price_rule.valid_from IS 'Ngày hiệu lực';

COMMENT ON COLUMN price_rule.valid_to IS 'Ngày hết hiệu lực, không bao gồm';

COMMENT ON COLUMN price_rule.day_kind IS 'ALL/WEEKDAY/WEEKEND/HOLIDAY/SPECIFIC';

COMMENT ON COLUMN price_rule.specific_date IS 'Ngày đặc biệt';

COMMENT ON COLUMN price_rule.minute_from IS 'Phút bắt đầu trong ngày 0..1439';

COMMENT ON COLUMN price_rule.minute_to IS 'Phút kết thúc 1..1440; tách dòng nếu qua đêm';

COMMENT ON COLUMN price_rule.hourly_price IS 'Giá giờ';

COMMENT ON COLUMN price_rule.overtime_hourly_price IS 'Giá vượt giờ';

COMMENT ON COLUMN price_rule.billing_step_minutes IS 'Bước làm tròn thời lượng';

COMMENT ON COLUMN price_rule.priority IS 'Ưu tiên giá';

COMMENT ON COLUMN price_rule.active IS 'Dùng cho báo giá mới';

COMMENT ON COLUMN price_rule.created_at IS 'Thời điểm tạo';

CREATE TABLE combo (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL,
  version integer NOT NULL,
  room_type_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  duration_minutes integer NOT NULL,
  price numeric(18,0) NOT NULL,
  deposit_required numeric(18,0) NOT NULL,
  overtime_hourly_price numeric(18,0) NOT NULL,
  valid_from timestamptz NOT NULL,
  valid_to timestamptz,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (code, version),
  CHECK (version > 0 AND duration_minutes > 0),
  CHECK (price >= 0 AND deposit_required >= 0 AND overtime_hourly_price >= 0),
  CHECK (valid_to IS NULL OR valid_to > valid_from)
);

COMMENT ON TABLE combo IS 'Phiên bản gói phòng và F&B; bản đã bán được giữ lịch sử.';

COMMENT ON COLUMN combo.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN combo.code IS 'Mã gói';

COMMENT ON COLUMN combo.version IS 'Phiên bản';

COMMENT ON COLUMN combo.room_type_id IS 'Loại phòng áp dụng';

COMMENT ON COLUMN combo.name IS 'Tên gói';

COMMENT ON COLUMN combo.duration_minutes IS 'Số phút bao gồm';

COMMENT ON COLUMN combo.price IS 'Giá trọn gói';

COMMENT ON COLUMN combo.deposit_required IS 'Cọc yêu cầu';

COMMENT ON COLUMN combo.overtime_hourly_price IS 'Đơn giá vượt giờ';

COMMENT ON COLUMN combo.valid_from IS 'Hiệu lực';

COMMENT ON COLUMN combo.valid_to IS 'Hết hiệu lực';

COMMENT ON COLUMN combo.active IS 'Còn bán';

COMMENT ON COLUMN combo.created_at IS 'Thời điểm tạo';

CREATE TABLE booking (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  code varchar(60) NOT NULL UNIQUE,
  contact_name varchar(200) NOT NULL,
  contact_phone varchar(30),
  channel varchar(20) NOT NULL,
  planned_start timestamptz NOT NULL,
  planned_end timestamptz NOT NULL,
  actual_checkin timestamptz,
  actual_checkout timestamptz,
  status varchar(20) NOT NULL,
  cancelled_at timestamptz,
  cancellation_reason text,
  refund_cutoff_minutes integer NOT NULL,
  created_by uuid NOT NULL,
  note text,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, branch_id),
  CHECK (planned_end > planned_start),
  CHECK (actual_checkout IS NULL OR (actual_checkin IS NOT NULL AND actual_checkout >= actual_checkin)),
  CHECK (status IN ('DRAFT','CONFIRMED','IN_HOUSE','COMPLETED','CANCELLED','NO_SHOW')),
  CHECK (refund_cutoff_minutes >= 0)
);

COMMENT ON TABLE booking IS 'Một lượt đặt/sử dụng tại một cơ sở; khách vãng lai cũng có booking.';

COMMENT ON COLUMN booking.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN booking.branch_id IS 'Cơ sở sở hữu, không đổi khi chuyển phòng';

COMMENT ON COLUMN booking.code IS 'Mã booking';

COMMENT ON COLUMN booking.contact_name IS 'Tên khách snapshot';

COMMENT ON COLUMN booking.contact_phone IS 'SĐT, không unique';

COMMENT ON COLUMN booking.channel IS 'WALK_IN/FACEBOOK/ZALO/PHONE/OTHER';

COMMENT ON COLUMN booking.planned_start IS 'Giờ dự kiến';

COMMENT ON COLUMN booking.planned_end IS 'Giờ kết thúc dự kiến';

COMMENT ON COLUMN booking.actual_checkin IS 'Check-in thực tế';

COMMENT ON COLUMN booking.actual_checkout IS 'Checkout thực tế';

COMMENT ON COLUMN booking.status IS 'DRAFT/CONFIRMED/IN_HOUSE/COMPLETED/CANCELLED/NO_SHOW';

COMMENT ON COLUMN booking.cancelled_at IS 'Giờ hủy';

COMMENT ON COLUMN booking.cancellation_reason IS 'Lý do';

COMMENT ON COLUMN booking.refund_cutoff_minutes IS 'Snapshot quy tắc, hiện tại 40 phút';

COMMENT ON COLUMN booking.created_by IS 'Lễ tân tạo';

COMMENT ON COLUMN booking.note IS 'Ghi chú';

COMMENT ON COLUMN booking.created_at IS 'Thời điểm tạo';

CREATE TABLE room_allocation (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  room_id uuid NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  actual_start timestamptz,
  actual_end timestamptz,
  state varchar(20) NOT NULL,
  change_reason text,
  change_cause varchar(20),
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, booking_id),
  CHECK (ends_at > starts_at),
  CHECK (state IN ('RESERVED','OCCUPIED','FINISHED','RELEASED')),
  CHECK (change_cause IS NULL OR change_cause IN ('CUSTOMER','BRANCH_FAULT')),
  CHECK (actual_end IS NULL OR (actual_start IS NOT NULL AND actual_end >= actual_start))
);

COMMENT ON TABLE room_allocation IS 'Mỗi chặng giữ/sử dụng phòng của booking; bảo toàn lịch sử đổi phòng.';

COMMENT ON COLUMN room_allocation.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN room_allocation.branch_id IS 'Cùng cơ sở với booking và phòng';

COMMENT ON COLUMN room_allocation.booking_id IS 'Booking';

COMMENT ON COLUMN room_allocation.room_id IS 'Phòng';

COMMENT ON COLUMN room_allocation.starts_at IS 'Đầu khoảng giữ phòng';

COMMENT ON COLUMN room_allocation.ends_at IS 'Cuối khoảng giữ phòng';

COMMENT ON COLUMN room_allocation.actual_start IS 'Vào phòng thực tế';

COMMENT ON COLUMN room_allocation.actual_end IS 'Rời phòng thực tế';

COMMENT ON COLUMN room_allocation.state IS 'RESERVED/OCCUPIED/FINISHED/RELEASED';

COMMENT ON COLUMN room_allocation.change_reason IS 'Lý do chuyển/sửa';

COMMENT ON COLUMN room_allocation.change_cause IS 'CUSTOMER/BRANCH_FAULT';

COMMENT ON COLUMN room_allocation.created_by IS 'Người thực hiện';

COMMENT ON COLUMN room_allocation.created_at IS 'Thời điểm tạo';

CREATE TABLE booking_charge (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL,
  allocation_id uuid,
  price_rule_id uuid,
  combo_id uuid,
  kind varchar(20) NOT NULL,
  description varchar(200) NOT NULL,
  quantity numeric(18,3) NOT NULL,
  unit_price numeric(18,0) NOT NULL,
  amount numeric(18,0) NOT NULL,
  pricing_snapshot jsonb NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0),
  CHECK (status IN ('ACTIVE','SUPERSEDED'))
);

COMMENT ON TABLE booking_charge IS 'Chi tiết báo giá/giá chốt theo từng đoạn giờ, combo, đổi phòng và gia hạn.';

COMMENT ON COLUMN booking_charge.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN booking_charge.booking_id IS 'Booking';

COMMENT ON COLUMN booking_charge.allocation_id IS 'Chặng phòng nếu có';

COMMENT ON COLUMN booking_charge.price_rule_id IS 'Nguồn giá giờ';

COMMENT ON COLUMN booking_charge.combo_id IS 'Nguồn combo';

COMMENT ON COLUMN booking_charge.kind IS 'ROOM/COMBO/OVERTIME/UPGRADE/SERVICE';

COMMENT ON COLUMN booking_charge.description IS 'Nội dung snapshot';

COMMENT ON COLUMN booking_charge.quantity IS 'Số đơn vị tính';

COMMENT ON COLUMN booking_charge.unit_price IS 'Giá snapshot';

COMMENT ON COLUMN booking_charge.amount IS 'Tiền sau làm tròn, lưu để tái hiện hóa đơn';

COMMENT ON COLUMN booking_charge.pricing_snapshot IS 'Bước tính, khung giờ, miễn chênh lệch do lỗi cơ sở';

COMMENT ON COLUMN booking_charge.status IS 'ACTIVE/SUPERSEDED';

COMMENT ON COLUMN booking_charge.created_at IS 'Thời điểm tạo';

CREATE TABLE booking_event (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  event_type varchar(60) NOT NULL,
  reason text,
  details jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE booking_event IS 'Lịch sử nghiệp vụ đặt, sửa, hủy, đổi phòng, gia hạn.';

COMMENT ON COLUMN booking_event.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN booking_event.booking_id IS 'Booking';

COMMENT ON COLUMN booking_event.actor_id IS 'Người thực hiện';

COMMENT ON COLUMN booking_event.event_type IS 'Loại sự kiện';

COMMENT ON COLUMN booking_event.reason IS 'Lý do';

COMMENT ON COLUMN booking_event.details IS 'Trước/sau và quy tắc đã áp dụng';

COMMENT ON COLUMN booking_event.created_at IS 'Thời điểm tạo';

CREATE TABLE bill (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL UNIQUE,
  branch_id uuid NOT NULL,
  number varchar(60) NOT NULL UNIQUE,
  status varchar(20) NOT NULL,
  gross_amount numeric(18,0) NOT NULL,
  discount_amount numeric(18,0) NOT NULL,
  discount_reference varchar(200),
  discount_reason text,
  discount_by uuid,
  net_amount numeric(18,0) NOT NULL,
  issued_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, booking_id),
  UNIQUE (id, branch_id),
  CHECK (status IN ('DRAFT','ISSUED','SETTLED','VOID')),
  CHECK (gross_amount >= 0 AND discount_amount >= 0 AND discount_amount <= gross_amount),
  CHECK (net_amount = gross_amount - discount_amount)
);

COMMENT ON TABLE bill IS 'Một hóa đơn chính mỗi booking; sửa sau phát hành bằng chứng từ điều chỉnh.';

COMMENT ON COLUMN bill.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN bill.booking_id IS 'Booking duy nhất';

COMMENT ON COLUMN bill.branch_id IS 'Cơ sở doanh thu';

COMMENT ON COLUMN bill.number IS 'Số hóa đơn';

COMMENT ON COLUMN bill.status IS 'DRAFT/ISSUED/SETTLED/VOID';

COMMENT ON COLUMN bill.gross_amount IS 'Tổng dòng trước giảm giá';

COMMENT ON COLUMN bill.discount_amount IS 'Giảm giá được duyệt';

COMMENT ON COLUMN bill.discount_reference IS 'Mã voucher nhập tay/tham chiếu';

COMMENT ON COLUMN bill.discount_reason IS 'Lý do giảm';

COMMENT ON COLUMN bill.discount_by IS 'Người áp dụng';

COMMENT ON COLUMN bill.net_amount IS 'Gross trừ discount, không trừ cọc';

COMMENT ON COLUMN bill.issued_at IS 'Phát hành';

COMMENT ON COLUMN bill.completed_at IS 'Hoàn thành và ghi nhận doanh thu';

COMMENT ON COLUMN bill.created_at IS 'Thời điểm tạo';

CREATE TABLE bill_line (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  bill_id uuid NOT NULL,
  booking_charge_id uuid UNIQUE,
  order_line_id uuid UNIQUE,
  description varchar(200) NOT NULL,
  quantity numeric(18,3) NOT NULL,
  unit_price numeric(18,0) NOT NULL,
  amount numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0),
  CHECK (num_nonnulls(booking_charge_id, order_line_id) = 1)
);

COMMENT ON TABLE bill_line IS 'Dòng hóa đơn snapshot; không tính F&B trong combo hai lần.';

COMMENT ON COLUMN bill_line.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN bill_line.bill_id IS 'Hóa đơn';

COMMENT ON COLUMN bill_line.booking_charge_id IS 'Dòng tiền phòng nguồn';

COMMENT ON COLUMN bill_line.order_line_id IS 'Dòng F&B nguồn';

COMMENT ON COLUMN bill_line.description IS 'Tên tại thời điểm bán';

COMMENT ON COLUMN bill_line.quantity IS 'Số lượng';

COMMENT ON COLUMN bill_line.unit_price IS 'Giá';

COMMENT ON COLUMN bill_line.amount IS 'Tiền dòng';

COMMENT ON COLUMN bill_line.created_at IS 'Thời điểm tạo';

CREATE TABLE unit (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE unit IS 'Đơn vị cơ sở của hàng tồn; v1 nhập cùng đơn vị, không BOM.';

COMMENT ON COLUMN unit.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN unit.code IS 'Mã';

COMMENT ON COLUMN unit.name IS 'Lon/chai/gói/kg...';

COMMENT ON COLUMN unit.created_at IS 'Thời điểm tạo';

CREATE TABLE inventory_item (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  sku varchar(60) NOT NULL,
  name varchar(200) NOT NULL,
  unit_id uuid NOT NULL,
  tracking_mode varchar(20) NOT NULL,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, sku),
  UNIQUE (id, branch_id),
  CHECK (tracking_mode IN ('AUTO_ON_SALE','MANUAL'))
);

COMMENT ON TABLE inventory_item IS 'Mặt hàng kho thuộc cơ sở; hàng đóng gói hoặc nguyên liệu thủ công.';

COMMENT ON COLUMN inventory_item.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN inventory_item.branch_id IS 'Cơ sở';

COMMENT ON COLUMN inventory_item.sku IS 'Mã hàng trong cơ sở';

COMMENT ON COLUMN inventory_item.name IS 'Tên';

COMMENT ON COLUMN inventory_item.unit_id IS 'Đơn vị gốc';

COMMENT ON COLUMN inventory_item.tracking_mode IS 'AUTO_ON_SALE/MANUAL';

COMMENT ON COLUMN inventory_item.active IS 'Còn dùng';

COMMENT ON COLUMN inventory_item.created_at IS 'Thời điểm tạo';

CREATE TABLE stock_balance (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  inventory_item_id uuid NOT NULL UNIQUE,
  quantity numeric(18,3) NOT NULL,
  minimum_quantity numeric(18,3) NOT NULL,
  version bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity >= 0 AND minimum_quantity >= 0 AND version >= 0)
);

COMMENT ON TABLE stock_balance IS 'Số dư tồn kho có khóa dòng; tổng hợp đối chiếu từ stock_movement.';

COMMENT ON COLUMN stock_balance.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN stock_balance.inventory_item_id IS 'Một số dư mỗi mặt hàng thuộc cơ sở';

COMMENT ON COLUMN stock_balance.quantity IS 'Tồn hiện tại';

COMMENT ON COLUMN stock_balance.minimum_quantity IS 'Ngưỡng thấp';

COMMENT ON COLUMN stock_balance.version IS 'Optimistic lock';

COMMENT ON COLUMN stock_balance.created_at IS 'Thời điểm tạo';

CREATE TABLE menu_item (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(200) NOT NULL,
  category varchar(200) NOT NULL,
  kind varchar(20) NOT NULL,
  inventory_item_id uuid,
  sale_price numeric(18,0) NOT NULL,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, code),
  UNIQUE (id, branch_id),
  CHECK (kind IN ('PACKAGED','PREPARED','SERVICE')),
  CHECK ((kind = 'PACKAGED') = (inventory_item_id IS NOT NULL)),
  CHECK (sale_price >= 0)
);

COMMENT ON TABLE menu_item IS 'Món F&B/dịch vụ của cơ sở.';

COMMENT ON COLUMN menu_item.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN menu_item.branch_id IS 'Cơ sở bán';

COMMENT ON COLUMN menu_item.code IS 'Mã món';

COMMENT ON COLUMN menu_item.name IS 'Tên';

COMMENT ON COLUMN menu_item.category IS 'Nhóm menu';

COMMENT ON COLUMN menu_item.kind IS 'PACKAGED/PREPARED/SERVICE';

COMMENT ON COLUMN menu_item.inventory_item_id IS 'Liên kết hàng trừ tự động đối với PACKAGED';

COMMENT ON COLUMN menu_item.sale_price IS 'Giá hiện hành';

COMMENT ON COLUMN menu_item.active IS 'Đang bán';

COMMENT ON COLUMN menu_item.created_at IS 'Thời điểm tạo';

CREATE TABLE combo_item (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  combo_id uuid NOT NULL,
  menu_item_id uuid NOT NULL,
  quantity numeric(18,3) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (combo_id, menu_item_id),
  CHECK (quantity > 0)
);

COMMENT ON TABLE combo_item IS 'Nội dung combo theo cơ sở/menu tương ứng.';

COMMENT ON COLUMN combo_item.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN combo_item.combo_id IS 'Phiên bản combo';

COMMENT ON COLUMN combo_item.menu_item_id IS 'Món của cơ sở';

COMMENT ON COLUMN combo_item.quantity IS 'Số lượng bao gồm';

COMMENT ON COLUMN combo_item.created_at IS 'Thời điểm tạo';

CREATE TABLE booking_combo_item (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL,
  combo_item_id uuid,
  menu_item_id uuid NOT NULL,
  item_name_snapshot varchar(200) NOT NULL,
  included_quantity numeric(18,3) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (booking_id, menu_item_id),
  UNIQUE (id, booking_id),
  CHECK (included_quantity > 0)
);

COMMENT ON TABLE booking_combo_item IS 'Snapshot quyền hưởng món trong combo của một booking.';

COMMENT ON COLUMN booking_combo_item.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN booking_combo_item.booking_id IS 'Booking';

COMMENT ON COLUMN booking_combo_item.combo_item_id IS 'Dòng gói nguồn';

COMMENT ON COLUMN booking_combo_item.menu_item_id IS 'Món';

COMMENT ON COLUMN booking_combo_item.item_name_snapshot IS 'Tên đã chốt';

COMMENT ON COLUMN booking_combo_item.included_quantity IS 'Số lượng được dùng miễn tính thêm';

COMMENT ON COLUMN booking_combo_item.created_at IS 'Thời điểm tạo';

CREATE TABLE room_qr_session (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL,
  allocation_id uuid NOT NULL,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (expires_at > created_at)
);

COMMENT ON TABLE room_qr_session IS 'Quyền gọi món gắn booking và chặng phòng; vô hiệu khi đổi phòng/checkout.';

COMMENT ON COLUMN room_qr_session.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN room_qr_session.booking_id IS 'Booking hiện tại';

COMMENT ON COLUMN room_qr_session.allocation_id IS 'Chặng phòng';

COMMENT ON COLUMN room_qr_session.token_hash IS 'Hash token ngẫu nhiên, không chỉ room_id';

COMMENT ON COLUMN room_qr_session.expires_at IS 'Hết hạn';

COMMENT ON COLUMN room_qr_session.revoked_at IS 'Thu hồi';

COMMENT ON COLUMN room_qr_session.created_at IS 'Thời điểm tạo';

CREATE TABLE fb_order (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  allocation_id uuid NOT NULL,
  channel varchar(20) NOT NULL,
  created_by uuid,
  qr_session_id uuid,
  status varchar(20) NOT NULL,
  accepted_by uuid,
  accepted_at timestamptz,
  served_at timestamptz,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, branch_id),
  UNIQUE (id, booking_id),
  CHECK (status IN ('PENDING','ACCEPTED','SERVED','REJECTED','CANCELLED')),
  CHECK ((channel = 'STAFF' AND created_by IS NOT NULL AND qr_session_id IS NULL) OR (channel = 'ROOM_QR' AND qr_session_id IS NOT NULL AND created_by IS NULL))
);

COMMENT ON TABLE fb_order IS 'Đơn F&B của booking đang hoạt động.';

COMMENT ON COLUMN fb_order.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN fb_order.branch_id IS 'Cơ sở';

COMMENT ON COLUMN fb_order.booking_id IS 'Booking';

COMMENT ON COLUMN fb_order.allocation_id IS 'Phòng tại thời điểm gọi';

COMMENT ON COLUMN fb_order.channel IS 'STAFF/ROOM_QR';

COMMENT ON COLUMN fb_order.created_by IS 'Nhân viên nếu tạo hộ';

COMMENT ON COLUMN fb_order.qr_session_id IS 'Nguồn QR';

COMMENT ON COLUMN fb_order.status IS 'PENDING/ACCEPTED/SERVED/REJECTED/CANCELLED';

COMMENT ON COLUMN fb_order.accepted_by IS 'Lễ tân xác nhận';

COMMENT ON COLUMN fb_order.accepted_at IS 'Thời điểm chốt bán/trừ kho';

COMMENT ON COLUMN fb_order.served_at IS 'Phục vụ';

COMMENT ON COLUMN fb_order.idempotency_key IS 'Chống gửi lặp';

COMMENT ON COLUMN fb_order.created_at IS 'Thời điểm tạo';

CREATE TABLE order_line (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  menu_item_id uuid NOT NULL,
  booking_combo_item_id uuid,
  name_snapshot varchar(200) NOT NULL,
  quantity numeric(18,3) NOT NULL,
  unit_price numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity > 0 AND unit_price >= 0),
  CHECK (booking_combo_item_id IS NULL OR unit_price = 0)
);

COMMENT ON TABLE order_line IS 'Dòng đơn; chia dòng trong combo và ngoài combo khi vượt quyền hưởng.';

COMMENT ON COLUMN order_line.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN order_line.order_id IS 'Đơn';

COMMENT ON COLUMN order_line.branch_id IS 'Cơ sở';

COMMENT ON COLUMN order_line.booking_id IS 'Booking để kiểm tra quyền hưởng combo';

COMMENT ON COLUMN order_line.menu_item_id IS 'Món';

COMMENT ON COLUMN order_line.booking_combo_item_id IS 'Null là món tính thêm';

COMMENT ON COLUMN order_line.name_snapshot IS 'Tên lúc đặt';

COMMENT ON COLUMN order_line.quantity IS 'Số lượng';

COMMENT ON COLUMN order_line.unit_price IS 'Giá snapshot; 0 với món đã nằm trong combo';

COMMENT ON COLUMN order_line.created_at IS 'Thời điểm tạo';

CREATE TABLE stock_receipt (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  code varchar(60) NOT NULL UNIQUE,
  supplier_name varchar(200) NOT NULL,
  supplier_invoice varchar(200),
  expense_id uuid,
  received_at timestamptz NOT NULL,
  status varchar(20) NOT NULL,
  posted_by uuid,
  posted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, branch_id),
  CHECK (status IN ('DRAFT','POSTED','CANCELLED'))
);

COMMENT ON TABLE stock_receipt IS 'Phiếu nhập mua hàng; không tự ghi chi phí lần hai khi trả tiền.';

COMMENT ON COLUMN stock_receipt.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN stock_receipt.branch_id IS 'Cơ sở nhận';

COMMENT ON COLUMN stock_receipt.code IS 'Mã phiếu';

COMMENT ON COLUMN stock_receipt.supplier_name IS 'Nhà cung cấp';

COMMENT ON COLUMN stock_receipt.supplier_invoice IS 'Số chứng từ';

COMMENT ON COLUMN stock_receipt.expense_id IS 'Chi phí mua liên quan';

COMMENT ON COLUMN stock_receipt.received_at IS 'Ngày nhận';

COMMENT ON COLUMN stock_receipt.status IS 'DRAFT/POSTED/CANCELLED';

COMMENT ON COLUMN stock_receipt.posted_by IS 'Người chốt';

COMMENT ON COLUMN stock_receipt.posted_at IS 'Ngày chốt';

COMMENT ON COLUMN stock_receipt.created_at IS 'Thời điểm tạo';

CREATE TABLE stock_receipt_line (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  receipt_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  inventory_item_id uuid NOT NULL,
  quantity numeric(18,3) NOT NULL,
  unit_cost numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity > 0 AND unit_cost >= 0)
);

COMMENT ON TABLE stock_receipt_line IS 'Chi tiết số lượng nhận theo đơn vị cơ sở.';

COMMENT ON COLUMN stock_receipt_line.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN stock_receipt_line.receipt_id IS 'Phiếu nhập';

COMMENT ON COLUMN stock_receipt_line.branch_id IS 'Cơ sở';

COMMENT ON COLUMN stock_receipt_line.inventory_item_id IS 'Hàng';

COMMENT ON COLUMN stock_receipt_line.quantity IS 'Số lượng nhận';

COMMENT ON COLUMN stock_receipt_line.unit_cost IS 'Giá vốn đơn vị';

COMMENT ON COLUMN stock_receipt_line.created_at IS 'Thời điểm tạo';

CREATE TABLE stock_movement (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  inventory_item_id uuid NOT NULL,
  kind varchar(20) NOT NULL,
  quantity_delta numeric(18,3) NOT NULL,
  quantity_before numeric(18,3) NOT NULL,
  quantity_after numeric(18,3) NOT NULL,
  unit_cost numeric(18,0),
  receipt_line_id uuid,
  order_line_id uuid,
  reverses_id uuid,
  reason text NOT NULL,
  actor_id uuid NOT NULL,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity_delta <> 0),
  CHECK (quantity_after = quantity_before + quantity_delta AND quantity_after >= 0 AND quantity_before >= 0),
  CHECK (kind IN ('OPENING','RECEIPT','SALE','ADJUSTMENT','RETURN')),
  CHECK (kind <> 'RECEIPT' OR (receipt_line_id IS NOT NULL AND quantity_delta > 0)),
  CHECK (kind <> 'SALE' OR (order_line_id IS NOT NULL AND quantity_delta < 0))
);

COMMENT ON TABLE stock_movement IS 'Sổ kho append-only: đầu kỳ, nhập, bán, điều chỉnh hoặc hoàn hàng thực tế.';

COMMENT ON COLUMN stock_movement.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN stock_movement.inventory_item_id IS 'Hàng tồn';

COMMENT ON COLUMN stock_movement.kind IS 'OPENING/RECEIPT/SALE/ADJUSTMENT/RETURN';

COMMENT ON COLUMN stock_movement.quantity_delta IS 'Biến động có dấu';

COMMENT ON COLUMN stock_movement.quantity_before IS 'Trước';

COMMENT ON COLUMN stock_movement.quantity_after IS 'Sau';

COMMENT ON COLUMN stock_movement.unit_cost IS 'Giá vốn nếu có';

COMMENT ON COLUMN stock_movement.receipt_line_id IS 'Dòng nhập nguồn';

COMMENT ON COLUMN stock_movement.order_line_id IS 'Dòng bán nguồn';

COMMENT ON COLUMN stock_movement.reverses_id IS 'Dòng bị đảo nếu có';

COMMENT ON COLUMN stock_movement.reason IS 'Lý do';

COMMENT ON COLUMN stock_movement.actor_id IS 'Người ghi';

COMMENT ON COLUMN stock_movement.idempotency_key IS 'Chống ghi kho hai lần';

COMMENT ON COLUMN stock_movement.created_at IS 'Thời điểm tạo';

CREATE TABLE asset_category (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(200) NOT NULL,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, code)
);

COMMENT ON TABLE asset_category IS 'Danh mục tài sản của từng cơ sở do quản lý cơ sở tạo.';

COMMENT ON COLUMN asset_category.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN asset_category.branch_id IS 'Cơ sở sở hữu danh mục';

COMMENT ON COLUMN asset_category.code IS 'Mã danh mục';

COMMENT ON COLUMN asset_category.name IS 'Tên nhóm';

COMMENT ON COLUMN asset_category.active IS 'Còn dùng';

COMMENT ON COLUMN asset_category.created_at IS 'Thời điểm tạo';

CREATE TABLE asset (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  category_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  room_id uuid,
  location_note varchar(200),
  name varchar(200) NOT NULL,
  serial_number varchar(200),
  purchase_date date,
  purchase_value numeric(18,0),
  expense_id uuid,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('IN_USE','BROKEN','REPAIRING','IN_TRANSIT','RETIRED')),
  CHECK (purchase_value IS NULL OR purchase_value >= 0)
);

COMMENT ON TABLE asset IS 'Tài sản/thiết bị xác định riêng bằng mã; cơ sở hiện tại thay đổi sau nhận bàn giao.';

COMMENT ON COLUMN asset.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN asset.code IS 'Mã toàn chuỗi';

COMMENT ON COLUMN asset.category_id IS 'Nhóm phân loại nguồn, giữ khi chuyển cơ sở';

COMMENT ON COLUMN asset.branch_id IS 'Cơ sở hiện tại';

COMMENT ON COLUMN asset.room_id IS 'Phòng hiện tại; null là khu chung';

COMMENT ON COLUMN asset.location_note IS 'Vị trí khác';

COMMENT ON COLUMN asset.name IS 'Tên';

COMMENT ON COLUMN asset.serial_number IS 'Số serial';

COMMENT ON COLUMN asset.purchase_date IS 'Ngày mua';

COMMENT ON COLUMN asset.purchase_value IS 'Giá mua';

COMMENT ON COLUMN asset.expense_id IS 'Chứng từ mua/thay thế';

COMMENT ON COLUMN asset.status IS 'IN_USE/BROKEN/REPAIRING/IN_TRANSIT/RETIRED';

COMMENT ON COLUMN asset.created_at IS 'Thời điểm tạo';

CREATE TABLE asset_incident (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  asset_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  reported_by uuid NOT NULL,
  description text NOT NULL,
  evidence_id uuid,
  room_unusable boolean NOT NULL,
  handling_plan varchar(20),
  assessed_by uuid,
  assessed_at timestamptz,
  assessment_note text,
  replacement_asset_id uuid,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('REPORTED','ASSESSED','IN_PROGRESS','VERIFIED','CLOSED')),
  CHECK (handling_plan IS NULL OR handling_plan IN ('REPAIR','REPLACE','NO_ACTION'))
);

COMMENT ON TABLE asset_incident IS 'Sự cố tài sản; lưu cơ sở phát sinh để không bị thay đổi sau điều chuyển.';

COMMENT ON COLUMN asset_incident.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN asset_incident.asset_id IS 'Tài sản';

COMMENT ON COLUMN asset_incident.branch_id IS 'Cơ sở tại lúc sự cố';

COMMENT ON COLUMN asset_incident.reported_by IS 'Người báo';

COMMENT ON COLUMN asset_incident.description IS 'Vấn đề';

COMMENT ON COLUMN asset_incident.evidence_id IS 'Ảnh/chứng cứ';

COMMENT ON COLUMN asset_incident.room_unusable IS 'Ảnh hưởng khả dụng phòng';

COMMENT ON COLUMN asset_incident.handling_plan IS 'REPAIR/REPLACE/NO_ACTION';

COMMENT ON COLUMN asset_incident.assessed_by IS 'QLCS đánh giá';

COMMENT ON COLUMN asset_incident.assessed_at IS 'Ngày đánh giá';

COMMENT ON COLUMN asset_incident.assessment_note IS 'Phương án';

COMMENT ON COLUMN asset_incident.replacement_asset_id IS 'Tài sản thay thế được đăng ký';

COMMENT ON COLUMN asset_incident.status IS 'REPORTED/ASSESSED/IN_PROGRESS/VERIFIED/CLOSED';

COMMENT ON COLUMN asset_incident.created_at IS 'Thời điểm tạo';

CREATE TABLE asset_repair (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL,
  vendor_name varchar(200) NOT NULL,
  planned_cost numeric(18,0),
  actual_cost numeric(18,0),
  started_at timestamptz,
  finished_at timestamptz,
  outcome text,
  verified_by uuid,
  verified_at timestamptz,
  expense_id uuid,
  evidence_id uuid,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (planned_cost IS NULL OR planned_cost >= 0),
  CHECK (actual_cost IS NULL OR actual_cost >= 0),
  CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at))
);

COMMENT ON TABLE asset_repair IS 'Một sự cố có nhiều lần sửa; có nghiệm thu và chi phí liên kết.';

COMMENT ON COLUMN asset_repair.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN asset_repair.incident_id IS 'Sự cố';

COMMENT ON COLUMN asset_repair.vendor_name IS 'Đơn vị sửa';

COMMENT ON COLUMN asset_repair.planned_cost IS 'Dự kiến';

COMMENT ON COLUMN asset_repair.actual_cost IS 'Thực tế';

COMMENT ON COLUMN asset_repair.started_at IS 'Bắt đầu';

COMMENT ON COLUMN asset_repair.finished_at IS 'Hoàn thành';

COMMENT ON COLUMN asset_repair.outcome IS 'Kết quả';

COMMENT ON COLUMN asset_repair.verified_by IS 'QLCS nghiệm thu';

COMMENT ON COLUMN asset_repair.verified_at IS 'Thời điểm nghiệm thu';

COMMENT ON COLUMN asset_repair.expense_id IS 'Chứng từ chi phí, không nhân đôi chi phí';

COMMENT ON COLUMN asset_repair.evidence_id IS 'Biên bản/ảnh';

COMMENT ON COLUMN asset_repair.created_at IS 'Thời điểm tạo';

CREATE TABLE asset_transfer (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  asset_id uuid NOT NULL,
  from_branch_id uuid NOT NULL,
  to_branch_id uuid NOT NULL,
  to_room_id uuid,
  initiated_by uuid NOT NULL,
  reason text NOT NULL,
  handed_over_by uuid,
  handed_over_at timestamptz,
  received_by uuid,
  received_at timestamptz,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (from_branch_id <> to_branch_id),
  CHECK (status IN ('REQUESTED','IN_TRANSIT','COMPLETED','CANCELLED')),
  CHECK (received_at IS NULL OR (handed_over_at IS NOT NULL AND received_at >= handed_over_at)),
  CHECK (status <> 'COMPLETED' OR (received_at IS NOT NULL AND received_by IS NOT NULL AND handed_over_by IS NOT NULL))
);

COMMENT ON TABLE asset_transfer IS 'Chuyển từng tài sản giữa cơ sở; hai xác nhận riêng.';

COMMENT ON COLUMN asset_transfer.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN asset_transfer.asset_id IS 'Tài sản';

COMMENT ON COLUMN asset_transfer.from_branch_id IS 'Nguồn';

COMMENT ON COLUMN asset_transfer.to_branch_id IS 'Đích';

COMMENT ON COLUMN asset_transfer.to_room_id IS 'Phòng nhận';

COMMENT ON COLUMN asset_transfer.initiated_by IS 'QLCC';

COMMENT ON COLUMN asset_transfer.reason IS 'Lý do';

COMMENT ON COLUMN asset_transfer.handed_over_by IS 'QLCS nguồn';

COMMENT ON COLUMN asset_transfer.handed_over_at IS 'Đã giao';

COMMENT ON COLUMN asset_transfer.received_by IS 'QLCS đích';

COMMENT ON COLUMN asset_transfer.received_at IS 'Đã nhận';

COMMENT ON COLUMN asset_transfer.status IS 'REQUESTED/IN_TRANSIT/COMPLETED/CANCELLED';

COMMENT ON COLUMN asset_transfer.created_at IS 'Thời điểm tạo';

CREATE TABLE employee (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  user_id uuid UNIQUE,
  full_name varchar(200) NOT NULL,
  identity_number varchar(30) UNIQUE,
  phone varchar(30),
  address text,
  bank_name varchar(200),
  bank_account varchar(60),
  bank_holder varchar(200),
  hired_on date NOT NULL,
  resigned_on date,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('ACTIVE','INACTIVE','RESIGNED')),
  CHECK (resigned_on IS NULL OR resigned_on >= hired_on)
);

COMMENT ON TABLE employee IS 'Hồ sơ nhân viên; có thể chưa có tài khoản.';

COMMENT ON COLUMN employee.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN employee.code IS 'Mã nhân viên';

COMMENT ON COLUMN employee.user_id IS 'Tối đa một tài khoản';

COMMENT ON COLUMN employee.full_name IS 'Họ tên';

COMMENT ON COLUMN employee.identity_number IS 'Số giấy tờ, phân quyền truy cập';

COMMENT ON COLUMN employee.phone IS 'Điện thoại';

COMMENT ON COLUMN employee.address IS 'Địa chỉ';

COMMENT ON COLUMN employee.bank_name IS 'Ngân hàng nhận lương';

COMMENT ON COLUMN employee.bank_account IS 'Tài khoản';

COMMENT ON COLUMN employee.bank_holder IS 'Chủ tài khoản';

COMMENT ON COLUMN employee.hired_on IS 'Ngày vào';

COMMENT ON COLUMN employee.resigned_on IS 'Ngày nghỉ';

COMMENT ON COLUMN employee.status IS 'ACTIVE/INACTIVE/RESIGNED';

COMMENT ON COLUMN employee.created_at IS 'Thời điểm tạo';

CREATE TABLE employee_assignment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  employee_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  valid_from timestamptz NOT NULL,
  valid_to timestamptz,
  is_branch_manager boolean NOT NULL,
  assigned_by uuid NOT NULL,
  reason text,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (valid_to IS NULL OR valid_to > valid_from)
);

COMMENT ON TABLE employee_assignment IS 'Lịch sử home branch và nhiệm vụ quản lý; không ghi đè branch cũ.';

COMMENT ON COLUMN employee_assignment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN employee_assignment.employee_id IS 'Nhân viên';

COMMENT ON COLUMN employee_assignment.branch_id IS 'Home branch';

COMMENT ON COLUMN employee_assignment.valid_from IS 'Bắt đầu';

COMMENT ON COLUMN employee_assignment.valid_to IS 'Kết thúc không bao gồm';

COMMENT ON COLUMN employee_assignment.is_branch_manager IS 'Phân công QLCS';

COMMENT ON COLUMN employee_assignment.assigned_by IS 'QLCC';

COMMENT ON COLUMN employee_assignment.reason IS 'Lý do phân công/chuyển';

COMMENT ON COLUMN employee_assignment.created_at IS 'Thời điểm tạo';

CREATE TABLE employee_pay_rate (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  employee_id uuid NOT NULL,
  valid_from date NOT NULL,
  valid_to date,
  hourly_rate numeric(18,0) NOT NULL,
  changed_by uuid NOT NULL,
  reason text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (hourly_rate >= 0),
  CHECK (valid_to IS NULL OR valid_to > valid_from)
);

COMMENT ON TABLE employee_pay_rate IS 'Lịch sử đơn giá lương giờ có ngày hiệu lực.';

COMMENT ON COLUMN employee_pay_rate.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN employee_pay_rate.employee_id IS 'Nhân viên';

COMMENT ON COLUMN employee_pay_rate.valid_from IS 'Ngày bắt đầu';

COMMENT ON COLUMN employee_pay_rate.valid_to IS 'Ngày kết thúc không bao gồm';

COMMENT ON COLUMN employee_pay_rate.hourly_rate IS 'Lương giờ';

COMMENT ON COLUMN employee_pay_rate.changed_by IS 'Người cấu hình được ủy quyền';

COMMENT ON COLUMN employee_pay_rate.reason IS 'Lý do';

COMMENT ON COLUMN employee_pay_rate.created_at IS 'Thời điểm tạo';

CREATE TABLE shift_template (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  start_minute integer NOT NULL,
  end_minute integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((start_minute,end_minute) IN ((0,420),(420,720),(720,1080),(1080,1440))),
  UNIQUE (start_minute, end_minute)
);

COMMENT ON TABLE shift_template IS 'Bốn ca cố định theo SRS.';

COMMENT ON COLUMN shift_template.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN shift_template.code IS 'NIGHT/MORNING/AFTERNOON/EVENING';

COMMENT ON COLUMN shift_template.start_minute IS 'Phút đầu ngày';

COMMENT ON COLUMN shift_template.end_minute IS 'Phút cuối, ca cuối kết thúc 1440';

COMMENT ON COLUMN shift_template.created_at IS 'Thời điểm tạo';

CREATE TABLE scheduled_shift (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  template_id uuid NOT NULL,
  work_date date NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  capacity integer NOT NULL,
  status varchar(20) NOT NULL,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, template_id, work_date),
  CHECK (ends_at > starts_at AND capacity > 0),
  CHECK (status IN ('OPEN','CLOSED','CANCELLED'))
);

COMMENT ON TABLE scheduled_shift IS 'Ca được mở theo ngày tại cơ sở; không chứa employee_id.';

COMMENT ON COLUMN scheduled_shift.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN scheduled_shift.branch_id IS 'Cơ sở';

COMMENT ON COLUMN scheduled_shift.template_id IS 'Ca';

COMMENT ON COLUMN scheduled_shift.work_date IS 'Ngày công địa phương';

COMMENT ON COLUMN scheduled_shift.starts_at IS 'Đầu ca thực tế đã quy đổi múi giờ';

COMMENT ON COLUMN scheduled_shift.ends_at IS 'Cuối ca';

COMMENT ON COLUMN scheduled_shift.capacity IS 'Số người được đăng ký';

COMMENT ON COLUMN scheduled_shift.status IS 'OPEN/CLOSED/CANCELLED';

COMMENT ON COLUMN scheduled_shift.created_by IS 'QLCS';

COMMENT ON COLUMN scheduled_shift.created_at IS 'Thời điểm tạo';

CREATE TABLE shift_assignment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  shift_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  source varchar(20) NOT NULL,
  status varchar(20) NOT NULL,
  assigned_by uuid NOT NULL,
  cancelled_at timestamptz,
  reason text,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('CONFIRMED','CANCELLED')),
  CHECK (source IN ('SELF','MANAGER'))
);

COMMENT ON TABLE shift_assignment IS 'Một bảng cho đăng ký tự động và phân ca bởi QLCS.';

COMMENT ON COLUMN shift_assignment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN shift_assignment.shift_id IS 'Ca';

COMMENT ON COLUMN shift_assignment.employee_id IS 'Nhân viên';

COMMENT ON COLUMN shift_assignment.source IS 'SELF/MANAGER';

COMMENT ON COLUMN shift_assignment.status IS 'CONFIRMED/CANCELLED';

COMMENT ON COLUMN shift_assignment.assigned_by IS 'Người đăng ký/phân công';

COMMENT ON COLUMN shift_assignment.cancelled_at IS 'Hủy phân công';

COMMENT ON COLUMN shift_assignment.reason IS 'Lý do thay đổi';

COMMENT ON COLUMN shift_assignment.created_at IS 'Thời điểm tạo';

CREATE TABLE attendance_period (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  month date NOT NULL,
  status varchar(20) NOT NULL,
  closed_by uuid,
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, month),
  CHECK (month = date_trunc('month',month)::date),
  CHECK (status IN ('OPEN','CLOSED')),
  CHECK (status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);

COMMENT ON TABLE attendance_period IS 'Bảng công tháng theo cơ sở, chốt sau khi giải quyết sai lệch.';

COMMENT ON COLUMN attendance_period.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN attendance_period.branch_id IS 'Cơ sở';

COMMENT ON COLUMN attendance_period.month IS 'Ngày đầu tháng';

COMMENT ON COLUMN attendance_period.status IS 'OPEN/CLOSED';

COMMENT ON COLUMN attendance_period.closed_by IS 'QLCS';

COMMENT ON COLUMN attendance_period.closed_at IS 'Chốt';

COMMENT ON COLUMN attendance_period.created_at IS 'Thời điểm tạo';

CREATE TABLE attendance (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  assignment_id uuid NOT NULL UNIQUE,
  period_id uuid NOT NULL,
  actual_in timestamptz,
  actual_out timestamptz,
  break_minutes integer NOT NULL,
  worked_minutes integer NOT NULL,
  exception_note text,
  recorded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (break_minutes >= 0 AND worked_minutes >= 0),
  CHECK (actual_out IS NULL OR (actual_in IS NOT NULL AND actual_out >= actual_in))
);

COMMENT ON TABLE attendance IS 'Chấm công theo phân ca; giờ thực tế độc lập giờ kế hoạch.';

COMMENT ON COLUMN attendance.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN attendance.assignment_id IS 'Phân ca';

COMMENT ON COLUMN attendance.period_id IS 'Tháng/cơ sở ghi công';

COMMENT ON COLUMN attendance.actual_in IS 'Vào làm';

COMMENT ON COLUMN attendance.actual_out IS 'Ra làm';

COMMENT ON COLUMN attendance.break_minutes IS 'Nghỉ không trả công';

COMMENT ON COLUMN attendance.worked_minutes IS 'Phút thực làm được QLCS xác nhận';

COMMENT ON COLUMN attendance.exception_note IS 'Đi muộn, về sớm, tăng ca...';

COMMENT ON COLUMN attendance.recorded_by IS 'QLCS';

COMMENT ON COLUMN attendance.created_at IS 'Thời điểm tạo';

CREATE TABLE attendance_issue (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  attendance_id uuid NOT NULL,
  reported_by uuid NOT NULL,
  description text NOT NULL,
  status varchar(20) NOT NULL,
  resolved_by uuid,
  resolved_at timestamptz,
  resolution text,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('OPEN','RESOLVED','REJECTED'))
);

COMMENT ON TABLE attendance_issue IS 'Phản ánh công và kết quả giải quyết, không sửa trực tiếp công bởi lễ tân.';

COMMENT ON COLUMN attendance_issue.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN attendance_issue.attendance_id IS 'Bản ghi công';

COMMENT ON COLUMN attendance_issue.reported_by IS 'Nhân viên';

COMMENT ON COLUMN attendance_issue.description IS 'Vấn đề';

COMMENT ON COLUMN attendance_issue.status IS 'OPEN/RESOLVED/REJECTED';

COMMENT ON COLUMN attendance_issue.resolved_by IS 'QLCS';

COMMENT ON COLUMN attendance_issue.resolved_at IS 'Giờ xử lý';

COMMENT ON COLUMN attendance_issue.resolution IS 'Kết quả';

COMMENT ON COLUMN attendance_issue.created_at IS 'Thời điểm tạo';

CREATE TABLE payroll_period (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  month date NOT NULL UNIQUE,
  status varchar(20) NOT NULL,
  confirmed_by uuid,
  confirmed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (month = date_trunc('month',month)::date),
  CHECK (status IN ('DRAFT','CONFIRMED')),
  CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);

COMMENT ON TABLE payroll_period IS 'Kỳ lương toàn chuỗi theo tháng.';

COMMENT ON COLUMN payroll_period.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN payroll_period.month IS 'Ngày đầu tháng';

COMMENT ON COLUMN payroll_period.status IS 'DRAFT/CONFIRMED';

COMMENT ON COLUMN payroll_period.confirmed_by IS 'Kế toán';

COMMENT ON COLUMN payroll_period.confirmed_at IS 'Xác nhận';

COMMENT ON COLUMN payroll_period.created_at IS 'Thời điểm tạo';

CREATE TABLE payroll_entry (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  period_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  gross_amount numeric(18,0) NOT NULL,
  deduction_amount numeric(18,0) NOT NULL,
  net_amount numeric(18,0) NOT NULL,
  bank_name_snapshot varchar(200),
  bank_account_snapshot varchar(60),
  bank_holder_snapshot varchar(200),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (period_id, employee_id),
  CHECK (gross_amount >= 0 AND deduction_amount >= 0),
  CHECK (net_amount = gross_amount - deduction_amount AND net_amount >= 0)
);

COMMENT ON TABLE payroll_entry IS 'Một người một kỳ lương, dù chuyển cơ sở giữa tháng.';

COMMENT ON COLUMN payroll_entry.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN payroll_entry.period_id IS 'Kỳ lương';

COMMENT ON COLUMN payroll_entry.employee_id IS 'Nhân viên';

COMMENT ON COLUMN payroll_entry.gross_amount IS 'Tổng cộng trước khấu trừ';

COMMENT ON COLUMN payroll_entry.deduction_amount IS 'Khấu trừ, gồm ứng thực trả';

COMMENT ON COLUMN payroll_entry.net_amount IS 'Thực nhận';

COMMENT ON COLUMN payroll_entry.bank_name_snapshot IS 'Ngân hàng khi chốt';

COMMENT ON COLUMN payroll_entry.bank_account_snapshot IS 'TK lúc chốt';

COMMENT ON COLUMN payroll_entry.bank_holder_snapshot IS 'Chủ TK lúc chốt';

COMMENT ON COLUMN payroll_entry.created_at IS 'Thời điểm tạo';

CREATE TABLE payroll_component (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  payroll_entry_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  attendance_id uuid,
  pay_rate_id uuid,
  kind varchar(25) NOT NULL,
  minutes integer NOT NULL,
  hourly_rate_snapshot numeric(18,0) NOT NULL,
  multiplier numeric(18,4) NOT NULL,
  amount numeric(18,0) NOT NULL,
  calculation_snapshot jsonb NOT NULL,
  reason text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (minutes >= 0 AND hourly_rate_snapshot >= 0 AND multiplier >= 0 AND amount >= 0),
  CHECK (kind IN ('BASE','OVERTIME','NIGHT','ADDITION','DEDUCTION'))
);

COMMENT ON TABLE payroll_component IS 'Dòng tính lương theo công, cơ sở và giá hiệu lực; hỗ trợ nhiều giá trong tháng.';

COMMENT ON COLUMN payroll_component.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN payroll_component.payroll_entry_id IS 'Nhân viên/kỳ';

COMMENT ON COLUMN payroll_component.branch_id IS 'Cơ sở chịu chi phí';

COMMENT ON COLUMN payroll_component.attendance_id IS 'Công nguồn';

COMMENT ON COLUMN payroll_component.pay_rate_id IS 'Giá nguồn';

COMMENT ON COLUMN payroll_component.kind IS 'BASE/OVERTIME/NIGHT/ADDITION/DEDUCTION';

COMMENT ON COLUMN payroll_component.minutes IS 'Số phút dùng tính dòng';

COMMENT ON COLUMN payroll_component.hourly_rate_snapshot IS 'Đơn giá lúc tính';

COMMENT ON COLUMN payroll_component.multiplier IS 'Hệ số của dòng';

COMMENT ON COLUMN payroll_component.amount IS 'Số tiền dương, loại dòng quy định cộng/trừ';

COMMENT ON COLUMN payroll_component.calculation_snapshot IS 'Phân đoạn giờ, loại ngày, quy tắc và làm tròn';

COMMENT ON COLUMN payroll_component.reason IS 'Mô tả';

COMMENT ON COLUMN payroll_component.created_at IS 'Thời điểm tạo';

CREATE TABLE salary_advance (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  employee_id uuid NOT NULL,
  period_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  amount numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL,
  paid_at timestamptz,
  processed_by uuid NOT NULL,
  external_reference varchar(200),
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK (status IN ('PENDING','PAID','FAILED','CANCELLED')),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

COMMENT ON TABLE salary_advance IS 'Khoản ứng lương; chỉ PAID mới được khấu trừ.';

COMMENT ON COLUMN salary_advance.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN salary_advance.employee_id IS 'Người nhận';

COMMENT ON COLUMN salary_advance.period_id IS 'Kỳ dự kiến khấu trừ';

COMMENT ON COLUMN salary_advance.branch_id IS 'Cơ sở của người nhận lúc ứng';

COMMENT ON COLUMN salary_advance.amount IS 'Số tiền';

COMMENT ON COLUMN salary_advance.status IS 'PENDING/PAID/FAILED/CANCELLED';

COMMENT ON COLUMN salary_advance.paid_at IS 'Đã trả';

COMMENT ON COLUMN salary_advance.processed_by IS 'Kế toán';

COMMENT ON COLUMN salary_advance.external_reference IS 'Mã chuyển';

COMMENT ON COLUMN salary_advance.idempotency_key IS 'Chống chi lặp';

COMMENT ON COLUMN salary_advance.created_at IS 'Thời điểm tạo';

CREATE TABLE advance_application (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  advance_id uuid NOT NULL UNIQUE,
  payroll_entry_id uuid NOT NULL,
  amount numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0)
);

COMMENT ON TABLE advance_application IS 'Mỗi khoản ứng thực trả được khấu trừ tối đa một lần.';

COMMENT ON COLUMN advance_application.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN advance_application.advance_id IS 'Khoản ứng';

COMMENT ON COLUMN advance_application.payroll_entry_id IS 'Bảng lương khấu trừ';

COMMENT ON COLUMN advance_application.amount IS 'Số tiền khấu trừ bằng khoản ứng thực trả';

COMMENT ON COLUMN advance_application.created_at IS 'Thời điểm tạo';

CREATE TABLE salary_payment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  payroll_entry_id uuid NOT NULL,
  amount numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL,
  paid_at timestamptz,
  external_reference varchar(200),
  failure_reason text,
  processed_by uuid NOT NULL,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK (status IN ('PENDING','PAID','FAILED')),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

COMMENT ON TABLE salary_payment IS 'Lần thử thanh toán lương; nhiều lần thất bại, chỉ một lần PAID.';

COMMENT ON COLUMN salary_payment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN salary_payment.payroll_entry_id IS 'Bảng lương';

COMMENT ON COLUMN salary_payment.amount IS 'Số tiền thực nhận đã chốt';

COMMENT ON COLUMN salary_payment.status IS 'PENDING/PAID/FAILED';

COMMENT ON COLUMN salary_payment.paid_at IS 'Xác nhận thành công';

COMMENT ON COLUMN salary_payment.external_reference IS 'Tham chiếu ngân hàng';

COMMENT ON COLUMN salary_payment.failure_reason IS 'Lỗi';

COMMENT ON COLUMN salary_payment.processed_by IS 'Kế toán';

COMMENT ON COLUMN salary_payment.idempotency_key IS 'Chống xử lý lặp';

COMMENT ON COLUMN salary_payment.created_at IS 'Thời điểm tạo';

CREATE TABLE financial_account (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  kind varchar(25) NOT NULL,
  branch_id uuid,
  bank_name varchar(200),
  bank_account_number varchar(60),
  account_holder varchar(200),
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (kind IN ('CENTRAL_FUND','BRANCH_FUND','CASH_DRAWER','BANK')),
  CHECK (kind NOT IN ('BRANCH_FUND','CASH_DRAWER') OR branch_id IS NOT NULL)
);

COMMENT ON TABLE financial_account IS 'Quỹ tiền mặt hoặc tài khoản ngân hàng; một TK chung có thể nhận tiền nhiều cơ sở.';

COMMENT ON COLUMN financial_account.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN financial_account.code IS 'Mã quỹ/TK';

COMMENT ON COLUMN financial_account.name IS 'Tên';

COMMENT ON COLUMN financial_account.kind IS 'CENTRAL_FUND/BRANCH_FUND/CASH_DRAWER/BANK';

COMMENT ON COLUMN financial_account.branch_id IS 'Null với quỹ chung/TK chung';

COMMENT ON COLUMN financial_account.bank_name IS 'Ngân hàng';

COMMENT ON COLUMN financial_account.bank_account_number IS 'Số TK';

COMMENT ON COLUMN financial_account.account_holder IS 'Tên chủ';

COMMENT ON COLUMN financial_account.active IS 'Còn dùng';

COMMENT ON COLUMN financial_account.created_at IS 'Thời điểm tạo';

CREATE TABLE payment_request (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  bill_id uuid,
  account_id uuid NOT NULL,
  purpose varchar(20) NOT NULL,
  reference_code varchar(80) NOT NULL UNIQUE,
  expected_amount numeric(18,0) NOT NULL,
  expires_at timestamptz NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, booking_id),
  CHECK (expected_amount > 0 AND expires_at > created_at),
  CHECK ((purpose = 'DEPOSIT' AND bill_id IS NULL) OR (purpose = 'BILL' AND bill_id IS NOT NULL)),
  CHECK (status IN ('PENDING','SETTLED','EXPIRED','CANCELLED'))
);

COMMENT ON TABLE payment_request IS 'Yêu cầu thu tiền và QR có mã tham chiếu duy nhất.';

COMMENT ON COLUMN payment_request.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN payment_request.branch_id IS 'Cơ sở phát sinh, không suy ra từ TK ngân hàng';

COMMENT ON COLUMN payment_request.booking_id IS 'Booking';

COMMENT ON COLUMN payment_request.bill_id IS 'Bắt buộc cho thanh toán hóa đơn';

COMMENT ON COLUMN payment_request.account_id IS 'TK nhận QR';

COMMENT ON COLUMN payment_request.purpose IS 'DEPOSIT/BILL';

COMMENT ON COLUMN payment_request.reference_code IS 'Ví dụ GZ-B01-X7K2, chứa định danh duy nhất';

COMMENT ON COLUMN payment_request.expected_amount IS 'Số tiền QR cố định cho yêu cầu';

COMMENT ON COLUMN payment_request.expires_at IS 'Hạn dùng';

COMMENT ON COLUMN payment_request.status IS 'PENDING/SETTLED/EXPIRED/CANCELLED';

COMMENT ON COLUMN payment_request.created_at IS 'Thời điểm tạo';

CREATE TABLE bank_transaction (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL,
  provider varchar(30) NOT NULL,
  external_id varchar(120) NOT NULL,
  direction varchar(10) NOT NULL,
  amount numeric(18,0) NOT NULL,
  transfer_content text NOT NULL,
  occurred_at timestamptz NOT NULL,
  match_status varchar(20) NOT NULL,
  raw_payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (provider, account_id, external_id),
  CHECK (amount > 0),
  CHECK (direction IN ('IN','OUT')),
  CHECK (match_status IN ('UNMATCHED','MATCHED','EXCEPTION'))
);

COMMENT ON TABLE bank_transaction IS 'Giao dịch ngân hàng nhận qua SePay; lưu giao dịch lệch/chưa khớp để đối soát.';

COMMENT ON COLUMN bank_transaction.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN bank_transaction.account_id IS 'TK nhận/chi';

COMMENT ON COLUMN bank_transaction.provider IS 'SEPAY';

COMMENT ON COLUMN bank_transaction.external_id IS 'ID giao dịch của provider';

COMMENT ON COLUMN bank_transaction.direction IS 'IN/OUT';

COMMENT ON COLUMN bank_transaction.amount IS 'Số tiền thực tế';

COMMENT ON COLUMN bank_transaction.transfer_content IS 'Nội dung CK';

COMMENT ON COLUMN bank_transaction.occurred_at IS 'Giờ ngân hàng';

COMMENT ON COLUMN bank_transaction.match_status IS 'UNMATCHED/MATCHED/EXCEPTION';

COMMENT ON COLUMN bank_transaction.raw_payload IS 'Payload đã loại bỏ bí mật';

COMMENT ON COLUMN bank_transaction.created_at IS 'Thời điểm tạo';

CREATE TABLE payment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  bill_id uuid,
  purpose varchar(20) NOT NULL,
  method varchar(20) NOT NULL,
  amount numeric(18,0) NOT NULL,
  cash_tendered numeric(18,0),
  cash_change numeric(18,0),
  request_id uuid UNIQUE,
  bank_transaction_id uuid UNIQUE,
  received_at timestamptz NOT NULL,
  received_by uuid,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (id, booking_id),
  CHECK (amount > 0),
  CHECK ((purpose = 'DEPOSIT' AND bill_id IS NULL) OR (purpose = 'BILL' AND bill_id IS NOT NULL)),
  CHECK ((method = 'BANK_TRANSFER' AND request_id IS NOT NULL AND bank_transaction_id IS NOT NULL AND cash_tendered IS NULL AND cash_change IS NULL) OR (method = 'CASH' AND request_id IS NULL AND bank_transaction_id IS NULL AND cash_tendered IS NOT NULL AND cash_change IS NOT NULL AND cash_tendered >= amount AND cash_change = cash_tendered - amount)),
  CHECK (purpose <> 'DEPOSIT' OR method = 'BANK_TRANSFER')
);

COMMENT ON TABLE payment IS 'Khoản thu khách đã xác nhận; pending thuộc payment_request.';

COMMENT ON COLUMN payment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN payment.branch_id IS 'Cơ sở hưởng dòng tiền';

COMMENT ON COLUMN payment.booking_id IS 'Booking';

COMMENT ON COLUMN payment.bill_id IS 'Null với thu cọc';

COMMENT ON COLUMN payment.purpose IS 'DEPOSIT/BILL';

COMMENT ON COLUMN payment.method IS 'CASH/BANK_TRANSFER';

COMMENT ON COLUMN payment.amount IS 'Tiền được chấp nhận thanh toán';

COMMENT ON COLUMN payment.cash_tendered IS 'Tiền khách đưa nếu tiền mặt';

COMMENT ON COLUMN payment.cash_change IS 'Tiền thừa trả ngay';

COMMENT ON COLUMN payment.request_id IS 'Yêu cầu QR khớp';

COMMENT ON COLUMN payment.bank_transaction_id IS 'Một giao dịch chỉ ghi nhận thu một lần';

COMMENT ON COLUMN payment.received_at IS 'Thời điểm thu';

COMMENT ON COLUMN payment.received_by IS 'Lễ tân, null nếu xác nhận hệ thống';

COMMENT ON COLUMN payment.idempotency_key IS 'Chống thu lặp';

COMMENT ON COLUMN payment.created_at IS 'Thời điểm tạo';

CREATE TABLE deposit (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE deposit IS 'Ví cọc của booking; số dư được tính từ các bút toán cọc.';

COMMENT ON COLUMN deposit.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN deposit.booking_id IS 'Một tài khoản cọc mỗi booking';

COMMENT ON COLUMN deposit.created_at IS 'Thời điểm tạo';

CREATE TABLE deposit_entry (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  deposit_id uuid NOT NULL,
  kind varchar(20) NOT NULL,
  amount numeric(18,0) NOT NULL,
  payment_id uuid UNIQUE,
  bill_id uuid,
  refund_id uuid UNIQUE,
  actor_id uuid,
  reason text NOT NULL,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK ((kind='RECEIVE' AND payment_id IS NOT NULL AND bill_id IS NULL AND refund_id IS NULL) OR (kind='APPLY' AND payment_id IS NULL AND bill_id IS NOT NULL AND refund_id IS NULL) OR (kind='REFUND' AND payment_id IS NULL AND bill_id IS NULL AND refund_id IS NOT NULL) OR (kind='FORFEIT' AND payment_id IS NULL AND bill_id IS NULL AND refund_id IS NULL))
);

COMMENT ON TABLE deposit_entry IS 'Nhật ký cọc: thu, áp vào hóa đơn, hoàn, tịch thu; không sửa/xóa.';

COMMENT ON COLUMN deposit_entry.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN deposit_entry.deposit_id IS 'Ví cọc';

COMMENT ON COLUMN deposit_entry.kind IS 'RECEIVE/APPLY/REFUND/FORFEIT';

COMMENT ON COLUMN deposit_entry.amount IS 'Số tiền dương';

COMMENT ON COLUMN deposit_entry.payment_id IS 'Thu cọc nguồn';

COMMENT ON COLUMN deposit_entry.bill_id IS 'Hóa đơn dùng cọc';

COMMENT ON COLUMN deposit_entry.refund_id IS 'Lần hoàn thành công';

COMMENT ON COLUMN deposit_entry.actor_id IS 'Người thực hiện';

COMMENT ON COLUMN deposit_entry.reason IS 'Lý do';

COMMENT ON COLUMN deposit_entry.idempotency_key IS 'Chống dùng lặp';

COMMENT ON COLUMN deposit_entry.created_at IS 'Thời điểm tạo';

CREATE TABLE refund_request (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  deposit_id uuid,
  overpayment_transaction_id uuid,
  kind varchar(20) NOT NULL,
  amount numeric(18,0) NOT NULL,
  reason text NOT NULL,
  beneficiary_name varchar(200) NOT NULL,
  bank_name varchar(200) NOT NULL,
  bank_account varchar(60) NOT NULL,
  requested_by uuid NOT NULL,
  status varchar(20) NOT NULL,
  processed_by uuid,
  paid_at timestamptz,
  external_reference varchar(200),
  evidence_id uuid,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK ((kind='DEPOSIT' AND deposit_id IS NOT NULL AND overpayment_transaction_id IS NULL) OR (kind='OVERPAYMENT' AND deposit_id IS NULL AND overpayment_transaction_id IS NOT NULL)),
  CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','PAID','REJECTED','FAILED')),
  CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND processed_by IS NOT NULL))
);

COMMENT ON TABLE refund_request IS 'Yêu cầu và kết quả hoàn cọc hoặc khoản thu thừa; kế toán thực chi.';

COMMENT ON COLUMN refund_request.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN refund_request.branch_id IS 'Cơ sở nguồn';

COMMENT ON COLUMN refund_request.booking_id IS 'Booking';

COMMENT ON COLUMN refund_request.deposit_id IS 'Nguồn cọc';

COMMENT ON COLUMN refund_request.overpayment_transaction_id IS 'Tiền chuyển thừa/chưa áp dụng';

COMMENT ON COLUMN refund_request.kind IS 'DEPOSIT/OVERPAYMENT';

COMMENT ON COLUMN refund_request.amount IS 'Yêu cầu hoàn';

COMMENT ON COLUMN refund_request.reason IS 'Lý do';

COMMENT ON COLUMN refund_request.beneficiary_name IS 'Tên người nhận';

COMMENT ON COLUMN refund_request.bank_name IS 'Ngân hàng';

COMMENT ON COLUMN refund_request.bank_account IS 'TK nhận';

COMMENT ON COLUMN refund_request.requested_by IS 'Người lập';

COMMENT ON COLUMN refund_request.status IS 'REQUESTED/APPROVED/PROCESSING/PAID/REJECTED/FAILED';

COMMENT ON COLUMN refund_request.processed_by IS 'Kế toán';

COMMENT ON COLUMN refund_request.paid_at IS 'Giờ thực hoàn';

COMMENT ON COLUMN refund_request.external_reference IS 'Tham chiếu hoàn';

COMMENT ON COLUMN refund_request.evidence_id IS 'Chứng từ';

COMMENT ON COLUMN refund_request.created_at IS 'Thời điểm tạo';

CREATE TABLE expense_category (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  class varchar(10) NOT NULL,
  active boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (class IN ('OPEX','CAPEX'))
);

COMMENT ON TABLE expense_category IS 'Danh mục chi phí chung do kế toán quản lý.';

COMMENT ON COLUMN expense_category.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense_category.code IS 'Mã';

COMMENT ON COLUMN expense_category.name IS 'Tên';

COMMENT ON COLUMN expense_category.class IS 'OPEX/CAPEX';

COMMENT ON COLUMN expense_category.active IS 'Còn dùng';

COMMENT ON COLUMN expense_category.created_at IS 'Thời điểm tạo';

CREATE TABLE expense (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  code varchar(60) NOT NULL UNIQUE,
  category_id uuid NOT NULL,
  class_snapshot varchar(10) NOT NULL,
  kind varchar(20) NOT NULL,
  payment_source varchar(20) NOT NULL,
  personal_payer_id uuid,
  requested_by uuid NOT NULL,
  recipient_name varchar(200) NOT NULL,
  recipient_bank varchar(200),
  recipient_account varchar(60),
  reason text NOT NULL,
  amount numeric(18,0) NOT NULL,
  approved_amount numeric(18,0),
  recognized_on date NOT NULL,
  approval_threshold_snapshot numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0 AND approval_threshold_snapshot >= 0),
  CHECK (approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= amount)),
  CHECK (class_snapshot IN ('OPEX','CAPEX')),
  CHECK ((kind='REIMBURSEMENT' AND payment_source='PERSONAL' AND personal_payer_id IS NOT NULL) OR (kind='PAYMENT' AND payment_source IN ('CENTRAL','BRANCH_FUND') AND personal_payer_id IS NULL)),
  CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PAID','CANCELLED'))
);

COMMENT ON TABLE expense IS 'Chi phí gốc và yêu cầu chi/hoàn ứng; mua/sửa thiết bị dùng cùng quy trình.';

COMMENT ON COLUMN expense.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense.branch_id IS 'Cơ sở chịu chi';

COMMENT ON COLUMN expense.code IS 'Mã yêu cầu';

COMMENT ON COLUMN expense.category_id IS 'Danh mục';

COMMENT ON COLUMN expense.class_snapshot IS 'OPEX/CAPEX lúc chốt';

COMMENT ON COLUMN expense.kind IS 'PAYMENT/REIMBURSEMENT';

COMMENT ON COLUMN expense.payment_source IS 'CENTRAL/BRANCH_FUND/PERSONAL';

COMMENT ON COLUMN expense.personal_payer_id IS 'Người thực dùng tiền cá nhân';

COMMENT ON COLUMN expense.requested_by IS 'Người tạo';

COMMENT ON COLUMN expense.recipient_name IS 'Người/đơn vị nhận';

COMMENT ON COLUMN expense.recipient_bank IS 'Ngân hàng';

COMMENT ON COLUMN expense.recipient_account IS 'TK';

COMMENT ON COLUMN expense.reason IS 'Mục đích chi';

COMMENT ON COLUMN expense.amount IS 'Tổng dòng yêu cầu';

COMMENT ON COLUMN expense.approved_amount IS 'Số tiền được duyệt';

COMMENT ON COLUMN expense.recognized_on IS 'Ngày tính chi phí quản trị';

COMMENT ON COLUMN expense.approval_threshold_snapshot IS 'Hiện tại 3000000';

COMMENT ON COLUMN expense.status IS 'DRAFT/SUBMITTED/APPROVED/REJECTED/PAID/CANCELLED';

COMMENT ON COLUMN expense.created_at IS 'Thời điểm tạo';

CREATE TABLE expense_line (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  expense_id uuid NOT NULL,
  description varchar(200) NOT NULL,
  quantity numeric(18,3) NOT NULL,
  unit_price numeric(18,0) NOT NULL,
  amount numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (quantity > 0 AND unit_price >= 0 AND amount = round(quantity * unit_price,0))
);

COMMENT ON TABLE expense_line IS 'Chi tiết chi phí; tổng bằng lượng nhân giá sau làm tròn VND.';

COMMENT ON COLUMN expense_line.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense_line.expense_id IS 'Yêu cầu';

COMMENT ON COLUMN expense_line.description IS 'Nội dung';

COMMENT ON COLUMN expense_line.quantity IS 'Số lượng';

COMMENT ON COLUMN expense_line.unit_price IS 'Đơn giá';

COMMENT ON COLUMN expense_line.amount IS 'Thành tiền làm tròn';

COMMENT ON COLUMN expense_line.created_at IS 'Thời điểm tạo';

CREATE TABLE expense_approval (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  expense_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  role_snapshot varchar(60) NOT NULL,
  decision varchar(20) NOT NULL,
  approved_amount numeric(18,0),
  note text,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (decision IN ('APPROVE','REJECT','RETURN')),
  CHECK (approved_amount IS NULL OR approved_amount > 0)
);

COMMENT ON TABLE expense_approval IS 'Lịch sử quyết định QLCC/kế toán, không ghi đè quyết định cũ.';

COMMENT ON COLUMN expense_approval.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense_approval.expense_id IS 'Yêu cầu';

COMMENT ON COLUMN expense_approval.actor_id IS 'Người quyết định';

COMMENT ON COLUMN expense_approval.role_snapshot IS 'Vai trò lúc quyết định';

COMMENT ON COLUMN expense_approval.decision IS 'APPROVE/REJECT/RETURN';

COMMENT ON COLUMN expense_approval.approved_amount IS 'Số tiền được chấp nhận';

COMMENT ON COLUMN expense_approval.note IS 'Ghi chú';

COMMENT ON COLUMN expense_approval.created_at IS 'Thời điểm tạo';

CREATE TABLE expense_evidence (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  expense_id uuid NOT NULL,
  attachment_id uuid NOT NULL,
  kind varchar(60) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (expense_id, attachment_id)
);

COMMENT ON TABLE expense_evidence IS 'Nhiều chứng từ cho một chi phí; FK thật thay source_table/source_id.';

COMMENT ON COLUMN expense_evidence.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense_evidence.expense_id IS 'Chi phí';

COMMENT ON COLUMN expense_evidence.attachment_id IS 'File';

COMMENT ON COLUMN expense_evidence.kind IS 'INVOICE/RECEIPT/PHOTO/OTHER';

COMMENT ON COLUMN expense_evidence.created_at IS 'Thời điểm tạo';

CREATE TABLE expense_payment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  expense_id uuid NOT NULL,
  account_id uuid NOT NULL,
  amount numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL,
  external_reference varchar(200),
  paid_at timestamptz,
  processed_by uuid NOT NULL,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK (status IN ('PENDING','PAID','FAILED')),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

COMMENT ON TABLE expense_payment IS 'Thanh toán chi phí hoặc hoàn ứng; chỉ một lần thành công cho mỗi yêu cầu v1.';

COMMENT ON COLUMN expense_payment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN expense_payment.expense_id IS 'Chi phí đã duyệt';

COMMENT ON COLUMN expense_payment.account_id IS 'Quỹ thực chi';

COMMENT ON COLUMN expense_payment.amount IS 'Số thực trả';

COMMENT ON COLUMN expense_payment.status IS 'PENDING/PAID/FAILED';

COMMENT ON COLUMN expense_payment.external_reference IS 'Tham chiếu';

COMMENT ON COLUMN expense_payment.paid_at IS 'Đã trả';

COMMENT ON COLUMN expense_payment.processed_by IS 'Kế toán';

COMMENT ON COLUMN expense_payment.idempotency_key IS 'Chống chi lặp';

COMMENT ON COLUMN expense_payment.created_at IS 'Thời điểm tạo';

CREATE TABLE financial_period (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  month date NOT NULL UNIQUE,
  status varchar(20) NOT NULL,
  closed_by uuid,
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (month = date_trunc('month',month)::date),
  CHECK (status IN ('OPEN','CLOSED')),
  CHECK (status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);

COMMENT ON TABLE financial_period IS 'Kỳ tài chính tháng toàn chuỗi; đã đóng không mở lại qua CRUD.';

COMMENT ON COLUMN financial_period.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN financial_period.month IS 'Ngày đầu tháng';

COMMENT ON COLUMN financial_period.status IS 'OPEN/CLOSED';

COMMENT ON COLUMN financial_period.closed_by IS 'Kế toán';

COMMENT ON COLUMN financial_period.closed_at IS 'Ngày khóa';

COMMENT ON COLUMN financial_period.created_at IS 'Thời điểm tạo';

CREATE TABLE fund_transfer (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  from_account_id uuid,
  to_account_id uuid NOT NULL,
  kind varchar(20) NOT NULL,
  amount numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL,
  occurred_at timestamptz,
  external_reference varchar(200),
  processed_by uuid NOT NULL,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount > 0),
  CHECK (from_account_id IS NULL OR from_account_id <> to_account_id),
  CHECK ((kind='HEAD_OFFICE' AND from_account_id IS NULL) OR (kind IN ('ALLOCATION','RETURN','REMITTANCE') AND from_account_id IS NOT NULL)),
  CHECK (status IN ('PENDING','COMPLETED','FAILED'))
);

COMMENT ON TABLE fund_transfer IS 'Nhận vốn/cấp quỹ/hoàn quỹ/nộp tiền; không phải doanh thu hay chi phí.';

COMMENT ON COLUMN fund_transfer.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN fund_transfer.from_account_id IS 'Null nếu nguồn Head Office ngoài sổ GCMB';

COMMENT ON COLUMN fund_transfer.to_account_id IS 'Quỹ nhận';

COMMENT ON COLUMN fund_transfer.kind IS 'HEAD_OFFICE/ALLOCATION/RETURN/REMITTANCE';

COMMENT ON COLUMN fund_transfer.amount IS 'Số tiền';

COMMENT ON COLUMN fund_transfer.status IS 'PENDING/COMPLETED/FAILED';

COMMENT ON COLUMN fund_transfer.occurred_at IS 'Thực hiện';

COMMENT ON COLUMN fund_transfer.external_reference IS 'Tham chiếu';

COMMENT ON COLUMN fund_transfer.processed_by IS 'Kế toán';

COMMENT ON COLUMN fund_transfer.idempotency_key IS 'Chống ghi lặp';

COMMENT ON COLUMN fund_transfer.created_at IS 'Thời điểm tạo';

CREATE TABLE financial_transaction (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  period_id uuid NOT NULL,
  booking_date date NOT NULL,
  occurred_at timestamptz NOT NULL,
  kind varchar(25) NOT NULL,
  payment_id uuid UNIQUE,
  refund_id uuid UNIQUE,
  expense_payment_id uuid UNIQUE,
  salary_payment_id uuid UNIQUE,
  salary_advance_id uuid UNIQUE,
  fund_transfer_id uuid UNIQUE,
  adjusts_transaction_id uuid,
  reason text NOT NULL,
  created_by uuid,
  idempotency_key varchar(100) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (kind IN ('PAYMENT','REFUND','EXPENSE','SALARY','ADVANCE','TRANSFER','OPENING','ADJUSTMENT')),
  CHECK ((kind='OPENING' AND num_nonnulls(payment_id,refund_id,expense_payment_id,salary_payment_id,salary_advance_id,fund_transfer_id,adjusts_transaction_id)=0) OR (kind<>'OPENING' AND num_nonnulls(payment_id,refund_id,expense_payment_id,salary_payment_id,salary_advance_id,fund_transfer_id,adjusts_transaction_id)=1)),
  CHECK ((payment_id IS NULL OR kind='PAYMENT') AND (refund_id IS NULL OR kind='REFUND') AND (expense_payment_id IS NULL OR kind='EXPENSE') AND (salary_payment_id IS NULL OR kind='SALARY') AND (salary_advance_id IS NULL OR kind='ADVANCE') AND (fund_transfer_id IS NULL OR kind='TRANSFER') AND (adjusts_transaction_id IS NULL OR kind='ADJUSTMENT'))
);

COMMENT ON TABLE financial_transaction IS 'Chứng từ sổ tiền append-only với FK nguồn có kiểu; sửa bằng chứng từ riêng.';

COMMENT ON COLUMN financial_transaction.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN financial_transaction.period_id IS 'Kỳ hạch toán hiện hành';

COMMENT ON COLUMN financial_transaction.booking_date IS 'Ngày ghi sổ';

COMMENT ON COLUMN financial_transaction.occurred_at IS 'Ngày thực tế';

COMMENT ON COLUMN financial_transaction.kind IS 'PAYMENT/REFUND/EXPENSE/SALARY/ADVANCE/TRANSFER/OPENING/ADJUSTMENT';

COMMENT ON COLUMN financial_transaction.payment_id IS 'Nguồn thu khách';

COMMENT ON COLUMN financial_transaction.refund_id IS 'Nguồn hoàn khách';

COMMENT ON COLUMN financial_transaction.expense_payment_id IS 'Nguồn chi';

COMMENT ON COLUMN financial_transaction.salary_payment_id IS 'Nguồn trả lương';

COMMENT ON COLUMN financial_transaction.salary_advance_id IS 'Nguồn ứng';

COMMENT ON COLUMN financial_transaction.fund_transfer_id IS 'Nguồn cấp/chuyển quỹ';

COMMENT ON COLUMN financial_transaction.adjusts_transaction_id IS 'Chứng từ gốc được sửa';

COMMENT ON COLUMN financial_transaction.reason IS 'Nội dung/lý do';

COMMENT ON COLUMN financial_transaction.created_by IS 'Người ghi hoặc hệ thống';

COMMENT ON COLUMN financial_transaction.idempotency_key IS 'Một sự kiện chỉ ghi sổ một lần';

COMMENT ON COLUMN financial_transaction.created_at IS 'Thời điểm tạo';

CREATE TABLE financial_entry (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  transaction_id uuid NOT NULL,
  account_id uuid NOT NULL,
  branch_id uuid,
  amount_signed numeric(18,0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (amount_signed <> 0),
  UNIQUE (transaction_id, account_id, branch_id)
);

COMMENT ON TABLE financial_entry IS 'Dòng tăng/giảm một quỹ; chuyển nội bộ ghi hai dòng trong một transaction.';

COMMENT ON COLUMN financial_entry.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN financial_entry.transaction_id IS 'Chứng từ';

COMMENT ON COLUMN financial_entry.account_id IS 'Quỹ/TK';

COMMENT ON COLUMN financial_entry.branch_id IS 'Cơ sở quy thuộc; bắt buộc cho thu khách dù TK chung';

COMMENT ON COLUMN financial_entry.amount_signed IS 'Dương là vào, âm là ra';

COMMENT ON COLUMN financial_entry.created_at IS 'Thời điểm tạo';

CREATE TABLE daily_cash_closing (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL,
  account_id uuid NOT NULL,
  business_date date NOT NULL,
  opening_amount numeric(18,0) NOT NULL,
  cash_in numeric(18,0) NOT NULL,
  cash_out numeric(18,0) NOT NULL,
  expected_closing numeric(18,0) NOT NULL,
  actual_closing numeric(18,0) NOT NULL,
  variance numeric(18,0) NOT NULL,
  explanation text,
  status varchar(20) NOT NULL,
  submitted_by uuid NOT NULL,
  submitted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (account_id, business_date),
  CHECK (opening_amount >= 0 AND cash_in >= 0 AND cash_out >= 0 AND actual_closing >= 0),
  CHECK (expected_closing = opening_amount + cash_in - cash_out),
  CHECK (variance = actual_closing - expected_closing),
  CHECK (status IN ('DRAFT','SUBMITTED','RECONCILED')),
  CHECK (status <> 'RECONCILED' OR variance = 0 OR length(trim(explanation)) > 0)
);

COMMENT ON TABLE daily_cash_closing IS 'Chốt từng két/quỹ tiền mặt theo ngày địa phương.';

COMMENT ON COLUMN daily_cash_closing.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN daily_cash_closing.branch_id IS 'Cơ sở';

COMMENT ON COLUMN daily_cash_closing.account_id IS 'CASH_DRAWER của cơ sở';

COMMENT ON COLUMN daily_cash_closing.business_date IS 'Ngày kinh doanh';

COMMENT ON COLUMN daily_cash_closing.opening_amount IS 'Đầu ngày';

COMMENT ON COLUMN daily_cash_closing.cash_in IS 'Thu tiền mặt ghi sổ';

COMMENT ON COLUMN daily_cash_closing.cash_out IS 'Chi tiền mặt ghi sổ';

COMMENT ON COLUMN daily_cash_closing.expected_closing IS 'Đầu + vào - ra';

COMMENT ON COLUMN daily_cash_closing.actual_closing IS 'Kiểm đếm';

COMMENT ON COLUMN daily_cash_closing.variance IS 'Thực tế - dự kiến';

COMMENT ON COLUMN daily_cash_closing.explanation IS 'Giải trình';

COMMENT ON COLUMN daily_cash_closing.status IS 'DRAFT/SUBMITTED/RECONCILED';

COMMENT ON COLUMN daily_cash_closing.submitted_by IS 'QLCS';

COMMENT ON COLUMN daily_cash_closing.submitted_at IS 'Gửi chốt';

COMMENT ON COLUMN daily_cash_closing.created_at IS 'Thời điểm tạo';

CREATE TABLE cash_reconciliation (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  closing_id uuid NOT NULL,
  reviewed_by uuid NOT NULL,
  decision varchar(25) NOT NULL,
  note text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (decision IN ('NEEDS_EXPLANATION','RECONCILED'))
);

COMMENT ON TABLE cash_reconciliation IS 'Lịch sử các lần kế toán xem và đối soát một bản chốt.';

COMMENT ON COLUMN cash_reconciliation.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN cash_reconciliation.closing_id IS 'Bản chốt';

COMMENT ON COLUMN cash_reconciliation.reviewed_by IS 'Kế toán';

COMMENT ON COLUMN cash_reconciliation.decision IS 'NEEDS_EXPLANATION/RECONCILED';

COMMENT ON COLUMN cash_reconciliation.note IS 'Nhận xét';

COMMENT ON COLUMN cash_reconciliation.created_at IS 'Thời điểm tạo';

CREATE TABLE business_adjustment (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  period_id uuid NOT NULL,
  branch_id uuid NOT NULL,
  bill_id uuid,
  expense_id uuid,
  amount_signed numeric(18,0) NOT NULL,
  reason text NOT NULL,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (num_nonnulls(bill_id, expense_id)=1),
  CHECK (amount_signed <> 0)
);

COMMENT ON TABLE business_adjustment IS 'Điều chỉnh doanh thu/chi phí tách khỏi điều chỉnh dòng tiền.';

COMMENT ON COLUMN business_adjustment.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN business_adjustment.period_id IS 'Kỳ mở nhận điều chỉnh';

COMMENT ON COLUMN business_adjustment.branch_id IS 'Cơ sở';

COMMENT ON COLUMN business_adjustment.bill_id IS 'Hóa đơn gốc';

COMMENT ON COLUMN business_adjustment.expense_id IS 'Chi phí gốc';

COMMENT ON COLUMN business_adjustment.amount_signed IS 'Giá trị điều chỉnh có dấu';

COMMENT ON COLUMN business_adjustment.reason IS 'Lý do';

COMMENT ON COLUMN business_adjustment.created_by IS 'Kế toán';

COMMENT ON COLUMN business_adjustment.created_at IS 'Thời điểm tạo';

CREATE TABLE export_job (
  id uuid PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
  kind varchar(25) NOT NULL,
  payroll_period_id uuid,
  requested_by uuid NOT NULL,
  filters jsonb NOT NULL,
  status varchar(20) NOT NULL,
  attachment_id uuid,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (kind IN ('PAYROLL','TAX_SOURCE','EXPENSE_EVIDENCE')),
  CHECK (status IN ('PENDING','COMPLETED','FAILED')),
  CHECK ((kind='PAYROLL') = (payroll_period_id IS NOT NULL))
);

COMMENT ON TABLE export_job IS 'Theo dõi xuất bảng lương/dữ liệu tài chính; không tạo bảng report cho mỗi màn hình.';

COMMENT ON COLUMN export_job.id IS 'Khóa chính kỹ thuật';

COMMENT ON COLUMN export_job.kind IS 'PAYROLL/TAX_SOURCE/EXPENSE_EVIDENCE';

COMMENT ON COLUMN export_job.payroll_period_id IS 'Kỳ lương nếu xuất payroll';

COMMENT ON COLUMN export_job.requested_by IS 'Người xuất';

COMMENT ON COLUMN export_job.filters IS 'Phạm vi cơ sở/kỳ';

COMMENT ON COLUMN export_job.status IS 'PENDING/COMPLETED/FAILED';

COMMENT ON COLUMN export_job.attachment_id IS 'Kết quả';

COMMENT ON COLUMN export_job.completed_at IS 'Hoàn tất';

COMMENT ON COLUMN export_job.created_at IS 'Thời điểm tạo';

ALTER TABLE role_permission ADD FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT;

CREATE INDEX ix_role_permission_role_id ON role_permission (role_id);

ALTER TABLE role_permission ADD FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE RESTRICT;

CREATE INDEX ix_role_permission_permission_id ON role_permission (permission_id);

ALTER TABLE user_account ADD FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT;

CREATE INDEX ix_user_account_role_id ON user_account (role_id);

ALTER TABLE auth_token ADD FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_auth_token_user_id ON auth_token (user_id);

ALTER TABLE audit_event ADD FOREIGN KEY (actor_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_audit_event_actor_id ON audit_event (actor_id);

ALTER TABLE audit_event ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_audit_event_branch_id ON audit_event (branch_id);

ALTER TABLE attachment ADD FOREIGN KEY (uploaded_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_attachment_uploaded_by ON attachment (uploaded_by);

ALTER TABLE room ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_branch_id ON room (branch_id);

ALTER TABLE room ADD FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_room_type_id ON room (room_type_id);

ALTER TABLE price_rule ADD FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE RESTRICT;

CREATE INDEX ix_price_rule_room_type_id ON price_rule (room_type_id);

ALTER TABLE price_rule ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_price_rule_branch_id ON price_rule (branch_id);

ALTER TABLE combo ADD FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE RESTRICT;

CREATE INDEX ix_combo_room_type_id ON combo (room_type_id);

ALTER TABLE booking ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_branch_id ON booking (branch_id);

ALTER TABLE booking ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_created_by ON booking (created_by);

ALTER TABLE room_allocation ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_allocation_branch_id ON room_allocation (branch_id);

ALTER TABLE room_allocation ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_allocation_booking_id ON room_allocation (booking_id);

ALTER TABLE room_allocation ADD FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_allocation_room_id ON room_allocation (room_id);

ALTER TABLE room_allocation ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_allocation_created_by ON room_allocation (created_by);

ALTER TABLE booking_charge ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_charge_booking_id ON booking_charge (booking_id);

ALTER TABLE booking_charge ADD FOREIGN KEY (allocation_id) REFERENCES room_allocation(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_charge_allocation_id ON booking_charge (allocation_id);

ALTER TABLE booking_charge ADD FOREIGN KEY (price_rule_id) REFERENCES price_rule(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_charge_price_rule_id ON booking_charge (price_rule_id);

ALTER TABLE booking_charge ADD FOREIGN KEY (combo_id) REFERENCES combo(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_charge_combo_id ON booking_charge (combo_id);

ALTER TABLE booking_event ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_event_booking_id ON booking_event (booking_id);

ALTER TABLE booking_event ADD FOREIGN KEY (actor_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_event_actor_id ON booking_event (actor_id);

ALTER TABLE bill ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_booking_id ON bill (booking_id);

ALTER TABLE bill ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_branch_id ON bill (branch_id);

ALTER TABLE bill ADD FOREIGN KEY (discount_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_discount_by ON bill (discount_by);

ALTER TABLE bill_line ADD FOREIGN KEY (bill_id) REFERENCES bill(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_line_bill_id ON bill_line (bill_id);

ALTER TABLE bill_line ADD FOREIGN KEY (booking_charge_id) REFERENCES booking_charge(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_line_booking_charge_id ON bill_line (booking_charge_id);

ALTER TABLE bill_line ADD FOREIGN KEY (order_line_id) REFERENCES order_line(id) ON DELETE RESTRICT;

CREATE INDEX ix_bill_line_order_line_id ON bill_line (order_line_id);

ALTER TABLE inventory_item ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_inventory_item_branch_id ON inventory_item (branch_id);

ALTER TABLE inventory_item ADD FOREIGN KEY (unit_id) REFERENCES unit(id) ON DELETE RESTRICT;

CREATE INDEX ix_inventory_item_unit_id ON inventory_item (unit_id);

ALTER TABLE stock_balance ADD FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_balance_inventory_item_id ON stock_balance (inventory_item_id);

ALTER TABLE menu_item ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_menu_item_branch_id ON menu_item (branch_id);

ALTER TABLE menu_item ADD FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_menu_item_inventory_item_id ON menu_item (inventory_item_id);

ALTER TABLE combo_item ADD FOREIGN KEY (combo_id) REFERENCES combo(id) ON DELETE RESTRICT;

CREATE INDEX ix_combo_item_combo_id ON combo_item (combo_id);

ALTER TABLE combo_item ADD FOREIGN KEY (menu_item_id) REFERENCES menu_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_combo_item_menu_item_id ON combo_item (menu_item_id);

ALTER TABLE booking_combo_item ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_combo_item_booking_id ON booking_combo_item (booking_id);

ALTER TABLE booking_combo_item ADD FOREIGN KEY (combo_item_id) REFERENCES combo_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_combo_item_combo_item_id ON booking_combo_item (combo_item_id);

ALTER TABLE booking_combo_item ADD FOREIGN KEY (menu_item_id) REFERENCES menu_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_booking_combo_item_menu_item_id ON booking_combo_item (menu_item_id);

ALTER TABLE room_qr_session ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_qr_session_booking_id ON room_qr_session (booking_id);

ALTER TABLE room_qr_session ADD FOREIGN KEY (allocation_id) REFERENCES room_allocation(id) ON DELETE RESTRICT;

CREATE INDEX ix_room_qr_session_allocation_id ON room_qr_session (allocation_id);

ALTER TABLE fb_order ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_branch_id ON fb_order (branch_id);

ALTER TABLE fb_order ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_booking_id ON fb_order (booking_id);

ALTER TABLE fb_order ADD FOREIGN KEY (allocation_id) REFERENCES room_allocation(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_allocation_id ON fb_order (allocation_id);

ALTER TABLE fb_order ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_created_by ON fb_order (created_by);

ALTER TABLE fb_order ADD FOREIGN KEY (qr_session_id) REFERENCES room_qr_session(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_qr_session_id ON fb_order (qr_session_id);

ALTER TABLE fb_order ADD FOREIGN KEY (accepted_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_accepted_by ON fb_order (accepted_by);

ALTER TABLE order_line ADD FOREIGN KEY (order_id) REFERENCES fb_order(id) ON DELETE RESTRICT;

CREATE INDEX ix_order_line_order_id ON order_line (order_id);

ALTER TABLE order_line ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_order_line_branch_id ON order_line (branch_id);

ALTER TABLE order_line ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_order_line_booking_id ON order_line (booking_id);

ALTER TABLE order_line ADD FOREIGN KEY (menu_item_id) REFERENCES menu_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_order_line_menu_item_id ON order_line (menu_item_id);

ALTER TABLE order_line ADD FOREIGN KEY (booking_combo_item_id) REFERENCES booking_combo_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_order_line_booking_combo_item_id ON order_line (booking_combo_item_id);

ALTER TABLE stock_receipt ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_branch_id ON stock_receipt (branch_id);

ALTER TABLE stock_receipt ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_expense_id ON stock_receipt (expense_id);

ALTER TABLE stock_receipt ADD FOREIGN KEY (posted_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_posted_by ON stock_receipt (posted_by);

ALTER TABLE stock_receipt_line ADD FOREIGN KEY (receipt_id) REFERENCES stock_receipt(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_line_receipt_id ON stock_receipt_line (receipt_id);

ALTER TABLE stock_receipt_line ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_line_branch_id ON stock_receipt_line (branch_id);

ALTER TABLE stock_receipt_line ADD FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_receipt_line_inventory_item_id ON stock_receipt_line (inventory_item_id);

ALTER TABLE stock_movement ADD FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_movement_inventory_item_id ON stock_movement (inventory_item_id);

ALTER TABLE stock_movement ADD FOREIGN KEY (receipt_line_id) REFERENCES stock_receipt_line(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_movement_receipt_line_id ON stock_movement (receipt_line_id);

ALTER TABLE stock_movement ADD FOREIGN KEY (order_line_id) REFERENCES order_line(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_movement_order_line_id ON stock_movement (order_line_id);

ALTER TABLE stock_movement ADD FOREIGN KEY (reverses_id) REFERENCES stock_movement(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_movement_reverses_id ON stock_movement (reverses_id);

ALTER TABLE stock_movement ADD FOREIGN KEY (actor_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_stock_movement_actor_id ON stock_movement (actor_id);

ALTER TABLE asset_category ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_category_branch_id ON asset_category (branch_id);

ALTER TABLE asset ADD FOREIGN KEY (category_id) REFERENCES asset_category(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_category_id ON asset (category_id);

ALTER TABLE asset ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_branch_id ON asset (branch_id);

ALTER TABLE asset ADD FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_room_id ON asset (room_id);

ALTER TABLE asset ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_expense_id ON asset (expense_id);

ALTER TABLE asset_incident ADD FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_asset_id ON asset_incident (asset_id);

ALTER TABLE asset_incident ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_branch_id ON asset_incident (branch_id);

ALTER TABLE asset_incident ADD FOREIGN KEY (reported_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_reported_by ON asset_incident (reported_by);

ALTER TABLE asset_incident ADD FOREIGN KEY (evidence_id) REFERENCES attachment(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_evidence_id ON asset_incident (evidence_id);

ALTER TABLE asset_incident ADD FOREIGN KEY (assessed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_assessed_by ON asset_incident (assessed_by);

ALTER TABLE asset_incident ADD FOREIGN KEY (replacement_asset_id) REFERENCES asset(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_incident_replacement_asset_id ON asset_incident (replacement_asset_id);

ALTER TABLE asset_repair ADD FOREIGN KEY (incident_id) REFERENCES asset_incident(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_repair_incident_id ON asset_repair (incident_id);

ALTER TABLE asset_repair ADD FOREIGN KEY (verified_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_repair_verified_by ON asset_repair (verified_by);

ALTER TABLE asset_repair ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_repair_expense_id ON asset_repair (expense_id);

ALTER TABLE asset_repair ADD FOREIGN KEY (evidence_id) REFERENCES attachment(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_repair_evidence_id ON asset_repair (evidence_id);

ALTER TABLE asset_transfer ADD FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_asset_id ON asset_transfer (asset_id);

ALTER TABLE asset_transfer ADD FOREIGN KEY (from_branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_from_branch_id ON asset_transfer (from_branch_id);

ALTER TABLE asset_transfer ADD FOREIGN KEY (to_branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_to_branch_id ON asset_transfer (to_branch_id);

ALTER TABLE asset_transfer ADD FOREIGN KEY (to_room_id) REFERENCES room(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_to_room_id ON asset_transfer (to_room_id);

ALTER TABLE asset_transfer ADD FOREIGN KEY (initiated_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_initiated_by ON asset_transfer (initiated_by);

ALTER TABLE asset_transfer ADD FOREIGN KEY (handed_over_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_handed_over_by ON asset_transfer (handed_over_by);

ALTER TABLE asset_transfer ADD FOREIGN KEY (received_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_asset_transfer_received_by ON asset_transfer (received_by);

ALTER TABLE employee ADD FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_user_id ON employee (user_id);

ALTER TABLE employee_assignment ADD FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_assignment_employee_id ON employee_assignment (employee_id);

ALTER TABLE employee_assignment ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_assignment_branch_id ON employee_assignment (branch_id);

ALTER TABLE employee_assignment ADD FOREIGN KEY (assigned_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_assignment_assigned_by ON employee_assignment (assigned_by);

ALTER TABLE employee_pay_rate ADD FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_pay_rate_employee_id ON employee_pay_rate (employee_id);

ALTER TABLE employee_pay_rate ADD FOREIGN KEY (changed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_employee_pay_rate_changed_by ON employee_pay_rate (changed_by);

ALTER TABLE scheduled_shift ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_scheduled_shift_branch_id ON scheduled_shift (branch_id);

ALTER TABLE scheduled_shift ADD FOREIGN KEY (template_id) REFERENCES shift_template(id) ON DELETE RESTRICT;

CREATE INDEX ix_scheduled_shift_template_id ON scheduled_shift (template_id);

ALTER TABLE scheduled_shift ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_scheduled_shift_created_by ON scheduled_shift (created_by);

ALTER TABLE shift_assignment ADD FOREIGN KEY (shift_id) REFERENCES scheduled_shift(id) ON DELETE RESTRICT;

CREATE INDEX ix_shift_assignment_shift_id ON shift_assignment (shift_id);

ALTER TABLE shift_assignment ADD FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_shift_assignment_employee_id ON shift_assignment (employee_id);

ALTER TABLE shift_assignment ADD FOREIGN KEY (assigned_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_shift_assignment_assigned_by ON shift_assignment (assigned_by);

ALTER TABLE attendance_period ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_period_branch_id ON attendance_period (branch_id);

ALTER TABLE attendance_period ADD FOREIGN KEY (closed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_period_closed_by ON attendance_period (closed_by);

ALTER TABLE attendance ADD FOREIGN KEY (assignment_id) REFERENCES shift_assignment(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_assignment_id ON attendance (assignment_id);

ALTER TABLE attendance ADD FOREIGN KEY (period_id) REFERENCES attendance_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_period_id ON attendance (period_id);

ALTER TABLE attendance ADD FOREIGN KEY (recorded_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_recorded_by ON attendance (recorded_by);

ALTER TABLE attendance_issue ADD FOREIGN KEY (attendance_id) REFERENCES attendance(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_issue_attendance_id ON attendance_issue (attendance_id);

ALTER TABLE attendance_issue ADD FOREIGN KEY (reported_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_issue_reported_by ON attendance_issue (reported_by);

ALTER TABLE attendance_issue ADD FOREIGN KEY (resolved_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_attendance_issue_resolved_by ON attendance_issue (resolved_by);

ALTER TABLE payroll_period ADD FOREIGN KEY (confirmed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_period_confirmed_by ON payroll_period (confirmed_by);

ALTER TABLE payroll_entry ADD FOREIGN KEY (period_id) REFERENCES payroll_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_entry_period_id ON payroll_entry (period_id);

ALTER TABLE payroll_entry ADD FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_entry_employee_id ON payroll_entry (employee_id);

ALTER TABLE payroll_component ADD FOREIGN KEY (payroll_entry_id) REFERENCES payroll_entry(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_component_payroll_entry_id ON payroll_component (payroll_entry_id);

ALTER TABLE payroll_component ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_component_branch_id ON payroll_component (branch_id);

ALTER TABLE payroll_component ADD FOREIGN KEY (attendance_id) REFERENCES attendance(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_component_attendance_id ON payroll_component (attendance_id);

ALTER TABLE payroll_component ADD FOREIGN KEY (pay_rate_id) REFERENCES employee_pay_rate(id) ON DELETE RESTRICT;

CREATE INDEX ix_payroll_component_pay_rate_id ON payroll_component (pay_rate_id);

ALTER TABLE salary_advance ADD FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_advance_employee_id ON salary_advance (employee_id);

ALTER TABLE salary_advance ADD FOREIGN KEY (period_id) REFERENCES payroll_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_advance_period_id ON salary_advance (period_id);

ALTER TABLE salary_advance ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_advance_branch_id ON salary_advance (branch_id);

ALTER TABLE salary_advance ADD FOREIGN KEY (processed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_advance_processed_by ON salary_advance (processed_by);

ALTER TABLE advance_application ADD FOREIGN KEY (advance_id) REFERENCES salary_advance(id) ON DELETE RESTRICT;

CREATE INDEX ix_advance_application_advance_id ON advance_application (advance_id);

ALTER TABLE advance_application ADD FOREIGN KEY (payroll_entry_id) REFERENCES payroll_entry(id) ON DELETE RESTRICT;

CREATE INDEX ix_advance_application_payroll_entry_id ON advance_application (payroll_entry_id);

ALTER TABLE salary_payment ADD FOREIGN KEY (payroll_entry_id) REFERENCES payroll_entry(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_payment_payroll_entry_id ON salary_payment (payroll_entry_id);

ALTER TABLE salary_payment ADD FOREIGN KEY (processed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_salary_payment_processed_by ON salary_payment (processed_by);

ALTER TABLE financial_account ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_account_branch_id ON financial_account (branch_id);

ALTER TABLE payment_request ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_request_branch_id ON payment_request (branch_id);

ALTER TABLE payment_request ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_request_booking_id ON payment_request (booking_id);

ALTER TABLE payment_request ADD FOREIGN KEY (bill_id) REFERENCES bill(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_request_bill_id ON payment_request (bill_id);

ALTER TABLE payment_request ADD FOREIGN KEY (account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_request_account_id ON payment_request (account_id);

ALTER TABLE bank_transaction ADD FOREIGN KEY (account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_bank_transaction_account_id ON bank_transaction (account_id);

ALTER TABLE payment ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_branch_id ON payment (branch_id);

ALTER TABLE payment ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_booking_id ON payment (booking_id);

ALTER TABLE payment ADD FOREIGN KEY (bill_id) REFERENCES bill(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_bill_id ON payment (bill_id);

ALTER TABLE payment ADD FOREIGN KEY (request_id) REFERENCES payment_request(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_request_id ON payment (request_id);

ALTER TABLE payment ADD FOREIGN KEY (bank_transaction_id) REFERENCES bank_transaction(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_bank_transaction_id ON payment (bank_transaction_id);

ALTER TABLE payment ADD FOREIGN KEY (received_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_received_by ON payment (received_by);

ALTER TABLE deposit ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_booking_id ON deposit (booking_id);

ALTER TABLE deposit_entry ADD FOREIGN KEY (deposit_id) REFERENCES deposit(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_entry_deposit_id ON deposit_entry (deposit_id);

ALTER TABLE deposit_entry ADD FOREIGN KEY (payment_id) REFERENCES payment(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_entry_payment_id ON deposit_entry (payment_id);

ALTER TABLE deposit_entry ADD FOREIGN KEY (bill_id) REFERENCES bill(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_entry_bill_id ON deposit_entry (bill_id);

ALTER TABLE deposit_entry ADD FOREIGN KEY (refund_id) REFERENCES refund_request(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_entry_refund_id ON deposit_entry (refund_id);

ALTER TABLE deposit_entry ADD FOREIGN KEY (actor_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_deposit_entry_actor_id ON deposit_entry (actor_id);

ALTER TABLE refund_request ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_branch_id ON refund_request (branch_id);

ALTER TABLE refund_request ADD FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_booking_id ON refund_request (booking_id);

ALTER TABLE refund_request ADD FOREIGN KEY (deposit_id) REFERENCES deposit(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_deposit_id ON refund_request (deposit_id);

ALTER TABLE refund_request ADD FOREIGN KEY (overpayment_transaction_id) REFERENCES bank_transaction(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_overpayment_transaction_id ON refund_request (overpayment_transaction_id);

ALTER TABLE refund_request ADD FOREIGN KEY (requested_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_requested_by ON refund_request (requested_by);

ALTER TABLE refund_request ADD FOREIGN KEY (processed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_processed_by ON refund_request (processed_by);

ALTER TABLE refund_request ADD FOREIGN KEY (evidence_id) REFERENCES attachment(id) ON DELETE RESTRICT;

CREATE INDEX ix_refund_request_evidence_id ON refund_request (evidence_id);

ALTER TABLE expense ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_branch_id ON expense (branch_id);

ALTER TABLE expense ADD FOREIGN KEY (category_id) REFERENCES expense_category(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_category_id ON expense (category_id);

ALTER TABLE expense ADD FOREIGN KEY (personal_payer_id) REFERENCES employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_personal_payer_id ON expense (personal_payer_id);

ALTER TABLE expense ADD FOREIGN KEY (requested_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_requested_by ON expense (requested_by);

ALTER TABLE expense_line ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_line_expense_id ON expense_line (expense_id);

ALTER TABLE expense_approval ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_approval_expense_id ON expense_approval (expense_id);

ALTER TABLE expense_approval ADD FOREIGN KEY (actor_id) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_approval_actor_id ON expense_approval (actor_id);

ALTER TABLE expense_evidence ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_evidence_expense_id ON expense_evidence (expense_id);

ALTER TABLE expense_evidence ADD FOREIGN KEY (attachment_id) REFERENCES attachment(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_evidence_attachment_id ON expense_evidence (attachment_id);

ALTER TABLE expense_payment ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_payment_expense_id ON expense_payment (expense_id);

ALTER TABLE expense_payment ADD FOREIGN KEY (account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_payment_account_id ON expense_payment (account_id);

ALTER TABLE expense_payment ADD FOREIGN KEY (processed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_expense_payment_processed_by ON expense_payment (processed_by);

ALTER TABLE financial_period ADD FOREIGN KEY (closed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_period_closed_by ON financial_period (closed_by);

ALTER TABLE fund_transfer ADD FOREIGN KEY (from_account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_fund_transfer_from_account_id ON fund_transfer (from_account_id);

ALTER TABLE fund_transfer ADD FOREIGN KEY (to_account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_fund_transfer_to_account_id ON fund_transfer (to_account_id);

ALTER TABLE fund_transfer ADD FOREIGN KEY (processed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_fund_transfer_processed_by ON fund_transfer (processed_by);

ALTER TABLE financial_transaction ADD FOREIGN KEY (period_id) REFERENCES financial_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_period_id ON financial_transaction (period_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (payment_id) REFERENCES payment(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_payment_id ON financial_transaction (payment_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (refund_id) REFERENCES refund_request(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_refund_id ON financial_transaction (refund_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (expense_payment_id) REFERENCES expense_payment(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_expense_payment_id ON financial_transaction (expense_payment_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (salary_payment_id) REFERENCES salary_payment(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_salary_payment_id ON financial_transaction (salary_payment_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (salary_advance_id) REFERENCES salary_advance(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_salary_advance_id ON financial_transaction (salary_advance_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (fund_transfer_id) REFERENCES fund_transfer(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_fund_transfer_id ON financial_transaction (fund_transfer_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (adjusts_transaction_id) REFERENCES financial_transaction(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_adjusts_transaction_id ON financial_transaction (adjusts_transaction_id);

ALTER TABLE financial_transaction ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_transaction_created_by ON financial_transaction (created_by);

ALTER TABLE financial_entry ADD FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_entry_transaction_id ON financial_entry (transaction_id);

ALTER TABLE financial_entry ADD FOREIGN KEY (account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_entry_account_id ON financial_entry (account_id);

ALTER TABLE financial_entry ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_financial_entry_branch_id ON financial_entry (branch_id);

ALTER TABLE daily_cash_closing ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_daily_cash_closing_branch_id ON daily_cash_closing (branch_id);

ALTER TABLE daily_cash_closing ADD FOREIGN KEY (account_id) REFERENCES financial_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_daily_cash_closing_account_id ON daily_cash_closing (account_id);

ALTER TABLE daily_cash_closing ADD FOREIGN KEY (submitted_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_daily_cash_closing_submitted_by ON daily_cash_closing (submitted_by);

ALTER TABLE cash_reconciliation ADD FOREIGN KEY (closing_id) REFERENCES daily_cash_closing(id) ON DELETE RESTRICT;

CREATE INDEX ix_cash_reconciliation_closing_id ON cash_reconciliation (closing_id);

ALTER TABLE cash_reconciliation ADD FOREIGN KEY (reviewed_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_cash_reconciliation_reviewed_by ON cash_reconciliation (reviewed_by);

ALTER TABLE business_adjustment ADD FOREIGN KEY (period_id) REFERENCES financial_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_business_adjustment_period_id ON business_adjustment (period_id);

ALTER TABLE business_adjustment ADD FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

CREATE INDEX ix_business_adjustment_branch_id ON business_adjustment (branch_id);

ALTER TABLE business_adjustment ADD FOREIGN KEY (bill_id) REFERENCES bill(id) ON DELETE RESTRICT;

CREATE INDEX ix_business_adjustment_bill_id ON business_adjustment (bill_id);

ALTER TABLE business_adjustment ADD FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE RESTRICT;

CREATE INDEX ix_business_adjustment_expense_id ON business_adjustment (expense_id);

ALTER TABLE business_adjustment ADD FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_business_adjustment_created_by ON business_adjustment (created_by);

ALTER TABLE export_job ADD FOREIGN KEY (payroll_period_id) REFERENCES payroll_period(id) ON DELETE RESTRICT;

CREATE INDEX ix_export_job_payroll_period_id ON export_job (payroll_period_id);

ALTER TABLE export_job ADD FOREIGN KEY (requested_by) REFERENCES user_account(id) ON DELETE RESTRICT;

CREATE INDEX ix_export_job_requested_by ON export_job (requested_by);

ALTER TABLE export_job ADD FOREIGN KEY (attachment_id) REFERENCES attachment(id) ON DELETE RESTRICT;

CREATE INDEX ix_export_job_attachment_id ON export_job (attachment_id);

-- Cross-entity consistency: branch and booking must agree.
ALTER TABLE room_allocation ADD FOREIGN KEY (room_id, branch_id) REFERENCES room(id, branch_id);
ALTER TABLE room_allocation ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);
ALTER TABLE bill ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);
ALTER TABLE booking_charge ADD FOREIGN KEY (allocation_id, booking_id) REFERENCES room_allocation(id, booking_id);
ALTER TABLE fb_order ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);
ALTER TABLE fb_order ADD FOREIGN KEY (allocation_id, booking_id) REFERENCES room_allocation(id, booking_id);
ALTER TABLE order_line ADD FOREIGN KEY (order_id, branch_id) REFERENCES fb_order(id, branch_id);
ALTER TABLE order_line ADD FOREIGN KEY (order_id, booking_id) REFERENCES fb_order(id, booking_id);
ALTER TABLE order_line ADD FOREIGN KEY (menu_item_id, branch_id) REFERENCES menu_item(id, branch_id);
ALTER TABLE order_line ADD FOREIGN KEY (booking_combo_item_id, booking_id) REFERENCES booking_combo_item(id, booking_id);
ALTER TABLE room_qr_session ADD FOREIGN KEY (allocation_id, booking_id) REFERENCES room_allocation(id, booking_id);
ALTER TABLE menu_item ADD FOREIGN KEY (inventory_item_id, branch_id) REFERENCES inventory_item(id, branch_id);
ALTER TABLE stock_receipt_line ADD FOREIGN KEY (receipt_id, branch_id) REFERENCES stock_receipt(id, branch_id);
ALTER TABLE stock_receipt_line ADD FOREIGN KEY (inventory_item_id, branch_id) REFERENCES inventory_item(id, branch_id);
ALTER TABLE asset ADD FOREIGN KEY (room_id, branch_id) REFERENCES room(id, branch_id);
ALTER TABLE asset_transfer ADD FOREIGN KEY (to_room_id, to_branch_id) REFERENCES room(id, branch_id);
ALTER TABLE payment_request ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);
ALTER TABLE payment_request ADD FOREIGN KEY (bill_id, booking_id) REFERENCES bill(id, booking_id);
ALTER TABLE payment ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);
ALTER TABLE payment ADD FOREIGN KEY (bill_id, booking_id) REFERENCES bill(id, booking_id);
ALTER TABLE payment ADD FOREIGN KEY (request_id, booking_id) REFERENCES payment_request(id, booking_id);
ALTER TABLE refund_request ADD FOREIGN KEY (booking_id, branch_id) REFERENCES booking(id, branch_id);

CREATE UNIQUE INDEX uq_user_username_ci ON user_account(lower(username));
CREATE UNIQUE INDEX uq_user_email_ci ON user_account(lower(email));
CREATE UNIQUE INDEX uq_active_assignment ON shift_assignment(shift_id,employee_id) WHERE status='CONFIRMED';
CREATE UNIQUE INDEX uq_salary_paid ON salary_payment(payroll_entry_id) WHERE status='PAID';
CREATE UNIQUE INDEX uq_salary_pending ON salary_payment(payroll_entry_id) WHERE status='PENDING';
CREATE UNIQUE INDEX uq_expense_paid ON expense_payment(expense_id) WHERE status='PAID';
CREATE UNIQUE INDEX uq_expense_pending ON expense_payment(expense_id) WHERE status='PENDING';
CREATE UNIQUE INDEX uq_active_asset_transfer ON asset_transfer(asset_id) WHERE status IN ('REQUESTED','IN_TRANSIT');
CREATE UNIQUE INDEX uq_active_stay ON room_allocation(booking_id) WHERE state='OCCUPIED';
CREATE UNIQUE INDEX uq_stock_receipt_post ON stock_movement(receipt_line_id) WHERE kind='RECEIPT';
CREATE UNIQUE INDEX uq_stock_sale_post ON stock_movement(order_line_id) WHERE kind='SALE';
CREATE INDEX ix_booking_branch_time ON booking(branch_id,planned_start,planned_end);
CREATE INDEX ix_bank_unmatched ON bank_transaction(occurred_at) WHERE match_status <> 'MATCHED';
CREATE INDEX ix_financial_posting ON financial_transaction(period_id,booking_date);
CREATE INDEX ix_audit_time ON audit_event(branch_id,created_at DESC);
CREATE INDEX ix_order_pending ON fb_order(branch_id,created_at) WHERE status='PENDING';
ALTER TABLE daily_cash_closing ADD CONSTRAINT explained_reconciled_variance CHECK
 (status <> 'RECONCILED' OR variance = 0 OR COALESCE(length(trim(explanation)),0) > 0);

-- Intervals are [start,end). Adjacent bookings are allowed; buffer time is a service policy.
ALTER TABLE room_allocation ADD CONSTRAINT no_room_overlap
EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
WHERE (state IN ('RESERVED','OCCUPIED','FINISHED')) DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE room_allocation ADD CONSTRAINT no_booking_parallel_rooms
EXCLUDE USING gist (booking_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
WHERE (state IN ('RESERVED','OCCUPIED','FINISHED')) DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE employee_assignment ADD CONSTRAINT no_home_branch_overlap
EXCLUDE USING gist (employee_id WITH =, tstzrange(valid_from,valid_to,'[)') WITH &&);
ALTER TABLE employee_assignment ADD CONSTRAINT no_branch_manager_overlap
EXCLUDE USING gist (branch_id WITH =, tstzrange(valid_from,valid_to,'[)') WITH &&)
WHERE (is_branch_manager);
ALTER TABLE employee_pay_rate ADD CONSTRAINT no_pay_rate_overlap
EXCLUDE USING gist (employee_id WITH =, daterange(valid_from,valid_to,'[)') WITH &&);

CREATE FUNCTION reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Append-only table %: create an explicit correction instead', TG_TABLE_NAME;
END; $$;
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_booking_event BEFORE UPDATE OR DELETE ON booking_event FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_deposit_entry BEFORE UPDATE OR DELETE ON deposit_entry FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_stock_movement BEFORE UPDATE OR DELETE ON stock_movement FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_payment BEFORE UPDATE OR DELETE ON payment FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_financial_transaction BEFORE UPDATE OR DELETE ON financial_transaction FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_financial_entry BEFORE UPDATE OR DELETE ON financial_entry FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_business_adjustment BEFORE UPDATE OR DELETE ON business_adjustment FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_expense_approval BEFORE UPDATE OR DELETE ON expense_approval FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();

-- Only validated incoming transfers can become customer payments.
CREATE FUNCTION validate_customer_payment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payment_request%ROWTYPE; b bank_transaction%ROWTYPE;
BEGIN
  IF NEW.method='BANK_TRANSFER' THEN
    SELECT * INTO r FROM payment_request WHERE id=NEW.request_id FOR UPDATE;
    SELECT * INTO b FROM bank_transaction WHERE id=NEW.bank_transaction_id FOR UPDATE;
    IF r.id IS NULL OR b.id IS NULL OR r.status <> 'PENDING' OR b.match_status <> 'UNMATCHED'
       OR b.direction <> 'IN' OR b.account_id <> r.account_id OR b.amount <> r.expected_amount
       OR NEW.amount <> r.expected_amount OR NEW.purpose <> r.purpose
       OR NEW.booking_id <> r.booking_id OR NEW.branch_id <> r.branch_id
       OR NEW.bill_id IS DISTINCT FROM r.bill_id THEN
      RAISE EXCEPTION 'Invalid, duplicated or mismatched payment';
    END IF;
    -- Reference extraction/signature/authentication is done by the webhook service.
    -- Transfer content may contain bank prefixes; do not implement naive substring matching here.
    UPDATE payment_request SET status='SETTLED' WHERE id=r.id;
    UPDATE bank_transaction SET match_status='MATCHED' WHERE id=b.id;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER validate_payment BEFORE INSERT ON payment FOR EACH ROW EXECUTE FUNCTION validate_customer_payment();

-- Deposit account row is the serialization lock for every use of that deposit.
CREATE FUNCTION validate_deposit_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d deposit%ROWTYPE; bal numeric; p payment%ROWTYPE; f refund_request%ROWTYPE; bid uuid;
BEGIN
  SELECT * INTO d FROM deposit WHERE id=NEW.deposit_id FOR UPDATE;
  IF d.id IS NULL THEN RAISE EXCEPTION 'Missing deposit account'; END IF;
  SELECT COALESCE(SUM(CASE WHEN kind='RECEIVE' THEN amount ELSE -amount END),0)
    INTO bal FROM deposit_entry WHERE deposit_id=d.id;
  IF NEW.kind='RECEIVE' THEN
    SELECT * INTO p FROM payment WHERE id=NEW.payment_id;
    IF p.id IS NULL OR p.purpose <> 'DEPOSIT' OR p.booking_id <> d.booking_id OR p.amount <> NEW.amount THEN
      RAISE EXCEPTION 'Invalid deposit receipt source';
    END IF;
  ELSE
    IF NEW.amount > bal THEN RAISE EXCEPTION 'Deposit over-consumption'; END IF;
    IF NEW.kind='APPLY' THEN
      SELECT booking_id INTO bid FROM bill WHERE id=NEW.bill_id;
      IF bid IS DISTINCT FROM d.booking_id THEN RAISE EXCEPTION 'Deposit and bill belong to different bookings'; END IF;
    ELSIF NEW.kind='REFUND' THEN
      SELECT * INTO f FROM refund_request WHERE id=NEW.refund_id;
      IF f.id IS NULL OR f.deposit_id IS DISTINCT FROM d.id OR f.status <> 'PAID' OR f.amount <> NEW.amount THEN
        RAISE EXCEPTION 'Refund is not confirmed or has an invalid source';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER guard_deposit BEFORE INSERT ON deposit_entry FOR EACH ROW EXECUTE FUNCTION validate_deposit_entry();

-- Only ledger insert changes the stock cache. Callers must not also decrement stock_balance.
CREATE FUNCTION post_stock_movement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q numeric; src record;
BEGIN
  SELECT quantity INTO q FROM stock_balance WHERE inventory_item_id=NEW.inventory_item_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Create stock_balance=0 before first movement'; END IF;
  IF q <> NEW.quantity_before OR NEW.quantity_after <> q+NEW.quantity_delta OR NEW.quantity_after < 0 THEN
    RAISE EXCEPTION 'Stale or negative stock movement';
  END IF;
  IF NEW.kind='RECEIPT' THEN
    SELECT l.inventory_item_id,l.quantity,r.status INTO src
      FROM stock_receipt_line l JOIN stock_receipt r ON r.id=l.receipt_id WHERE l.id=NEW.receipt_line_id;
    IF NOT FOUND OR src.inventory_item_id <> NEW.inventory_item_id OR src.quantity <> NEW.quantity_delta OR src.status <> 'POSTED' THEN
      RAISE EXCEPTION 'Invalid stock receipt source';
    END IF;
  ELSIF NEW.kind='SALE' THEN
    SELECT m.inventory_item_id,l.quantity,o.status,i.tracking_mode INTO src
      FROM order_line l JOIN fb_order o ON o.id=l.order_id JOIN menu_item m ON m.id=l.menu_item_id
      JOIN inventory_item i ON i.id=m.inventory_item_id WHERE l.id=NEW.order_line_id;
    IF NOT FOUND OR src.inventory_item_id <> NEW.inventory_item_id OR -src.quantity <> NEW.quantity_delta
       OR src.status NOT IN ('ACCEPTED','SERVED') OR src.tracking_mode <> 'AUTO_ON_SALE' THEN
      RAISE EXCEPTION 'Invalid stock sale source';
    END IF;
  END IF;
  UPDATE stock_balance SET quantity=NEW.quantity_after,version=version+1 WHERE inventory_item_id=NEW.inventory_item_id;
  RETURN NEW;
END; $$;
CREATE TRIGGER stock_post BEFORE INSERT ON stock_movement FOR EACH ROW EXECUTE FUNCTION post_stock_movement();

CREATE FUNCTION require_open_financial_period() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p financial_period%ROWTYPE;
BEGIN
  SELECT * INTO p FROM financial_period WHERE id=NEW.period_id FOR SHARE;
  IF p.id IS NULL OR p.status <> 'OPEN' THEN RAISE EXCEPTION 'Financial period is closed or absent'; END IF;
  IF TG_TABLE_NAME='financial_transaction' THEN
    IF date_trunc('month',NEW.booking_date)::date <> p.month THEN RAISE EXCEPTION 'Posting date does not match financial period'; END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER financial_period_guard BEFORE INSERT ON financial_transaction FOR EACH ROW EXECUTE FUNCTION require_open_financial_period();
CREATE TRIGGER business_adjustment_period_guard BEFORE INSERT ON business_adjustment FOR EACH ROW EXECUTE FUNCTION require_open_financial_period();

CREATE FUNCTION validate_financial_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p financial_period%ROWTYPE; t financial_transaction%ROWTYPE; a financial_account%ROWTYPE;
BEGIN
  SELECT * INTO t FROM financial_transaction WHERE id=NEW.transaction_id;
  SELECT * INTO p FROM financial_period WHERE id=t.period_id FOR SHARE;
  SELECT * INTO a FROM financial_account WHERE id=NEW.account_id;
  IF p.id IS NULL OR p.status <> 'OPEN' THEN RAISE EXCEPTION 'Cannot add entries to a closed period'; END IF;
  IF a.branch_id IS NOT NULL AND NEW.branch_id IS DISTINCT FROM a.branch_id THEN
    RAISE EXCEPTION 'Entry attribution does not match account branch';
  END IF;
  IF t.kind='PAYMENT' AND NOT EXISTS (SELECT 1 FROM payment WHERE id=t.payment_id AND branch_id=NEW.branch_id) THEN
    RAISE EXCEPTION 'Customer collection must retain originating branch';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER financial_entry_guard BEFORE INSERT ON financial_entry FOR EACH ROW EXECUTE FUNCTION validate_financial_entry();

-- Prevent confirmed finance/payroll periods from being reopened by normal CRUD.
CREATE FUNCTION no_reopen_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Periods are retained'; END IF;
  IF OLD.status IN ('CLOSED','CONFIRMED') THEN RAISE EXCEPTION 'Final period is immutable'; END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER lock_financial_period BEFORE UPDATE OR DELETE ON financial_period FOR EACH ROW EXECUTE FUNCTION no_reopen_period();
CREATE TRIGGER lock_attendance_period BEFORE UPDATE OR DELETE ON attendance_period FOR EACH ROW EXECUTE FUNCTION no_reopen_period();
CREATE TRIGGER lock_payroll_period BEFORE UPDATE OR DELETE ON payroll_period FOR EACH ROW EXECUTE FUNCTION no_reopen_period();

CREATE FUNCTION guard_attendance_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE pid uuid; s text;
BEGIN
  IF TG_OP <> 'INSERT' THEN
    SELECT status INTO s FROM attendance_period WHERE id=OLD.period_id FOR SHARE;
    IF s <> 'OPEN' THEN RAISE EXCEPTION 'Closed attendance cannot be edited'; END IF;
  END IF;
  IF TG_OP <> 'DELETE' THEN
    pid:=NEW.period_id;
    SELECT status INTO s FROM attendance_period WHERE id=pid FOR SHARE;
    IF s IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Attendance period must be open'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;
CREATE TRIGGER attendance_lock BEFORE INSERT OR UPDATE OR DELETE ON attendance FOR EACH ROW EXECUTE FUNCTION guard_attendance_change();

CREATE VIEW v_deposit_balance AS
SELECT d.id,d.booking_id,COALESCE(sum(CASE WHEN e.kind='RECEIVE' THEN e.amount ELSE -e.amount END),0) AS balance
FROM deposit d LEFT JOIN deposit_entry e ON e.deposit_id=d.id GROUP BY d.id,d.booking_id;

CREATE VIEW v_account_balance AS
SELECT a.id,a.code,a.branch_id,COALESCE(sum(e.amount_signed),0) AS balance
FROM financial_account a LEFT JOIN financial_entry e ON e.account_id=a.id GROUP BY a.id,a.code,a.branch_id;

CREATE VIEW v_bill_balance AS
SELECT b.id,b.booking_id,b.net_amount,
 COALESCE((SELECT sum(e.amount) FROM deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS deposit_applied,
 COALESCE((SELECT sum(p.amount) FROM payment p WHERE p.bill_id=b.id AND p.purpose='BILL'),0) AS payments,
 b.net_amount+COALESCE((SELECT sum(x.amount_signed) FROM business_adjustment x WHERE x.bill_id=b.id),0)
 -COALESCE((SELECT sum(e.amount) FROM deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0)
 -COALESCE((SELECT sum(p.amount) FROM payment p WHERE p.bill_id=b.id AND p.purpose='BILL'),0) AS remaining_due
FROM bill b;

CREATE VIEW v_cash_variance_alert AS
SELECT id,branch_id,business_date,variance,explanation,status
FROM daily_cash_closing WHERE variance <> 0 AND status <> 'RECONCILED';

INSERT INTO role(code,name) VALUES
 ('HEAD_OFFICE_MANAGER','Quản lý cấp cao'),('BRANCH_MANAGER','Quản lý cơ sở'),
 ('RECEPTIONIST','Lễ tân'),('ACCOUNTANT','Kế toán');
INSERT INTO shift_template(code,start_minute,end_minute) VALUES
 ('NIGHT',0,420),('MORNING',420,720),('AFTERNOON',720,1080),('EVENING',1080,1440);

