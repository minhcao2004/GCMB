import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import './dashboard.css';

const now = new Date();
const monthNow = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;
const label = { report: 'Hiệu quả tháng', expenses: 'Chi phí', periods: 'Khóa kỳ', transactions: 'Giao dịch', export: 'Xuất dữ liệu' };
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.';
const parseAdjustments = (value) => { try { return JSON.parse(value || '[]'); } catch { return []; } };

export default function AccountantFinancePage({ reportsOnly = false }) {
  const navigate = useNavigate();
  const [tab, setTab] = useState('report');
  const [month, setMonth] = useState(monthNow);
  const [data, setData] = useState(null);
  const [branchOptions, setBranchOptions] = useState([]);
  const [branchId, setBranchId] = useState('');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [user, setUser] = useState(null);
  const [scope, setScope] = useState('ALL');
  const [adjusting, setAdjusting] = useState('');
  const [adjustmentConfirmed, setAdjustmentConfirmed] = useState(false);
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');

  useEffect(() => {
    authApi.me().then((identity) => { if (identity.role !== 'ACCOUNTANT' && !(reportsOnly && identity.role === 'HEAD_OFFICE_MANAGER')) navigate(identity.destination, { replace: true }); else setUser(identity); })
      .catch(() => navigate('/dang-nhap', { replace: true }));
  }, [navigate]);

  useEffect(() => {
    let live = true;
    setError(''); setData(null); setRows([]); setLoading(true);
    const path = tab === 'report' ? '/accounting/reports/monthly'
      : tab === 'expenses' ? '/accounting/reports/expense-breakdown'
        : tab === 'periods' ? '/accounting/financial-periods'
          : tab === 'transactions' ? '/accounting/transactions' : null;
    if (!path) { setLoading(false); return undefined; }
    const config = tab === 'periods' ? undefined : { params: { month, ...(['report', 'expenses'].includes(tab) && branchId ? { branchId } : {}) } };
    api.get(path, config).then(({ data: result }) => { if (live) { tab === 'periods' ? setRows(result) : setData(result); if (tab === 'report' && !branchId) setBranchOptions(result.results); } })
      .catch((e) => { if (live) setError(errorText(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [tab, month, branchId]);

  async function closePeriod(periodMonth) {
    setBusy(true); setError('');
    try { await api.post(`/accounting/financial-periods/${periodMonth}/close`, { confirmed: true }); setRows((list) => list.map((row) => row.month === `${periodMonth}-01` || row.month === periodMonth ? { ...row, status: 'CLOSED' } : row)); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  // BR-100/101: xác nhận điều chỉnh được ghi riêng, liên kết giao dịch gốc và không sửa giao dịch gốc.
  async function submitAdjustment(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await api.post(`/accounting/transactions/${adjusting}/adjustments`, { amountSigned: amount, reason });
      setAdjusting(''); setAmount(''); setReason(''); setAdjustmentConfirmed(false);
      const { data: result } = await api.get('/accounting/transactions', { params: { month } }); setRows(result);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function download() {
    setBusy(true); setError('');
    try {
      const response = await api.get('/accounting/tax-data.csv', { params: { month, scope }, responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `gcmb-accounting-${month}.csv`; anchor.click(); URL.revokeObjectURL(url);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function logout() {
    setLogoutBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (e) { setError(errorText(e)); }
    finally { setLogoutBusy(false); }
  }

  const content = <section className="performance-dashboard accountant-finance" aria-labelledby="finance-title">
    <header className="performance-heading"><div><h1 id="finance-title">Tài chính &amp; báo cáo</h1><p>Đối soát sổ giao dịch, theo dõi kết quả và khóa kỳ tài chính.</p></div>
      {tab !== 'periods' && <div className="finance-filters"><label className="finance-month">Tháng<input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} /></label>{['report', 'expenses'].includes(tab) && <label className="finance-month">Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>}</div>}
    </header>
    <nav className="finance-tabs" aria-label="Nghiệp vụ tài chính">{Object.entries(label).filter(([key]) => !reportsOnly || ['report', 'expenses'].includes(key)).map(([key, value]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => { setTab(key); setError(''); }}>{value}</button>)}</nav>
    {!reportsOnly && <p className="finance-shortcuts"><Link to="/ke-toan/hoan-coc">Yêu cầu hoàn cọc</Link><Link to="/ke-toan/hoa-don-thue">Hóa đơn thuế</Link></p>}
    {error && <p className="performance-state performance-error" role="alert">{error}</p>}
    {loading && <p className="performance-state" role="status">Đang tải dữ liệu…</p>}
    {tab === 'report' && data && <>
      <div className="performance-metrics"><div className="performance-metric"><span>Doanh thu dịch vụ</span><strong>{money(data.totals.revenue)}</strong></div><div className="performance-metric"><span>Chi vận hành</span><strong>{money(data.totals.expenses)}</strong></div><div className="performance-metric"><span>Lợi nhuận vận hành</span><strong>{money(data.totals.profit)}</strong></div><div className="performance-metric"><span>Dòng tiền thuần</span><strong>{money(data.totals.netCashFlow)}</strong></div></div>
      <DataTable columns={['Cơ sở', 'Bill hoàn tất', 'Doanh thu', 'Chi vận hành', 'Trong đó · lương/ứng', 'Đầu tư', 'Lợi nhuận', 'Biên lợi nhuận', 'Dòng tiền thuần']} rows={data.results} values={(r) => [r.name, r.billCount, money(r.revenue), money(r.expenses), money(r.salary), money(r.investment), money(r.profit), percent(r.margin), money(r.netCashFlow)]} />
    </>}
    {tab === 'expenses' && data && <DataTable columns={['Loại', 'Danh mục', 'Cơ sở', 'Đã thanh toán', 'Số lần chi']} rows={data.rows} values={(r) => [r.class === 'CAPEX' ? 'Đầu tư' : 'Vận hành', r.categoryName, r.branchName, money(r.amount), r.paymentCount]} />}
    {!reportsOnly && tab === 'periods' && <DataTable columns={['Kỳ', 'Trạng thái', 'Chốt tiền chưa đối soát', 'Người chốt', '']} rows={rows} values={(r) => [String(r.month).slice(0, 7), r.status === 'CLOSED' ? 'Đã khóa' : 'Đang mở', r.unreconciledClosings, r.closedBy || '—', r.status === 'OPEN' ? <button disabled={busy || Number(r.unreconciledClosings) > 0} onClick={() => { if (window.confirm(`Khóa kỳ ${String(r.month).slice(0, 7)}?`)) closePeriod(String(r.month).slice(0, 7)); }}>Khóa kỳ</button> : ''] } />}
    {!reportsOnly && tab === 'transactions' && <>
      <DataTable columns={['Ngày ghi sổ', 'Loại', 'Lý do', 'Số tiền', 'Điều chỉnh đã ghi', '']} rows={rows} values={(r) => [r.bookingDate, r.kind, r.reason, money(r.amount), r.adjustmentCount ? <details><summary>{r.adjustmentCount} lần</summary><ul>{parseAdjustments(r.adjustments).map((item, i) => <li key={`${r.id}-${i}`}>{money(item.amount)} · {item.reason} · {item.createdBy || 'Hệ thống'} · {item.createdAt}</li>)}</ul></details> : '—', r.kind !== 'TRANSFER' && r.kind !== 'OPENING' && r.kind !== 'ADJUSTMENT' ? <button type="button" onClick={() => { setAdjusting(r.id); setAmount(''); setReason(''); setAdjustmentConfirmed(false); }}>Điều chỉnh</button> : '']} />
      {adjusting && <form className="finance-adjust-form" onSubmit={submitAdjustment}><h2>Điều chỉnh giao dịch</h2><p>Giao dịch gốc: {adjusting}. Số tiền dương ghi tăng quỹ; số âm ghi giảm quỹ.</p><label>Số tiền điều chỉnh (VND)<input type="number" step="1" value={amount} onChange={(e) => setAmount(e.target.value)} required /></label><label>Lý do<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required /></label><p>Điều chỉnh mới: {amount ? money(Number(amount)) : '—'} · {reason || 'Chưa có lý do'}</p><label className="finance-confirm"><input type="checkbox" checked={adjustmentConfirmed} onChange={(e) => setAdjustmentConfirmed(e.target.checked)} /> Tôi đã kiểm tra điều chỉnh sẽ được ghi riêng, không sửa giao dịch gốc.</label><button type="submit" disabled={busy || !adjustmentConfirmed}>Xác nhận ghi điều chỉnh</button><button type="button" disabled={busy} onClick={() => setAdjusting('')}>Hủy</button></form>}
    </>}
    {!reportsOnly && tab === 'export' && <section className="finance-export"><h2>Xuất dữ liệu kế toán</h2><p>Tệp CSV gồm các dòng sổ tiền trong kỳ đã chọn. Việc xuất không thay đổi giao dịch.</p><label>Phạm vi dữ liệu<select value={scope} onChange={(e) => setScope(e.target.value)}><option value="ALL">Tất cả dòng tiền</option><option value="INFLOW">Tiền vào</option><option value="OUTFLOW">Tiền ra</option></select></label><button type="button" disabled={busy} onClick={download}>{busy ? 'Đang tạo tệp…' : 'Tải CSV'}</button></section>}
  </section>;
  return reportsOnly ? content : <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout}
    breadcrumb="Tài chính & báo cáo" activeTab="overview" audience="accountant" hideSearch>{content}</AppShell>;
}

function DataTable({ columns, rows, values }) {
  return <div className="performance-panel finance-table-panel"><div className="performance-table-wrap"><table className="performance-table"><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id || `${index}-${row.categoryName || row.month || ''}`}>{values(row).map((value, cell) => <td key={cell}>{value}</td>)}</tr>)}</tbody></table></div>{rows.length === 0 && <p className="performance-empty">Chưa có dữ liệu trong phạm vi đã chọn.</p>}</div>;
}
