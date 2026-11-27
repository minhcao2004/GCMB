import { useEffect, useState } from 'react';
import { Modal } from '../../../shared/components/Overlays';
import { PaginatedTable } from '../../../shared/components/Pagination';
import MoneyInput from '../../../shared/components/MoneyInput';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, today, vnd } from '../../finance/services/financeApi';

export const payrollApiPath = '/accounting/payroll';
export const paymentStatus = { PAID: 'Đã thanh toán', FAILED: 'Không thành công', PENDING: 'Chờ xác nhận' };
export const monthText = (month) => month ? String(month).slice(0, 7).split('-').reverse().join('/') : '—';
export const dateText = (date) => date ? String(date).slice(0, 10).split('-').reverse().join('/') : '—';
export const payrollError = (error) => Object.values(apiErrors(error)).join(' ') || 'Không thể hoàn tất thao tác. Vui lòng thử lại.';
export const hasBankDetails = (entry) => [entry.bank_name_snapshot, entry.bank_account_snapshot, entry.bank_holder_snapshot]
  .every((value) => Boolean(value?.trim()));

export function PayrollBankDetails({ entry }) {
  return hasBankDetails(entry) ? <div className="accountant-payroll-bank">
    <strong>{entry.bank_name_snapshot} · {entry.bank_account_snapshot}</strong>
    <span>{entry.bank_holder_snapshot}</span>
  </div> : <span className="accountant-payroll-warning">Thiếu tài khoản nhận</span>;
}

export function PayrollBatchDialog({ month, branch, rows, version, onClose, onRecorded }) {
  const [requestId] = useState(() => window.crypto.randomUUID());
  const [paymentDate, setPaymentDate] = useState(today);
  const [bankName, setBankName] = useState('');
  const [reference, setReference] = useState('');
  const [reviewed, setReviewed] = useState(false);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');
  const total = rows.reduce((sum, row) => sum + Number(row.net_amount), 0);
  const selection = { version, branchId: branch.id, employeeIds: rows.map((row) => row.employee_id) };
  const path = `${payrollApiPath}/${month}/payment-batches`;

  async function exportRecipients() {
    setAction('export');
    setError('');
    try {
      await api.downloadPost(`${path}/export`, selection, `Danh-sach-chuyen-luong-${month}.xlsx`);
    } catch (failure) {
      let detail = failure;
      if (failure.response?.data instanceof Blob) {
        try {
          detail = { ...failure, response: { ...failure.response, data: JSON.parse(await failure.response.data.text()) } };
        } catch { /* Use the standard error message. */ }
      }
      setError(payrollError(detail));
    } finally {
      setAction('');
    }
  }

  async function record(event) {
    event.preventDefault();
    if (action || !reviewed) return;
    setAction('save');
    setError('');
    try {
      await api.post(path, { requestId, selection, paymentDate, bankName: bankName.trim(), reference: reference.trim() });
      onRecorded({ paymentDate, count: rows.length, amount: total });
    } catch (failure) {
      setError(payrollError(failure));
    } finally {
      setAction('');
    }
  }

  return <Modal open title="Lô chuyển khoản lương" className="accountant-payroll-modal"
    onClose={() => { if (!action) onClose(); }}
    footer={<>
      <button type="button" onClick={onClose} disabled={Boolean(action)}>Hủy</button>
      <button type="submit" form="payroll-batch-form" className="finance-primary"
        disabled={Boolean(action) || !reviewed || !bankName.trim() || !reference.trim() || !paymentDate}>
        {action === 'save' ? 'Đang ghi nhận…' : `Ghi nhận ${rows.length} nhân viên`}
      </button>
    </>}>
    <div className="accountant-payroll-dialog-summary">
      <div><strong>{branch.name}</strong><span>Tháng lương {monthText(month)} · {rows.length} nhân viên</span></div>
      <strong className="accountant-payroll-money">{vnd(total)}</strong>
    </div>
    <div className="accountant-payroll-export-line">
      <p>Tải danh sách để lập lệnh tại ngân hàng. Đối chiếu theo mẫu của ngân hàng chuyển.</p>
      <button type="button" disabled={Boolean(action)} onClick={exportRecipients}>
        {action === 'export' ? 'Đang xuất…' : 'Tải Excel người nhận'}
      </button>
    </div>
    <div className="finance-table-wrap accountant-payroll-scroll">
      <PaginatedTable pageSize={5} paginationLabel="Phân trang người nhận trong lô">
        <thead><tr><th scope="col">Nhân viên</th><th scope="col">Tài khoản nhận</th><th scope="col" className="accountant-payroll-number">Số tiền</th></tr></thead>
        <tbody>{rows.map((row) => <tr key={row.id}>
          <td><strong>{row.name}</strong><small>{row.code}</small></td>
          <td><PayrollBankDetails entry={row}/></td><td className="accountant-payroll-number">{vnd(row.net_amount)}</td>
        </tr>)}</tbody>
      </PaginatedTable>
    </div>
    <form id="payroll-batch-form" className="accountant-payroll-dialog-form" onSubmit={record}>
      <fieldset disabled={Boolean(action)}>
        <legend>Thông tin lô đã chuyển</legend>
        <label>Ngày thanh toán<input type="date" required min="2000-01-01" max={today()} value={paymentDate}
          onChange={(event) => { setPaymentDate(event.target.value); setReviewed(false); }}/></label>
        <label>Ngân hàng chuyển<input required maxLength={200} value={bankName}
          onChange={(event) => { setBankName(event.target.value); setReviewed(false); }}/></label>
        <label>Mã giao dịch lô<input required maxLength={200} value={reference}
          onChange={(event) => { setReference(event.target.value); setReviewed(false); }}/></label>
      </fieldset>
      <label className="accountant-payroll-check"><input type="checkbox" required checked={reviewed}
        disabled={Boolean(action)} onChange={(event) => setReviewed(event.target.checked)}/>
        <span>Tôi đã đối chiếu và xác nhận ngân hàng chuyển thành công cho toàn bộ người nhận trong lô.</span>
      </label>
      {error && <p className="finance-error" role="alert">{error}</p>}
    </form>
  </Modal>;
}

