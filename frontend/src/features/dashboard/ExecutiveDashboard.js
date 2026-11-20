import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import {
  CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import './executive-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(Number(value) * 100)}%`;
const tooltipMoney = (value) => money(value);
const axisMoney = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value / 1_000_000)} tr`;
const todayVn = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const addDays = (day, amount) => new Date(Date.parse(`${day}T00:00:00Z`) + amount * 86400000).toISOString().slice(0, 10);
const dateLabel = (day) => day.split('-').reverse().join('/');
const periods = [['day', 'Hôm nay'], ['week', 'Tuần này'], ['month', 'Tháng này'], ['quarter', 'Quý này'], ['year', 'Năm nay'], ['custom', 'Tùy chỉnh']];
const validDate = (value) => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
  && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;

function presetRange(period, anchor, today) {
  const date = new Date(`${anchor}T00:00:00Z`);
  const month = date.getUTCMonth();
  const from = period === 'day' ? anchor : period === 'week' ? addDays(anchor, -((date.getUTCDay() + 6) % 7))
    : period === 'quarter' ? `${anchor.slice(0, 4)}-${String(Math.floor(month / 3) * 3 + 1).padStart(2, '0')}-01`
      : period === 'year' ? `${anchor.slice(0, 4)}-01-01` : `${anchor.slice(0, 7)}-01`;
  const end = new Date(`${from}T00:00:00Z`);
  if (period === 'week') end.setUTCDate(end.getUTCDate() + 6);
  if (period === 'month') { end.setUTCMonth(end.getUTCMonth() + 1); end.setUTCDate(0); }
  if (period === 'quarter') { end.setUTCMonth(end.getUTCMonth() + 3); end.setUTCDate(0); }
  if (period === 'year') { end.setUTCFullYear(end.getUTCFullYear() + 1); end.setUTCDate(0); }
  const last = period === 'day' ? anchor : end.toISOString().slice(0, 10);
  return { from, to: last > today ? today : last };
}

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return <div className="executive-tooltip"><strong>{label}</strong>{payload.map((item) => <div key={item.dataKey}>
    <span><i style={{ background: item.color || item.fill }} />{item.name}</span><b>{tooltipMoney(item.value)}</b>
  </div>)}</div>;
}

function PanelHeading({ title, description, trailing }) {
  return <div className="executive-panel-heading"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{trailing}</div>;
}

function Metric({ label, value, footnote, tone = '', href, linkLabel }) {
  return <article className={`executive-metric${tone ? ` is-${tone}` : ''}`}>
    <h2>{label}</h2><strong>{value}</strong><p>{footnote}</p>{href && <Link to={href}>{linkLabel}</Link>}
  </article>;
}

function RankingTable({ rows, emptyMessage }) {
  return <div className="executive-table-scroll" tabIndex="0" role="region" aria-label="Bảng kết quả theo cơ sở, cuộn ngang khi cần"><table className="executive-ranking-table">
    <thead><tr><th scope="col">Cơ sở</th><th scope="col">Thu nhập vận hành</th><th scope="col">Chi phí (theo ngày thanh toán)</th><th scope="col">Lợi nhuận vận hành</th><th scope="col">Lấp đầy (ước tính)</th><th scope="col">Trạng thái hiện tại</th></tr></thead>
    <tbody>{rows.length ? rows.map((row) => <tr key={row.id} className={Number(row.profit) < 0 || row.status !== 'ACTIVE' ? 'is-attention' : ''}>
      <th scope="row"><span className="executive-branch-name"><span>{row.name}<small>{row.code}</small></span></span></th>
      <td className="executive-number">{money(row.revenue)}</td>
      <td className="executive-number" title="Khoản chi vận hành theo ngày thanh toán; gồm lương và ứng lương.">{money(row.cost)}</td>
      <td className={`executive-number${Number(row.profit) < 0 ? ' is-loss' : ''}`}>{money(row.profit)}{Number(row.profit) < 0 && <small>Lỗ vận hành</small>}</td>
      <td>{percent(row.occupancy)}</td>
      <td><span className={`executive-status${row.status === 'ACTIVE' ? '' : ' is-paused'}`}><i />{row.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm ngừng'}</span></td>
    </tr>) : <tr><td className="executive-empty-cell" colSpan="6">{emptyMessage}</td></tr>}</tbody>
  </table></div>;
}

export default function ExecutiveDashboard() {
  const [searchParams, setSearchParams] = useSearchParams();
  const today = todayVn();
  const initial = presetRange('month', today, today);
  const from = searchParams.has('from') ? searchParams.get('from') : initial.from;
  const to = searchParams.has('to') ? searchParams.get('to') : initial.to;
  const requestedPeriod = periods.some(([key]) => key === searchParams.get('period')) ? searchParams.get('period') : searchParams.has('from') ? 'custom' : 'month';
  const matchingPreset = requestedPeriod !== 'custom' && validDate(from) && validDate(to)
    && presetRange(requestedPeriod, from, today).from === from && presetRange(requestedPeriod, from, today).to === to;
  const period = matchingPreset ? requestedPeriod : 'custom';
  const reportBranchId = searchParams.get('branchId') || '';
  const [branchOptions, setBranchOptions] = useState([]);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const rangeError = !validDate(from) || !validDate(to) ? 'Cần chọn đủ ngày bắt đầu và ngày kết thúc.'
    : from > today || to > today ? 'Không thể chọn ngày trong tương lai.'
      : from > to ? 'Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.' // BR-163 / MSG106.
        : (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000 + 1 > 366 ? 'Khoảng ngày tối đa là 366 ngày.' : '';
  useEffect(() => {
    if (searchParams.has('from') && searchParams.has('to') && searchParams.get('period') === period) return;
    const next = new URLSearchParams(searchParams);
    if (!next.has('from')) next.set('from', from);
    if (!next.has('to')) next.set('to', to);
    next.set('period', period);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams, from, to, period]);

  function updateScope(values) {
    const next = new URLSearchParams(searchParams);
    Object.entries(values).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    setSearchParams(next, { replace: true });
  }

  function choosePeriod(value) {
    updateScope(value === 'custom' ? { period: value } : { period: value, ...presetRange(value, today, today) });
  }

  function chooseAnchor(value) {
    if (validDate(value) && value <= today) updateScope({ period, ...presetRange(period, value, today) });
  }

  function movePeriod(direction) {
    const anchor = new Date(`${from}T00:00:00Z`);
    if (period === 'day') anchor.setUTCDate(anchor.getUTCDate() + direction);
    if (period === 'week') anchor.setUTCDate(anchor.getUTCDate() + 7 * direction);
    if (period === 'month') anchor.setUTCMonth(anchor.getUTCMonth() + direction);
    if (period === 'quarter') anchor.setUTCMonth(anchor.getUTCMonth() + 3 * direction);
    if (period === 'year') anchor.setUTCFullYear(anchor.getUTCFullYear() + direction);
    const next = anchor.toISOString().slice(0, 10);
    if (next <= today) chooseAnchor(next);
  }

  useEffect(() => {
    if (rangeError) { setData(null); setLoading(false); return undefined; }
    let active = true;
    setLoading(true); setError(''); setData(null);
    Promise.allSettled([
      api.get('/head-office/dashboard', { params: { from, to, ...(reportBranchId ? { branchId: reportBranchId } : {}) } }),
      api.get('/head-office/finance'),
    ]).then(([reportResult, financeResult]) => {
      if (!active) return;
      if (reportResult.status === 'rejected') { setData(null); setError(reportResult.reason.response?.data?.detail || 'Không tải được báo cáo chuỗi.'); return; }
      const report = reportResult.value.data;
      const branches = report.results || [];
      const totals = report.totals || {};
      setBranchOptions(report.branches || []);
      const requests = financeResult.status === 'fulfilled' ? financeResult.value.data.requests || [] : [];
      setData({
        // BR-102/103/104/105/107: giữ số KPI đã tính ở backend, không nhập cọc vào thu nhập hay CAPEX vào chi phí vận hành.
        kpis: { revenue: totals.revenue, heldDeposits: totals.heldDeposits, forfeitedDeposits: totals.forfeitedDeposits, bookings: totals.bookingCount, occupancy: totals.occupancyRate == null ? null : Number(totals.occupancyRate) / 100, cost: totals.expenses, grossProfit: totals.profit, margin: totals.margin, activeBranches: branches.filter((row) => row.status === 'ACTIVE').length, suspendedBranches: branches.filter((row) => row.status !== 'ACTIVE').length, branchTotal: branches.length },
        revenueByDay: (report.dailyRevenue || []).map((row) => {
          const date = String(row.date).slice(0, 10);
          return { label: `${date.slice(8, 10)}/${date.slice(5, 7)}`, revenue: Number(row.billRevenue) || 0 };
        }),
        ranking: [...branches].map((row) => ({ ...row, occupancy: row.occupancyRate == null ? null : Number(row.occupancyRate) / 100, cost: row.expenses })).sort((a, b) =>
          Number(Number(b.profit) < 0 || b.status !== 'ACTIVE') - Number(Number(a.profit) < 0 || a.status !== 'ACTIVE') || Number(a.profit) - Number(b.profit)),
        // BR-91: quản lý cấp cao chỉ duyệt đề nghị đang chờ cấp này.
        approvals: requests.filter((row) => row.status === 'WAITING_HEAD_OFFICE').map((row) => ({ id: row.id, code: row.code || row.id, category: row.category_name || 'Đề nghị chi', description: row.description || '', branch: row.branch_name || '—', amount: row.amount || 0 })),
        warnings: branches.filter((row) => Number(row.profit) < 0 || row.status !== 'ACTIVE').map((row) => ({ id: row.id, branch: row.name, profit: row.profit, loss: Number(row.profit) < 0, paused: row.status !== 'ACTIVE' })),
        approvalsAvailable: financeResult.status === 'fulfilled',
      });
      if (financeResult.status === 'rejected') setError('Không tải được danh sách đề nghị chi; các biểu đồ báo cáo vẫn đã được tải.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [from, to, reportBranchId, rangeError, revision]);
  const safeData = data || { kpis: {}, revenueByDay: [], ranking: [], approvals: [], warnings: [] };
  const { kpis } = safeData;
  const branchName = reportBranchId ? branchOptions.find((branch) => branch.id === reportBranchId)?.name || 'cơ sở đang chọn' : 'toàn chuỗi';
  const scopeLabel = `${dateLabel(from)} – ${dateLabel(to)} · ${branchName}`;
  const currentPreset = period !== 'custom' && presetRange(period, today, today).from === from && to === today;
  const periodLabel = period === 'custom' ? 'Khoảng tùy chỉnh' : currentPreset
    ? periods.find(([key]) => key === period)?.[1]
    : period === 'day' ? `Ngày ${dateLabel(from)}` : period === 'week' ? `Tuần ${dateLabel(from)} – ${dateLabel(to)}`
      : period === 'month' ? `Tháng ${from.slice(5, 7)}/${from.slice(0, 4)}`
        : period === 'quarter' ? `Quý ${Math.floor((Number(from.slice(5, 7)) - 1) / 3) + 1}/${from.slice(0, 4)}`
          : `Năm ${from.slice(0, 4)}`;
  // BR-103/107: số chi theo ngày thanh toán mở chi tiết cùng kỳ/cơ sở và cùng cách ghi nhận.
  const expenseQuery = new URLSearchParams({ screen: 'Báo cáo chi phí', from, to, period, basis: 'CASH', costType: 'OPEX', returnTo: 'executive' });
  if (reportBranchId) expenseQuery.set('branchId', reportBranchId);
  const expenseHref = `/quan-ly-he-thong?${expenseQuery}`;
  const marginText = Number(kpis.revenue) > 0 && kpis.margin != null
    ? `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(Number(kpis.margin))}%` : '—';
  const noActivity = data && Number(kpis.revenue) === 0 && Number(kpis.cost) === 0 && Number(kpis.bookings) === 0;
  const noReportMessage = loading ? 'Đang tải báo cáo…' : error ? 'Không tải được báo cáo.' : 'Chưa có dữ liệu báo cáo.';

  return <section className="manager-content executive-dashboard" aria-labelledby="executive-title" aria-busy={loading}>
    <header className="executive-heading">
      <div><h1 id="executive-title">Tổng quan toàn chuỗi</h1><p>Thu nhập vận hành chỉ gồm doanh thu hóa đơn hoàn tất. Cọc đang giữ và cọc bị tịch thu được trình bày riêng. Chi phí vận hành chỉ tính khoản đã thanh toán, không gồm chuyển quỹ nội bộ. Chi đầu tư được trình bày riêng.</p></div>
      <div className="executive-filters">
        <label className="executive-period">Kỳ báo cáo<select value={period} onChange={(event) => choosePeriod(event.target.value)}>{periods.map(([key, label]) => <option key={key} value={key}>{key === period && !currentPreset && key !== 'custom' ? label.replace(' này', '').replace('Hôm nay', 'Ngày') : label}</option>)}</select></label>
        {period !== 'custom' && <><label className="executive-period">Chọn ngày thuộc kỳ<input type="date" value={to} max={today} onChange={(event) => chooseAnchor(event.target.value)} /></label>
          <div className="executive-period-nav" aria-label="Chuyển kỳ báo cáo"><button type="button" onClick={() => movePeriod(-1)}>Kỳ trước</button><button type="button" disabled={currentPreset} onClick={() => movePeriod(1)}>Kỳ sau</button><button type="button" disabled={currentPreset} onClick={() => choosePeriod(period)}>Kỳ này</button></div></>}
        <label className="executive-period">Từ ngày<input type="date" value={from} max={today} onChange={(event) => updateScope({ from: event.target.value, period: 'custom' })} /></label>
        <label className="executive-period">Đến ngày<input type="date" value={to} max={today} onChange={(event) => updateScope({ to: event.target.value, period: 'custom' })} /></label>
        <label className="executive-period">Phạm vi báo cáo<select value={reportBranchId} onChange={(event) => updateScope({ branchId: event.target.value })}><option value="">Toàn chuỗi</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name} · {branch.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}</option>)}</select></label>
      </div>
    </header>
    <p className="executive-scope">Kỳ đang xem: {periodLabel} · {scopeLabel}. Số liệu tài chính theo khoảng lọc; việc chờ duyệt và trạng thái cơ sở là hiện tại.</p>
    {loading && <p role="status">Đang tải báo cáo theo kỳ và cơ sở đã chọn…</p>}
    {rangeError && <p role="alert">{rangeError}</p>}
    {error && <div className="executive-feedback" role="alert"><span>{error}</span><button type="button" onClick={() => setRevision((value) => value + 1)}>Thử lại</button></div>}

    <div className="executive-kpis" aria-label="Kết quả chính trong kỳ">
      <Metric label="Thu nhập vận hành" value={data ? money(kpis.revenue) : '—'} footnote="Từ hóa đơn hoàn tất; không cộng tiền cọc." />
      <Metric label="Chi phí vận hành (theo ngày thanh toán)" value={data ? money(kpis.cost) : '—'} footnote="Gồm lương và ứng lương đã chi; không gồm chi đầu tư." href={expenseHref} linkLabel="Xem khoản chi theo ngày thanh toán" />
      <Metric label="Lợi nhuận vận hành" value={data ? money(kpis.grossProfit) : '—'} footnote={`Thu nhập trừ chi phí vận hành · Biên lợi nhuận: ${data ? marginText : '—'}`} tone={!data ? '' : Number(kpis.grossProfit) < 0 ? 'negative' : 'positive'} />
      <Metric label="Tỷ lệ lấp đầy (ước tính)" value={data ? percent(kpis.occupancy) : '—'} footnote="Giờ có khách ÷ (phòng khả dụng hiện tại × ngày lọc × 12 giờ, 08–20h)." />
    </div>

    <div className="executive-support" aria-label="Chỉ số hỗ trợ">
      <Metric label="Lượt đặt không hủy · trong kỳ" value={data ? new Intl.NumberFormat('vi-VN').format(kpis.bookings || 0) : '—'} footnote="Theo ngày bắt đầu dự kiến; gồm khách không đến." />
      <Metric label="Cọc đang giữ · cuối kỳ" value={data ? money(kpis.heldDeposits) : '—'} footnote="Số dư cọc tại cuối khoảng; không thuộc doanh thu." />
      <Metric label="Cọc bị tịch thu · trong kỳ" value={data ? money(kpis.forfeitedDeposits) : '—'} footnote="Trình bày riêng khỏi doanh thu hóa đơn." />
      <Metric label="Cơ sở · trạng thái hiện tại" value={data ? `${kpis.activeBranches} hoạt động / ${kpis.suspendedBranches} tạm ngừng` : '—'} footnote="Trong phạm vi cơ sở đã chọn; không theo bộ lọc ngày." />
    </div>
    {noActivity && <p className="executive-no-activity" role="status">Không có hóa đơn hoàn tất, chi phí đã thanh toán hoặc lượt đặt trong khoảng này. Trạng thái cơ sở và việc chờ duyệt vẫn là dữ liệu hiện tại.</p>}

    <div className="executive-work-grid">
      <article className="executive-panel executive-panel-trend">
        <PanelHeading title="Xu hướng thu nhập vận hành từ hóa đơn hoàn tất" description={`Trong kỳ ${scopeLabel} · Theo ngày hoàn tất hóa đơn; không gồm cọc.`} />
        <div className="executive-chart-area executive-chart-area-tall" role={safeData.revenueByDay.length ? 'img' : undefined} aria-label={safeData.revenueByDay.length ? `Xu hướng thu nhập vận hành từ hóa đơn hoàn tất trong kỳ ${scopeLabel}` : undefined}>
          {safeData.revenueByDay.length ? <ResponsiveContainer width="100%" height="100%">
            <LineChart data={safeData.revenueByDay} margin={{ top: 12, right: 18, left: 4, bottom: 4 }}>
              <CartesianGrid vertical={false} stroke="var(--ed-line)" strokeDasharray="3 5" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={24} tick={{ fill: 'var(--ed-copy)', fontSize: 11 }} />
              <YAxis tickFormatter={axisMoney} tickLine={false} axisLine={false} width={54} tick={{ fill: 'var(--ed-copy)', fontSize: 11 }} />
              <Tooltip content={<ChartTooltip />} />
              <Line type="monotone" dataKey="revenue" name="Thu nhập vận hành từ hóa đơn hoàn tất" stroke="var(--ed-primary)" strokeWidth={2.5} dot={false} activeDot={{ r: 5 }} />
            </LineChart>
          </ResponsiveContainer> : <p className="executive-empty">{data ? 'Chưa có hóa đơn hoàn tất trong khoảng đã chọn.' : noReportMessage}</p>}
        </div>
      </article>

      <div className="executive-action-column" aria-label="Việc cần xử lý">
        <article className="executive-panel executive-approvals">
          <PanelHeading title="Việc cần duyệt" description="Trạng thái hiện tại của toàn chuỗi · không theo kỳ hoặc cơ sở đang lọc." trailing={<span className="executive-pending-count">{loading || !data?.approvalsAvailable ? '—' : safeData.approvals.length} chờ duyệt</span>} />
          {safeData.approvals.length ? <div className="executive-approval-list">{safeData.approvals.map((item) => <div className="executive-approval-row" key={item.id}>
            <div><strong>{item.branch} · {item.code}</strong><span>{item.category}{item.description ? ` · ${item.description}` : ''}</span></div><b>{money(item.amount)}</b>
          </div>)}</div> : <p className="executive-empty">{loading ? 'Đang tải đề nghị chi…' : !data ? noReportMessage : data.approvalsAvailable ? 'Không có đề nghị đang chờ duyệt.' : 'Không tải được danh sách đề nghị chi.'}</p>}
          <Link className="executive-task-link" to={`/quan-ly-he-thong?${new URLSearchParams({ screen: 'Phê duyệt chi', tab: 'expense_requests' })}`}>Mở phê duyệt chi</Link>
        </article>
        <article className="executive-panel executive-warnings">
          <PanelHeading title="Cơ sở cần chú ý" description={`Lỗ vận hành trong kỳ ${scopeLabel}; tạm ngừng theo trạng thái hiện tại.`} trailing={<span className="executive-warning-count">{loading || !data ? '—' : safeData.warnings.length} cơ sở</span>} />
          {safeData.warnings.length ? <ul className="executive-warning-list">{safeData.warnings.map((item) => <li key={item.id} className={item.loss ? 'is-critical' : ''}>
            <span className="executive-warning-mark" aria-hidden="true" /><div><strong>{item.branch}</strong><p>{item.loss && <>Lỗ vận hành {money(item.profit)} trong kỳ. </>}{item.paused && <>Cơ sở tạm ngừng hiện tại.</>}</p></div>
          </li>)}</ul> : <p className="executive-empty">{loading ? 'Đang tải trạng thái cơ sở…' : data ? 'Không có cơ sở lỗ vận hành hoặc tạm ngừng trong phạm vi này.' : noReportMessage}</p>}
        </article>
      </div>
    </div>

    <article className="executive-panel executive-ranking">
      <PanelHeading title="Kết quả theo cơ sở" description={`Trong kỳ ${scopeLabel} · Cơ sở lỗ hoặc tạm ngừng được đưa lên trước; trạng thái hoạt động là hiện tại.`} trailing={<span className="executive-count">{safeData.ranking.length} cơ sở</span>} />
      <RankingTable rows={safeData.ranking} emptyMessage={data ? 'Không có cơ sở trong phạm vi đã chọn.' : noReportMessage} />
    </article>
  </section>;
}
