ALTER TABLE gcmb.inventory_item
    ADD COLUMN category varchar(100) NOT NULL DEFAULT 'Chưa phân nhóm';

ALTER TABLE gcmb.stock_receipt
    ADD COLUMN evidence_object_key varchar(255),
    ADD COLUMN evidence_original_name varchar(200),
    ADD COLUMN evidence_mime_type varchar(100);

ALTER TABLE gcmb.stock_movement
    ADD COLUMN evidence_object_key varchar(255),
    ADD COLUMN evidence_original_name varchar(200),
    ADD COLUMN evidence_mime_type varchar(100),
    ADD COLUMN occurred_at timestamptz;

INSERT INTO gcmb.unit(code,name) VALUES
    ('CHAI','Chai'),
    ('GOI','Gói'),
    ('PHAN','Phần'),
    ('LON','Lon'),
    ('KG','Kg'),
    ('LIT','Lít'),
    ('CAI','Cái'),
    ('THUNG','Thùng')
ON CONFLICT(code) DO NOTHING;
