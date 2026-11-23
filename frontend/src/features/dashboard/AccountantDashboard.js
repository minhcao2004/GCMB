import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import './accountantDashboard.css';

const monthNow = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
const todayVn = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const availableYears = () => { const current = Number(monthNow().slice(0, 4)); return Array.from({ length: current - 2019 }, (_, index) => current - index); };
const monthBounds = (month) => {
  const start = `${month}-01`;
  const lastDay = `${month}-${String(new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate()).padStart(2, '0')}`;
  return { start, end: month === monthNow() && lastDay > todayVn() ? todayVn() : lastDay };
};
const ledgerDate = (row, basis) => {
  const fallback = String(row.bookingDate || '').slice(0, 10);
  if (basis !== 'cash' || !row.occurredAt) return fallback;
  const occurredAt = new Date(row.occurredAt);
  if (Number.isNaN(occurredAt.getTime())) return fallback;
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(occurredAt);
  return `${parts.find((part) => part.type === 'year')?.value}-${parts.find((part) => part.type === 'month')?.value}-${parts.find((part) => part.type === 'day')?.value}`;
};
const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không tải được dữ liệu kế toán. Vui lòng thử lại.';
const dateVi = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).format(date); };
const statusVi = (value) => ({
  UNRESOLVED: 'Chưa xử lý', RESOLVED: 'Đã xử lý', COMPLETED: 'Hoàn tất', REQUESTED: 'Đang chờ xử lý', APPROVED: 'Đã duyệt', PROCESSING: 'Đang xử lý',
  WAITING_ACCOUNTANT: 'Chờ kế toán xử lý', WAITING_CONFIRMATION: 'Chờ xác nhận', PAID: 'Đã thanh toán', REJECTED: 'Đã từ chối',
  OPEN: 'Đang mở', CLOSED: 'Đã khóa', RECONCILED: 'Đã đối soát', NEEDS_EXPLANATION: 'Cần giải trình', FAILED: 'Không thành công', EXCEPTION: 'Ngoại lệ', PENDING: 'Đang chờ xử lý', CONFIRMED: 'Đã xác nhận',
})[value] || (value ? 'Chưa xác định' : '—');

function LiveProfitChart({ transactions, dateBasis, periodType }) {
  const daily = new Map();
  transactions.forEach((row) => {
    const date = ledgerDate(row, dateBasis);
    if (!date) return;
    let bucket = date;
    if (periodType === 'year') bucket = date.slice(0, 7);
    else if (periodType === 'week' || periodType === 'quarter') {
      const week = new Date(`${date}T12:00:00`);
      week.setDate(week.getDate() - ((week.getDay() + 6) % 7));
      bucket = `${week.getFullYear()}-${String(week.getMonth() + 1).padStart(2, '0')}-${String(week.getDate()).padStart(2, '0')}`;
    } else if (periodType === 'day' && row.occurredAt) bucket = `${date} ${String(new Date(row.occurredAt).getHours()).padStart(2, '0')}:00`;
    const item = daily.get(bucket) || { income: 0, expense: 0 };
    const amount = Number(row.amount) || 0;
    if (amount >= 0) item.income += amount;
    else item.expense += Math.abs(amount);
    daily.set(bucket, item);
  });
  const entries = [...daily.entries()].sort(([a], [b]) => a.localeCompare(b));
  if (!entries.length) return <p className="accountant-dash-empty">Không có giao dịch để vẽ biểu đồ trong kỳ này.</p>;
  const max = Math.max(1, ...entries.flatMap(([, values]) => [values.income, values.expense]));
  const width = 760; const height = 240; const left = 58; const right = 18; const top = 16; const bottom = 38;
  const chartWidth = width - left - right; const chartHeight = height - top - bottom;
  const x = (index) => left + (entries.length === 1 ? chartWidth / 2 : index * chartWidth / (entries.length - 1));
  const y = (value) => top + chartHeight - (value / max) * chartHeight;
  const pointsFor = (key) => entries.map(([, values], index) => `${x(index)},${y(values[key])}`).join(' ');
  const axisLabel = (key) => key.length === 7 ? `${key.slice(5, 7)}/${key.slice(0, 4)}`
    : key.includes(' ') ? `${key.slice(8, 10)}/${key.slice(5, 7)} ${key.slice(11, 16)}`
      : `${key.slice(8, 10)}/${key.slice(5, 7)}`;
  const tickEvery = Math.max(1, Math.ceil(entries.length / 7));
  return <div className="accountant-live-chart-scroll"><svg viewBox={`0 0 ${width} ${height}`} className="accountant-live-chart-svg" role="img" aria-label="Biểu đồ tiền thu và tiền chi theo ngày">
    {[0, 1, 2, 3, 4].map((tick) => { const value = max * (4 - tick) / 4; const lineY = top + chartHeight * tick / 4; return <g key={tick}><line x1={left} x2={width - right} y1={lineY} y2={lineY} /><text className="accountant-chart-axis-value" x={left - 8} y={lineY + 4}>{new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}</text></g>; })}
    <polyline className="accountant-chart-income-line" points={pointsFor('income')} />
    <polyline className="accountant-chart-expense-line" points={pointsFor('expense')} />
    {entries.map(([day, values], index) => <g key={day}><circle className="accountant-chart-income-dot" cx={x(index)} cy={y(values.income)} r="3.5"><title>{day} · Thu {money(values.income)}</title></circle><circle className="accountant-chart-expense-dot" cx={x(index)} cy={y(values.expense)} r="3.5"><title>{day} · Chi {money(values.expense)}</title></circle>{(index % tickEvery === 0 || index === entries.length - 1) && <text className="accountant-chart-axis-day" x={x(index)} y={height - 10}>{axisLabel(day)}</text>}</g>)}
  </svg></div>;
}

