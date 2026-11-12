-- Author: HuyDV. Cấp két: chuyển cho QLCS, lưu người nhận và chứng từ theo từng yêu cầu.
ALTER TABLE gcmb.cash_drawer_funding
  ADD COLUMN recipient_name varchar(200),
  ADD COLUMN recipient_bank_name varchar(200),
  ADD COLUMN recipient_bank_account varchar(60),
  ADD COLUMN recipient_bank_holder varchar(200),
  ADD COLUMN evidence_original_name varchar(255),
  ADD COLUMN evidence_mime_type varchar(100),
  ADD COLUMN evidence_object_key text;

DROP TRIGGER retain_drawer_funding ON gcmb.cash_drawer_funding;
-- Chỉ bổ sung yêu cầu đang chờ. Không suy đoán tài khoản đã nhận cho các giao dịch cũ đã trả.
UPDATE gcmb.cash_drawer_funding f SET recipient_name=e.full_name,
  recipient_bank_name=e.bank_name,recipient_bank_account=e.bank_account,recipient_bank_holder=e.bank_holder
FROM gcmb.employee e WHERE e.user_id=f.requested_by AND f.status='REQUESTED'
  AND length(trim(e.full_name))>0 AND length(trim(e.bank_name))>0
  AND e.bank_account ~ '^[A-Za-z0-9]{3,60}$' AND length(trim(e.bank_holder))>0;

ALTER TABLE gcmb.cash_drawer_funding ADD CONSTRAINT complete_drawer_recipient CHECK (
  (recipient_name IS NULL AND recipient_bank_name IS NULL AND recipient_bank_account IS NULL AND recipient_bank_holder IS NULL)
  OR (recipient_name IS NOT NULL AND recipient_bank_name IS NOT NULL
    AND recipient_bank_account IS NOT NULL AND recipient_bank_holder IS NOT NULL
    AND length(trim(recipient_name))>0 AND length(trim(recipient_bank_name))>0
    AND recipient_bank_account ~ '^[A-Za-z0-9]{3,60}$' AND length(trim(recipient_bank_holder))>0)
);
ALTER TABLE gcmb.cash_drawer_funding ADD CONSTRAINT complete_drawer_payment_evidence CHECK (
  (evidence_object_key IS NULL AND evidence_original_name IS NULL AND evidence_mime_type IS NULL)
  OR (evidence_object_key IS NOT NULL AND evidence_original_name IS NOT NULL AND evidence_mime_type IS NOT NULL)
);

CREATE OR REPLACE FUNCTION gcmb.protect_drawer_funding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Drawer funding history is retained'; END IF;
  IF OLD.status<>'REQUESTED' THEN RAISE EXCEPTION 'Settled drawer funding is immutable'; END IF;
  IF NEW.status='REQUESTED' AND OLD.recipient_name IS NULL AND NEW.recipient_name IS NOT NULL
    AND length(trim(NEW.recipient_name))>0 AND length(trim(NEW.recipient_bank_name))>0
    AND NEW.recipient_bank_account ~ '^[A-Za-z0-9]{3,60}$' AND length(trim(NEW.recipient_bank_holder))>0
    AND (to_jsonb(NEW)-ARRAY['recipient_name','recipient_bank_name','recipient_bank_account','recipient_bank_holder'])
      IS NOT DISTINCT FROM
      (to_jsonb(OLD)-ARRAY['recipient_name','recipient_bank_name','recipient_bank_account','recipient_bank_holder']) THEN
    RETURN NEW;
  END IF;
  IF NEW.status NOT IN ('PAID','REJECTED') OR
    (to_jsonb(NEW)-ARRAY['status','processed_by','processed_at','payment_reference','resolution_note',
      'evidence_original_name','evidence_mime_type','evidence_object_key']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['status','processed_by','processed_at','payment_reference','resolution_note',
      'evidence_original_name','evidence_mime_type','evidence_object_key']) THEN
    RAISE EXCEPTION 'Drawer funding may be settled only once';
  END IF;
  IF NEW.status='PAID' AND (NEW.recipient_name IS NULL OR NEW.recipient_bank_name IS NULL
    OR NEW.recipient_bank_account IS NULL OR NEW.recipient_bank_holder IS NULL
    OR (length(trim(NEW.payment_reference))=0 AND NEW.evidence_object_key IS NULL)) THEN
    RAISE EXCEPTION 'Drawer payment requires recipient and payment reference or evidence';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER retain_drawer_funding BEFORE UPDATE OR DELETE ON gcmb.cash_drawer_funding
FOR EACH ROW EXECUTE FUNCTION gcmb.protect_drawer_funding();
