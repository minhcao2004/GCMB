ALTER TABLE gcmb.menu_item
    ADD COLUMN description varchar(1000),
    ADD COLUMN image_object_key varchar(100),
    ADD COLUMN image_original_name varchar(200),
    ADD COLUMN image_mime_type varchar(120),
    ADD CONSTRAINT ck_menu_item_image_metadata CHECK (
        (image_object_key IS NULL AND image_original_name IS NULL AND image_mime_type IS NULL)
        OR (image_object_key IS NOT NULL AND image_original_name IS NOT NULL AND image_mime_type IS NOT NULL)
    );

COMMENT ON COLUMN gcmb.menu_item.description IS 'Mô tả món hoặc dịch vụ hiển thị trong thực đơn.';
COMMENT ON COLUMN gcmb.menu_item.image_object_key IS 'Khóa tệp ảnh hoặc tài liệu món trong vùng lưu trữ riêng.';
COMMENT ON COLUMN gcmb.menu_item.image_original_name IS 'Tên tệp ảnh hoặc tài liệu do người dùng tải lên.';
COMMENT ON COLUMN gcmb.menu_item.image_mime_type IS 'Kiểu nội dung đã xác thực từ dữ liệu tệp.';
