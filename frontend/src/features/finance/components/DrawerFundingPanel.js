import { Drawer } from '../../../shared/components/Overlays';
import { PaginatedTable } from '../../../shared/components/Pagination';
/** Author: HuyDV. QLCS requests 500k..1m cash; accountant records the actual payment. */
import { useEffect, useState } from 'react';
import { ToastStack } from '../../../shared/components/Feedback';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, vnd } from '../services/financeApi';
import MoneyInput from '../../../shared/components/MoneyInput';
import { dateTimeLabel } from '../utils/financeDates';
import '../styles/accountant-finance-workspace.css';

const statusLabel = { REQUESTED: 'Chờ kế toán thanh toán', PAID: 'Đã thanh toán', REJECTED: 'Đã từ chối' };
const statusTone = { REQUESTED: 'pending', PAID: 'received', REJECTED: 'neutral' };
const message = (error) => Object.values(apiErrors(error)).join(' ');
const hasRecipient = (row) => ['recipient_name', 'recipient_bank_name', 'recipient_bank_account', 'recipient_bank_holder']
  .every((key) => row?.[key]);

function RecipientDetails({ recipient }) {
  return <dl className="drawer-recipient-details">
    {[
      ['recipient_name', 'Quản lý cơ sở nhận tiền'],
      ['recipient_bank_name', 'Ngân hàng'],
      ['recipient_bank_account', 'Số tài khoản'],
      ['recipient_bank_holder', 'Chủ tài khoản'],
    ].map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{recipient?.[key] || 'Chưa cập nhật'}</dd></div>)}
  </dl>;
}

