ALTER TABLE gcmb.asset_repair ADD COLUMN invoice_attachment_id uuid REFERENCES gcmb.attachment(id) ON DELETE RESTRICT;
ALTER TABLE gcmb.asset_repair ADD COLUMN transfer_attachment_id uuid REFERENCES gcmb.attachment(id) ON DELETE RESTRICT;
CREATE INDEX ix_asset_repair_invoice_attachment_id ON gcmb.asset_repair(invoice_attachment_id);
CREATE INDEX ix_asset_repair_transfer_attachment_id ON gcmb.asset_repair(transfer_attachment_id);
