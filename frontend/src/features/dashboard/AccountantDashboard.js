import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import './accountantDashboard.css';

const monthNow = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không tải được dữ liệu kế toán. Vui lòng thử lại.';
const dateVi = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).format(date); };
const statusVi = (value) => ({
  UNRESOLVED: 'Chưa xử lý', RESOLVED: 'Đã xử lý', REQUESTED: 'Đang chờ xử lý', APPROVED: 'Đã duyệt', PROCESSING: 'Đang xử lý',
  WAITING_ACCOUNTANT: 'Chờ kế toán xử lý', WAITING_CONFIRMATION: 'Chờ xác nhận', PAID: 'Đã thanh toán', REJECTED: 'Đã từ chối',
  OPEN: 'Đang mở', CLOSED: 'Đã khóa', RECONCILED: 'Đã đối soát', NEEDS_EXPLANATION: 'Cần giải trình', FAILED: 'Không thành công', EXCEPTION: 'Ngoại lệ', PENDING: 'Đang chờ xử lý', CONFIRMED: 'Đã xác nhận',
})[value] || (value ? 'Chưa xác định' : '—');

function DataTable({ title, columns, rows, renderRow }) {
  return <section className="accountant-dash-panel accountant-table-panel"><div className="accountant-panel-heading"><h2>{title}</h2><span>{rows.length} mục</span></div>{rows.length ? <div className="accountant-table-scroll"><table><thead><tr>{columns.map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{rows.map(renderRow)}</tbody></table></div> : <p className="accountant-dash-empty">Không có dữ liệu trong kỳ đã chọn.</p>}</section>;
}

function LiveProfitChart({ rows }) {
  const max = Math.max(1, ...rows.flatMap((row) => [Number(row.revenue) || 0, Number(row.expenses) || 0]));
  const profits = rows.map((row) => Number(row.profit) || 0);
  const maxProfit = Math.max(1, ...profits.map((value) => Math.max(0, value)));
  const maxLoss = Math.max(1, ...profits.map((value) => Math.abs(Math.min(0, value))));
  const baseline = 165;
  const points = rows.map((row, index) => {
    const profit = Number(row.profit) || 0;
    return { x: 70 + index * 180, y: profit >= 0 ? baseline - profit / maxProfit * 95 : baseline + Math.abs(profit) / maxLoss * 38, profit };
  });
  return <div className="accountant-live-chart-scroll"><svg viewBox={`0 0 ${Math.max(540, rows.length * 180 + 80)} 240`} className="accountant-live-chart-svg" role="img" aria-label="Thu và chi theo cơ sở; điểm xanh là lãi, điểm đỏ là lỗ">
    {[40, 80, 120, 160].map((y) => <line key={y} x1="45" x2={Math.max(520, rows.length * 180 + 60)} y1={y} y2={y} />)}
    <line className="accountant-live-zero-line" x1="45" x2={Math.max(520, rows.length * 180 + 60)} y1={baseline} y2={baseline} />
    {rows.map((row, index) => { const x = 65 + index * 180; const rev = Number(row.revenue) || 0; const cost = Number(row.expenses) || 0; return <g key={row.id}><rect className="is-revenue" x={x} y={baseline - rev / max * 125} width="32" height={rev / max * 125} rx="3" /><rect className="is-expenses" x={x + 38} y={baseline - cost / max * 125} width="32" height={cost / max * 125} rx="3" /><text x={x + 35} y="226">{row.name}</text></g>; })}
    {points.slice(1).map((point, index) => <path key={`profit-${rows[index + 1].id}`} className={`accountant-live-profit-segment ${point.profit > 0 ? 'is-profit' : point.profit < 0 ? 'is-loss' : 'is-even'}`} d={`M ${points[index].x} ${points[index].y} L ${point.x} ${point.y}`} />)}
    {points.map((point, index) => <circle key={rows[index].id} className={`accountant-live-profit-dot ${point.profit > 0 ? 'is-profit' : point.profit < 0 ? 'is-loss' : 'is-even'}`} cx={point.x} cy={point.y} r="4" />)}
  </svg></div>;
}

export default function AccountantDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [month, setMonth] = useState(monthNow);
  const [branchId, setBranchId] = useState('');
  const [branches, setBranches] = useState([]);
  const [report, setReport] = useState(null);
  const [refunds, setRefunds] = useState([]);
  const [requests, setRequests] = useState([]);
  const [fundBalance, setFundBalance] = useState(null);
  const [closings, setClosings] = useState([]);
  const [payroll, setPayroll] = useState(null);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [sepayExceptions, setSepayExceptions] = useState([]);
  const [periods, setPeriods] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [exceptionFilter, setExceptionFilter] = useState('UNRESOLVED');
  const [resolutionTarget, setResolutionTarget] = useState(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolutionBusy, setResolutionBusy] = useState(false);

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'ACCOUNTANT') navigate(identity.destination, { replace: true });
      else setUser(identity);
    }).catch(() => { if (active) navigate('/dang-nhap', { replace: true }); });
    return () => { active = false; };
  }, [navigate]);

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const params = { month, ...(branchId ? { branchId } : {}) };
    const from = `${month}-01`;
    const end = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).toISOString().slice(0, 10);
    Promise.allSettled([
      api.get('/accounting/reports/monthly', { params }),
      api.get('/accounting/deposits/refund-requests'),
      api.get('/accounting/finance'),
      api.get('/accounting/cash-closing', { params: { from, to: end, ...(branchId ? { branchId } : {}) } }),
      api.get(`/accounting/payroll/${month}`),
      api.get('/accounting/financial-periods'),
      api.get('/accounting/dashboard-details', { params }),
    ]).then((results) => {
      if (!active) return;
      const [summary, refund, finance, cash, payrollResult, periodResult, details] = results;
      if (summary.status === 'fulfilled') {
        setReport(summary.value.data);
        const list = summary.value.data.results || [];
        setBranches((current) => current.length ? current : list.map((row) => ({ id: row.id, name: row.name })));
      } else { setReport(null); setError(errorText(summary.reason)); }
      setRefunds(refund.status === 'fulfilled' ? refund.value.data.items || [] : []);
      setRequests(finance.status === 'fulfilled' ? finance.value.data.requests || [] : []);
      setFundBalance(finance.status === 'fulfilled' ? finance.value.data.balance : null);
      setClosings(cash.status === 'fulfilled' ? cash.value.data || [] : []);
      setPayroll(payrollResult.status === 'fulfilled' ? payrollResult.value.data : null);
      setPeriods(periodResult.status === 'fulfilled' ? periodResult.value.data || [] : []);
      setPaymentMethods(details.status === 'fulfilled' ? details.value.data.paymentMethods || [] : []);
      setSepayExceptions(details.status === 'fulfilled' ? details.value.data.sepayExceptions || [] : []);
      if (results.slice(1).some((item) => item.status === 'rejected')) setError((current) => current || 'Một số bảng nghiệp vụ không tải được; số liệu bị thiếu được để trống.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [month, branchId]);

  const totals = report?.totals || {};
  const rows = report?.results || [];
  const refundRows = refunds.filter((row) => ['REQUESTED', 'APPROVED', 'PROCESSING'].includes(row.status));
  const expenseRequests = requests.filter((row) => ['WAITING_ACCOUNTANT', 'WAITING_CONFIRMATION'].includes(row.status));
  const selectedRefunds = branchId ? refundRows.filter((row) => row.branchId === branchId || row.branchName === branches.find((item) => item.id === branchId)?.name) : refundRows;
  const selectedRequests = branchId ? expenseRequests.filter((row) => row.branchId === branchId || row.branchName === branches.find((item) => item.id === branchId)?.name) : expenseRequests;
  const refundAmount = selectedRefunds.reduce((sum, row) => sum + Number(row.amount || row.refundAmount || 0), 0);
  const pendingAmount = selectedRequests.reduce((sum, row) => sum + Number(row.amount || 0), 0);
  const payrollRows = payroll?.rows || payroll?.entries || [];
  const payrollTotal = payrollRows.reduce((sum, row) => sum + Number(row.netAmount || row.net_amount || 0), 0);
  const payrollPaid = payrollRows.filter((row) => row.paid).reduce((sum, row) => sum + Number(row.netAmount || row.net_amount || 0), 0);
  const currentPeriod = periods.find((row) => String(row.month).slice(0, 7) === month);
  const exceptions = sepayExceptions;
  const visibleExceptions = exceptions.filter((row) => exceptionFilter === 'ALL' || row.resolutionStatus === exceptionFilter);
  const fundByBranch = [...closings].sort((a, b) => String(b.businessDate || b.business_date || '').localeCompare(String(a.businessDate || a.business_date || '')))
    .reduce((map, row) => { const name = row.branchName || 'Cơ sở'; if (!map.has(name)) map.set(name, Number(row.actual ?? row.actual_closing) || 0); return map; }, new Map());
  const fundMax = Math.max(1, ...fundByBranch.values());
  const paymentTotal = paymentMethods.reduce((sum, item) => sum + Number(item.value || 0), 0);
  let paymentOffset = 0;
  const methodParts = paymentMethods.map((item, index) => {
    const start = paymentOffset;
    paymentOffset += paymentTotal ? Number(item.value || 0) / paymentTotal * 100 : 0;
    return `${['#168b87', '#6a3ee8', '#df9b28'][index % 3]} ${start}% ${paymentOffset}%`;
  }).join(', ');
  const branchLabel = branchId ? branches.find((item) => item.id === branchId)?.name || 'Cơ sở đang chọn' : 'Toàn chuỗi';

  async function logout() {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch { setError('Không thể đăng xuất. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  async function resolveSePayException() {
    if (!resolutionTarget || !resolutionNote.trim()) return;
    setResolutionBusy(true); setError('');
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      const { data: resolved } = await api.post(`/accounting/sepay-exceptions/${resolutionTarget.id}/resolve`,
        { resolutionNote }, { headers: { [csrf.headerName]: csrf.token } });
      setSepayExceptions((current) => current.map((item) => item.id === resolved.id ? { ...item, ...resolved } : item));
      setResolutionTarget(null); setResolutionNote('');
    } catch (e) { setError(errorText(e)); }
    finally { setResolutionBusy(false); }
  }

  return <AppShell user={user} branchName="toàn chuỗi" busy={busy} onLogout={logout} breadcrumb="Tổng quan kế toán" activeTab="overview" audience="accountant" hideSearch showCurrentMonth={false}>
    <main className="accountant-dash" aria-labelledby="accountant-dash-title">
      <header className="accountant-dash-header"><div><h1 id="accountant-dash-title">Tổng quan tài chính</h1></div><div className="accountant-dash-filters"><label>Kỳ tài chính<input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label><label>Cơ sở<select value={branchId} onChange={(event) => setBranchId(event.target.value)}><option value="">Tất cả cơ sở</option>{branches.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label></div></header>
      <p className="accountant-dash-demo-note">{month.slice(5, 7)}/{month.slice(0, 4)} · {branchLabel}</p>
      {error && <p className="accountant-dash-error" role="alert">{error}</p>}{loading && <p role="status">Đang tải số liệu…</p>}
      {/* BR-211: Thu nhập vận hành gồm doanh thu hóa đơn hoàn tất và cọc bị tịch thu. */}
      <section className="accountant-kpis" aria-label="Chỉ số tài chính trong kỳ">{[
        ['Thu nhập vận hành', totals.revenue, 'Hóa đơn hoàn tất + cọc bị tịch thu'], ['Tổng chi', totals.expenses, 'Chi phí đã trả và lương'], ['Lợi nhuận', totals.profit, 'Theo báo cáo kỳ'], ['Số dư quỹ', fundBalance, 'Số dư quỹ hiện tại'], ['Hoàn cọc chờ xử lý', refundAmount, `${selectedRefunds.length} yêu cầu`], ['Chi phí chờ chi trả', pendingAmount, `${selectedRequests.length} yêu cầu`],
      ].map(([label, value, note]) => {
        const profitKpi = label === 'Lợi nhuận' && report;
        const profitChange = report?.changes?.profit;
        const profitTone = !profitKpi ? '' : Number(value) > 0 ? 'is-profit' : Number(value) < 0 ? 'is-loss' : 'is-even';
        const changeTone = profitChange == null ? '' : profitChange > 0 ? 'is-improving' : profitChange < 0 ? 'is-declining' : 'is-stable';
        const changeLabel = profitChange == null ? 'Chưa có kỳ trước để so sánh' : profitChange > 0 ? `Tăng ${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(profitChange)}% so với kỳ trước` : profitChange < 0 ? `Giảm ${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(Math.abs(profitChange))}% so với kỳ trước` : 'Không đổi so với kỳ trước';
        return <article className={`accountant-kpi ${profitTone}`} key={label}><span>{label}</span><strong>{money(value)}</strong><small>{note}</small>{label === 'Thu nhập vận hành' && <><small>Doanh thu hóa đơn hoàn tất: {money(report?.totals?.billRevenue)}</small><small>Cọc bị tịch thu: {money(report?.totals?.forfeitedDeposits)}</small></>}{profitKpi && <small className={`accountant-kpi-change ${changeTone}`}>{changeLabel}</small>}</article>;
      })}</section>
      <div className="accountant-dash-grid">
        <section className="accountant-dash-panel"><div className="accountant-panel-heading"><div><h2>Thu, chi và lợi nhuận</h2><p>Số liệu báo cáo thực tế · {branchLabel}</p></div></div><div className="accountant-chart-key"><span><i className="is-income" />Thu</span><span><i className="is-expense" />Chi</span><span><i className="is-profit" />Lãi</span><span><i className="is-loss" />Lỗ</span></div><LiveProfitChart rows={rows} />{!rows.length && !loading && <p className="accountant-dash-empty">Không có báo cáo trong kỳ này.</p>}</section>
        <section className="accountant-dash-panel"><div className="accountant-panel-heading"><div><h2>Số dư quỹ theo cơ sở</h2><p>Số đếm thực tế ở phiếu chốt quỹ gần nhất trong kỳ</p></div></div><div className="accountant-fund-bars">{[...fundByBranch.entries()].map(([name, value]) => <div className="accountant-fund-row" key={name}><span>{name}</span><div><i style={{ width: `${value / fundMax * 100}%` }} /></div><strong>{money(value)}</strong></div>)}</div>{!fundByBranch.size && !loading && <p className="accountant-dash-empty">Chưa có phiếu chốt quỹ trong kỳ.</p>}</section>
      </div>
        <section className="accountant-dash-panel"><div className="accountant-panel-heading"><div><h2>Thu theo phương thức</h2><p>Tỷ trọng tiền mặt, SePay và thanh toán kết hợp</p></div></div>{paymentMethods.length ? <div className="accountant-methods"><div className="accountant-method-donut" style={{ '--method-parts': methodParts }}><div><strong>{money(paymentTotal)}</strong><span>Tổng thu</span></div></div><div className="accountant-method-legend">{paymentMethods.map((item, index) => <div key={item.label}><span><i style={{ background: ['#168b87', '#6a3ee8', '#df9b28'][index % 3] }} />{item.label}</span><strong>{money(item.value)}</strong></div>)}</div></div> : <p className="accountant-dash-empty">Không có giao dịch thu từ hóa đơn trong kỳ.</p>}</section>
      <DataTable title="Đối soát tiền mặt" columns={['Cơ sở', 'Ngày', 'Tiền hệ thống', 'Tiền thực tế', 'Chênh lệch']} rows={closings} renderRow={(row) => <tr key={row.id}><td>{row.branchName || '—'}</td><td>{dateVi(row.businessDate || row.business_date || row.date)}</td><td>{money(row.expected ?? row.expected_closing)}</td><td>{money(row.actual ?? row.actual_closing)}</td><td className={Number(row.variance || 0) ? 'accountant-deviation' : 'accountant-matched'}>{money(row.variance)}</td></tr>} />
      <div className="accountant-dash-grid">
        <section className="accountant-dash-panel accountant-table-panel">
          <div className="accountant-panel-heading"><h2>Giao dịch SePay ngoại lệ</h2><span>{visibleExceptions.length} mục</span></div>
          <label className="accountant-exception-filter">Trạng thái xử lý<select value={exceptionFilter} onChange={(event) => setExceptionFilter(event.target.value)}><option value="UNRESOLVED">Chưa xử lý</option><option value="RESOLVED">Đã xử lý</option><option value="ALL">Tất cả</option></select></label>
          {visibleExceptions.length ? <div className="accountant-table-scroll"><table><thead><tr>{['Mã giao dịch', 'Cơ sở', 'Ngày', 'Số tiền', 'Trạng thái', 'Lý do ngoại lệ', 'Nội dung chuyển khoản', 'Người xử lý', 'Thời điểm xử lý', 'Ghi chú xử lý', 'Thao tác'].map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{visibleExceptions.map((row) => <tr key={row.id}><td>{row.externalId}</td><td>{row.branchName}</td><td>{dateVi(row.occurredAt)}</td><td>{money(row.amount)}</td><td>{statusVi(row.status)}</td><td>{row.exceptionReason || '—'}</td><td>{row.reason || '—'}</td><td>{row.resolvedBy || '—'}</td><td>{row.resolvedAt ? new Date(row.resolvedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'}</td><td>{row.resolutionNote || '—'}</td><td>{row.resolutionStatus === 'UNRESOLVED' ? <button type="button" onClick={() => { setResolutionTarget(row); setResolutionNote(''); }}>Đánh dấu đã xử lý</button> : 'Đã xử lý'}</td></tr>)}</tbody></table></div> : <p className="accountant-dash-empty">Không có giao dịch ngoại lệ theo bộ lọc.</p>}
        </section>
        <DataTable title="Hoàn cọc chờ xử lý" columns={['Mã đặt phòng', 'Cơ sở', 'Ngày yêu cầu', 'Trạng thái', 'Số tiền']} rows={selectedRefunds} renderRow={(row) => <tr key={row.id}><td>{row.bookingCode || row.id}</td><td>{row.branchName || '—'}</td><td>{dateVi(row.requestedAt)}</td><td>{statusVi(row.status)}</td><td>{money(row.amount)}</td></tr>} />
      </div>
      <div className="accountant-dash-grid">
        <DataTable title="Chi phí chờ chi trả và hoàn ứng" columns={['Mã yêu cầu', 'Cơ sở', 'Khoản mục', 'Trạng thái', 'Số tiền']} rows={selectedRequests} renderRow={(row) => <tr key={row.id}><td>{row.code || row.id}</td><td>{row.branchName || '—'}</td><td>{row.description || row.categoryName || '—'}</td><td>{statusVi(row.status)}</td><td>{money(row.amount)}</td></tr>} />
        <section className="accountant-dash-panel accountant-payroll"><div className="accountant-panel-heading"><div><h2>Quỹ lương kỳ</h2><p>Trạng thái kỳ lương</p></div><span>{statusVi(payroll?.period?.status)}</span></div><strong className="accountant-payroll-total">{money(payrollRows.length ? payrollTotal : null)}</strong><div className="accountant-payroll-progress"><div><span>Đã chi</span><strong>{money(payrollRows.length ? payrollPaid : null)}</strong></div><div className="accountant-expense-track"><i style={{ width: `${payrollTotal ? payrollPaid / payrollTotal * 100 : 0}%` }} /></div><div><span>{payrollTotal ? `${Math.round(payrollPaid / payrollTotal * 100)}% đã chi` : '—'}</span><strong>{money(payrollRows.length ? payrollTotal - payrollPaid : null)} chưa chi</strong></div></div></section>
      </div>
      <section className="accountant-period-status"><strong>Kỳ tài chính {month.slice(5, 7)}/{month.slice(0, 4)}: {statusVi(currentPeriod?.status ?? report?.periodStatus)}</strong><span>{currentPeriod ? `${currentPeriod.unreconciledClosings ?? 0} phiếu chốt quỹ chưa đối soát` : 'Chưa có thông tin đối soát cho kỳ này'}</span><Link to="/ke-toan/bao-cao?tab=periods">Xem điều kiện chốt kỳ</Link></section>
      <nav className="accountant-dash-shortcuts" aria-label="Lối tắt nghiệp vụ kế toán"><Link to="/ke-toan/bao-cao">Báo cáo chi tiết</Link><Link to="/ke-toan/bao-cao?tab=expenses">Chi phí</Link><Link to="/ke-toan/bao-cao?tab=periods">Chốt kỳ</Link><Link to="/ke-toan/bao-cao?tab=transactions">Giao dịch &amp; điều chỉnh</Link><Link to="/ke-toan/hoan-coc">Hoàn cọc</Link><Link to="/ke-toan/chot-quy">Đối soát chốt quỹ</Link></nav>
      {resolutionTarget && <div className="accountant-exception-backdrop" role="presentation"><section className="accountant-exception-dialog" role="dialog" aria-modal="true" aria-labelledby="resolve-sepay-title"><h2 id="resolve-sepay-title">Đánh dấu ngoại lệ đã xử lý</h2><p>Giao dịch {resolutionTarget.externalId} · {money(resolutionTarget.amount)}. Thao tác chỉ ghi nhận trạng thái và ghi chú, không tạo bút toán tiền.</p><label>Ghi chú xử lý<textarea value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} maxLength={1000} required /></label><div><button type="button" disabled={resolutionBusy} onClick={() => setResolutionTarget(null)}>Hủy</button><button type="button" disabled={resolutionBusy || !resolutionNote.trim()} onClick={resolveSePayException}>{resolutionBusy ? 'Đang lưu…' : 'Xác nhận đã xử lý'}</button></div></section></div>}
    </main>
  </AppShell>;
}
