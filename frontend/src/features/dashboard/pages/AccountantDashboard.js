import { PaginatedList, Pagination } from '../../../shared/components/Pagination';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../../api/client';
import { authApi } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';<<<<<<< HEAD
import { readStale, staleKey, writeStale } from '../../../shared/utils/staleCache';

import { dateKey, dateLabel } from '../../finance/utils/financeDates';
import DashboardPeriodControls, { initialPeriodValue, periodRange } from '../components/DashboardPeriodControls';
import KpiComparison, { percentChange } from '../components/KpiComparison';

import '../styles/accountant-dashboard.css';

const pendingRefunds = new Set(['REQUESTED', 'APPROVED', 'PROCESSING']);
const comparisonMode = { DAY: 'previous-day', MONTH: 'previous-month', YEAR: 'previous-year' };
const transactionKinds = { PAYMENT: 'Thu thanh toán', REFUND: 'Hoàn cọc', EXPENSE: 'Chi phí', SALARY: 'Trả lương',
  ADVANCE: 'Ứng lương', TRANSFER: 'Chuyển quỹ', ADJUSTMENT: 'Điều chỉnh' };
const expenseKinds = { EXPENSE: 'Chi phí và hoàn ứng', SALARY: 'Lương và ứng lương', ADVANCE: 'Lương và ứng lương',
  REFUND: 'Hoàn tiền', TRANSFER: 'Chuyển quỹ', ADJUSTMENT: 'Điều chỉnh giảm', PAYMENT: 'Điều chỉnh khoản thu' };
const vndFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });
const percentFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const compactFormat = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 });
const hourFormat = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', hourCycle: 'h23' });
const money = (value) => value == null ? '—' : `${vndFormat.format(value)} ₫`;
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không tải được số liệu thu chi. Vui lòng thử lại.';

function LiveCashChart({ transactions, periodType }) {
  const buckets = new Map();
  for (const row of transactions) {
    const date = row.cashDate;
    const key = periodType === 'YEAR' ? date.slice(0, 7) : periodType === 'DAY'
      ? (row.occurredAt ? `${hourFormat.format(new Date(row.occurredAt))}:00` : 'Không rõ giờ') : date;
    const item = buckets.get(key) || { income: 0, expense: 0 };
    const amount = Number(row.amount);
    if (amount > 0) item.income += amount;
    else item.expense -= amount;
    buckets.set(key, item);
  }
  const entries = [...buckets.entries()].sort(([left], [right]) => left.localeCompare(right));
  if (!entries.length) return <p className="accountant-dash-empty">Không có giao dịch trong kỳ đã chọn.</p>;
  const max = Math.max(1, ...entries.flatMap(([, item]) => [item.income, item.expense]));
  const width = 760, height = 240, left = 58, right = 20, top = 16, bottom = 38;
  const chartWidth = width - left - right, chartHeight = height - top - bottom;
  const x = (index) => left + (entries.length === 1 ? chartWidth / 2 : index * chartWidth / (entries.length - 1));
  const y = (value) => top + chartHeight - (value / max) * chartHeight;
  const points = (metric) => entries.map(([, item], index) => `${x(index)},${y(item[metric])}`).join(' ');
  const axisLabel = (key) => periodType === 'YEAR' ? `${key.slice(5, 7)}/${key.slice(0, 4)}`
    : periodType === 'MONTH' ? `${key.slice(8, 10)}/${key.slice(5, 7)}` : key;
  const tickEvery = Math.max(1, Math.ceil(entries.length / 7));
  return <div className="accountant-live-chart-scroll">
    <svg viewBox={`0 0 ${width} ${height}`} className="accountant-live-chart-svg" role="img" aria-label="Biểu đồ tiền thực thu và tiền thực chi">
      {[0, 1, 2, 3, 4].map((tick) => {
        const line = top + chartHeight * tick / 4;
        return <g key={tick}><line x1={left} x2={width - right} y1={line} y2={line}/>
          <text className="accountant-chart-axis-value" x={left - 8} y={line + 4}>{compactFormat.format(max * (4 - tick) / 4)}</text></g>;
      })}
      <polyline className="accountant-chart-income-line" points={points('income')}/>
      <polyline className="accountant-chart-expense-line" points={points('expense')}/>
      {entries.map(([key, item], index) => <g key={key}>
        <circle className="accountant-chart-income-dot" cx={x(index)} cy={y(item.income)} r="3.5"><title>{key} · Thực thu {money(item.income)}</title></circle>
        <circle className="accountant-chart-expense-dot" cx={x(index)} cy={y(item.expense)} r="3.5"><title>{key} · Thực chi {money(item.expense)}</title></circle>
        {(index % tickEvery === 0 || index === entries.length - 1) &&
          <text className="accountant-chart-axis-day" x={x(index)} y={height - 10}>{axisLabel(key)}</text>}
      </g>)}
    </svg>
  </div>;
}

