/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: Tạo cấu trúc dữ liệu danh mục hạng phòng và bảng giá.
 */

-- Chain-wide Cinema room types and price catalogue.
ALTER TABLE gcmb.combo ADD COLUMN mode varchar(20);
UPDATE gcmb.combo c SET mode = rt.mode FROM gcmb.room_type rt WHERE rt.id = c.room_type_id;
ALTER TABLE gcmb.combo ALTER COLUMN mode SET NOT NULL;
ALTER TABLE gcmb.combo ADD CONSTRAINT combo_mode_check CHECK (mode IN ('CINEMA','MUSIC_BOX'));
ALTER TABLE gcmb.combo ADD COLUMN branch_id uuid;
ALTER TABLE gcmb.combo ADD COLUMN day_group varchar(20) NOT NULL DEFAULT 'ALL';
ALTER TABLE gcmb.combo ADD COLUMN minute_from integer NOT NULL DEFAULT 0;
ALTER TABLE gcmb.combo ADD COLUMN minute_to integer NOT NULL DEFAULT 1440;
ALTER TABLE gcmb.combo ADD COLUMN min_guests integer NOT NULL DEFAULT 1;
ALTER TABLE gcmb.combo ADD COLUMN max_guests integer;
ALTER TABLE gcmb.combo ADD COLUMN included_description text NOT NULL DEFAULT '';
ALTER TABLE gcmb.combo ADD COLUMN priority integer NOT NULL DEFAULT 100;
ALTER TABLE gcmb.combo ADD CONSTRAINT combo_day_group_check CHECK (day_group IN ('ALL','MON_THU','FRI_SUN','WEEKDAY','WEEKEND'));
ALTER TABLE gcmb.combo ADD CONSTRAINT combo_schedule_check CHECK (minute_from BETWEEN 0 AND 1439 AND minute_to BETWEEN 1 AND 1440 AND minute_from <> minute_to);
ALTER TABLE gcmb.combo ADD CONSTRAINT combo_guest_range_check CHECK (min_guests > 0 AND (max_guests IS NULL OR max_guests >= min_guests));
ALTER TABLE gcmb.combo ADD FOREIGN KEY (branch_id) REFERENCES gcmb.branch(id) ON DELETE RESTRICT;
CREATE INDEX ix_combo_room_mode_active ON gcmb.combo(mode, active, priority, minute_from);
CREATE INDEX ix_combo_branch_id ON gcmb.combo(branch_id);

INSERT INTO gcmb.room_type (code, name, mode, capacity, description, active) VALUES
 ('RT-QN', 'Queen', 'CINEMA', 4, 'Không gian tiêu chuẩn · Sức chứa 2–4 khách', true),
 ('RT-KG', 'King', 'CINEMA', 6, 'Không gian rộng · Sức chứa 4–6 khách', true)
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name, mode=EXCLUDED.mode, capacity=EXCLUDED.capacity,
  description=EXCLUDED.description, active=true;

WITH source(code, name, price) AS (VALUES
 ('RT-QN', 'Queen · Giờ lẻ', 96000::numeric),
 ('RT-KG', 'King · Giờ lẻ', 106000::numeric)
)
INSERT INTO gcmb.price_rule (room_type_id, name, valid_from, day_kind, minute_from, minute_to,
 hourly_price, overtime_hourly_price, billing_step_minutes, priority, active)
SELECT rt.id, s.name, CURRENT_DATE, 'ALL', 0, 1440, s.price, s.price, 60, 100, true
FROM source s JOIN gcmb.room_type rt ON rt.code=s.code
WHERE NOT EXISTS (SELECT 1 FROM gcmb.price_rule p WHERE p.room_type_id=rt.id AND p.name=s.name AND p.active=true);

WITH source(code, room_code, name, duration, price, description, minute_from, minute_to) AS (VALUES
 ('CIN_C1_QN','RT-QN','Combo 1 · 2 giờ',120,236000::numeric,'2 giờ xem phim; 1 khô gà; 2 đồ uống tự chọn',0,1440),
 ('CIN_C1_KG','RT-KG','Combo 1 · 2 giờ',120,246000::numeric,'2 giờ xem phim; 1 khô gà; 2 đồ uống tự chọn',0,1440),
 ('CIN_C2_QN','RT-QN','Combo 2 · 2 giờ',120,276000::numeric,'2 giờ xem phim; 1 nem; 1 khoai; 2 đồ uống tự chọn',0,1440),
 ('CIN_C2_KG','RT-KG','Combo 2 · 2 giờ',120,286000::numeric,'2 giờ xem phim; 1 nem; 1 khoai; 2 đồ uống tự chọn',0,1440),
 ('CIN_C3_QN','RT-QN','Combo 3 · 2 giờ',120,316000::numeric,'2 giờ xem phim; 1 khô gà; 1 khoai; 2 đồ uống tự chọn',0,1440),
 ('CIN_C3_KG','RT-KG','Combo 3 · 2 giờ',120,336000::numeric,'2 giờ xem phim; 1 khô gà; 1 khoai; 2 đồ uống tự chọn',0,1440),
 ('CIN_4H_QN','RT-QN','Combo 4 giờ',240,296000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_4H_KG','RT-KG','Combo 4 giờ',240,326000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_5H_QN','RT-QN','Combo 5 giờ',300,366000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_5H_KG','RT-KG','Combo 5 giờ',300,396000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_6H_QN','RT-QN','Combo 6 giờ',360,416000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_6H_KG','RT-KG','Combo 6 giờ',360,446000::numeric,'Tùy chọn giờ xem phim',0,1440),
 ('CIN_DAY_QN','RT-QN','Combo ngày · 07:00–12:00',300,196000::numeric,'5 giờ xem phim; 1 nước pha chế',420,720),
 ('CIN_DAY_KG','RT-KG','Combo ngày · 07:00–12:00',300,246000::numeric,'5 giờ xem phim; 1 nước pha chế',420,720),
 ('CIN_NIGHT_QN','RT-QN','Combo đêm · 23:00–07:00',480,296000::numeric,'8 giờ xem phim; 1 nước pha chế',1380,420),
 ('CIN_NIGHT_KG','RT-KG','Combo đêm · 23:00–07:00',480,336000::numeric,'8 giờ xem phim; 1 nước pha chế',1380,420)
)
INSERT INTO gcmb.combo (code,version,room_type_id,mode,name,duration_minutes,price,deposit_required,
 overtime_hourly_price,valid_from,active,branch_id,day_group,minute_from,minute_to,min_guests,
 included_description,priority)
SELECT s.code,1,rt.id,'CINEMA',s.name,s.duration,s.price,0,
       CASE WHEN s.room_code='RT-QN' THEN 96000 ELSE 106000 END,
       CURRENT_TIMESTAMP,true,NULL,'ALL',s.minute_from,s.minute_to,1,s.description,100
FROM source s JOIN gcmb.room_type rt ON rt.code=s.room_code
WHERE NOT EXISTS (SELECT 1 FROM gcmb.combo c WHERE c.code=s.code AND c.version=1);
