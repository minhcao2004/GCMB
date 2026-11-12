/** Author: HuyDV. QLCS requests 500k..1m cash; accountant records the actual payment. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, vnd } from '../services/financeApi';
import MoneyInput from './MoneyInput';

const statusLabel = { REQUESTED: 'Chờ kế toán thanh toán', PAID: 'Đã thanh toán', REJECTED: 'Đã từ chối' };
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
  const [notice, setNotice] = useState('');
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
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
      setVersion((n) => n + 1);
      onChange?.();
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  };

  const settle = async (reject) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      let body = { confirmed, reference, note };
      if (!reject && proof) {
        const form = new FormData();
        form.append('command', new Blob([JSON.stringify(body)], { type: 'application/json' }));
        form.append('proof', proof);
        body = form;
      }
      await api.post(base + '/' + selected.id + (reject ? '/reject' : '/pay'), body);
      setSelected(null);
      setNotice(reject ? 'Đã từ chối yêu cầu cấp két.' : 'Đã ghi nhận thanh toán cho quản lý cơ sở, trừ quỹ kế toán và cộng tiền vào két cơ sở.');
      setProof(null);
      setVersion((n) => n + 1);
      onChange?.();
    } catch (e) { setError(message(e)); }
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
    setError('');
    if (hasRecipient(row)) return;
    setBusy(true);
    try {
      const completed = await api.post(base + '/' + row.id + '/recipient', {});
      setSelected({ ...row, ...completed });
      setVersion((value) => value + 1);
    } catch (failure) { setError(message(failure)); }
    finally { setBusy(false); }
  };

  const rows = data?.requests || [];
  const pending = rows.some((row) => row.status === 'REQUESTED' && row.account_id === data?.accountId);
  const validAmount = /^\d+$/.test(amount) && Number(amount) >= 500000 && Number(amount) <= 1000000;
  return <section className="finance-card">
    <h2>{accounting ? 'Cấp tiền két cơ sở' : 'Yêu cầu cấp tiền két'}</h2>
    {error && <p className="finance-error" role="alert">{error}</p>}
    {notice && <p className="finance-success" role="status">{notice}</p>}
    {loading ? <p role="status">Đang tải yêu cầu cấp két…</p> : data && <>
      {accounting ? <div className="finance-toolbar">
        <p>Quỹ kế toán còn: <strong>{vnd(data.fundBalance)}</strong></p>
        <label>Cơ sở<select value={filter} onChange={(e) => { setFilter(e.target.value); setSelected(null); }}>
          <option value="">Tất cả cơ sở</option>
          {data.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select></label>
        <button disabled={busy} onClick={() => { setSelected(null); setProof(null); setVersion((n) => n + 1); }}>Tải lại yêu cầu</button>
      </div> : <>
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
      {selected && <form className="finance-info" onSubmit={(e) => { e.preventDefault(); settle(false); }}>
        <h3>{selected.code} · {selected.branchName}</h3>
        <p>Thanh toán cấp két: <strong>{vnd(selected.amount)}</strong></p>
        <RecipientDetails recipient={selected} />
        {!hasRecipient(selected) && <p className="finance-warning">Yêu cầu cũ chưa lưu thông tin nhận tiền. Quản lý cơ sở gửi yêu cầu cần bổ sung thông tin tại phần lịch sử yêu cầu cấp két trước khi thanh toán.</p>}
        <label>Mã giao dịch / chứng từ<input required={!proof} maxLength={200} value={reference} onChange={(e) => setReference(e.target.value)} /></label>
        <label>Minh chứng thanh toán (ảnh/PDF)
          <input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf,.heic,.heif"
            disabled={busy} onChange={(event) => {
              const file = event.target.files?.[0] || null;
              if (file?.size > 20_000_000) {
                setError('Minh chứng không được vượt quá 20 MB.');
                event.target.value = '';
                setProof(null);
              } else {
                setProof(file);
                setError('');
              }
            }} />
          <small>Nhập mã giao dịch hoặc tải minh chứng để tham chiếu khoản đã chuyển cho quản lý cơ sở.</small>
        </label>
        <label>Ghi chú / lý do từ chối<textarea maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} /></label>
        <label className="finance-check"><input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />Tôi xác nhận đã thanh toán đủ tiền cấp két cho quản lý cơ sở nhận tiền ở trên.</label>
        <div className="finance-actions">
          <button type="button" disabled={busy} onClick={() => setSelected(null)}>Đóng</button>
          <button type="button" disabled={busy || !note.trim()} onClick={() => settle(true)}>Từ chối yêu cầu</button>
          <button className="finance-primary" disabled={busy || !confirmed || !hasRecipient(selected) || (!reference.trim() && !proof)}>Xác nhận đã thanh toán</button>
        </div>
      </form>}
      <h3>Lịch sử yêu cầu cấp két</h3>
      <div className="finance-table-wrap"><table>
        <thead><tr><th>Yêu cầu / cơ sở</th><th>Số tiền</th><th>Người nhận / ngân hàng</th><th>Nội dung</th><th>Trạng thái</th><th>Chứng từ / thao tác</th></tr></thead>
        <tbody>{rows.map((row) => <tr key={row.id}>
          <td>{row.code}<small>{row.branchName} · {new Date(row.requested_at).toLocaleString('vi-VN')}</small></td>
          <td>{vnd(row.amount)}</td>
          <td>{row.recipient_name || 'Chưa lưu thông tin nhận tiền'}
            {row.recipient_bank_name && <small>{row.recipient_bank_name} · {row.recipient_bank_account}</small>}
            {row.recipient_bank_holder && <small>Chủ tài khoản: {row.recipient_bank_holder}</small>}
          </td>
          <td>{row.reason}{row.resolution_note && <small>{row.resolution_note}</small>}</td>
          <td>{statusLabel[row.status]}{row.processed_at && <small>{new Date(row.processed_at).toLocaleString('vi-VN')} · {row.processedBy}</small>}</td>
          <td>
            {row.payment_reference && <p>Mã giao dịch: {row.payment_reference}</p>}
            {row.hasEvidence && <button type="button" disabled={busy} onClick={() => downloadEvidence(row)}>Tải minh chứng · {row.evidence_original_name}</button>}
            {!accounting && row.status === 'REQUESTED' && !hasRecipient(row) && row.requested_by === data.requesterId && (
              <button type="button" disabled={busy || user.readOnly} onClick={() => completeRecipient(row)}>Bổ sung thông tin nhận tiền</button>
            )}
            {accounting && row.status === 'REQUESTED' && <button disabled={busy} onClick={() => openPayment(row)}>Thanh toán cấp két</button>}
          </td>
        </tr>)}</tbody>
      </table>{!rows.length && <p>Chưa có yêu cầu cấp tiền két.</p>}</div>
    </>}
  </section>;
}