const proofAccept = 'image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf,.jpg,.jpeg,.png,.webp,.heic,.heif,.pdf';

export function PayrollResultDialog({ entry, employees, month, version, onClose, onRecorded }) {
  const isAdvance = !entry;
  const [payment, setPayment] = useState(() => ({
    employeeId: entry?.employee_id || '', amount: '', result: 'PENDING', reference: '', requestId: window.crypto.randomUUID(),
  }));
  const [available, setAvailable] = useState(null);
  const [proof, setProof] = useState(null);
  const [reviewed, setReviewed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isAdvance || !payment.employeeId) return undefined;
    let active = true;
    api.get(`${payrollApiPath}/${month}/available/${payment.employeeId}`)
      .then((result) => { if (active) setAvailable(result); })
      .catch((failure) => { if (active) setError(payrollError(failure)); });
    return () => { active = false; };
  }, [isAdvance, month, payment.employeeId]);

  function update(field, value) {
    setPayment((current) => ({ ...current, [field]: value }));
    setReviewed(false);
  }

  async function submit(event) {
    event.preventDefault();
    if (busy || (!isAdvance && !reviewed)) return;
    setBusy(true);
    setError('');
    try {
      const { amount, ...result } = payment;
      let body = isAdvance ? { ...result, amount: Number(amount) } : { version, payments: [result] };
      if (proof) {
        const form = new FormData();
        form.append('command', new Blob([JSON.stringify(body)], { type: 'application/json' }));
        form.append(`proof_${result.employeeId}`, proof);
        body = form;
      }
      const response = await api.post(`${payrollApiPath}/${month}/${isAdvance ? 'advance' : 'payments'}`, body);
      onRecorded(response?.outcomes?.[0]?.message || response?.message || (isAdvance ? 'Đã ghi nhận ứng lương.' : 'Đã ghi nhận kết quả trả lương.'));
    } catch (failure) {
      setError(payrollError(failure));
    } finally {
      setBusy(false);
    }
  }

  return <Modal open title={isAdvance ? 'Ghi nhận ứng lương' : 'Ghi nhận kết quả từng người'} className="accountant-payroll-modal"
    onClose={() => { if (!busy) onClose(); }}
    footer={<>
      <button type="button" disabled={busy} onClick={onClose}>Hủy</button>
      <button type="submit" form="payroll-result-form" className="finance-primary"
        disabled={busy || (isAdvance ? !available : !reviewed)}>{busy ? 'Đang ghi nhận…' : 'Ghi nhận kết quả'}</button>
    </>}>
    {!isAdvance && <div className="accountant-payroll-dialog-summary">
      <div><strong>{entry.name}</strong><PayrollBankDetails entry={entry}/></div><strong>{vnd(entry.net_amount)}</strong>
    </div>}
    <form id="payroll-result-form" className="accountant-payroll-dialog-form" onSubmit={submit}>
      <fieldset disabled={busy}>
        {isAdvance && <>
          <label>Nhân viên<select required value={payment.employeeId} onChange={(event) => {
            setAvailable(null); setError(''); update('employeeId', event.target.value);
          }}>
            <option value="">Chọn nhân viên</option>
            {employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.code} · {employee.name}</option>)}
          </select></label>
          <label>Số tiền ứng<MoneyInput required value={payment.amount} onValueChange={(value) => update('amount', value)}/></label>
        </>}
        <label>Kết quả thực tế<select value={payment.result} onChange={(event) => update('result', event.target.value)}>
          <option value="PENDING">Chờ xác nhận</option><option value="PAID">Đã trả thành công</option><option value="FAILED">Không thành công</option>
        </select></label>
        <label>Mã chứng từ / tham chiếu<input required={payment.result === 'PAID' && !proof}
          maxLength={200} value={payment.reference} onChange={(event) => update('reference', event.target.value)}/></label>
        {!isAdvance && <label>Chứng từ (tùy chọn)<input type="file" accept={proofAccept} onChange={(event) => {
          const file = event.target.files?.[0] || null;
          const invalid = file && (file.size === 0 || file.size > 20000000 ||
            !/\.(jpe?g|png|webp|heic|heif|pdf)$/i.test(file.name));
          if (invalid) event.target.value = '';
          setProof(invalid ? null : file);
          setError(invalid ? 'Chọn ảnh hoặc PDF, dung lượng tối đa 20 MB.' : '');
          setReviewed(false);
        }}/></label>}
      </fieldset>
      {isAdvance && available && <p className="finance-info">
        Lương tích lũy {vnd(available.earned)} · Đã ứng {vnd(available.paidAdvances)} · Còn có thể ứng <strong>{vnd(available.available)}</strong>
      </p>}
      {!isAdvance && <label className="accountant-payroll-check">
        <input type="checkbox" required checked={reviewed} disabled={busy} onChange={(event) => setReviewed(event.target.checked)}/>
        <span>Tôi xác nhận kết quả đúng với giao dịch thực tế.</span>
      </label>}
      {error && <p className="finance-error" role="alert">{error}</p>}
    </form>
  </Modal>;
}