export default function DrawerFundingPanel({ user, refreshKey = 0, onChange }) {
  const accounting = user.role === 'ACCOUNTANT';
  const base = accounting ? '/accounting/cash-closing/funding' : '/branch-management/cash-closing/funding';
  const branch = accounting ? undefined : user.branchId;
  const [data, setData] = useState(null);
  const [filter, setFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [amount, setAmount] = useState('500000');
  const [reason, setReason] = useState('Cấp tiền lẻ cho két cơ sở sau khi nộp hết tiền.');
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [selected, setSelected] = useState(null);
  const [confirmed, setConfirmed] = useState(false);
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  const [proof, setProof] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [notice, setNotice] = useState('');
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setData(null);
    api.get(base, { branchId: filter || undefined }, branch)
      .then((result) => { if (active) setData(result); })
      .catch((e) => { if (active) setError(message(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [base, branch, filter, version, refreshKey]);

  const create = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await api.post(base, { amount: Number(amount), reason, requestId }, branch);
      setRequestId(crypto.randomUUID());
      setNotice('Đã gửi yêu cầu cấp két. Tiền chỉ được cộng khi kế toán xác nhận đã thanh toán.');
      setVersion((previousVersion) => previousVersion + 1);
      onChange?.();
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  };

  const settle = async (event) => {
    event.preventDefault();
    if (busy || !confirmed || !hasRecipient(selected) || (!reference.trim() && !proof)) return;
    setBusy(true);
    setPaymentError('');
    setNotice('');
    try {
      let body = { confirmed, reference: reference.trim(), note: note.trim() };
      if (proof) {
        const form = new FormData();
        form.append('command', new Blob([JSON.stringify(body)], { type: 'application/json' }));
        form.append('proof', proof);
        body = form;
      }
      await api.post(base + '/' + selected.id + '/pay', body);
      setSelected(null);
      setNotice('Đã ghi nhận thanh toán cấp két cho quản lý cơ sở.');
      setProof(null);
      setVersion((previousVersion) => previousVersion + 1);
      onChange?.();
    } catch (e) { setPaymentError(message(e)); }
    finally { setBusy(false); }
  };

  const completeRecipient = async (row) => {
    setBusy(true);
    setError('');
    try {
      await api.post(base + '/' + row.id + '/recipient', {}, branch);
      setNotice('Đã bổ sung thông tin nhận tiền của bạn vào yêu cầu.');
      setVersion((value) => value + 1);
    } catch (failure) { setError(message(failure)); }
    finally { setBusy(false); }
  };
  const downloadEvidence = async (row) => {
    setBusy(true);
    setError('');
    try { await api.download(base + '/' + row.id + '/evidence', row.evidence_original_name, branch); }
    catch (failure) { setError(message(failure)); }
    finally { setBusy(false); }
  };
  const openPayment = async (row) => {
    setSelected(row);
    setConfirmed(false);
    setReference('');
    setNote('');
    setProof(null);
    setPaymentError('');
    if (hasRecipient(row)) return;
    setBusy(true);
    try {
      const completed = await api.post(base + '/' + row.id + '/recipient', {});
      setSelected({ ...row, ...completed });
      setVersion((value) => value + 1);
    } catch (failure) { setPaymentError(message(failure)); }
    finally { setBusy(false); }
  };

  const rows = data?.requests || [];
  const visibleRows = rows.filter((row) => !statusFilter || row.status === statusFilter);
  const pending = rows.some((row) => row.status === 'REQUESTED' && row.account_id === data?.accountId);
  const validAmount = /^\d+$/.test(amount) && Number(amount) >= 500000 && Number(amount) <= 1000000;
  return <section className={accounting ? 'finance-panel finance-workspace-view drawer-funding-workspace' : 'finance-card'} aria-busy={loading}>
    <header className={accounting ? 'finance-workspace-header' : undefined}>
      <h2>{accounting ? 'Cấp tiền két cơ sở' : 'Yêu cầu cấp tiền két'}</h2>
      {accounting && <button type="button" disabled={busy || loading} onClick={() => setVersion((value) => value + 1)}>Tải lại yêu cầu</button>}
    </header>
    <ToastStack error={error} notice={notice} onCloseError={() => setError('')} onCloseNotice={() => setNotice('')} />
    {loading ? <p className="finance-empty" role="status">Đang tải yêu cầu cấp két…</p> : data && <>
      {accounting ? <>
        <dl className="finance-workspace-metrics" aria-label="Tổng hợp cấp tiền két">
          <div><dt>Quỹ kế toán còn</dt><dd>{vnd(data.fundBalance)}</dd></div>
          <div><dt>Yêu cầu chờ thanh toán</dt><dd className="is-pending">{rows.filter((row) => row.status === 'REQUESTED').length}</dd></div>
          <div><dt>Yêu cầu đã thanh toán</dt><dd className="is-received">{rows.filter((row) => row.status === 'PAID').length}</dd></div>
        </dl>
        <div className="finance-workspace-filters" role="group" aria-label="Lọc yêu cầu cấp tiền két">
          <label>Cơ sở<select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="">Tất cả cơ sở</option>
            {data.branches.map((branchItem) => <option key={branchItem.id} value={branchItem.id}>{branchItem.name}</option>)}
          </select></label>
          <label>Trạng thái<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
        </div>
      </> : <>
        <p>Két hiện tại: <strong>{vnd(data.drawerBalance)}</strong></p>
        <RecipientDetails recipient={data.recipient} />
        {!hasRecipient(data.recipient) && <p className="finance-warning">
          Bạn cần cập nhật ngân hàng, số tài khoản và chủ tài khoản tại Thông tin tài khoản → Chỉnh sửa thông tin.
        </p>}
        {pending ? <p className="finance-info">Đã có yêu cầu chờ kế toán thanh toán. Không tạo thêm yêu cầu trùng.</p>
          : Number(data.drawerBalance) !== 0 ? null
            : <form onSubmit={create}>
              <fieldset disabled={busy || user.readOnly}>
                <label>Số tiền đề nghị cấp két *<MoneyInput required value={amount} onValueChange={setAmount} /><small>Tối thiểu 500.000đ, tối đa 1.000.000đ.</small></label>
                <label>Nội dung yêu cầu *<textarea required maxLength={2000} value={reason} onChange={(e) => setReason(e.target.value)} /></label>
                <button className="finance-primary" disabled={!validAmount || !reason.trim() || !hasRecipient(data.recipient)}>Gửi yêu cầu cấp két</button>
              </fieldset>
            </form>}
      </>}
      {selected && <Drawer open title="Ghi nhận thanh toán cấp két" className="finance-workspace-drawer"
        onClose={() => { if (!busy) setSelected(null); }}
        footer={<>
          <button type="button" disabled={busy} onClick={() => setSelected(null)}>Đóng</button>
          <button type="submit" form="drawer-payment-form" className="finance-primary"
            disabled={busy || !confirmed || !hasRecipient(selected) || (!reference.trim() && !proof)}>
            {busy ? 'Đang xử lý…' : 'Xác nhận đã thanh toán'}
          </button>
        </>}>
        <div className="finance-workspace-payment-summary"><strong>{selected.branchName}</strong><b>{vnd(selected.amount)}</b></div>
        <RecipientDetails recipient={selected}/>
        {!hasRecipient(selected) && <p className="finance-warning">Cần bổ sung tài khoản nhận cho yêu cầu cũ trước khi thanh toán.</p>}
        <form id="drawer-payment-form" className="finance-workspace-form" onSubmit={settle}>
          <fieldset className="finance-workspace-form-single" disabled={busy}>
            <legend>Chứng từ chuyển khoản</legend>
            <label>Mã giao dịch<input required={!proof} maxLength={200} value={reference} onChange={(event) => {
              setReference(event.target.value); setConfirmed(false);
            }}/></label>
            <label>Minh chứng (ảnh hoặc PDF)<input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf,.heic,.heif"
              onChange={(event) => {
                const file = event.target.files?.[0] || null;
                setConfirmed(false);
                if (file && (file.size === 0 || file.size > 20_000_000)) {
                  setPaymentError('Minh chứng phải lớn hơn 0 byte và tối đa 20 MB.');
                  event.target.value = '';
                  setProof(null);
                } else {
                  setProof(file);
                  setPaymentError('');
                }
              }}/></label>
            <p className="finance-workspace-note">Nhập mã giao dịch hoặc đính kèm minh chứng tối đa 20 MB.</p>
            <label>Ghi chú (tùy chọn)<textarea rows={2} maxLength={2000} value={note} onChange={(event) => setNote(event.target.value)}/></label>
          </fieldset>
          <label className="finance-workspace-check"><input type="checkbox" checked={confirmed} disabled={busy}
            onChange={(event) => setConfirmed(event.target.checked)}/>Tôi xác nhận đã chuyển đủ tiền cấp két cho người nhận trên.</label>
          {paymentError && <p className="finance-error" role="alert">{paymentError}</p>}
        </form>
      </Drawer>}
      <h3 className={accounting ? "finance-workspace-subtitle" : undefined}>Lịch sử yêu cầu cấp két</h3>
      <div className={accounting ? 'finance-table-wrap finance-workspace-table' : 'finance-table-wrap'}>
        <PaginatedTable pageSize={5} resetKey={JSON.stringify([filter, statusFilter])} paginationLabel="Phân trang cấp tiền két">
        <thead><tr><th>Cơ sở / thời điểm</th><th>Số tiền</th><th>Người nhận / ngân hàng</th><th>Nội dung</th><th>Trạng thái</th><th>Chứng từ / thao tác</th></tr></thead>
        <tbody>{visibleRows.map((row) => <tr key={row.id}>
          <td>{row.branchName}<small>{dateTimeLabel(row.requested_at)}</small></td>
          <td>{vnd(row.amount)}</td>
          <td>{row.recipient_name || 'Chưa lưu thông tin nhận tiền'}
            {row.recipient_bank_name && <small>{row.recipient_bank_name} · {row.recipient_bank_account}</small>}
            {row.recipient_bank_holder && <small>Chủ tài khoản: {row.recipient_bank_holder}</small>}
          </td>
          <td>{row.reason}{row.resolution_note && <small>{row.resolution_note}</small>}</td>
          <td>{accounting ? <span className={'finance-status ' + statusTone[row.status]}>{statusLabel[row.status]}</span> : statusLabel[row.status]}{row.processed_at && <small>{dateTimeLabel(row.processed_at)} · {row.processedBy}</small>}</td>
          <td>
            {row.payment_reference && <p>Mã giao dịch: {row.payment_reference}</p>}
            {row.hasEvidence && <button type="button" disabled={busy} onClick={() => downloadEvidence(row)}>Tải minh chứng · {row.evidence_original_name}</button>}
            {!accounting && row.status === 'REQUESTED' && !hasRecipient(row) && row.requested_by === data.requesterId && (
              <button type="button" disabled={busy || user.readOnly} onClick={() => completeRecipient(row)}>Bổ sung thông tin nhận tiền</button>
            )}
            {accounting && row.status === 'REQUESTED' && <button type="button" aria-haspopup="dialog" disabled={busy} onClick={() => openPayment(row)}>Thanh toán cấp két</button>}
          </td>
        </tr>)}</tbody>
      </PaginatedTable>{!visibleRows.length && <p>Không có yêu cầu cấp tiền két phù hợp với bộ lọc.</p>}</div>
    </>}
  </section>;
}