function ExpenseBreakdown({ transactions, expense }) {
  const groups = new Map();
  for (const row of transactions) {
    if (Number(row.amount) >= 0) continue;
    const name = expenseKinds[row.kind] || 'Chi khác';
    const group = groups.get(name) || { amount: 0, count: 0 };
    group.amount += Math.abs(Number(row.amount));
    group.count += 1;
    groups.set(name, group);
  }
  const lines = [...groups.entries()].sort((left, right) => right[1].amount - left[1].amount);
  return <section className="accountant-dash-panel accountant-expense-mix" aria-labelledby="accountant-expense-mix-title">
    <div className="accountant-panel-heading"><div><h2 id="accountant-expense-mix-title">Cơ cấu khoản chi</h2><p>Tiền thực chi theo nhóm giao dịch</p></div>
      <strong>{money(expense)}</strong></div>
    {lines.length ? <div className="accountant-table-scroll">
      <table><thead><tr><th scope="col">Nhóm khoản chi</th><th scope="col">Giao dịch</th><th scope="col">Số tiền</th><th scope="col">Tỷ trọng</th></tr></thead>
        <tbody>{lines.map(([name, group]) => <tr key={name}><td>{name}</td><td>{group.count}</td>
          <td><strong>{money(group.amount)}</strong></td><td>{percentFormat.format(group.amount * 100 / expense)}%</td></tr>)}</tbody>
      </table>
    </div> : <p className="accountant-dash-empty">Không có khoản chi trong kỳ đã chọn.</p>}
  </section>;
}

