-- The cash-closing notification migration was assigned V59 before salary
-- payment evidence occupied that version. Preserve existing V66 equipment
-- counter installations and apply notifications at the next version.
CREATE TABLE IF NOT EXISTS gcmb.cash_closing_notification (
  id UUID PRIMARY KEY,
  closing_id UUID NOT NULL REFERENCES gcmb.daily_cash_closing(id),
  report_version INTEGER NOT NULL,
  recipient_id UUID NOT NULL REFERENCES gcmb.user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT cash_closing_notification_once UNIQUE (closing_id, report_version, recipient_id)
);

CREATE INDEX IF NOT EXISTS cash_closing_notification_recipient
  ON gcmb.cash_closing_notification(recipient_id, created_at DESC);