export default function AccountantDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [month, setMonth] = useState(monthNow);
  const periodType = 'month';
  const [dateFrom, setDateFrom] = useState(() => monthBounds(monthNow()).start);
  const [dateTo, setDateTo] = useState(() => monthBounds(monthNow()).end);
  const [branchId, setBranchId] = useState('');
  const dateBasis = 'cash';
  const [branches, setBranches] = useState([]);
  const [refunds, setRefunds] = useState([]);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [recentTransactions, setRecentTransactions] = useState([]);
  const [sourceTransactions, setSourceTransactions] = useState([]);
  const [recentDay, setRecentDay] = useState('');
  const [recentPage, setRecentPage] = useState(1);
  const [recentPageSize, setRecentPageSize] = useState(8);
  const [periods, setPeriods] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

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
    Promise.allSettled([
      api.get('/accounting/reports/monthly', { params }),
      api.get('/accounting/deposits/refund-requests'),
      api.get('/accounting/financial-periods'),
      api.get('/accounting/dashboard-details', { params: { month, from: dateFrom, to: dateTo, ...(branchId ? { branchId } : {}) } }),
      api.get('/accounting/transactions', { params }),
      api.get('/accounting/reports/source-imports', { params: { from: monthBounds(month).start, to: monthBounds(month).end, ...(branchId ? { branchId } : {}) } }),
    ]).then((results) => {
      if (!active) return;
      const [summary, refund, periodResult, details, transactions, imported] = results;
      if (summary.status === 'fulfilled') {
        const list = summary.value.data.results || [];
        setBranches((current) => {
          const combined = [...current, ...list.map((row) => ({ id: row.id, name: row.name }))];
          if (imported.status === 'fulfilled') combined.push(...(imported.value.data.branchOptions || []).map((row) => ({ id: row.id, name: row.name })));
          return [...new Map(combined.map((row) => [row.id, row])).values()];
        });
      } else { setError(errorText(summary.reason)); }
      setRefunds(refund.status === 'fulfilled' ? refund.value.data.items || [] : []);
      setPeriods(periodResult.status === 'fulfilled' ? periodResult.value.data || [] : []);
      setPaymentMethods(details.status === 'fulfilled' ? details.value.data.paymentMethods || [] : []);
      setRecentTransactions(transactions.status === 'fulfilled' ? transactions.value.data || [] : []);
      const sourceRows = imported.status === 'fulfilled' ? imported.value.data.rows || [] : [];
      const sourceBranchesWithInvoices = new Set(sourceRows.filter((row) => row.recordType === 'KIOT_INVOICE').map((row) => row.branchId));
      setSourceTransactions(sourceRows.filter((row) => row.recordType === 'KIOT_INVOICE' || !sourceBranchesWithInvoices.has(row.branchId))
        .map((row) => ({ id: `source-${row.id}`, bookingDate: row.businessDate, occurredAt: `${row.businessDate}T12:00:00`, branchId: row.branchId, branchName: row.branchName,
          kind: 'PAYMENT', amount: Number(row.paidAmount || 0), method: row.paymentMethod || 'Theo dữ liệu nguồn',
          reason: row.invoiceNumber || row.sourceKey || 'Booking nhập nguồn', status: row.sourceSystem === 'SYNTHETIC_DEMO' ? 'Dữ liệu mô phỏng' : 'Dữ liệu nguồn', sourceSystem: row.sourceSystem })));
      const sections = ['Báo cáo tháng', 'Hoàn tiền cọc', 'Kỳ tài chính', 'Phương thức thanh toán', 'Giao dịch', 'Dữ liệu nguồn'];
      const failedSections = results.flatMap((item, index) => item.status === 'rejected' ? [sections[index]] : []);
      if (failedSections.length) setError(`Không tải được: ${failedSections.join(', ')}. Số liệu có thể chưa đầy đủ. Vui lòng thử lại.`);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [month, branchId, dateFrom, dateTo]);

  const displayBranches = branches;
  const activeRefunds = refunds;
  const refundRows = activeRefunds.filter((row) => ['REQUESTED', 'APPROVED', 'PROCESSING'].includes(row.status)
    && String(row.requestedAt || '').slice(0, 10) >= dateFrom && String(row.requestedAt || '').slice(0, 10) <= dateTo);
  const selectedRefunds = branchId ? refundRows.filter((row) => row.branchId === branchId || row.branchName === displayBranches.find((item) => item.id === branchId)?.name) : refundRows;
  const refundAmount = selectedRefunds.reduce((sum, row) => sum + Number(row.amount || row.refundAmount || 0), 0);
  const currentPeriod = periods.find((row) => String(row.month).slice(0, 7) === month);
  const branchLabel = branchId ? displayBranches.find((item) => item.id === branchId)?.name || 'Cơ sở đang chọn' : 'Toàn chuỗi';
  const canonicalBranches = new Set(recentTransactions.filter((row) => row.kind === 'PAYMENT' && Number(row.amount) > 0).map((row) => row.branchId).filter(Boolean));
  const activeTransactions = [...recentTransactions, ...sourceTransactions.filter((row) => !canonicalBranches.has(row.branchId))];
  const filteredTransactions = activeTransactions.filter((row) => {
    const date = ledgerDate(row, dateBasis);
    return (!branchId || row.branchId === branchId)
      && date >= dateFrom && date <= dateTo;
  });
  const chartTransactions = filteredTransactions;
  const activePaymentMethods = paymentMethods.length ? paymentMethods : [...sourceTransactions.filter((row) => row.bookingDate >= dateFrom && row.bookingDate <= dateTo && (!branchId || row.branchId === branchId)).reduce((methods, row) => {
    const label = row.method || 'Theo dữ liệu nguồn';
    methods.set(label, (methods.get(label) || 0) + Math.max(0, Number(row.amount) || 0));
    return methods;
  }, new Map()).entries()].map(([label, value]) => ({ label, value }));
  const paymentTotal = activePaymentMethods.reduce((sum, item) => sum + Number(item.value || 0), 0);
  const cashIncome = filteredTransactions.reduce((sum, row) => sum + Math.max(0, Number(row.amount) || 0), 0);
  const cashExpense = filteredTransactions.reduce((sum, row) => sum + Math.max(0, -(Number(row.amount) || 0)), 0);
  const recentRows = filteredTransactions.filter((row) => !recentDay || ledgerDate(row, dateBasis) === recentDay);
  const branchRevenueRows = displayBranches.map((branch) => ({
    ...branch,
    revenue: filteredTransactions.filter((row) => row.branchId === branch.id && Number(row.amount) > 0).reduce((sum, row) => sum + Number(row.amount), 0),
  })).filter((row) => row.revenue > 0);
  const branchRevenueMax = Math.max(1, ...branchRevenueRows.map((row) => row.revenue));
  const recentPageCount = Math.max(1, Math.ceil(recentRows.length / recentPageSize));
  const visibleRecentRows = recentRows.slice((recentPage - 1) * recentPageSize, recentPage * recentPageSize);

  async function logout() {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch { setError('Không thể đăng xuất. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  return <AppShell user={user} branchName="toàn chuỗi" busy={busy} onLogout={logout} breadcrumb="Tổng quan kế toán" activeTab="overview" audience="accountant" hideSearch showCurrentMonth={false}>
    <main className="accountant-dash" aria-labelledby="accountant-dash-title">
      <header className="accountant-dash-header"><div><h1 id="accountant-dash-title">Tổng quan tài chính</h1><p>Thu chi · đối soát · hoàn cọc</p></div></header>
      <div className="accountant-overview-filterbar">
        <label>Kỳ báo cáo<select aria-label="Chọn kỳ báo cáo theo tháng" value={month.slice(5, 7)} onChange={(event) => { const nextMonth = `${month.slice(0, 4)}-${event.target.value}`; setMonth(nextMonth); const bounds = monthBounds(nextMonth); setDateFrom(bounds.start); setDateTo(bounds.end); setRecentDay(''); setRecentPage(1); }}>{Array.from({ length: 12 }, (_, index) => <option key={index + 1} value={String(index + 1).padStart(2, '0')}>Tháng {index + 1}</option>)}</select></label>
        <label>Năm<select value={month.slice(0, 4)} onChange={(event) => { const nextMonth = `${event.target.value}-${month.slice(5, 7)}`; setMonth(nextMonth); const bounds = monthBounds(nextMonth); setDateFrom(bounds.start); setDateTo(bounds.end); setRecentDay(''); setRecentPage(1); }}>{availableYears().map((year) => <option key={year} value={year}>{year}</option>)}</select></label>
        <label>Từ ngày<input type="date" min={`${month}-01`} max={`${month}-${monthBounds(month).end.slice(-2)}`} value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); if (event.target.value > dateTo) setDateTo(event.target.value); setRecentDay(''); setRecentPage(1); }} /></label>
        <label>Đến ngày<input type="date" min={dateFrom} max={monthBounds(month).end} value={dateTo} onChange={(event) => { setDateTo(event.target.value); setRecentDay(''); setRecentPage(1); }} /></label>
        <label>Cơ sở<select value={branchId} onChange={(event) => { setBranchId(event.target.value); setRecentPage(1); }}><option value="">Tất cả cơ sở</option>{displayBranches.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label>
        <span>{branchLabel}</span>
      </div>
      {error && <p className="accountant-dash-error" role="alert">{error}</p>}{loading && <p role="status">Đang tải số liệu…</p>}
      <section className="accountant-kpis" aria-label="Chỉ số tài chính trong kỳ">{[
        [dateBasis === 'cash' ? 'Tiền thực thu' : 'Khoản thu ghi nhận', cashIncome, dateBasis === 'cash' ? 'Theo ngày thanh toán' : 'Theo ngày ghi nhận nghiệp vụ'],
        [dateBasis === 'cash' ? 'Tiền thực chi' : 'Chi phí ghi nhận', cashExpense, 'Trong khoảng thời gian được chọn'],
        ['Chênh lệch thu − chi', cashIncome - cashExpense, 'Không đồng nghĩa với lợi nhuận'],
        ['Hoàn cọc chờ xử lý', refundAmount, `${selectedRefunds.length} yêu cầu`],
      ].map(([label, value, note]) => {
        const profitKpi = label === 'Chênh lệch thu − chi';
        const profitTone = !profitKpi ? '' : Number(value) > 0 ? 'is-profit' : Number(value) < 0 ? 'is-loss' : 'is-even';
        return <article className={`accountant-kpi ${profitTone}`} key={label}><span>{label}</span><strong>{money(value)}</strong><small>{note}</small></article>;
      })}</section>
      <div className="accountant-dash-grid accountant-overview-primary">
        <section className="accountant-dash-panel accountant-overview-chart"><div className="accountant-panel-heading"><div><h2>Xu hướng thu và chi</h2><p>{dateBasis === 'cash' ? 'Theo ngày thanh toán' : 'Theo ngày ghi nhận'} · {branchLabel}</p></div><span className="accountant-period-chip">{{ day: 'Theo giờ', week: 'Theo tuần', month: 'Theo ngày', quarter: 'Theo tuần', year: 'Theo tháng', custom: 'Theo ngày' }[periodType]}</span></div><div className="accountant-chart-key"><span><i className="is-income" />Tiền thu</span><span><i className="is-expense" />Tiền chi</span></div><LiveProfitChart transactions={chartTransactions} dateBasis={dateBasis} periodType={periodType} /></section>
        <section className="accountant-dash-panel accountant-overview-tasks"><div className="accountant-panel-heading"><div><h2>Công việc cần xử lý</h2><p>Các hàng đợi cần kế toán theo dõi</p></div><span className="accountant-period-chip">Ưu tiên</span></div>
          <Link to="/ke-toan/hoan-coc"><span><strong>Hoàn tiền cọc</strong><small>Yêu cầu đang chờ xử lý</small></span><b>{selectedRefunds.length}</b></Link>
          <Link to="/ke-toan/chot-quy"><span><strong>Đối soát chốt quỹ</strong><small>Phiếu chưa đối soát trong kỳ</small></span><b>{currentPeriod?.unreconciledClosings ?? 0}</b></Link>
        </section>
      </div>
      <div className="accountant-dash-grid accountant-overview-secondary">
        <section className="accountant-dash-panel accountant-method-panel"><div className="accountant-panel-heading"><div><h2>Thu theo phương thức</h2></div><strong>{money(paymentTotal)}</strong></div>{activePaymentMethods.length ? <div className="accountant-method-ledger">{activePaymentMethods.map((item, index) => { const amount = Number(item.value) || 0; const share = paymentTotal ? amount / paymentTotal * 100 : 0; return <div className="accountant-method-ledger-row" key={item.label}><span className="accountant-method-index">{String(index + 1).padStart(2, '0')}</span><span className="accountant-method-name">{item.label}</span><span className="accountant-method-track"><i style={{ width: `${share}%` }} /></span><strong>{money(amount)}</strong><small>{share.toFixed(1)}%</small></div>; })}</div> : <p className="accountant-dash-empty">Không có giao dịch thu từ hóa đơn trong kỳ.</p>}</section>
        <section className="accountant-dash-panel"><div className="accountant-panel-heading"><div><h2>Thu tiền theo cơ sở</h2><p>Theo bộ lọc ngày, kỳ và cơ sở đã chọn</p></div><Link to="/ke-toan/bao-cao">Chi tiết</Link></div>{branchRevenueRows.length ? <div className="accountant-fund-bars accountant-branch-revenue">{[...branchRevenueRows].sort((a, b) => b.revenue - a.revenue).map((row) => <div className="accountant-fund-row" key={row.id}><span title={row.name}>{row.name}</span><div><i style={{ width: `${row.revenue / branchRevenueMax * 100}%` }} /></div><strong>{money(row.revenue)}</strong></div>)}</div> : <p className="accountant-dash-empty">Không có báo cáo doanh thu theo bộ lọc.</p>}</section>
      </div>
      <section className="accountant-dash-panel accountant-table-panel accountant-recent-panel">
        <div className="accountant-panel-heading"><div><h2>Giao dịch gần đây trong kỳ</h2><p>{recentRows.length} giao dịch · {dateFrom.slice(8, 10)}/{dateFrom.slice(5, 7)}/{dateFrom.slice(0, 4)}–{dateTo.slice(8, 10)}/{dateTo.slice(5, 7)}/{dateTo.slice(0, 4)} · {branchLabel}</p></div><div className="accountant-recent-controls"><label className="accountant-recent-date">Ngày giao dịch<input type="date" value={recentDay} min={dateFrom} max={dateTo} onChange={(event) => { setRecentDay(event.target.value); setRecentPage(1); }} /></label><label>Hiển thị<select value={recentPageSize} onChange={(event) => { setRecentPageSize(Number(event.target.value)); setRecentPage(1); }}><option value="8">8 dòng</option><option value="25">25 dòng</option><option value="50">50 dòng</option></select></label></div></div>
        {visibleRecentRows.length ? <div className="accountant-table-scroll"><table><thead><tr>{['Mã giao dịch', 'Thời gian', 'Cơ sở', 'Loại', 'Phương thức', 'Trạng thái', 'Số tiền'].map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{visibleRecentRows.map((row) => <tr key={row.id}><td title={row.id}>{String(row.id).slice(0, 11).toUpperCase()}</td><td>{dateVi(ledgerDate(row, dateBasis))}</td><td>{row.branchName || 'Toàn chuỗi'}</td><td>{({ PAYMENT: 'Thu thanh toán', REFUND: 'Hoàn cọc', EXPENSE: 'Chi phí', SALARY: 'Trả lương', ADVANCE: 'Ứng lương', TRANSFER: 'Chuyển quỹ', OPENING: 'Số dư đầu kỳ', ADJUSTMENT: 'Điều chỉnh' })[row.kind] || row.kind}</td><td>{row.method || '—'}</td><td>{row.sourceSystem === 'SYNTHETIC_DEMO' ? 'Dữ liệu mô phỏng' : row.sourceSystem ? 'Dữ liệu nhập nguồn' : row.status ? statusVi(row.status) : 'Đã ghi sổ'}</td><td className={Number(row.amount) < 0 ? 'accountant-deviation' : 'accountant-matched'}>{money(row.amount)}</td></tr>)}</tbody></table></div> : <p className="accountant-dash-empty">Không có giao dịch trong ngày và kỳ đã chọn.</p>}
        <div className="accountant-recent-pager"><span>Trang {Math.min(recentPage, recentPageCount)} / {recentPageCount}</span><div><button type="button" disabled={recentPage <= 1} onClick={() => setRecentPage((page) => Math.max(1, page - 1))}>Trước</button><button type="button" disabled={recentPage >= recentPageCount} onClick={() => setRecentPage((page) => Math.min(recentPageCount, page + 1))}>Tiếp</button></div></div>
      </section>
    </main>
  </AppShell>;
}
