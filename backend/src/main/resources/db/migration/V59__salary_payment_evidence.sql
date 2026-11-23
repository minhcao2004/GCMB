-- HuyDV / UC-95: bằng chứng gắn với từng kết quả trả lương, không tạo thêm khoản chi.
CREATE TABLE gcmb.salary_payment_evidence (
    payment_id uuid PRIMARY KEY REFERENCES gcmb.salary_payment(id) ON DELETE RESTRICT,
    object_key text NOT NULL,
    original_name varchar(200) NOT NULL,
    mime_type varchar(100) NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size > 0 AND byte_size <= 20000000),
    sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
