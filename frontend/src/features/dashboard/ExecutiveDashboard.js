import { PaginatedTable, PaginatedList } from '../../shared/components/Pagination';
import { useEffect, useRef, useState } from 'react';
import api from '../../api/client';
import { financeApi } from '../finance/services/financeApi';
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import CustomerSourcesChart from './CustomerSourcesChart';
import PeriodControls, { initialPeriodValue, vietnamToday } from './DashboardPeriodControls';
import './executive-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(Number(value) * 100)}%`;
const changeText = (value) => value == null ? 'Chưa có doanh thu kỳ trước để so sánh' : `${value >= 0 ? '+' : ''}${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}% so kỳ trước`;
const tooltipMoney = (value) => money(value);
const axisMoney = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value / 1_000_000)} tr`;

function periodRange(type, value) {
  const today = vietnamToday();
  if (type === 'DAY') return { from: value, to: value };
  if (type === 'YEAR') return { from: `${value}-01-01`, to: `${value === today.slice(0, 4) ? today : `${value}-12-31`}` };
  const start = new Date(`${value}-01T00:00:00Z`);
  start.setUTCMonth(start.getUTCMonth() + 1);
  const end = new Date(start.getTime() - 86400000).toISOString().slice(0, 10);
  return { from: `${value}-01`, to: end > today ? today : end };
}

function periodDescription(type, value) {
  return type === 'DAY' ? `ngày ${value}` : type === 'YEAR' ? `năm ${value}` : `tháng ${value}`;
}

function financialWarnings(overview, type, value, scope = 'Toàn chuỗi', branchId = '') {
  const totals = overview?.totals || {};
  const changes = overview?.changes || {};
  const period = periodDescription(type, value);
  const range = periodRange(type, value);
  const context = { branchId, from: range.from, to: range.to };
  const warnings = [];
  const revenueChange = Number(changes.revenue);
  const profitChange = Number(changes.profit);
  if (Number(totals.profit) < 0) warnings.push({ ...context, id: 'finance-profit-loss', branch: scope, kind: `Lợi nhuận âm · ${period}`, detail: `Lỗ ${money(Math.abs(Number(totals.profit)))} trong kỳ.`, level: 'critical' });
  else if (Number.isFinite(profitChange) && profitChange <= -20) warnings.push({ ...context, id: 'finance-profit-drop', branch: scope, kind: `Lợi nhuận giảm ${Math.abs(profitChange).toFixed(0)}% · ${period}`, detail: `Còn ${money(totals.profit)} so với kỳ trước.`, level: 'warning' });
  if (Number.isFinite(revenueChange) && revenueChange <= -20) warnings.push({ ...context, id: 'finance-revenue-drop', branch: scope, kind: `Doanh thu giảm ${Math.abs(revenueChange).toFixed(0)}% · ${period}`, detail: `Đạt ${money(totals.revenue)} so với kỳ trước.`, level: 'warning' });
  return warnings;
}

function overviewParams(type, value, branchId) {
  const range = periodRange(type, value);
  return { ...range, compare: 'prev', granularity: type === 'YEAR' ? 'month' : 'day', ...(branchId ? { branchIds: [branchId] } : {}) };
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

function Metric({ label, value, footnote, tone = '', icon }) {
  const iconPaths = {
    revenue: <><path d="M4 19V5m0 14h16"/><path d="m7 15 4-4 3 2 5-6"/></>,
    booking: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18m4 5h.01M9 15h.01"/></>,
    occupancy: <><path d="M3 20V8l9-5 9 5v12"/><path d="M7 20v-6h10v6M8 9h.01M12 9h.01M16 9h.01"/></>,
    cost: <><circle cx="12" cy="12" r="9"/><path d="M15 9.5c-.7-.8-1.7-1.2-3-1.2-1.6 0-2.6.8-2.6 2 0 3.1 5.2 1.3 5.2 4.5 0 1.3-1.1 2.2-2.8 2.2-1.3 0-2.5-.5-3.3-1.4M12 6v12"/></>,
    profit: <><path d="M4 18 9 13l3 2 7-8"/><path d="M14 7h5v5M4 21h16"/></>,
    branches: <><path d="M4 21V5l8-3 8 3v16M9 21v-5h6v5M8 8h.01M12 8h.01M16 8h.01M8 11h.01M12 11h.01M16 11h.01"/></>,
  };
  return <article className={`executive-metric${tone ? ` is-${tone}` : ''}`}>
    <span className="executive-metric-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{iconPaths[icon]}</svg></span>
    <div className="executive-metric-copy"><h2>{label}</h2><strong>{value}</strong><p>{footnote}</p></div>
  </article>;
}

function OperationalAlertBubble({ warnings, onAlertNavigate }) {
  const [expanded, setExpanded] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [position, setPosition] = useState(null);
  const bubbleRef = useRef(null);
  const dragRef = useRef(null);
  const suppressClickRef = useRef(false);
  const index = activeIndex % warnings.length;
  const alert = warnings[index];
  const handlePointerDown = (event) => {
    const bounds = bubbleRef.current?.getBoundingClientRect();
    if (!bounds) return;
    dragRef.current = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, left: bounds.left, top: bounds.top, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const handlePointerMove = (event) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (Math.abs(dx) + Math.abs(dy) > 5) drag.moved = true;
    if (!drag.moved) return;
    const bounds = bubbleRef.current?.getBoundingClientRect();
    const width = bounds?.width || 340;
    const height = bounds?.height || 60;
    setPosition({
      x: Math.max(8, Math.min(window.innerWidth - width - 8, drag.left + dx)),
      y: Math.max(8, Math.min(window.innerHeight - height - 8, drag.top + dy)),
    });
  };
  const handlePointerUp = (event) => {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    suppressClickRef.current = dragRef.current.moved;
    dragRef.current = null;
  };
  const handlePillClick = () => {
    if (suppressClickRef.current) { suppressClickRef.current = false; return; }
    setExpanded((open) => !open);
  };
  useEffect(() => {
    if (!position || !bubbleRef.current) return undefined;
    const bounds = bubbleRef.current.getBoundingClientRect();
    const x = Math.max(8, Math.min(window.innerWidth - bounds.width - 8, position.x));
    const y = Math.max(8, Math.min(window.innerHeight - bounds.height - 8, position.y));
    if (x !== position.x || y !== position.y) setPosition({ x, y });
    return undefined;
  }, [expanded, position]);
  if (!warnings.length) return null;
  return <aside ref={bubbleRef} id="executive-alert-panel" className={`executive-alert-float is-${alert.level}${position ? ' is-moved' : ''}`} style={position ? { left: position.x, top: position.y } : undefined} aria-label="Cảnh báo vận hành toàn chuỗi">
    <button type="button" className="executive-alert-pill" onPointerDown={handlePointerDown} onPointerMove={handlePointerMove} onPointerUp={handlePointerUp} onPointerCancel={handlePointerUp} onClick={handlePillClick} aria-expanded={expanded} aria-controls="executive-alert-details" title="Kéo để di chuyển · chạm để xem cảnh báo">
      <span className="executive-alert-pill-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 2.8 19h18.4L12 3Z"/><path d="M12 9v4m0 3h.01"/></svg></span>
      <span className="executive-alert-pill-copy"><small>{alert.level === 'critical' ? 'CẢNH BÁO KHẨN' : 'CẢNH BÁO VẬN HÀNH'}</small><strong>{alert.kind}</strong></span>
      <span className="executive-alert-pill-count">{warnings.length}</span><span className="executive-alert-pill-chevron" aria-hidden="true">{expanded ? '−' : '+'}</span>
    </button>
    {expanded && <div id="executive-alert-details" className="executive-alert-card" role={alert.level === 'critical' ? 'alert' : 'status'} aria-live={alert.level === 'critical' ? 'assertive' : 'polite'} aria-atomic="true" key={alert.id}>
      <span className="executive-alert-tail" aria-hidden="true" />
      <PaginatedList as="div" pageSize={5} className="executive-alert-list" aria-label="Danh sách cảnh báo">{warnings.map((item, itemIndex) => <button type="button" key={item.id} className={itemIndex === index ? 'is-active' : ''} onClick={() => { setActiveIndex(itemIndex); onAlertNavigate?.(item); }} aria-label={`Mở cảnh báo ${item.kind} tại ${item.branch}`}>
        <i className={item.level === 'critical' ? 'is-critical' : ''} aria-hidden="true" /><span><strong>{item.kind}</strong><small>{item.branch}</small><em>{item.detail}</em></span><b aria-hidden="true">→</b>
      </button>)}</PaginatedList>
      <div className="executive-alert-message"><strong>{alert.branch}</strong><p>{alert.detail}</p></div>
      <footer className="executive-alert-footer"><span>{index + 1} / {warnings.length}</span><span>Chọn cảnh báo để mở</span></footer>
    </div>}
  </aside>;
}

function RankingTable({ rows, emptyMessage }) {
  return <div className="executive-table-scroll"><PaginatedTable pageSize={5} className="executive-ranking-table">
    <thead><tr><th>Cơ sở</th><th>Thu nhập vận hành</th><th>Lượt đặt không hủy</th><th>Lấp đầy</th><th>Chi phí</th><th>Trạng thái</th></tr></thead>
    <tbody>{rows.length ? rows.map((row, index) => <tr key={row.id}>
      <td><span className="executive-branch-name"><span className="executive-rank">{String(index + 1).padStart(2, '0')}</span><span>{row.name}<small>{row.code}</small></span></span></td>
      <td className="executive-number">{money(row.revenue)}</td><td className="executive-number">{new Intl.NumberFormat('vi-VN').format(row.bookings)}</td>
      <td><span className="executive-occupancy-value">{percent(row.occupancy)}</span>{row.occupancy != null && <span className="executive-mini-track"><i style={{ width: `${row.occupancy * 100}%` }} /></span>}</td>
      <td className="executive-number">{money(row.cost)}</td>
      <td><span className={`executive-status${row.status === 'ACTIVE' ? '' : ' is-paused'}`}><i />{row.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm ngừng'}</span></td>
    </tr>) : <tr><td className="executive-empty-cell" colSpan="6">{emptyMessage}</td></tr>}</tbody>
  </PaginatedTable></div>;
}

export default function ExecutiveDashboard({ onNavigate, onAlertNavigate }) {
  const [periodType, setPeriodType] = useState('MONTH');
  const [periodValue, setPeriodValue] = useState(() => initialPeriodValue('MONTH'));
  const [incomePeriodType, setIncomePeriodType] = useState('MONTH');
  const [incomePeriodValue, setIncomePeriodValue] = useState(() => initialPeriodValue('MONTH'));
  const [trendPeriodType, setTrendPeriodType] = useState('MONTH');
  const [trendPeriodValue, setTrendPeriodValue] = useState(() => initialPeriodValue('MONTH'));
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [reportBranchId, setReportBranchId] = useState('');
  const [incomeBranchId, setIncomeBranchId] = useState('');
  const [branchOptions, setBranchOptions] = useState([]);
  const [incomeRows, setIncomeRows] = useState([]);
  const [trendRows, setTrendRows] = useState([]);
  const [trendLoading, setTrendLoading] = useState(true);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setData(null);
    const selectedRange = periodRange(periodType, periodValue);
    const today = vietnamToday();
    const operationsMonth = periodType === 'YEAR'
      ? `${periodValue}-${periodValue === today.slice(0, 4) ? today.slice(5, 7) : '12'}`
      : selectedRange.from.slice(0, 7);
    Promise.allSettled([
      api.get('/head-office/dashboard', { params: { month: operationsMonth, ...(reportBranchId ? { branchId: reportBranchId } : {}) } }),
      api.get('/head-office/finance'),
      financeApi.financialOverview(overviewParams(periodType, periodValue, reportBranchId)),
    ]).then(([reportResult, financeResult, overviewResult]) => {
      if (!active) return;
      if (reportResult.status === 'rejected' || overviewResult.status === 'rejected') {
        setData(null);
        const cause = overviewResult.status === 'rejected' ? overviewResult.reason : reportResult.reason;
        setError(cause.response?.data?.detail || 'Không tải được báo cáo chuỗi.');
        return;
      }
      const report = reportResult.value.data;
      const overview = overviewResult.value;
      const monthlyBranches = report.results || [];
      const branches = overview.branches || [];
      const monthlyById = new Map(monthlyBranches.map((row) => [String(row.id), row]));
      if (!reportBranchId) setBranchOptions(report.branches?.length ? report.branches : monthlyBranches.filter((row) => row.status === 'ACTIVE'));
      const requests = financeResult.status === 'fulfilled' ? financeResult.value.data.requests || [] : [];
      const totals = overview.totals || {};
      const monthlyTotals = report.totals || {};
      const selectedBranchName = reportBranchId ? (report.branches || []).find((branch) => String(branch.id) === String(reportBranchId))?.name : null;
      const financeAlerts = financialWarnings(overview, periodType, periodValue, selectedBranchName || 'Toàn chuỗi', reportBranchId);
      const operationalAlerts = report.warnings || [];
      const revenueRows = branches.map((row) => {
        const monthlyRow = monthlyById.get(String(row.id)) || {};
        return { ...row, shortName: row.code, revenue: Number(row.revenue) || 0, occupancy: monthlyRow.occupancyRate == null ? null : Number(monthlyRow.occupancyRate) / 100,
          bookings: monthlyRow.bookingCount || 0, cost: Number(row.expenses) || 0 };
      });
      setData({
        kpis: { revenue: totals.revenue, billRevenue: totals.billRevenue, forfeitedDeposits: totals.forfeitedDeposits, revenueChange: overview.changes?.revenue, bookings: monthlyTotals.bookingCount, occupancy: monthlyTotals.occupancyRate == null ? null : Number(monthlyTotals.occupancyRate) / 100, cost: totals.expenses, grossProfit: totals.profit, activeBranches: branches.filter((row) => row.status === 'ACTIVE').length, suspendedBranches: branches.filter((row) => row.status !== 'ACTIVE').length, branchTotal: branches.length, contextMonth: operationsMonth },
        revenueByBranch: revenueRows,
        occupancyByBranch: monthlyBranches.map((row) => ({ ...row, occupancy: row.occupancyRate == null ? 0 : Number(row.occupancyRate) / 100 })),
        revenueByDay: (overview.series || []).map((row) => ({ ...row, label: row.label, roomRevenue: Number(row.revenue) || 0, fnbRevenue: Number(row.profit) || 0, overtimeRevenue: 0, otherRevenue: 0 })),
        ranking: [...revenueRows].sort((a, b) => b.revenue - a.revenue),
        approvals: requests.filter((row) => ['WAITING_HEAD_OFFICE', 'WAITING_APPROVAL', 'WAITING_MANAGER'].includes(row.status)).map((row) => ({ id: row.id, category: row.description || row.categoryName || 'Đề nghị chi', branch: row.branchName || '—', amount: row.amount || 0 })),
        warnings: [...financeAlerts, ...operationalAlerts],
        approvalsAvailable: financeResult.status === 'fulfilled',
      });
      if (financeResult.status === 'rejected') setError('Không tải được danh sách đề nghị chi; các biểu đồ báo cáo vẫn đã được tải.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [periodType, periodValue, reportBranchId]);

  useEffect(() => {
    let active = true;
    financeApi.financialOverview(overviewParams(incomePeriodType, incomePeriodValue, incomeBranchId)).then((result) => {
      if (active) setIncomeRows((result.branches || []).map((row) => ({ ...row, revenue: Number(row.revenue) || 0 })));
    }).catch(() => { if (active) setIncomeRows([]); });
    return () => { active = false; };
  }, [incomePeriodType, incomePeriodValue, incomeBranchId]);

  useEffect(() => {
    let active = true;
    setTrendLoading(true);
    financeApi.financialOverview(overviewParams(trendPeriodType, trendPeriodValue)).then((result) => {
      if (active) setTrendRows(result.series || []);
    }).catch(() => { if (active) setTrendRows([]); }).finally(() => { if (active) setTrendLoading(false); });
    return () => { active = false; };
  }, [trendPeriodType, trendPeriodValue]);
  const safeData = data || { kpis: {}, revenueByBranch: [], occupancyByBranch: [], revenueByDay: [], ranking: [], approvals: [], warnings: [] };
  const selectedBranch = incomeRows.find((branch) => branch.id === selectedBranchId);
  const { kpis } = safeData;
  const rankedRevenue = [...incomeRows].sort((a, b) => b.revenue - a.revenue);
  const chartRevenueRows = rankedRevenue.slice(0, 8);
  const leadingBranch = rankedRevenue[0];
  const activeBranchCount = Math.max(1, Number(kpis.activeBranches) || 0);
  const averageBranchRevenue = data ? (Number(kpis.revenue) || 0) / activeBranchCount : null;
  const averageDailyRevenue = data && safeData.revenueByDay.length
    ? safeData.revenueByDay.reduce((sum, row) => sum + row.roomRevenue, 0) / safeData.revenueByDay.length
    : null;
  const rankedOccupancy = [...safeData.occupancyByBranch].sort((a, b) => b.occupancy - a.occupancy);
  const occupancyAverage = rankedOccupancy.length
    ? rankedOccupancy.reduce((sum, row) => sum + row.occupancy, 0) / rankedOccupancy.length
    : null;
  const noReportMessage = loading ? 'Đang tải báo cáo…' : 'Chưa có dữ liệu báo cáo.';

  const selectChartBranch = (event) => {
    const row = event?.activePayload?.[0]?.payload;
    if (row?.id) setSelectedBranchId(row.id);
  };

  return <section className="manager-content executive-dashboard" aria-labelledby="executive-title" aria-busy={loading}>
    <header className="executive-heading">
      <div className="executive-heading-copy"><span className="executive-eyebrow">TRUNG TÂM ĐIỀU HÀNH</span><h1 id="executive-title">Tổng quan toàn chuỗi</h1><p>Hiệu suất, doanh thu và ưu tiên trong kỳ.</p><span className="executive-data-status"><i /> Số liệu vận hành</span></div>
      <div className="executive-filters">
        <PeriodControls id="tổng quan" type={periodType} value={periodValue} onTypeChange={setPeriodType} onValueChange={setPeriodValue} />
        <label className="executive-period">Phạm vi báo cáo<select value={reportBranchId} onChange={(event) => setReportBranchId(event.target.value)}><option value="">Toàn chuỗi</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
      </div>
    </header>
    <OperationalAlertBubble warnings={safeData.warnings} onAlertNavigate={onAlertNavigate} />
    {loading && <p role="status">Đang tải báo cáo…</p>}{error && <p role="alert">{error}</p>}

    <div className="executive-kpis" aria-label="Chỉ số chính">
      <Metric icon="revenue" label="Thu nhập vận hành" value={data ? money(kpis.revenue) : '—'} footnote={data ? `${changeText(kpis.revenueChange)} · hóa đơn và cọc` : 'Chưa có dữ liệu'} tone={kpis.revenueChange == null ? '' : kpis.revenueChange >= 0 ? 'positive' : 'negative'} />
      <Metric icon="occupancy" label="Tỷ lệ lấp đầy" value={data ? percent(kpis.occupancy) : '—'} footnote={`Tháng ${kpis.contextMonth || ''}`} />
      <Metric icon="cost" label="Chi phí vận hành" value={data ? money(kpis.cost) : '—'} footnote="Các khoản chi đã thanh toán" />
      <Metric icon="profit" label="Lợi nhuận vận hành" value={data ? money(kpis.grossProfit) : '—'} footnote="Thu nhập trừ chi phí vận hành" tone={!data ? '' : kpis.grossProfit >= 0 ? 'positive' : 'negative'} />
    </div>

    <section className="executive-insights" aria-label="Tóm tắt điều hành">
      <div className="executive-insights-heading"><div><span>NHÌN NHANH</span><h2>Điểm chính trong kỳ</h2></div></div>
      <div className="executive-insight-grid">
        <article className="executive-insight-card is-leader"><span>Dẫn đầu doanh thu</span><strong>{data ? leadingBranch?.name || 'Chưa có dữ liệu' : '—'}</strong><small>{data && leadingBranch ? money(leadingBranch.revenue) : '—'}</small></article>
        <article className="executive-insight-card"><span>Bình quân / cơ sở</span><strong>{data ? money(averageBranchRevenue) : '—'}</strong><small>{kpis.activeBranches || 0} cơ sở hoạt động</small></article>
        <article className="executive-insight-card"><span>Bình quân / {periodType === 'YEAR' ? 'tháng' : 'ngày'}</span><strong>{data ? money(averageDailyRevenue) : '—'}</strong><small>{safeData.revenueByDay.length} {periodType === 'YEAR' ? 'tháng' : 'ngày'} ghi nhận</small></article>
        <article className={`executive-insight-card ${(safeData.warnings.length || safeData.approvals.length) ? 'is-attention' : 'is-clear'}`}>
          <span>Ưu tiên quản lý</span><strong>{data ? safeData.warnings.length + safeData.approvals.length : '—'} việc</strong>
          <small>{safeData.warnings.length} cảnh báo · {safeData.approvals.length} đề nghị chờ duyệt</small>
        </article>
      </div>
    </section>

    <div className="executive-chart-grid">
      <article className="executive-panel executive-panel-branch-revenue">
        <PanelHeading title="Thu nhập theo cơ sở" description={incomeBranchId ? 'Kết quả cơ sở đang chọn' : '8 cơ sở dẫn đầu'} trailing={<span className="executive-panel-kicker">{incomeBranchId ? 'CƠ SỞ' : 'TOP 8'}</span>} />
        <div className="executive-chart-controls">
          <label className="executive-compact-filter"><span>Phạm vi</span><select aria-label="Phạm vi thu nhập theo cơ sở" value={incomeBranchId} onChange={(event) => { setIncomeBranchId(event.target.value); setSelectedBranchId(''); }}><option value="">Tất cả chuỗi</option>{branchOptions.map((branch) => <option value={branch.id} key={branch.id}>{branch.name}</option>)}</select></label>
          <PeriodControls id="thu nhập theo cơ sở" type={incomePeriodType} value={incomePeriodValue} onTypeChange={setIncomePeriodType} onValueChange={setIncomePeriodValue} />
        </div>
        <div className="executive-chart-area executive-chart-area-branches" style={{ height: Math.max(250, chartRevenueRows.length * 34) }}>
          {chartRevenueRows.length ? <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartRevenueRows} layout="vertical" margin={{ top: 6, right: 28, left: 0, bottom: 6 }} onClick={selectChartBranch}>
              <CartesianGrid horizontal={false} stroke="#e8eaf1" strokeDasharray="3 5" />
              <XAxis type="number" tickFormatter={axisMoney} tickLine={false} axisLine={false} tick={{ fill: '#59657a', fontSize: 12 }} />
              <YAxis type="category" dataKey="name" width={140} tickLine={false} axisLine={false} tick={{ fill: '#39445a', fontSize: 11 }} />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: '#f5f4fa' }} />
              <Bar dataKey="revenue" name="Thu nhập vận hành" fill="#6a3ee8" radius={[0, 4, 4, 0]} barSize={18} cursor="pointer" onClick={(item) => item?.payload?.id && setSelectedBranchId(item.payload.id)} />
            </BarChart>
          </ResponsiveContainer> : <p className="executive-empty">{data ? 'Không có dữ liệu cơ sở trong kỳ báo cáo.' : noReportMessage}</p>}
        </div>
        {selectedBranch && <div className="executive-branch-insight" aria-live="polite">
          <div><strong>{selectedBranch.name}</strong><small>{selectedBranch.code} · {selectedBranch.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}</small></div>
          <span>Thu nhập vận hành <b>{money(selectedBranch.revenue)}</b></span><span>Lượt đặt không hủy <b>{new Intl.NumberFormat('vi-VN').format(selectedBranch.bookings)}</b></span><span>Lấp đầy <b>{percent(selectedBranch.occupancy)}</b></span><span>Chi phí vận hành <b>{money(selectedBranch.cost)}</b></span>
          <button type="button" aria-label="Đóng chi tiết cơ sở" onClick={() => setSelectedBranchId('')}>Đóng</button>
        </div>}
      </article>

      <article className="executive-panel executive-panel-trend">
        <PanelHeading title="Xu hướng doanh thu" description="Doanh thu và lợi nhuận" />
        <div className="executive-chart-controls executive-trend-controls"><PeriodControls id="xu hướng doanh thu" type={trendPeriodType} value={trendPeriodValue} onTypeChange={setTrendPeriodType} onValueChange={setTrendPeriodValue} /></div>
        <div className="executive-chart-summary"><span><small>Bình quân / {trendPeriodType === 'YEAR' ? 'tháng' : 'ngày'}</small><strong>{trendRows.length ? money(trendRows.reduce((sum, row) => sum + (Number(row.revenue) || 0), 0) / trendRows.length) : '—'}</strong></span><span><small>Số kỳ</small><strong>{trendRows.length}</strong></span></div>
        <div className="executive-chart-area executive-chart-area-tall">
          {trendRows.length ? <ResponsiveContainer width="100%" height="100%">
            <LineChart data={trendRows} margin={{ top: 10, right: 14, left: 3, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="#e8eaf1" strokeDasharray="3 5" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={18} tick={{ fill: '#758096', fontSize: 10 }} />
              <YAxis tickFormatter={axisMoney} tickLine={false} axisLine={false} width={48} tick={{ fill: '#758096', fontSize: 10 }} />
              <Tooltip content={<ChartTooltip />} />
              <Legend verticalAlign="top" align="right" height={32} iconType="circle" wrapperStyle={{ fontSize: 11 }} />
              <Line type="monotone" dataKey="revenue" name="Doanh thu" stroke="#176b68" strokeWidth={2.8} dot={{ r: 2, strokeWidth: 0 }} activeDot={{ r: 5 }} />
              <Line type="monotone" dataKey="profit" name="Lợi nhuận" stroke="#6a3ee8" strokeWidth={2.4} dot={{ r: 2, strokeWidth: 0 }} activeDot={{ r: 5 }} />
              <Line type="monotone" dataKey="comparisonRevenue" name="Doanh thu kỳ trước" stroke="#8bb7b3" strokeWidth={1.7} strokeDasharray="5 4" dot={false} />
              <Line type="monotone" dataKey="comparisonProfit" name="Lợi nhuận kỳ trước" stroke="#b7a7e2" strokeWidth={1.7} strokeDasharray="5 4" dot={false} />
            </LineChart>
          </ResponsiveContainer> : <p className="executive-empty">{trendLoading ? 'Đang tải…' : 'Chưa có dữ liệu.'}</p>}
        </div>
      </article>

      <CustomerSourcesChart headOffice className="executive-panel executive-panel-sources" />

      <article className="executive-panel executive-panel-occupancy">
        <PanelHeading title="Tỷ lệ lấp đầy" description="Số giờ phòng có khách · khung 08:00–20:00" trailing={<span className="executive-panel-kicker">BÌNH QUÂN {occupancyAverage == null ? '—' : percent(occupancyAverage)}</span>} />
        {rankedOccupancy.length ? <PaginatedList as="div" pageSize={10} className="executive-occupancy-list" aria-label="Tỷ lệ lấp đầy từng cơ sở">{rankedOccupancy.map((branch, index) => <div className="executive-occupancy-row" key={branch.id}>
          <span className="executive-occupancy-rank">{String(index + 1).padStart(2, '0')}</span><span className="executive-occupancy-name" title={branch.name}>{branch.name}<small>{branch.code}</small></span>
          <span className="executive-occupancy-track" aria-hidden="true"><i className={branch.occupancy < occupancyAverage ? 'is-below-average' : ''} style={{ width: `${Math.max(0, Math.min(100, branch.occupancy * 100))}%` }} /></span><strong>{percent(branch.occupancy)}</strong>
        </div>)}</PaginatedList> : <p className="executive-empty">{data ? 'Chưa có dữ liệu lấp đầy trong kỳ.' : noReportMessage}</p>}
      </article>
    </div>

    <article className="executive-panel executive-ranking">
      <PanelHeading title="Xếp hạng cơ sở" description="So sánh kết quả kinh doanh và trạng thái vận hành." trailing={<span className="executive-count">{safeData.ranking.length} cơ sở</span>} />
      <RankingTable rows={safeData.ranking} emptyMessage={data ? 'Không có cơ sở trong kỳ báo cáo.' : noReportMessage} />
    </article>

    <div className="executive-bottom-grid">
      <article className="executive-panel executive-approvals">
        <PanelHeading title="Việc cần làm" description="Các đề nghị cần quản lý cấp cao xem xét và xử lý." trailing={<span className="executive-pending-count">{safeData.approvals.length} chờ duyệt</span>} />
        {safeData.approvals.length ? <PaginatedList as="div" pageSize={5} className="executive-approval-list">{safeData.approvals.map((item) => <article className="executive-approval-row" key={item.id}>
          <div><strong>{item.category}</strong><span>{item.branch} · Mã đề nghị {item.id}</span></div><b>{money(item.amount)}</b>
          <button type="button" onClick={onNavigate} aria-label={`Mở phê duyệt đề nghị ${item.id}`}>Xem đề nghị <span aria-hidden="true">→</span></button>
        </article>)}</PaginatedList> : <p className="executive-empty">{loading ? 'Đang tải danh sách đề nghị…' : !data ? 'Chưa có dữ liệu báo cáo.' : data.approvalsAvailable ? 'Không có đề nghị vượt ngưỡng cần duyệt trong kỳ.' : 'Không tải được danh sách đề nghị chi.'}</p>}
      </article>

    </div>
  </section>;
}
