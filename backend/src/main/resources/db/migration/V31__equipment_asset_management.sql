/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Bổ sung trạng thái sử dụng, hạn bảo hành và ảnh cho tài sản thiết bị UC65–UC69.
 */
ALTER TABLE gcmb.asset
    ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS warranty_expiry date,
    ADD COLUMN IF NOT EXISTS image_attachment_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_asset_image_attachment'
          AND conrelid = 'gcmb.asset'::regclass
    ) THEN
        ALTER TABLE gcmb.asset
            ADD CONSTRAINT fk_asset_image_attachment
            FOREIGN KEY (image_attachment_id) REFERENCES gcmb.attachment(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_asset_warranty_expiry'
          AND conrelid = 'gcmb.asset'::regclass
    ) THEN
        ALTER TABLE gcmb.asset
            ADD CONSTRAINT ck_asset_warranty_expiry
            CHECK (warranty_expiry IS NULL OR purchase_date IS NULL OR warranty_expiry >= purchase_date);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_asset_image_attachment_id ON gcmb.asset(image_attachment_id);
