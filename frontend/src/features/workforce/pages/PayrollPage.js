/** UC-91..97: bảng lương, chi trả và đối chiếu kết quả của kế toán. */
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ToastStack } from '../../../shared/components/Feedback';
import { Modal } from '../../../shared/components/Overlays';
import { PaginatedList, PaginatedTable, Pagination, usePagination } from '../../../shared/components/Pagination';
import { financeApi as api, today, vnd } from '../../finance/services/financeApi';
import {
  PayrollBankDetails, PayrollBatchDialog, PayrollResultDialog, dateText, hasBankDetails, monthText,
  paymentStatus, payrollApiPath, payrollError,
} from '../components/PayrollPaymentDialogs';
import PayrollPaymentHistory from '../components/PayrollPaymentHistory';
import '../../finance/styles/finance.css';
import '../styles/accountant-payroll.css';

const componentNames = {
  BASE: 'Tiền công theo ca', OVERTIME: 'Ngoài giờ ngày thường', REST: 'Làm ngày nghỉ',
  HOLIDAY: 'Làm ngày lễ', NIGHT: 'Phụ cấp đêm', ADDITION: 'Thưởng đã duyệt', DEDUCTION: 'Khấu trừ đã duyệt',
};

const canPay = (row) => !row.paid && Number(row.net_amount) > 0;
const canBatch = (row) => canPay(row) && hasBankDetails(row);
const belongsToBranch = (row, branchId) => !branchId ||
  row.submitted_branch_id === branchId || row.components?.some((component) => component.branch_id === branchId);

