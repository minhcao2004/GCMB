CREATE TABLE gcmb.cash_closing_notification (
  id UUID PRIMARY KEY,
  closing_id UUID NOT NULL REFERENCES gcmb.daily_cash_closing(id),
  report_version INTEGER NOT NULL,
  recipient_id UUID NOT NULL REFERENCES gcmb.user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT cash_closing_notification_once UNIQUE (closing_id, report_version, recipient_id)
);

CREATE INDEX cash_closing_notification_recipient
  ON gcmb.cash_closing_notification(recipient_id, created_at DESC);
