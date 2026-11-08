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
const formatMonth = (value) => `${value.slice(5, 7)}/${value.slice(0, 4)}`;
const label = { report: 'Tổng quan', expenses: 'Chi phí', periods: 'Chốt kỳ', transactions: 'Giao dịch', export: 'Xuất dữ liệu' };
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
  const [loadedAt, setLoadedAt] = useState(null);
  const [periodInfo, setPeriodInfo] = useState(null);
  const [periodLoading, setPeriodLoading] = useState(false);
  const [periodError, setPeriodError] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    authApi.me().then((identity) => { if (identity.role !== 'ACCOUNTANT' && !(reportsOnly && identity.role === 'HEAD_OFFICE_MANAGER')) navigate(identity.destination, { replace: true }); else setUser(identity); })
      .catch(() => navigate('/dang-nhap', { replace: true }));
  }, [navigate, reportsOnly]);

  useEffect(() => {
    let live = true;
    setError(''); setData(null); setRows([]); setLoadedAt(null); setLoading(true);
    const path = tab === 'report' ? '/accounting/reports/monthly'
      : tab === 'expenses' ? '/accounting/reports/expense-breakdown'
        : tab === 'periods' ? '/accounting/financial-periods'
          : tab === 'transactions' ? '/accounting/transactions' : null;
    if (!path) { setLoading(false); return undefined; }
    const config = tab === 'periods' ? undefined : { params: { month, ...(['report', 'expenses'].includes(tab) && branchId ? { branchId } : {}) } };
    api.get(path, config).then(({ data: result }) => { if (live) { tab === 'periods' ? setRows(result) : setData(result); setLoadedAt(new Date()); if (tab === 'report' && !branchId) setBranchOptions(result.results); } })
      .catch((e) => { if (live) setError(errorText(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [tab, month, branchId, revision]);

  useEffect(() => {
    if (reportsOnly || user?.role !== 'ACCOUNTANT' || tab !== 'report') return undefined;
    let live = true;
    setPeriodInfo(null); setPeriodError(false); setPeriodLoading(true);
    api.get('/accounting/financial-periods')
      .then(({ data: periods }) => { if (live) setPeriodInfo(periods.find((period) => String(period.month).slice(0, 7) === month) || null); })
      .catch(() => { if (live) setPeriodError(true); })
      .finally(() => { if (live) setPeriodLoading(false); });
    return () => { live = false; };
  }, [reportsOnly, user?.role, tab, month, revision]);

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
    <header className="finance-heading">
      <div><h1 id="finance-title">Tài chính &amp; báo cáo</h1><p>Kiểm tra số liệu trong kỳ, theo dõi chốt quỹ và xử lý giao dịch.</p></div>
      {tab !== 'periods' && <div className="finance-filters">
        <label className="finance-month">Kỳ báo cáo<input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} /></label>
        {['report', 'expenses'].includes(tab) && <label className="finance-month">Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>}
      </div>}
    </header>
    <nav className="finance-tabs" aria-label="Nghiệp vụ tài chính">{Object.entries(label).filter(([key]) => !reportsOnly || ['report', 'expenses'].includes(key)).map(([key, value]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => { setTab(key); setError(''); }}>{value}</button>)}</nav>
    {error && <p className="finance-state finance-error" role="alert">{error}</p>}
    {loading && <p className="finance-state" role="status">Đang tải dữ liệu…</p>}
    {tab === 'report' && data && <>
      {!reportsOnly && <section className={`finance-period${periodInfo?.status === 'CLOSED' ? ' is-closed' : Number(periodInfo?.unreconciledClosings) > 0 ? ' is-blocked' : ''}`} aria-labelledby="finance-period-title">
        <div>
          <h2 id="finance-period-title">Tình trạng kỳ {formatMonth(month)}</h2>
          {(!user || periodLoading) && <p role="status">Đang kiểm tra tình trạng chốt kỳ…</p>}
          {periodError && <p>Không tải được tình trạng chốt kỳ. Hãy tải lại dữ liệu.</p>}
          {user && !periodLoading && !periodError && !periodInfo && <p>Chưa có thông tin chốt kỳ cho tháng này.</p>}
          {/* BR-99: chỉ cho chốt kỳ khi mọi phiếu chốt quỹ trong kỳ đã đối soát. */}
          {user && !periodLoading && !periodError && periodInfo && <p>{periodInfo.status === 'CLOSED'
            ? 'Kỳ tài chính đã chốt.'
            : Number(periodInfo.unreconciledClosings) > 0
              ? `Chưa thể chốt kỳ tài chính vì còn ${periodInfo.unreconciledClosings} phiếu chốt quỹ chưa đối soát. Vui lòng hoàn tất đối soát tất cả phiếu trong kỳ.`
              : 'Kỳ chưa chốt · Không còn phiếu chốt quỹ chưa đối soát được ghi nhận.'}</p>}
        </div>
        <button type="button" onClick={() => setTab('periods')}>Xem kỳ tài chính</button>
      </section>}
      <div className="finance-section-heading">
        <div><h2>Kết quả tháng {formatMonth(month)}</h2><p>{branchId ? 'Cơ sở đang chọn' : `Toàn bộ ${data.results.length} cơ sở`} · Đơn vị: đồng (VND)</p></div>
        <div className="finance-freshness"><span>{loadedAt ? `Tải trên màn hình lúc ${loadedAt.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })}` : ''}</span><button type="button" onClick={() => setRevision((value) => value + 1)}>Tải lại</button></div>
      </div>
      {/* BR-102/211, BR-103/107, BR-104, BR-106: hiển thị đúng nguồn của từng chỉ số; dòng tiền thuần không phải số dư quỹ. */}
      <dl className="finance-metrics">
        <div><dt>Doanh thu dịch vụ</dt><dd>{money(data.totals.revenue)}</dd><small>Bill đã hoàn tất; không gồm cọc chưa sử dụng</small></div>
        <div><dt>Chi vận hành đã trả</dt><dd>{money(data.totals.expenses)}</dd><small>Gồm lương và ứng lương; không gồm đầu tư</small></div>
        <div><dt>Lợi nhuận vận hành</dt><dd className={Number(data.totals.profit) < 0 ? 'finance-negative' : ''}>{money(data.totals.profit)}</dd><small>Doanh thu trừ chi vận hành đã trả</small></div>
        <div><dt>Dòng tiền thuần</dt><dd className={Number(data.totals.netCashFlow) < 0 ? 'finance-negative' : ''}>{money(data.totals.netCashFlow)}</dd><small>Tiền vào trừ tiền ra theo sổ; không phải số dư quỹ</small></div>
      </dl>
      <DataTable caption={`Kết quả của ${data.results.length} cơ sở trong tháng ${formatMonth(month)}`} columns={['Cơ sở', 'Bill hoàn tất', 'Doanh thu', 'Chi vận hành', 'Trong đó · lương/ứng', 'Đầu tư', 'Lợi nhuận', 'Biên lợi nhuận', 'Dòng tiền thuần']} numericColumns={[1, 2, 3, 4, 5, 6, 7, 8]} rows={data.results} values={(r) => [r.name, r.billCount, money(r.revenue), money(r.expenses), money(r.salary), money(r.investment), money(r.profit), percent(r.margin), money(r.netCashFlow)]} />
    </>}
    {!reportsOnly && tab === 'report' && <p className="finance-shortcuts"><span>Nghiệp vụ liên quan</span><Link to="/ke-toan/hoan-coc">Yêu cầu hoàn cọc</Link><Link to="/ke-toan/hoa-don-thue">Hóa đơn thuế</Link></p>}
    {tab === 'expenses' && data && <DataTable caption={`Chi phí đã thanh toán trong tháng ${formatMonth(month)}`} columns={['Loại', 'Danh mục', 'Cơ sở', 'Đã thanh toán', 'Số lần chi']} numericColumns={[3, 4]} rows={data.rows} values={(r) => [r.class === 'CAPEX' ? 'Đầu tư' : 'Vận hành', r.categoryName, r.branchName, money(r.amount), r.paymentCount]} />}
    {!reportsOnly && tab === 'periods' && !loading && !error && <DataTable caption="Tình trạng chốt kỳ tài chính" columns={['Kỳ', 'Trạng thái', 'Phiếu chốt quỹ chưa đối soát', 'Người chốt', 'Thao tác']} numericColumns={[2]} rows={rows} values={(r) => [formatMonth(String(r.month).slice(0, 7)), r.status === 'CLOSED' ? 'Đã chốt' : 'Chưa chốt', r.unreconciledClosings, r.closedBy || '—', r.status === 'OPEN' ? <button type="button" disabled={busy || Number(r.unreconciledClosings) > 0} title={Number(r.unreconciledClosings) > 0 ? `Còn ${r.unreconciledClosings} phiếu chốt quỹ chưa đối soát` : undefined} onClick={() => { if (window.confirm(`Chốt kỳ tài chính ${formatMonth(String(r.month).slice(0, 7))}?`)) closePeriod(String(r.month).slice(0, 7)); }}>Chốt kỳ</button> : '—'] } />}
    {!reportsOnly && tab === 'transactions' && !loading && !error && <>
      <DataTable caption={`Giao dịch tháng ${formatMonth(month)}`} columns={['Ngày ghi sổ', 'Loại', 'Lý do', 'Số tiền', 'Điều chỉnh đã ghi', 'Thao tác']} numericColumns={[3]} rows={rows} values={(r) => [r.bookingDate, r.kind, r.reason, money(r.amount), r.adjustmentCount ? <details><summary>{r.adjustmentCount} lần</summary><ul>{parseAdjustments(r.adjustments).map((item, i) => <li key={`${r.id}-${i}`}>{money(item.amount)} · {item.reason} · {item.createdBy || 'Hệ thống'} · {item.createdAt}</li>)}</ul></details> : '—', r.kind !== 'TRANSFER' && r.kind !== 'OPENING' && r.kind !== 'ADJUSTMENT' ? <button type="button" onClick={() => { setAdjusting(r.id); setAmount(''); setReason(''); setAdjustmentConfirmed(false); }}>Điều chỉnh</button> : '—']} />
      {adjusting && <form className="finance-adjust-form" onSubmit={submitAdjustment}><h2>Điều chỉnh giao dịch</h2><p>Giao dịch gốc: {adjusting}. Số tiền dương ghi tăng quỹ; số âm ghi giảm quỹ.</p><label>Số tiền điều chỉnh (VND)<input type="number" step="1" value={amount} onChange={(e) => setAmount(e.target.value)} required /></label><label>Lý do<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required /></label><p>Điều chỉnh mới: {amount ? money(Number(amount)) : '—'} · {reason || 'Chưa có lý do'}</p><label className="finance-confirm"><input type="checkbox" checked={adjustmentConfirmed} onChange={(e) => setAdjustmentConfirmed(e.target.checked)} /> Tôi đã kiểm tra điều chỉnh sẽ được ghi riêng, không sửa giao dịch gốc.</label><button type="submit" disabled={busy || !adjustmentConfirmed}>Xác nhận ghi điều chỉnh</button><button type="button" disabled={busy} onClick={() => setAdjusting('')}>Hủy</button></form>}
    </>}
    {!reportsOnly && tab === 'export' && <section className="finance-export"><h2>Xuất dữ liệu kế toán</h2><p>Tệp CSV gồm các dòng sổ tiền trong kỳ đã chọn. Việc xuất không thay đổi giao dịch.</p><label>Phạm vi dữ liệu<select value={scope} onChange={(e) => setScope(e.target.value)}><option value="ALL">Tất cả dòng tiền</option><option value="INFLOW">Tiền vào</option><option value="OUTFLOW">Tiền ra</option></select></label><button type="button" disabled={busy} onClick={download}>{busy ? 'Đang tạo tệp…' : 'Tải CSV'}</button></section>}
  </section>;
  return reportsOnly ? content : <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout}
    breadcrumb="Tài chính & báo cáo" activeTab="overview" audience="accountant" hideSearch showCurrentMonth={false}>{content}</AppShell>;
}

function DataTable({ caption, columns, numericColumns = [], rows, values }) {
  return <div className="finance-table-panel"><div className="finance-table-wrap"><table className="finance-table"><caption>{caption}</caption><thead><tr>{columns.map((column, cell) => <th scope="col" className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id || `${index}-${row.categoryName || row.month || ''}`}>{values(row).map((value, cell) => <td className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={cell}>{value}</td>)}</tr>)}</tbody></table></div>{rows.length === 0 && <p className="finance-empty">Chưa có dữ liệu trong phạm vi đã chọn.</p>}</div>;
}