export default function PayrollPage() {
  const [searchParams] = useSearchParams();
  const initialMonth = searchParams.get('month') || '';
  const initialBranch = searchParams.get('branchId') || '';
  const [month, setMonth] = useState(() => /^\d{4}-(0[1-9]|1[0-2])$/.test(initialMonth) ? initialMonth : today().slice(0, 7));
  const [branchId, setBranchId] = useState(initialBranch);
  const [historyMonth, setHistoryMonth] = useState(() => today().slice(0, 7));
  const [historyBranch, setHistoryBranch] = useState(initialBranch);
  const [view, setView] = useState('register');
  const [status, setStatus] = useState('');
  const [bank, setBank] = useState('');
  const [search, setSearch] = useState('');
  const [data, setData] = useState(null);
  const [options, setOptions] = useState(null);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);
  const [dialog, setDialog] = useState(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setData(null);
    setSelectedIds([]);
    setDialog(null);
    Promise.all([api.get(`${payrollApiPath}/${month}`), api.get(`${payrollApiPath}/options`)])
      .then(([result, choices]) => {
        if (active) { setData(result); setOptions(choices); }
      })
      .catch((failure) => { if (active) setError(payrollError(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [month, revision]);

  const branches = options?.branches || [];
  const branch = branches.find((item) => item.id === branchId);
  const readyRows = (data?.rows || []).filter((row) => data.status === 'CONFIRMED' || row.submitted_branch_id);
  const branchRows = readyRows.filter((row) => belongsToBranch(row, branchId));
  const banks = [...new Set(branchRows.map((row) => row.bank_name_snapshot?.trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right, 'vi'));
  const query = search.trim().toLocaleLowerCase('vi');
  const rows = branchRows.filter((row) => `${row.code} ${row.name}`.toLocaleLowerCase('vi').includes(query) &&
    (!status || (status === 'paid' ? row.paid : canPay(row))) &&
    (!bank || bank === row.bank_name_snapshot?.trim()));
  const pagination = usePagination(rows, 10, JSON.stringify([month, branchId, status, bank, search]));
  const due = rows.filter(canPay);
  const missingBank = due.filter((row) => !hasBankDetails(row)).length;
  const totalDue = due.reduce((sum, row) => sum + Number(row.net_amount), 0);
  const paid = rows.filter((row) => row.paid);
  const paidTotal = paid.reduce((sum, row) => sum + Number(row.net_amount), 0);
  const eligible = branch ? rows.filter(canBatch) : [];
  const selected = branch ? branchRows.filter((row) => selectedIds.includes(row.employee_id) && canBatch(row)) : [];
  const selectedTotal = selected.reduce((sum, row) => sum + Number(row.net_amount), 0);
  const allEligibleSelected = Boolean(eligible.length) && eligible.every((row) => selectedIds.includes(row.employee_id));
  const pendingBranches = (data?.pendingBranches || []).filter((row) => !branchId || row.branch_id === branchId);
  const advances = (data?.advances || []).filter((row) => !branchId || row.branch_id === branchId);

  function toggleEmployee(id) {
    setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  function toggleAll() {
    const eligibleIds = new Set(eligible.map((row) => row.employee_id));
    setSelectedIds((current) => allEligibleSelected ? current.filter((id) => !eligibleIds.has(id))
      : [...new Set([...current, ...eligibleIds])]);
  }

  function updateFilter(setter, value) {
    setter(value);
    pagination.setPage(0);
  }

  function changeScope(setter, value) {
    updateFilter(setter, value);
    setSelectedIds([]);
    setBank('');
    setDialog(null);
  }

  function recorded(message) {
    setDialog(null);
    setNotice(message);
    setRevision((value) => value + 1);
  }

  async function exportPayroll() {
    setExporting(true);
    setError('');
    try {
      const query = branchId ? `?branchId=${encodeURIComponent(branchId)}` : '';
      await api.download(`${payrollApiPath}/${month}/export${query}`, `Bang-luong-${month}.xlsx`);
    } catch {
      setError('Không thể xuất bảng lương. Vui lòng kiểm tra lại các dòng đã chốt.');
    } finally {
      setExporting(false);
    }
  }

  return <section className="finance-page accountant-payroll">
    <header className="accountant-payroll-heading">
      <h1>Lương nhân viên</h1>
      {view === 'register' && <div className="accountant-payroll-actions">
        <button type="button" disabled={loading || !options} onClick={() => setDialog({ type: 'advance' })}>Ghi nhận ứng lương</button>
        <button type="button" disabled={loading || exporting || !branchRows.length} onClick={exportPayroll}>{exporting ? 'Đang xuất…' : 'Xuất bảng lương'}</button>
      </div>}
    </header>
    <ToastStack notice={notice} onCloseNotice={() => setNotice('')}/>
    <nav className="finance-tabs accountant-payroll-tabs" aria-label="Quản lý lương">
      <button type="button" className={view === 'register' ? 'active' : ''} aria-pressed={view === 'register'} onClick={() => setView('register')}>Bảng lương</button>
      <button type="button" className={view === 'history' ? 'active' : ''} aria-pressed={view === 'history'} onClick={() => setView('history')}>Lịch sử thanh toán</button>
    </nav>
    {view === 'history' ? <PayrollPaymentHistory month={historyMonth} onMonthChange={setHistoryMonth}
      branchId={historyBranch} onBranchChange={setHistoryBranch} branches={branches}/> : <>
      <dl className="accountant-payroll-metrics" aria-label="Tổng hợp bảng lương" aria-busy={loading}>
        <div className="is-due"><dt>Còn phải trả</dt><dd>{data ? vnd(totalDue) : '—'}</dd><dd className="metric-count">{due.length} nhân viên</dd></div>
        <div><dt>Đã thanh toán</dt><dd>{data ? vnd(paidTotal) : '—'}</dd><dd className="metric-count">{paid.length} nhân viên</dd></div>
        <div><dt>Tổng thực nhận</dt><dd>{data ? vnd(totalDue + paidTotal) : '—'}</dd><dd className="metric-count">{rows.length} nhân viên</dd></div>
      </dl>
      <section className="finance-panel accountant-payroll-panel" aria-label="Danh sách bảng lương" aria-busy={loading}>
        <div className="accountant-payroll-section-head">
          <h2>Bảng lương tháng {monthText(month)}</h2>
          <button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Tải lại</button>
        </div>
        <div className="accountant-payroll-filters">
          <label>Tháng lương<input type="month" required value={month}
            onChange={(event) => event.target.value && changeScope(setMonth, event.target.value)}/></label>
          <label>Cơ sở<select value={branchId} onChange={(event) => changeScope(setBranchId, event.target.value)}>
            <option value="">Tất cả cơ sở</option>
            {branches.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select></label>
          <label>Trạng thái<select value={status} onChange={(event) => updateFilter(setStatus, event.target.value)}>
            <option value="">Tất cả</option><option value="unpaid">Chưa thanh toán</option><option value="paid">Đã thanh toán</option>
          </select></label>
          <label>Ngân hàng nhận<select value={bank} onChange={(event) => {
            updateFilter(setBank, event.target.value);
            setSelectedIds([]);
          }}>
            <option value="">Tất cả ngân hàng</option>
            {banks.map((name) => <option key={name}>{name}</option>)}
          </select></label>
          <label>Tìm nhân viên<input type="search" value={search} placeholder="Mã hoặc họ tên"
            onChange={(event) => updateFilter(setSearch, event.target.value)}/></label>
        </div>
        {loading && <p className="accountant-payroll-empty" role="status">Đang tải bảng lương…</p>}
        {error && <p className="finance-error" role="alert">{error}</p>}
        {data && <>
          <div className="accountant-payroll-selection">
            <div className="accountant-payroll-selection-copy">
              {branch ? <>
                <strong aria-live="polite">Đã chọn {selected.length} nhân viên · {vnd(selectedTotal)}</strong>
                {missingBank > 0 && <span className="accountant-payroll-warning">{missingBank} nhân viên thiếu tài khoản nhận</span>}
                {selected.length > 500 && <span role="alert">Mỗi lô tối đa 500 nhân viên.</span>}
              </> : <span>Chọn một cơ sở để lập lô chuyển khoản.</span>}
            </div>
            <div className="accountant-payroll-actions">
              {branch && eligible.length > 0 && <button type="button" onClick={toggleAll}>
                {allEligibleSelected ? 'Bỏ chọn theo bộ lọc' : `Chọn tất cả ${eligible.length} nhân viên`}
              </button>}
              {selected.length > 0 && <button type="button" onClick={() => setSelectedIds([])}>Xóa lựa chọn</button>}
              <button type="button" className="finance-primary" disabled={!selected.length || selected.length > 500}
                onClick={() => setDialog({ type: 'batch', rows: selected })}>Lập lô chuyển khoản</button>
            </div>
          </div>
          <div className="finance-table-wrap accountant-payroll-scroll" role="region" aria-label="Bảng lương chi tiết" tabIndex={0}>
            <table className="accountant-payroll-register">
              <thead><tr>
                {branch && <th scope="col">Chọn</th>}
                <th scope="col">Nhân viên</th><th scope="col">Tài khoản nhận lương</th>
                <th scope="col" className="accountant-payroll-number">Tổng lương</th>
                <th scope="col" className="accountant-payroll-number">Khấu trừ</th>
                <th scope="col" className="accountant-payroll-number">Thực nhận</th>
                <th scope="col">Trạng thái</th><th scope="col">Thao tác</th>
              </tr></thead>
              <tbody>{pagination.items.map((row) => <tr key={row.id} className={selectedIds.includes(row.employee_id) ? 'is-selected' : ''}>
                {branch && <td><input type="checkbox" aria-label={`Chọn ${row.name}`} disabled={!canBatch(row)}
                  checked={selectedIds.includes(row.employee_id)} onChange={() => toggleEmployee(row.employee_id)}/></td>}
                <td><strong>{row.name}</strong><small>{row.code}{row.submitted_branch_name ? ` · ${row.submitted_branch_name}` : ''}</small></td>
                <td><PayrollBankDetails entry={row}/></td>
                <td className="accountant-payroll-number">{vnd(row.gross_amount)}</td>
                <td className="accountant-payroll-number">{vnd(row.deduction_amount)}</td>
                <td className="accountant-payroll-number"><strong>{vnd(row.net_amount)}</strong></td>
                <td><span className={`accountant-payroll-status ${row.paid ? 'is-paid' : canPay(row) ? 'is-pending' : ''}`}>
                  {row.paid ? 'Đã thanh toán' : canPay(row) ? 'Chưa thanh toán' : 'Không còn phải trả'}
                </span></td>
                <td><button type="button" aria-haspopup="dialog" onClick={() => setDialog({ type: 'detail', row })}>Chi tiết</button></td>
              </tr>)}
              {!rows.length && <tr><td colSpan={branch ? 8 : 7} className="accountant-payroll-empty">{readyRows.length
                ? 'Không có nhân viên phù hợp.' : 'Chưa có bảng lương đã chốt trong tháng này.'}</td></tr>}
              </tbody>
            </table>
          </div>
          <Pagination {...pagination} label="Phân trang lương nhân viên" onPageChange={pagination.setPage}/>
        </>}
      </section>
      {pendingBranches.length > 0 && <details className="finance-panel accountant-payroll-secondary">
        <summary>Chờ cơ sở chốt đủ ({pendingBranches.length})</summary>
        <PaginatedList pageSize={10}>{pendingBranches.map((row, index) =>
          <p key={index}>{row.code} · {row.name} — {row.branch_name} đã gửi</p>)}</PaginatedList>
      </details>}
      <details className="finance-panel accountant-payroll-secondary">
        <summary>Lịch sử ứng lương ({advances.length})</summary>
        <div className="finance-table-wrap accountant-payroll-scroll"><PaginatedTable pageSize={5}>
          <thead><tr><th scope="col">Nhân viên</th><th scope="col">Số tiền</th><th scope="col">Trạng thái</th><th scope="col">Tham chiếu</th></tr></thead>
          <tbody>{advances.map((item) => <tr key={item.id}>
            <td>{item.code} · {item.name}</td><td>{vnd(item.amount)}</td><td>{paymentStatus[item.status]}</td><td>{item.external_reference || '—'}</td>
          </tr>)}
          {!advances.length && <tr><td colSpan="4" className="accountant-payroll-empty">Chưa có ứng lương.</td></tr>}</tbody>
        </PaginatedTable></div>
      </details>
    </>}
    {dialog?.type === 'batch' && <PayrollBatchDialog month={month} branch={branch} rows={dialog.rows} version={data.version}
      onClose={() => setDialog(null)} onRecorded={({ paymentDate, count, amount }) => {
        setHistoryMonth(paymentDate.slice(0, 7));
        setHistoryBranch(branchId);
        setView('history');
        recorded(`Đã ghi nhận lô ${count} nhân viên · ${vnd(amount)}.`);
      }}/>}
    {(dialog?.type === 'advance' || dialog?.type === 'individual') && <PayrollResultDialog
      entry={dialog.row} employees={options?.employees || []} month={month} version={data?.version}
      onClose={() => setDialog(null)} onRecorded={recorded}/>}
    {dialog?.type === 'detail' && <PayrollEntryDetail entry={dialog.row} month={month} onClose={() => setDialog(null)}
      onRecord={() => setDialog({ type: 'individual', row: dialog.row })}/>}
  </section>;
}

function PayrollEntryDetail({ entry, month, onClose, onRecord }) {
  const [error, setError] = useState('');
  async function downloadProof(item) {
    setError('');
    try {
      await api.download(`${payrollApiPath}/payments/${item.id}/evidence`, item.evidence_name);
    } catch {
      setError('Không tải được chứng từ trả lương. Vui lòng thử lại.');
    }
  }

  return <Modal open title="Chi tiết lương" className="accountant-payroll-modal" onClose={onClose}
    footer={<>
      <button type="button" onClick={onClose}>Đóng</button>
      {canPay(entry) && <button type="button" className="finance-primary" onClick={onRecord}>Ghi nhận kết quả từng người</button>}
    </>}>
    <div className="accountant-payroll-dialog-summary">
      <div><strong>{entry.name}</strong><span>{entry.code} · Tháng lương {monthText(month)}</span></div>
      <strong className="accountant-payroll-money">{vnd(entry.net_amount)}</strong>
    </div>
    <PayrollBankDetails entry={entry}/>
    <PaginatedList pageSize={10}>{(entry.components || []).map((component, index) => <div className="finance-line" key={index}>
      <span>{componentNames[component.reason] || componentNames[component.kind]}
        {component.branch_name && <small>{component.branch_name}</small>}
        {['ADDITION', 'DEDUCTION'].includes(component.kind) && <small>{component.reason}</small>}
      </span><strong>{vnd(component.amount)}</strong>
    </div>)}</PaginatedList>
    <div className="finance-line"><span>Ứng đã trả</span><strong>{vnd(entry.advances)}</strong></div>
    {error && <p className="finance-error" role="alert">{error}</p>}
    <PaginatedList pageSize={5}>{(entry.payments || []).map((item) => <div className="finance-line" key={item.id}>
      <span>{paymentStatus[item.status]}{item.payment_date && ` · ${dateText(item.payment_date)}`}
        {item.external_reference && <small>{item.external_reference}</small>}
        {item.evidence_name && <button type="button" onClick={() => downloadProof(item)}>Tải chứng từ</button>}</span>
      <strong>{vnd(item.amount)}</strong>
    </div>)}</PaginatedList>
  </Modal>;
}
