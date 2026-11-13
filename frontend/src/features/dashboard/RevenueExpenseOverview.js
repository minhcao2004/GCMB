import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { financeApi } from '../finance/services/financeApi';
import './revenue-expense-overview.css';

const MAX_RANGE_DAYS = 366;
const ALERT_THRESHOLD_PERCENT = 20;
const ALL_BRANCHES = '__ALL__';
const EMPTY_ROWS = [];
const number = (value) => Number(value) || 0;
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(number(value))} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(number(value))}%`;
const compactMoney = (value) => new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(number(value));
const requestError = (error) => error.response?.data?.detail || error.response?.data?.message || (error.request
  ? 'Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại.'
  : 'Không tải được báo cáo. Vui lòng thử lại.');

function vietnamToday() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const part = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${part.year}-${part.month}-${part.day}`;
}

function addDays(value, amount) {
  const date = new Date(`${value}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

function monthRange(month, today = vietnamToday()) {
  const from = `${month}-01`;
  const nextMonth = new Date(`${month}-01T00:00:00Z`);
  nextMonth.setUTCMonth(nextMonth.getUTCMonth() + 1);
  const last = new Date(nextMonth.getTime() - 86400000).toISOString().slice(0, 10);
  return { from, to: last > today ? today : last };
}

function dateDiffDays(from, to) {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000) + 1;
}

function initialRange() {
  const today = vietnamToday();
  return { from: `${today.slice(0, 7)}-01`, to: today };
}

function changePercent(current, previous) {
  return previous == null || number(previous) === 0 ? null : ((number(current) - number(previous)) / Math.abs(number(previous))) * 100;
}

function Delta({ current, previous, invert = false, points = false }) {
  if (current == null || previous == null) return <span className="rx-delta rx-delta--flat">Chưa có kỳ so sánh</span>;
  const difference = number(current) - number(previous);
  const change = points ? difference : changePercent(current, previous);
  if (change == null) return <span className="rx-delta rx-delta--flat">— so với kỳ so sánh</span>;
  const good = invert ? change < 0 : change > 0;
  const flat = Math.abs(change) < 0.005;
  const tone = flat ? 'flat' : good ? 'good' : 'bad';
  const value = points
    ? `${difference > 0 ? '+' : ''}${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(difference)} điểm %`
    : `${change > 0 ? '+' : ''}${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(change)}%`;
  return <span className={`rx-delta rx-delta--${tone}`}>{flat ? '•' : change > 0 ? '▲' : '▼'} {value} <span className="rx-delta__ref">so với {points ? 'kỳ so sánh' : 'kỳ so sánh'}</span></span>;
}

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return <div className="rx-tip"><div className="rx-tip__title">{label}</div>{payload.map((item) => (
    <div className="rx-tip__row" key={item.dataKey}>
      <span className="rx-dot" style={{ background: item.color || item.fill }} />
      <span>{item.name}</span><strong>{money(item.value)}</strong>
    </div>
  ))}</div>;
}

function Segmented({ label, options, value, onChange }) {
  return <div className="rx-seg" role="group" aria-label={label}>{options.map((option) => (
    <button type="button" key={option.id} aria-pressed={value === option.id}
      className={`rx-seg__btn${value === option.id ? ' is-active' : ''}`} onClick={() => onChange(option.id)}>
      {option.label}
    </button>
  ))}</div>;
}

function metricTone(current, previous, invert = false) {
  const delta = number(current) - number(previous);
  if (!delta) return 'is-flat';
  return (invert ? delta < 0 : delta > 0) ? 'is-good' : 'is-bad';
}

function Kpi({ label, value, footnote, current, previous, invert = false, points = false, tone = '' }) {
  return <article className={`rx-kpi ${tone}`}>
    <span className="rx-kpi__label">{label}</span><strong className="rx-kpi__value">{value}</strong>
    <Delta current={current} previous={previous} invert={invert} points={points} />
    {footnote && <small className="rx-kpi__note">{footnote}</small>}
  </article>;
}

function budgetValue(budgets, categoryKey, branchIds) {
  if (!budgets) return null;
  if (!Array.isArray(budgets)) {
    const value = budgets[categoryKey];
    return value == null ? null : number(value);
  }
  const matches = budgets.filter((item) => item.categoryKey === categoryKey
    && (!item.branchId || branchIds.length === 0 || branchIds.includes(item.branchId)));
  return matches.length ? matches.reduce((sum, item) => sum + number(item.amount), 0) : null;
}

function sortRows(rows, sort) {
  return [...rows].sort((a, b) => {
    const left = a[sort.key] == null ? Number.NEGATIVE_INFINITY : number(a[sort.key]);
    const right = b[sort.key] == null ? Number.NEGATIVE_INFINITY : number(b[sort.key]);
    return (left - right) * (sort.direction === 'asc' ? 1 : -1);
  });
}

/** budgets: [{ categoryKey, amount, branchId? }] or { [categoryKey]: amount }. */
export default function RevenueExpenseOverview({ role, budgets = {}, month, branchId = '' }) {
  const navigate = useNavigate();
  const [range, setRange] = useState(() => month ? monthRange(month) : initialRange());
  const [branchIds, setBranchIds] = useState(() => branchId ? [branchId] : []);
  const [branchOptions, setBranchOptions] = useState([]);
  const [granularity, setGranularity] = useState('day');
  const [comparison, setComparison] = useState('prev');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [rangeError, setRangeError] = useState('');
  const [revision, setRevision] = useState(0);
  const [exporting, setExporting] = useState('');
  const [sort, setSort] = useState({ key: 'revenue', direction: 'desc' });
  const [monthFilter, setMonthFilter] = useState('');
  const selectedBranchIds = useMemo(() => branchId ? [branchId] : branchIds, [branchId, branchIds]);

  useEffect(() => {
    if (!range.from || !range.to || range.from > range.to) {
      setRangeError('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
      setData(null); setLoading(false); return undefined;
    }
    if (range.to > vietnamToday()) {
      setRangeError('Không thể chọn ngày trong tương lai.');
      setData(null); setLoading(false); return undefined;
    }
    if (dateDiffDays(range.from, range.to) > MAX_RANGE_DAYS) {
      setRangeError(`Khoảng ngày tối đa là ${MAX_RANGE_DAYS} ngày.`);
      setData(null); setLoading(false); return undefined;
    }
    setRangeError('');
    let active = true;
    setLoading(true); setError('');
    const params = {
      from: range.from, to: range.to, compare: comparison, granularity,
      ...(selectedBranchIds.length ? { branchIds: selectedBranchIds } : {}),
    };
    financeApi.financialOverview(params).then((result) => {
      if (!active) return;
      setData(result);
      setBranchOptions(result.branchOptions || []);
    }).catch((cause) => {
      if (active) { setData(null); setError(requestError(cause)); }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [range.from, range.to, selectedBranchIds, comparison, granularity, revision]);

  const params = useMemo(() => ({
    from: range.from, to: range.to, compare: comparison, granularity,
    ...(selectedBranchIds.length ? { branchIds: selectedBranchIds } : {}),
  }), [range.from, range.to, selectedBranchIds, comparison, granularity]);
  const rows = data?.series || EMPTY_ROWS;
  const totals = data?.totals || {};
  const previous = data?.comparisonTotals || {};
  const categories = useMemo(() => {
    const values = [...(data?.categories || [])]
      .filter((item) => number(item.current) > 0 || number(item.comparison) > 0)
      .sort((a, b) => number(b.current) - number(a.current));
    const sum = values.reduce((result, item) => result + number(item.current), 0);
    return values.map((item, index) => ({
      ...item,
      color: `var(--rx-chart-${(index % 7) + 1})`,
      percent: sum > 0 ? number(item.current) * 100 / sum : 0,
      budget: budgetValue(budgets, item.categoryKey, selectedBranchIds),
    }));
  }, [data?.categories, budgets, selectedBranchIds]);
  const chartRows = useMemo(() => rows.map((row) => ({
    ...row,
    expense: selectedCategory ? number(row.expensesByCategory?.[selectedCategory]) : number(row.expenses),
    compareExpense: selectedCategory ? number(row.comparisonExpensesByCategory?.[selectedCategory]) : number(row.comparisonExpenses),
  })), [rows, selectedCategory]);
  const branchRows = useMemo(() => sortRows(data?.branches || [], sort), [data?.branches, sort]);
  const selectedMeta = categories.find((item) => item.categoryKey === selectedCategory);

  useEffect(() => {
    if (!month) return;
    setRange(monthRange(month));
    setMonthFilter(month);
  }, [month]);

  useEffect(() => {
    setBranchIds(branchId ? [branchId] : []);
  }, [branchId]);

  function validateRange(next) {
    setRange(next);
  }

  function choosePreset(id) {
    const today = vietnamToday();
    const days = id === '7' ? 7 : id === '30' ? 30 : id === '90' ? 90 : null;
    const next = days ? { from: addDays(today, -(days - 1)), to: today } : initialRange();
    setRange(next); setMonthFilter(id === 'month' ? today.slice(0, 7) : '');
    setGranularity(id === '90' ? 'week' : 'day');
  }

  function applyMonth(month) {
    setMonthFilter(month);
    if (month) setRange(monthRange(month));
  }

  function chooseBranches(event) {
    const selected = Array.from(event.target.selectedOptions, (option) => option.value);
    setBranchIds(selected.includes(ALL_BRANCHES) || selected.length === 0 ? [] : selected);
  }

  function toggleSort(key) {
    setSort((current) => ({ key, direction: current.key === key && current.direction === 'desc' ? 'asc' : 'desc' }));
  }

  function drillDown(category) {
    if (category.categoryKey === 'PAYROLL') return;
    const query = new URLSearchParams();
    query.set('from', range.from); query.set('to', range.to);
    query.set('costType', 'OPEX'); query.set('categoryId', category.categoryKey);
    selectedBranchIds.forEach((id) => query.append('branchIds', id));
    if (role === 'HEAD_OFFICE_MANAGER') {
      query.set('screen', 'Báo cáo chi phí');
      navigate(`/quan-ly-he-thong?${query.toString()}`);
    } else navigate(`/ke-toan/bao-cao-chi-phi?${query.toString()}`);
  }

  async function exportFile(format) {
    setExporting(format); setError('');
    try {
      const blob = await financeApi.exportFinancialOverview({ ...params, format });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `gcmb-financial-overview-${range.from}-${range.to}.${format}`; anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) {
      setError(requestError(cause));
    } finally { setExporting(''); }
  }

  const rangePresets = [
    { id: '7', label: '7 ngày' }, { id: '30', label: '30 ngày' },
    { id: '90', label: '90 ngày' }, { id: 'month', label: 'Tháng này' },
  ];
  const groupOptions = [
    { id: 'day', label: 'Ngày' }, { id: 'week', label: 'Tuần' }, { id: 'month', label: 'Tháng' },
  ];
  const comparisonOptions = [
    { id: 'prev', label: 'Kỳ trước' }, { id: 'yoy', label: 'Cùng kỳ năm trước' },
  ];

  return <section className="rx" aria-label="Tổng quan thu nhập và chi phí" aria-busy={loading}>
    <header className="rx-head">
      <div><h2 className="rx-title">Thu nhập và chi phí</h2>
        <p className="rx-sub">Thu nhập vận hành gồm doanh thu hóa đơn hoàn tất và cọc bị tịch thu. Chi phí vận hành chỉ tính khoản đã thanh toán, không tính chuyển quỹ nội bộ. Chi đầu tư được trình bày riêng.</p>
      </div>
      <div className="rx-export-actions" aria-label="Xuất báo cáo">
        <button type="button" disabled={loading || Boolean(exporting)} onClick={() => exportFile('csv')}>{exporting === 'csv' ? 'Đang xuất…' : 'Xuất CSV'}</button>
        <button type="button" disabled={loading || Boolean(exporting)} onClick={() => exportFile('xlsx')}>{exporting === 'xlsx' ? 'Đang xuất…' : 'Xuất XLSX'}</button>
      </div>
    </header>

    <div className="rx-filters">
      <div className="rx-filter-row">
        <Segmented label="Khoảng thời gian" options={rangePresets} value={rangePresets.some((item) => item.id === monthFilter) ? monthFilter : ''} onChange={choosePreset} />
        <label className="rx-filter">Kỳ tài chính<input type="month" value={monthFilter} max={vietnamToday().slice(0, 7)} onChange={(event) => applyMonth(event.target.value)} aria-label="Chọn kỳ tài chính theo tháng" /></label>
        <label className="rx-filter">Từ ngày<input type="date" value={range.from} max={vietnamToday()} onChange={(event) => { setMonthFilter(''); validateRange({ ...range, from: event.target.value }); }} aria-label="Ngày bắt đầu" /></label>
        <label className="rx-filter">Đến ngày<input type="date" value={range.to} max={vietnamToday()} onChange={(event) => { setMonthFilter(''); validateRange({ ...range, to: event.target.value }); }} aria-label="Ngày kết thúc" /></label>
        <label className="rx-filter rx-filter--branches">Cơ sở<select multiple disabled={Boolean(branchId)} value={selectedBranchIds.length ? selectedBranchIds : [ALL_BRANCHES]} onChange={chooseBranches} size="3" aria-label="Chọn một hoặc nhiều cơ sở">
          <option value={ALL_BRANCHES}>Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}{branch.status === 'ACTIVE' ? '' : ' · Tạm ngừng'}</option>)}
        </select></label>
      </div>
      <div className="rx-filter-row rx-filter-row--segments">
        <Segmented label="So sánh với" options={comparisonOptions} value={comparison} onChange={setComparison} />
        <Segmented label="Gom nhóm biểu đồ" options={groupOptions} value={granularity} onChange={setGranularity} />
        {rangeError && <p className="rx-range-error" role="alert">{rangeError}</p>}
      </div>
    </div>

    {error && <div className="rx-error" role="alert"><span>{error}</span><button type="button" onClick={() => setRevision((value) => value + 1)}>Thử lại</button></div>}

    {loading ? <div className="rx-skeleton" role="status" aria-label="Đang tải báo cáo"><i /><i /><i /><i /></div> : data && <>
      <div className="rx-kpis">
        <Kpi label="Thu nhập vận hành" value={money(totals.revenue)} current={totals.revenue} previous={previous.revenue}
          footnote={`Hóa đơn hoàn tất ${money(totals.billRevenue)} · Cọc bị tịch thu ${money(totals.forfeitedDeposits)}`} tone={metricTone(totals.revenue, previous.revenue)} />
        <Kpi label="Chi phí vận hành" value={money(totals.expenses)} current={totals.expenses} previous={previous.expenses} invert
          footnote="Chi phí đã thanh toán gồm lương và ứng lương; không gồm chi đầu tư." tone={metricTone(totals.expenses, previous.expenses, true)} />
        <Kpi label="Lợi nhuận vận hành" value={money(totals.profit)} current={totals.profit} previous={previous.profit}
          footnote="Thu nhập vận hành − chi phí vận hành đã thanh toán." tone={metricTone(totals.profit, previous.profit)} />
        <Kpi label="Biên lợi nhuận vận hành" value={percent(totals.margin)} current={totals.margin} previous={previous.margin} points
          footnote="Lợi nhuận ÷ thu nhập vận hành." tone={metricTone(totals.margin, previous.margin)} />
      </div>
      <div className="rx-capex"><div><strong>Chi đầu tư</strong><span>Mua sắm, sửa chữa lớn · trình bày riêng, không trừ vào lợi nhuận vận hành.</span></div><strong>{money(totals.investment)}</strong></div>

      {!data.hasData && <div className="rx-empty" role="status">Không có giao dịch thu hoặc chi trong khoảng ngày và phạm vi đã chọn.</div>}
      {data.hasData && <>
        <article className="rx-card">
          <div className="rx-card__head"><div><h3 className="rx-card__title">Thu nhập vận hành và chi phí theo thời gian</h3>
            <p className="rx-card__hint">{range.from.split('-').reverse().join('/')} – {range.to.split('-').reverse().join('/')} · So sánh với {data.comparisonFrom.split('-').reverse().join('/')} – {data.comparisonTo.split('-').reverse().join('/')}{selectedMeta ? ` · Chi phí: ${selectedMeta.categoryName}` : ' · Chi phí vận hành'}</p>
          </div></div>
          <div className="rx-chart rx-chart--main"><ResponsiveContainer width="100%" height="100%"><ComposedChart data={chartRows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--rx-border)" />
            <XAxis dataKey="label" tick={{ fontSize: 11, fill: 'var(--rx-muted)' }} tickLine={false} minTickGap={20} />
            <YAxis tickFormatter={compactMoney} tick={{ fontSize: 11, fill: 'var(--rx-muted)' }} tickLine={false} axisLine={false} width={60} />
            <Tooltip content={<ChartTooltip />} cursor={{ fill: 'var(--rx-hover)' }} />
            <Legend wrapperStyle={{ fontSize: 11 }} />
            <Bar dataKey="revenue" name="Thu nhập vận hành" fill="var(--rx-revenue)" radius={[4, 4, 0, 0]} maxBarSize={32} />
            <Line type="monotone" dataKey="expense" name={selectedMeta ? `Chi: ${selectedMeta.categoryName}` : 'Chi phí vận hành'} stroke="var(--rx-expense)" strokeWidth={2.5} dot={chartRows.length <= 16} />
            <Line type="monotone" dataKey="comparisonRevenue" name="Thu nhập kỳ so sánh" stroke="var(--rx-revenue-compare)" strokeDasharray="6 4" strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="compareExpense" name="Chi phí kỳ so sánh" stroke="var(--rx-expense-compare)" strokeDasharray="4 4" strokeWidth={2} dot={false} />
          </ComposedChart></ResponsiveContainer></div>
        </article>

        <div className="rx-grid">
          <article className="rx-card">
            <div className="rx-card__head"><div><h3 className="rx-card__title">Cơ cấu chi phí vận hành</h3><p className="rx-card__hint">Chọn danh mục để lọc đường chi phí; mở chi tiết để xem chứng từ.</p></div></div>
            {categories.length ? <div className="rx-donut-wrap">
              <div className="rx-donut"><ResponsiveContainer width="100%" height="100%"><PieChart>
                <Pie data={categories} dataKey="current" nameKey="categoryName" innerRadius="62%" outerRadius="92%" paddingAngle={2} stroke="none" onClick={(item) => {
                  const key = item?.categoryKey || item?.payload?.categoryKey;
                  if (key) setSelectedCategory((value) => value === key ? '' : key);
                }}>
                  {categories.map((item) => <Cell key={item.categoryKey} fill={item.color} fillOpacity={selectedCategory && selectedCategory !== item.categoryKey ? .28 : 1} />)}
                </Pie><Tooltip formatter={(value) => money(value)} />
              </PieChart></ResponsiveContainer><div className="rx-donut__center"><span>Chi phí vận hành</span><strong>{money(totals.expenses)}</strong></div></div>
              <ul className="rx-legend">{categories.map((item) => {
                const delta = item.changePercent;
                const anomaly = delta != null && delta > ALERT_THRESHOLD_PERCENT;
                const overBudget = item.budget != null && number(item.current) > number(item.budget);
                return <li key={item.categoryKey} className={anomaly ? 'has-anomaly' : ''}>
                  <div className="rx-legend__row">
                    <button type="button" className={`rx-legend__item${selectedCategory === item.categoryKey ? ' is-active' : ''}`} aria-pressed={selectedCategory === item.categoryKey} onClick={() => setSelectedCategory((value) => value === item.categoryKey ? '' : item.categoryKey)}>
                      <span className="rx-dot" style={{ background: item.color }} /><span className="rx-legend__name">{item.categoryName}</span>
                      <span className="rx-legend__pct">{item.percent.toFixed(1).replace('.', ',')}%</span><span className="rx-legend__val">{money(item.current)}</span>
                      <span className={`rx-legend__delta${delta == null ? '' : delta > 0 ? ' bad' : delta < 0 ? ' good' : ''}`}>{delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}</span>
                    </button>
                    {item.categoryKey !== 'PAYROLL' && <button className="rx-link" type="button" onClick={() => drillDown(item)}>Chi tiết</button>}
                    {item.categoryKey === 'PAYROLL' && <span className="rx-legend__detail-hint">Chi tiết ở bảng lương</span>}
                  </div>
                  {anomaly && <span className="rx-alert-badge" title={`Danh mục tăng ${delta.toFixed(1)}% so với kỳ so sánh.`}>Tăng trên {ALERT_THRESHOLD_PERCENT}%</span>}
                  {overBudget && <span className="rx-budget-badge" title="Khoản thực chi đã vượt ngân sách được truyền vào dashboard.">Vượt ngân sách {money(item.budget)}</span>}
                </li>;
              })}</ul>
            </div> : <div className="rx-empty">Chưa có chi phí vận hành đã thanh toán trong kỳ so sánh.</div>}
          </article>

          <article className="rx-card">
            <div className="rx-card__head"><div><h3 className="rx-card__title">Chi phí vận hành theo cơ sở</h3><p className="rx-card__hint">Số liệu theo bộ lọc kỳ và cơ sở đang chọn.</p></div></div>
            <div className="rx-chart rx-chart--branch"><ResponsiveContainer width="100%" height="100%"><BarChart data={[...branchRows].sort((a,b)=>number(b.expenses)-number(a.expenses)).slice(0, 10)} layout="vertical" margin={{ top: 4, right: 20, left: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--rx-border)" />
              <XAxis type="number" tickFormatter={compactMoney} tick={{ fontSize: 11, fill: 'var(--rx-muted)' }} tickLine={false} axisLine={false} />
              <YAxis type="category" dataKey="name" width={126} tick={{ fontSize: 11, fill: 'var(--rx-text)' }} tickLine={false} axisLine={false} />
              <Tooltip content={<ChartTooltip />} /><Bar dataKey="expenses" name="Chi phí vận hành" fill="var(--rx-expense)" radius={[0, 4, 4, 0]} maxBarSize={22} />
            </BarChart></ResponsiveContainer></div>
          </article>
        </div>

        <article className="rx-card rx-ranking">
          <div className="rx-card__head"><div><h3 className="rx-card__title">Xếp hạng cơ sở</h3><p className="rx-card__hint">Bấm tiêu đề cột để sắp xếp tăng hoặc giảm dần.</p></div></div>
          <div className="rx-table-wrap"><table><caption>Thu nhập, chi phí và biên lợi nhuận theo cơ sở</caption><thead><tr>
            {[['name','Cơ sở'],['revenue','Thu nhập vận hành'],['expenses','Chi phí vận hành'],['profit','Lợi nhuận vận hành'],['margin','Biên lợi nhuận']].map(([key,label]) => <th scope="col" key={key}><button type="button" onClick={() => key !== 'name' && toggleSort(key)} aria-label={`Sắp xếp theo ${label}`}>{label}{sort.key === key ? (sort.direction === 'asc' ? ' ▲' : ' ▼') : ''}</button></th>)}
          </tr></thead><tbody>{branchRows.length ? branchRows.map((branch) => <tr key={branch.id}>
            <th scope="row">{branch.name}<small>{branch.code}</small></th><td>{money(branch.revenue)}</td><td>{money(branch.expenses)}</td><td>{money(branch.profit)}</td><td>{percent(branch.margin)}</td>
          </tr>) : <tr><td colSpan="5">Không có cơ sở trong phạm vi đã chọn.</td></tr>}</tbody></table></div>
        </article>
      </>}
    </>}
  </section>;
}
