ALTER TABLE gcmb.user_account ADD COLUMN branch_id uuid REFERENCES gcmb.branch(id);

-- Temporary branch records requested for account creation. Addresses await completion.
INSERT INTO gcmb.branch(code,name,address,timezone,status)
SELECT seed.code,seed.name,'Chưa cập nhật','Asia/Ho_Chi_Minh','ACTIVE'
FROM (VALUES ('HO_TAY','Hồ Tây'),('NGUYEN_VAN_LOC','Nguyễn Văn Lộc'),('TRIEU_KHUC','Triều Khúc')) AS seed(code,name)
WHERE NOT EXISTS (SELECT 1 FROM gcmb.branch b WHERE lower(b.name)=lower(seed.name))
ON CONFLICT (code) DO NOTHING;
