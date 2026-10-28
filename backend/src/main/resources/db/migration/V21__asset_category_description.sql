/*
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Bổ sung mô tả cho nhóm thiết bị theo màn hình quản lý UC60–UC64.
 */
ALTER TABLE gcmb.asset_category
    ADD COLUMN description varchar(500) NOT NULL DEFAULT '';
