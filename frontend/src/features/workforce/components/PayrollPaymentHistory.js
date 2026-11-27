import { useEffect, useState } from 'react';
import { Modal } from '../../../shared/components/Overlays';
import { PaginatedTable } from '../../../shared/components/Pagination';
import { financeApi as api, vnd } from '../../finance/services/financeApi';
import { dateText, monthText, paymentStatus, payrollApiPath, payrollError } from './PayrollPaymentDialogs';

export default function PayrollPaymentHistory({ month, onMonthChange, branchId, onBranchChange, branches }) {
  const [records, setRecords] = useState([]);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloadError, setDownloadError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setRecords([]);
    api.get(`${payrollApiPath}/payment-history`, { month, ...(branchId && { branchId }) })
      .then((response) => { if (active) setRecords(response); })
      .catch((failure) => { if (active) setError(payrollError(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [month, branchId, revision]);

  const groups = new Map();
  for (const payment of records) {
    const key = payment.batch_id || payment.id;
    if (!groups.has(key)) groups.set(key, { ...payment, key, recipients: [], total: 0 });
    const group = groups.get(key);
    group.recipients.push(payment);
    group.total += Number(payment.amount);
  }
  const needle = query.trim().toLocaleLowerCase('vi');
  const visible = [...groups.values()].filter((group) => !needle || [
    group.external_reference, group.bank_name, ...group.recipients.map((item) => `${item.employee_code} ${item.employee_name}`),
  ].some((value) => value?.toLocaleLowerCase('vi').includes(needle)));
  const totalPaid = visible.reduce((sum, group) => group.status === 'PAID' ? sum + group.total : sum, 0);

  async function downloadProof(payment) {
    setDownloadError('');
    try {
      await api.download(`${payrollApiPath}/payments/${payment.id}/evidence`, payment.evidence_name);
    } catch {
      setDownloadError('Không tải được chứng từ trả lương. Vui lòng thử lại.');
    }
  }

  return <section className="finance-panel accountant-payroll-panel" aria-label="Lịch sử thanh toán lương" aria-busy={loading}>
    <div className="accountant-payroll-section-head">
      <div><h2>Lịch sử thanh toán lương</h2>
        {!loading && !error && <span>{visible.length} giao dịch · Đã chi <strong>{vnd(totalPaid)}</strong></span>}</div>
      <button type="button" onClick={() => setRevision((value) => value + 1)} disabled={loading}>Tải lại</button>
    </div>
    <div className="accountant-payroll-filters accountant-payroll-history-filters">
      <label>Tháng thanh toán<input type="month" required value={month}
        onChange={(event) => event.target.value && onMonthChange(event.target.value)}/></label>
      <label>Cơ sở<select value={branchId} onChange={(event) => onBranchChange(event.target.value)}>
        <option value="">Tất cả cơ sở</option>
        {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select></label>
      <label>Tìm giao dịch<input type="search" value={query} placeholder="Mã giao dịch, ngân hàng, nhân viên"
        onChange={(event) => setQuery(event.target.value)}/></label>
    </div>
    {loading && <p className="accountant-payroll-empty" role="status">Đang tải lịch sử thanh toán…</p>}
    {error && <p className="finance-error" role="alert">{error}</p>}
    {!loading && !error && <div className="finance-table-wrap accountant-payroll-scroll">
      <PaginatedTable pageSize={10} paginationLabel="Phân trang lịch sử thanh toán lương">
        <thead><tr>
          <th scope="col">Ngày thanh toán</th><th scope="col">Mã giao dịch</th><th scope="col">Cơ sở</th>
          <th scope="col">Tháng lương</th><th scope="col" className="accountant-payroll-number">Số tiền</th>
          <th scope="col">Trạng thái</th><th scope="col">Thao tác</th>
        </tr></thead>
        <tbody>{visible.map((group) => <tr key={group.key}>
          <td>{dateText(group.payment_date)}</td>
          <td><strong>{group.external_reference || '—'}</strong>
            <small>{group.batch_id ? `${group.bank_name} · ${group.recipients.length} người` : group.employee_name}</small></td>
          <td>{group.branch_name || '—'}</td><td>{monthText(group.payroll_month)}</td>
          <td className="accountant-payroll-number">{vnd(group.total)}</td>
          <td><span className={`accountant-payroll-status is-${group.status.toLowerCase()}`}>{paymentStatus[group.status] || '—'}</span></td>
          <td><button type="button" aria-haspopup="dialog" onClick={() => { setSelected(group); setDownloadError(''); }}>Chi tiết</button></td>
        </tr>)}
          {!visible.length && <tr><td colSpan="7" className="accountant-payroll-empty">
            {records.length ? 'Không tìm thấy giao dịch phù hợp.' : 'Chưa có thanh toán lương trong phạm vi này.'}
          </td></tr>}
        </tbody>
      </PaginatedTable>
    </div>}
    {selected && <Modal open title={selected.batch_id ? 'Chi tiết lô chuyển khoản' : 'Chi tiết thanh toán lương'}
      className="accountant-payroll-modal" onClose={() => setSelected(null)}>
      <div className="accountant-payroll-dialog-summary">
        <div><strong>{selected.external_reference || selected.employee_name}</strong>
          <span>{selected.branch_name || 'Toàn chuỗi'} · Tháng lương {monthText(selected.payroll_month)}</span></div>
        <strong className="accountant-payroll-money">{vnd(selected.total)}</strong>
      </div>
      <dl className="accountant-payroll-history-meta">
        <div><dt>Ngày thanh toán</dt><dd>{dateText(selected.payment_date)}</dd></div>
        <div><dt>Trạng thái</dt><dd>{paymentStatus[selected.status]}</dd></div>
        {selected.bank_name && <div><dt>Ngân hàng chuyển</dt><dd>{selected.bank_name}</dd></div>}
        <div><dt>Người ghi nhận</dt><dd>{selected.recorded_by}</dd></div>
      </dl>
      {selected.failure_reason && <p>{selected.failure_reason}</p>}
      {downloadError && <p className="finance-error" role="alert">{downloadError}</p>}
      <div className="finance-table-wrap accountant-payroll-scroll">
        <PaginatedTable pageSize={10} paginationLabel="Phân trang chi tiết thanh toán lương">
          <thead><tr><th scope="col">Nhân viên</th><th scope="col" className="accountant-payroll-number">Số tiền</th><th scope="col">Chứng từ</th></tr></thead>
          <tbody>{selected.recipients.map((person) => <tr key={person.id}>
            <td><strong>{person.employee_name}</strong><small>{person.employee_code}</small></td>
            <td className="accountant-payroll-number">{vnd(person.amount)}</td>
            <td>{person.evidence_name ? <button type="button" onClick={() => downloadProof(person)}>Tải chứng từ</button> : '—'}</td>
          </tr>)}</tbody>
        </PaginatedTable>
      </div>
    </Modal>}
  </section>;
}