export default function AccountantDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [periodType, setPeriodType] = useState('MONTH');
  const [periodValue, setPeriodValue] = useState(() => initialPeriodValue('MONTH'));
  const [customRange, setCustomRange] = useState(null);
  const [branchId, setBranchId] = useState('');
  const [branches, setBranches] = useState([]);
  const [refunds, setRefunds] = useState(null);
  const [refundsFailed, setRefundsFailed] = useState(false);
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [recentDay, setRecentDay] = useState('');
  const [recentPage, setRecentPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const bounds = periodRange(periodType, periodValue);
  const { from, to } = customRange || bounds;
  const compare = comparisonMode[periodType];
  const scopeKey = JSON.stringify([from, to, branchId, compare, revision]);
  const current = overview?.key === scopeKey ? overview.data : null;
  const loading = !current && error === '';
  const transactions = current?.flow?.rows || [];
  const totals = current?.flow?.current;
  const prior = current?.flow?.previous;

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role === 'ACCOUNTANT') setUser(identity);
      else navigate(identity.destination, { replace: true });
    }).catch(() => { if (active) navigate('/dang-nhap', { replace: true }); });
    return () => { active = false; };
  }, [navigate]);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;

    setError('');
    const cacheKey = staleKey('accountant', month, branchId, dateFrom, dateTo);
    const cached = readStale(cacheKey);
    const params = { month, ...(branchId ? { branchId } : {}) };
    const apply = (results) => {
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
    };
    // Hiện ngay số liệu lần trước (nếu có) trong lúc tải bản mới ở nền.
    if (cached) { apply(cached); setLoading(false); } else setLoading(true);
    Promise.allSettled([
      api.get('/accounting/reports/monthly', { params }),
      api.get('/accounting/deposits/refund-requests'),
      api.get('/accounting/financial-periods'),
      api.get('/accounting/dashboard-details', { params: { month, from: dateFrom, to: dateTo, ...(branchId ? { branchId } : {}) } }),
      api.get('/accounting/transactions', { params }),
      api.get('/accounting/reports/source-imports', { params: { from: monthBounds(month).start, to: monthBounds(month).end, ...(branchId ? { branchId } : {}) } }),
    ]).then((results) => {
      if (!active) return;
      if (results.every((item) => item.status === 'fulfilled')) writeStale(cacheKey, results);
      apply(results);
    }).finally(() => { if (active) setLoading(false); });

    setRefundsFailed(false);
    setRefunds(null);
    api.get('/accounting/deposits/refund-requests').then(({ data }) => { if (active) setRefunds(data.items || []); })
      .catch(() => { if (active) setRefundsFailed(true); });

    return () => { active = false; };
  }, [user, revision]);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    setError('');
    const params = { from, to, ...(branchId && { branchId }) };
    Promise.allSettled([
      api.get('/accounting/reports/cash-flow', { params: { ...params, compare } }),
      api.get('/accounting/cash-closing/unreconciled-count', { params }),
    ]).then(([flow, closing]) => {
      if (!active) return;
      if (flow.status === 'fulfilled') setBranches(flow.value.data.branches || []);
      if (flow.status === 'rejected') setError(errorText(flow.reason));
      setOverview({ key: scopeKey, data: { flow: flow.status === 'fulfilled' ? flow.value.data : null,
        closingCount: closing.status === 'fulfilled' ? closing.value.data.count : null } });
    });
    return () => { active = false; };
  }, [user, from, to, branchId, compare, scopeKey]);

  function resetRecent() {
    setRecentDay('');
    setRecentPage(0);
  }

  function updateFrom(value) {
    if (!value || value < bounds.from || value > bounds.to) return;
    setCustomRange({ from: value, to: value > to ? value : to });
    resetRecent();
  }

  function updateTo(value) {
    if (!value || value < from || value > bounds.to) return;
    setCustomRange({ from, to: value });
    resetRecent();
  }

  const branchLabel = branchId ? branches.find((item) => item.id === branchId)?.name || 'Cơ sở đã chọn' : 'Toàn chuỗi';
  const selectedRefunds = (refunds || []).filter((row) => {
    const date = dateKey(row.requestedAt);
    return pendingRefunds.has(row.status) && date >= from && date <= to
      && (!branchId || row.branchId === branchId || (!row.branchId && row.branchName === branchLabel));
  });
  const refundAmount = selectedRefunds.reduce((sum, row) => sum + Number(row.amount || row.refundAmount || 0), 0);
  const branchTotals = new Map();
  for (const row of transactions) {
    if (!row.branchId || Number(row.amount) <= 0) continue;
    const branch = branchTotals.get(row.branchId) || { id: row.branchId, name: row.branchName, revenue: 0 };
    branch.revenue += Number(row.amount);
    branchTotals.set(row.branchId, branch);
  }
  const branchRevenue = [...branchTotals.values()].sort((left, right) => right.revenue - left.revenue);
  const branchMax = Math.max(1, ...branchRevenue.map((row) => row.revenue));
  const recent = transactions.filter((row) => !recentDay || row.cashDate === recentDay)
    .sort((left, right) => right.cashDate.localeCompare(left.cashDate) || String(right.occurredAt || '').localeCompare(String(left.occurredAt || '')));
  const pageSize = 5;
  const recentScope = Math.min(recentPage, Math.max(0, Math.ceil(recent.length / pageSize) - 1));
  const recentRows = recent.slice(recentScope * pageSize, (recentScope + 1) * pageSize);
  const filterParams = new URLSearchParams({ from, to, status: 'UNRECONCILED', ...(branchId && { branchId }) });

  async function logout() {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch { setActionError('Không thể đăng xuất. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  return <AppShell user={user} branchName="toàn chuỗi" busy={busy} onLogout={logout} breadcrumb="Tổng quan kế toán" activeTab="overview" audience="accountant" hideSearch showCurrentMonth={false}>
    <main className="accountant-dash" aria-labelledby="accountant-dash-title">
      <header className="accountant-dash-header"><h1 id="accountant-dash-title">Tổng quan tài chính</h1>
        <button type="button" className="accountant-overview-reload" onClick={() => setRevision((value) => value + 1)} disabled={loading}>Tải lại</button>
      </header>
      <div className="accountant-overview-filterbar" role="group" aria-label="Kỳ và cơ sở báo cáo">
        <DashboardPeriodControls id="tổng quan tài chính" type={periodType} value={periodValue} className="accountant-overview-period" fieldClassName="accountant-overview-period-field"
          onTypeChange={setPeriodType} onValueChange={(value) => { setPeriodValue(value); setCustomRange(null); resetRecent(); }}/>
        {periodType !== 'DAY' && <>
          <label>Từ ngày<input type="date" min={bounds.from} max={bounds.to} value={from} onChange={(event) => updateFrom(event.target.value)}/></label>
          <label>Đến ngày<input type="date" min={from} max={bounds.to} value={to} onChange={(event) => updateTo(event.target.value)}/></label>
        </>}
        <label>Cơ sở<select value={branchId} onChange={(event) => { setBranchId(event.target.value); resetRecent(); }}>
          <option value="">Tất cả cơ sở</option>{branches.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}
        </select></label>
      </div>
      {(error || actionError) && <p className="accountant-dash-error" role="alert">{error || actionError}</p>}
      {loading && <p role="status">Đang tải số liệu thu chi…</p>}
      <section className="accountant-kpis" aria-label="Chỉ số tài chính" aria-busy={loading}>
        {[['Tiền thực thu', 'income'], ['Tiền thực chi', 'expense'], ['Chênh lệch thu − chi', 'net']].map(([label, key]) =>
          <article className={`accountant-kpi ${key === 'net' && totals ? Number(totals.net) < 0 ? 'is-loss' : Number(totals.net) > 0 ? 'is-profit' : 'is-even' : ''}`} key={key}>
            <span>{label}</span><strong>{money(totals?.[key])}</strong>
            {current?.flow && <KpiComparison change={percentChange(totals[key], prior[key])} periodType={periodType}
              from={current.flow.comparisonFrom} to={current.flow.comparisonTo} lowerIsBetter={key === 'expense'}/>}
          </article>)}
        <article className="accountant-kpi"><span>Hoàn cọc chờ xử lý</span><strong>{money(refunds ? refundAmount : null)}</strong>
          <small>{refundsFailed ? 'Chưa tải được yêu cầu' : refunds ? `${selectedRefunds.length} yêu cầu` : 'Đang tải yêu cầu'}</small></article>
      </section>
      <div className="accountant-dash-grid accountant-overview-primary">
        <section className="accountant-dash-panel accountant-overview-chart" aria-labelledby="accountant-flow-title">
          <div className="accountant-panel-heading"><div><h2 id="accountant-flow-title">Xu hướng thu chi</h2>
            <p>{periodType === 'YEAR' ? 'Theo tháng' : periodType === 'DAY' ? 'Theo giờ' : 'Theo ngày'} · {branchLabel}</p></div>
            <div className="accountant-chart-key"><span><i className="is-income"/>Thực thu</span><span><i className="is-expense"/>Thực chi</span></div>
          </div>
          {totals && <LiveCashChart transactions={transactions} periodType={periodType}/>}
        </section>
        <section className="accountant-dash-panel accountant-overview-tasks" aria-labelledby="accountant-tasks-title">
          <div className="accountant-panel-heading"><h2 id="accountant-tasks-title">Công việc cần xử lý</h2></div>
          <Link to="/ke-toan/hoan-coc"><span><strong>Hoàn tiền cọc</strong><small>Yêu cầu đang chờ xử lý</small></span><b>{refunds ? selectedRefunds.length : '—'}</b></Link>
          <Link to={`/ke-toan/chot-quy?${filterParams}`}><span><strong>Đối soát chốt quỹ</strong><small>Phiếu chưa đối soát trong kỳ</small></span><b>{current?.closingCount ?? '—'}</b></Link>
        </section>
      </div>
      <div className="accountant-dash-grid accountant-overview-secondary">
        <ExpenseBreakdown transactions={transactions} expense={totals?.expense}/>
        <section className="accountant-dash-panel" aria-labelledby="accountant-branch-income-title">
          <div className="accountant-panel-heading"><div><h2 id="accountant-branch-income-title">Thu tiền theo cơ sở</h2></div><Link to="/ke-toan/bao-cao">Chi tiết</Link></div>
          {branchRevenue.length ? <PaginatedList pageSize={10} className="accountant-fund-bars accountant-branch-revenue">
            {branchRevenue.map((row) => <div className="accountant-fund-row" key={row.id}><span title={row.name}>{row.name}</span>
              <div aria-hidden="true"><i style={{ width: `${row.revenue / branchMax * 100}%` }}/></div><strong>{money(row.revenue)}</strong></div>)}
          </PaginatedList> : <p className="accountant-dash-empty">Không có khoản thu theo cơ sở trong kỳ.</p>}
        </section>
      </div>
      <section className="accountant-dash-panel accountant-table-panel accountant-recent-panel" aria-labelledby="accountant-recent-title">
        <div className="accountant-panel-heading"><div><h2 id="accountant-recent-title">Giao dịch gần đây trong kỳ</h2>
          <p>{recent.length} giao dịch · {dateLabel(from)}{to !== from && ` – ${dateLabel(to)}`} · {branchLabel}</p></div>
          <label className="accountant-recent-date">Ngày giao dịch<input type="date" value={recentDay} min={from} max={to}
            onChange={(event) => { setRecentDay(event.target.value); setRecentPage(0); }}/></label>
        </div>
        {recentRows.length ? <div className="accountant-table-scroll"><table>
          <thead><tr>{['Mã giao dịch', 'Ngày', 'Cơ sở', 'Loại', 'Phương thức', 'Trạng thái', 'Số tiền'].map((item) => <th scope="col" key={item}>{item}</th>)}</tr></thead>
          <tbody>{recentRows.map((row) => <tr key={row.id}><td title={row.id}>{String(row.id).slice(0, 11).toUpperCase()}</td>
            <td>{dateLabel(row.cashDate)}</td><td>{row.branchName || 'Toàn chuỗi'}</td><td>{transactionKinds[row.kind] || row.kind}</td>
            <td>{row.method || '—'}</td>
            <td>{row.sourceSystem === 'SYNTHETIC_DEMO' ? 'Dữ liệu mô phỏng' : row.sourceSystem === 'OPERATIONAL' ? 'Hóa đơn vận hành' : row.sourceSystem ? 'Dữ liệu nhập nguồn' : 'Đã ghi sổ'}</td>
            <td className={Number(row.amount) < 0 ? 'accountant-deviation' : 'accountant-matched'}>{money(row.amount)}</td>
          </tr>)}</tbody>
        </table></div> : <p className="accountant-dash-empty">Không có giao dịch trong ngày và kỳ đã chọn.</p>}
        <Pagination page={recentScope} pageSize={pageSize} total={recent.length} onPageChange={setRecentPage} label="Phân trang giao dịch gần đây"/>
      </section>
    </main>
  </AppShell>;
}
