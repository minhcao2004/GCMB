import { PaginatedTable, PaginatedList } from '../../../shared/components/Pagination';
import { useEffect, useState } from 'react';
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import api from '../../../api/client';
import CustomerSourcesChart from '../../dashboard/CustomerSourcesChart';
import './branch-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
const compactMoney = (value) => {
  const amount = Number(value) || 0;
  if (Math.abs(amount) >= 1_000_000) return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(amount / 1_000_000)} tr`;
  if (Math.abs(amount) >= 1_000) return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(amount / 1_000)} k`;
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(amount);
};
const days = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const months = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
const shiftNames = { NIGHT: 'Ca đêm', MORNING: 'Ca sáng', AFTERNOON: 'Ca chiều', EVENING: 'Ca tối' };
const localDate = (value) => String(value || '').slice(0, 10);
const localTime = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(date); };
const todayDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const currentMonth = () => todayDate().slice(0, 7);
const monthStart = (year, month) => `${year}-${String(month).padStart(2, '0')}-01`;
const monthEnd = (year, month) => `${year}-${String(month).padStart(2, '0')}-${String(new Date(Number(year), Number(month), 0).getDate()).padStart(2, '0')}`;
const addDays = (value, count) => { const date = new Date(`${value}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + count); return date.toISOString().slice(0, 10); };
const dateLabel = (value) => value ? value.split('-').reverse().join('/') : '';
const daysBetween = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);
const adjustRangeStart = (value, setStart, currentEnd, setEnd) => {
  setStart(value);
  if (currentEnd < value) setEnd(value);
  else if (daysBetween(value, currentEnd) > 365) setEnd(addDays(value, 365));
};
const adjustRangeEnd = (value, currentStart, setStart, setEnd) => {
  setEnd(value);
  if (value < currentStart) setStart(value);
  else if (daysBetween(currentStart, value) > 365) setStart(addDays(value, -365));
};

function selectedRange(period, month, year, from, to, today) {
  if (period === 'YEAR') return { from: `${year}-01-01`, to: year === today.slice(0, 4) ? today : `${year}-12-31` };
  if (period === 'RANGE') return { from, to };
  const selectedMonth = Number(month);
  const start = monthStart(year, selectedMonth);
  const end = monthEnd(year, selectedMonth);
  return { from: start, to: start <= today ? (end < today ? end : today) : end };
}

function rangeLabel(period, selectedMonth, selectedYear, from, to) {
  if (period === 'YEAR') return `Năm ${selectedYear}`;
  if (period === 'RANGE') return `${dateLabel(from)} – ${dateLabel(to)}`;
  return `Tháng ${Number(selectedMonth)}/${selectedYear}`;
}

function Panel({ title, note, children, className = '', trailing }) {
  return <section className={`bm-panel ${className}`}><header className="bm-panel-head"><div><h2>{title}</h2>{note && <p>{note}</p>}</div>{trailing}</header>{children}</section>;
}

const incidentStatus = (status) => ({
  REPORTED: 'Mới báo', WAITING_APPROVAL: 'Chờ duyệt', IN_PROGRESS: 'Đang xử lý',
  ASSESSED: 'Đã đánh giá', VERIFIED: 'Đã xác minh', CLOSED: 'Đã đóng', REJECTED: 'Từ chối',
}[status] || status || 'Chưa cập nhật');
const incidentTone = (status) => ({
  REPORTED: 'warning', WAITING_APPROVAL: 'warning', IN_PROGRESS: 'info',
  ASSESSED: 'info', REJECTED: 'danger',
}[status] || 'neutral');
const attendanceTone = (status) => status === 'ĐANG LÀM' || status === 'ĐÃ CHẤM CÔNG'
  ? 'success' : status === 'VẮNG' ? 'danger' : 'warning';

function RevenueTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return <div className="bm-chart-tooltip"><span>{point.fullLabel}</span><strong>{money(point.value)}</strong></div>;
}

function RevenueChart({ series, chartKey, hasRevenue }) {
  if (!series.length || !hasRevenue) return <p className="bm-chart-empty">Chưa có doanh thu trong kỳ.</p>;
  return <div className="bm-revenue-chart" role="img" aria-label="Biểu đồ doanh thu theo kỳ">
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart key={chartKey} data={series} margin={{ top: 12, right: 14, left: 4, bottom: 3 }}>
        <defs><linearGradient id="bmRevenueFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#168b83" stopOpacity={0.23} /><stop offset="95%" stopColor="#168b83" stopOpacity={0.015} /></linearGradient></defs>
        <CartesianGrid vertical={false} stroke="#e8eef2" strokeDasharray="3 5" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={20} tick={{ fill: '#758596', fontSize: 10 }} />
        <YAxis tickFormatter={compactMoney} tickLine={false} axisLine={false} width={48} tick={{ fill: '#758596', fontSize: 10 }} />
        <Tooltip content={<RevenueTooltip />} cursor={{ stroke: '#9bb9b5', strokeDasharray: '4 4' }} />
        <Area type="monotone" dataKey="value" name="Doanh thu" stroke="#147b75" strokeWidth={2.6} fill="url(#bmRevenueFill)" activeDot={{ r: 5, strokeWidth: 2, fill: '#fff' }} dot={false} isAnimationActive={false} />
      </AreaChart>
    </ResponsiveContainer>
  </div>;
}

function PeriodControls({ idPrefix, period, onPeriodChange, month, onMonthChange, year, onYearChange, from, to, onFromChange, onToChange, years, today }) {
  return <div className="bm-period-controls">
    <label htmlFor={`${idPrefix}-period`}>Xem theo<select id={`${idPrefix}-period`} value={period} onChange={(event) => onPeriodChange(event.target.value)}>
      <option value="MONTH">Tháng</option><option value="YEAR">Năm</option><option value="RANGE">Khoảng ngày</option>
    </select></label>
    {period === 'MONTH' && <>
      <label htmlFor={`${idPrefix}-month`}>Tháng<select id={`${idPrefix}-month`} value={month} onChange={(event) => onMonthChange(event.target.value)}>{months.map((name, index) => <option key={name} value={String(index + 1).padStart(2, '0')}>{name}</option>)}</select></label>
      <label htmlFor={`${idPrefix}-year`}>Năm<select id={`${idPrefix}-year`} value={year} onChange={(event) => onYearChange(event.target.value)}>{years.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
    </>}
    {period === 'YEAR' && <label htmlFor={`${idPrefix}-year`}>Năm<select id={`${idPrefix}-year`} value={year} onChange={(event) => onYearChange(event.target.value)}>{years.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>}
    {period === 'RANGE' && <>
      <label htmlFor={`${idPrefix}-from`}>Từ ngày<input id={`${idPrefix}-from`} type="date" value={from} max={today} onChange={(event) => { if (event.target.value) onFromChange(event.target.value); }} /></label>
      <label htmlFor={`${idPrefix}-to`}>Đến ngày<input id={`${idPrefix}-to`} type="date" value={to} min={from} max={today} onChange={(event) => { if (event.target.value) onToChange(event.target.value); }} /></label>
    </>}
  </div>;
}

function DataTable({ headings, rows, labels }) {
  return <div className="bm-table-wrap"><PaginatedTable pageSize={10} className="bm-table"><thead><tr>{headings.map((heading) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead><tbody>{rows.length ? rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, index) => <td key={index} data-label={labels[index]}>{cell}</td>)}</tr>) : <tr><td colSpan={headings.length}>Chưa có dữ liệu cho kỳ báo cáo này.</td></tr>}</tbody></PaginatedTable></div>;
}

export default function BranchOverviewDashboard({ user }) {
  const today = todayDate();
  const initialMonth = currentMonth();
  const [scopePeriod, setScopePeriod] = useState('MONTH');
  const [scopeMonth, setScopeMonth] = useState(initialMonth.slice(5, 7));
  const [scopeYear, setScopeYear] = useState(initialMonth.slice(0, 4));
  const [scopeFrom, setScopeFrom] = useState(`${initialMonth}-01`);
  const [scopeTo, setScopeTo] = useState(today);
  const [chartPeriod, setChartPeriod] = useState('MONTH');
  const [chartMonth, setChartMonth] = useState(initialMonth.slice(5, 7));
  const [chartYear, setChartYear] = useState(initialMonth.slice(0, 4));
  const [chartFrom, setChartFrom] = useState(`${initialMonth}-01`);
  const [chartTo, setChartTo] = useState(today);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const currentYear = today.slice(0, 4);
  const availableYears = Array.from({ length: Math.max(1, Number(currentYear) - 2019) }, (_, index) => String(Number(currentYear) - index));
  const reportRange = selectedRange(scopePeriod, scopeMonth, scopeYear, scopeFrom, scopeTo, today);
  const chartRange = selectedRange(chartPeriod, chartMonth, chartYear, chartFrom, chartTo, today);
  const reportRangeLabel = rangeLabel(scopePeriod, scopeMonth, scopeYear, reportRange.from, reportRange.to);
  const chartRangeLabel = rangeLabel(chartPeriod, chartMonth, chartYear, chartRange.from, chartRange.to);
  const revenueDate = chartPeriod === 'MONTH' ? `${chartYear}-${chartMonth}` : chartPeriod === 'YEAR' ? chartYear : undefined;
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const month = reportRange.from.slice(0, 7);
    const params = {
      month,
      scopePeriod,
      scopeFrom: reportRange.from,
      scopeTo: reportRange.to,
      revenuePeriod: chartPeriod,
      revenueDate,
      ...(chartPeriod === 'RANGE' ? { revenueFrom: chartRange.from, revenueTo: chartRange.to } : {}),
    };
    api.get('/branch-management/dashboard', { params, headers: { 'X-GCMB-Branch-Id': user.branchId } })
      .then(({ data: result }) => { if (active) setData(result); })
      .catch((cause) => { if (active) setError(cause.response?.data?.detail || cause.response?.data?.message || 'Không tải được dữ liệu cơ sở.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user.branchId, scopePeriod, reportRange.from, reportRange.to, chartPeriod, chartRange.from, chartRange.to, revenueDate]);
  const result = data?.result || {};
  const ops = data?.operations || {};
  const occupied = Number(ops.occupiedRooms) || 0;
  const maintenance = Number(ops.maintenanceRooms) || 0;
  const totalRooms = Number(ops.roomCount) || 0;
  const booked = Number(ops.bookedRooms) || 0;
  const revenueRows = data?.dailyRevenue || [];
  const revenueByDate = new Map(revenueRows.map((row) => [localDate(row.date), Number(row.revenue) || 0]));
  const showMonthlyBuckets = chartPeriod === 'YEAR' || (chartPeriod === 'RANGE' && daysBetween(chartRange.from, chartRange.to) > 62);
  const chartSeries = (() => {
    if (showMonthlyBuckets) {
      const totals = new Map();
      revenueByDate.forEach((value, date) => {
        const month = date.slice(0, 7);
        totals.set(month, (totals.get(month) || 0) + value);
      });
      const monthsInRange = [];
      let year = Number(chartRange.from.slice(0, 4));
      let month = Number(chartRange.from.slice(5, 7));
      const lastKey = chartRange.to.slice(0, 7);
      while (`${year}-${String(month).padStart(2, '0')}` <= lastKey) {
        const key = `${year}-${String(month).padStart(2, '0')}`;
        monthsInRange.push({ label: `${key.slice(5, 7)}/${key.slice(2, 4)}`, fullLabel: `${months[Number(key.slice(5, 7)) - 1]} ${key.slice(0, 4)}`, value: totals.get(key) || 0 });
        month += 1;
        if (month > 12) { year += 1; month = 1; }
      }
      return monthsInRange;
    }
    const resultRows = [];
    for (let cursor = chartRange.from; cursor <= chartRange.to; cursor = addDays(cursor, 1)) {
      const sameMonth = chartRange.from.slice(0, 7) === chartRange.to.slice(0, 7);
      resultRows.push({ label: sameMonth ? cursor.slice(8, 10) : `${cursor.slice(8, 10)}/${cursor.slice(5, 7)}`, fullLabel: dateLabel(cursor), value: revenueByDate.get(cursor) || 0 });
    }
    return resultRows;
  })();
  const rooms = { occupied, maintenance, booked, vacant: Math.max(0, totalRooms - occupied - maintenance - booked) };
  const roomTypes = (data?.roomTypePerformance || []).map((row) => [row.name, row.count, money(row.revenue), `${Math.round(Number(row.minutes) || 0)} phút`]);
  const inventory = data?.lowStockItems || [];
  const incidents = data?.openIncidents || [];
  const shifts = (data?.todayShifts || []).map((row) => {
    const date = localDate(row.date);
    return { ...row, date, dateLabel: date.split('-').reverse().join('/'), dayLabel: date === today ? 'Hôm nay' : 'Ngày mai', shiftLabel: shiftNames[row.shift] || row.shift };
  });
  const reportSummary = data?.reportSummary || {};
  const chartSummary = data?.revenueSummary || {};
  const fnbRevenue = Number(reportSummary.fnbRevenue ?? result.fnbRevenue) || 0;
  const selectedRevenue = Number(reportSummary.revenue ?? result.revenue) || 0;
  const selectedTransactions = Number(reportSummary.billCount ?? result.billCount) || 0;
  const hourly = new Map((data?.hourlyOccupancy || []).map((row) => [`${row.weekday}-${row.hour}`, Number(row.occupied_room_hours) || 0]));
  const peakMax = Math.max(0, ...hourly.values());
  const heatLevel = (weekday, hour) => {
    const value = hourly.get(`${weekday}-${hour}`) || 0;
    return value === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(value / peakMax * 4)));
  };
  const dataView = {
    kpis: { revenue: selectedRevenue, transactions: selectedTransactions, occupied, roomTotal: totalRooms, fnbRevenue },
    rooms,
    revenue: chartSeries,
    peakHours: hourly, roomTypes, shifts,
  };
  if (!data) return <main className="bm-dashboard" aria-live="polite"><h1>Tổng quan cơ sở</h1>{loading && <p>Đang tải dữ liệu…</p>}{error && <p role="alert">{error}</p>}</main>;
  const { kpis } = dataView;
  const total = rooms.vacant + rooms.booked + rooms.occupied + rooms.maintenance;
  const occupiedShare = total ? rooms.occupied / total * 100 : 0;
  const slices = [rooms.vacant, rooms.booked, rooms.occupied, rooms.maintenance].map((n) => n / Math.max(1, total) * 100);
  const gradient = `conic-gradient(#16855b 0 ${slices[0]}%, #2586a0 ${slices[0]}% ${slices[0] + slices[1]}%, #a91669 ${slices[0] + slices[1]}% ${slices[0] + slices[1] + slices[2]}%, #bf6744 ${slices[0] + slices[1] + slices[2]}% 100%)`;
  const shiftRows = dataView.shifts;
  return <main className="bm-dashboard" aria-labelledby="bm-title">
    <div className="bm-heading"><div><span className="bm-eyebrow">QUẢN TRỊ VẬN HÀNH</span><h1 id="bm-title">Tổng quan cơ sở</h1><p>{user.branchName || 'Cơ sở đang quản lý'} · {reportRangeLabel}</p></div></div>
    {error && <p role="alert">{error}</p>}{loading && <p role="status">Đang cập nhật…</p>}
    <section className="bm-report-filter" aria-label="Phạm vi báo cáo tổng quan">
      <div className="bm-filter-summary"><span>PHẠM VI BÁO CÁO</span><strong>{reportRangeLabel}</strong></div>
      <PeriodControls idPrefix="bm-scope" period={scopePeriod} onPeriodChange={setScopePeriod}
        month={scopeMonth} onMonthChange={setScopeMonth} year={scopeYear} onYearChange={setScopeYear}
        from={scopeFrom} to={scopeTo} onFromChange={(value) => adjustRangeStart(value, setScopeFrom, scopeTo, setScopeTo)}
        onToChange={(value) => adjustRangeEnd(value, scopeFrom, setScopeFrom, setScopeTo)} years={availableYears} today={today} />
    </section>
    <div className="bm-kpis" aria-label="Các chỉ số vận hành chính">{[
      ['revenue', 'Doanh thu', money(kpis.revenue), reportRangeLabel], ['transactions', 'Tổng giao dịch', new Intl.NumberFormat('vi-VN').format(kpis.transactions), 'Hóa đơn đã hoàn tất'],
      ['rooms', 'Phòng đang sử dụng', `${kpis.occupied} / ${kpis.roomTotal}`, 'Trạng thái phòng hiện tại'], ['fnb', 'Doanh thu F&B', money(kpis.fnbRevenue), reportRangeLabel],
      ['inventory', 'Mặt hàng tồn thấp', new Intl.NumberFormat('vi-VN').format(ops.lowStockItems ?? 0), 'Dưới định mức tối thiểu'],
      ['staff', 'Nhân viên đi làm hôm nay', `${ops.staffPresent ?? '—'} / ${ops.staffTotal ?? '—'}`, 'Đã vào ca / được phân ca'],
    ].map(([tone, label, value, note]) => <article className={`bm-kpi is-${tone}`} key={label}>
      <span className="bm-kpi-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{tone === 'revenue' ? <><path d="M4 19V5m0 14h16"/><path d="m7 15 4-4 3 2 5-6"/></> : tone === 'transactions' ? <><path d="M7 3h8l4 4v14H7z"/><path d="M15 3v5h5M10 13h6m-6 4h6"/></> : tone === 'rooms' ? <><path d="M3 20V8l9-5 9 5v12"/><path d="M7 20v-6h10v6M8 9h.01M12 9h.01M16 9h.01"/></> : tone === 'fnb' ? <><path d="M4 3v7a3 3 0 0 0 6 0V3M7 3v18m8-18v18m0-18c3 2 4 5 4 9h-4"/></> : tone === 'inventory' ? <><path d="m3 7 9-4 9 4-9 4-9-4Z"/><path d="M3 7v10l9 4 9-4V7m-9 4v10"/></> : <><circle cx="9" cy="8" r="4"/><path d="M2 21v-2a7 7 0 0 1 14 0v2m3-13a4 4 0 0 1 0 8m3 5v-2a7 7 0 0 0-3-5.7"/></>}</svg></span>
      <div className="bm-kpi-copy"><span>{label}</span><strong>{value}</strong><small>{note}</small></div>
    </article>)}</div>

    <div className="bm-grid bm-grid-lead">
      <Panel title="Trạng thái phòng" note={`${total} phòng trong cơ sở`} className="bm-room-panel">
        <div className="bm-room-content"><div className="bm-donut" style={{ '--bm-donut': gradient }} role="img" aria-label={`Đang sử dụng ${rooms.occupied} trên ${total} phòng`}><div><strong>{Math.round(occupiedShare)}%</strong><span>đang dùng</span></div></div>
          <ul className="bm-legend">{[['Trống', rooms.vacant, 'vacant'], ['Đã đặt', rooms.booked, 'booked'], ['Đang sử dụng', rooms.occupied, 'occupied'], ['Bảo trì', rooms.maintenance, 'maintenance']].map(([name, count, tone]) => <li key={name}><i className={`is-${tone}`} /><span>{name}</span><strong>{count == null ? '—' : count}</strong></li>)}</ul></div>
      </Panel>
      <Panel title="Xu hướng doanh thu" note={`Doanh thu vận hành · ${chartRangeLabel}`} className="bm-revenue-panel">
        <div className="bm-revenue-controls">
          <PeriodControls idPrefix="bm-chart" period={chartPeriod} onPeriodChange={setChartPeriod}
            month={chartMonth} onMonthChange={setChartMonth} year={chartYear} onYearChange={setChartYear}
            from={chartFrom} to={chartTo} onFromChange={(value) => adjustRangeStart(value, setChartFrom, chartTo, setChartTo)}
            onToChange={(value) => adjustRangeEnd(value, chartFrom, setChartFrom, setChartTo)} years={availableYears} today={today} />
        </div>
        <div className="bm-revenue-total"><span>Tổng doanh thu</span><strong>{money(chartSummary.revenue || 0)}</strong><small>{chartRangeLabel}</small></div>
        <RevenueChart series={dataView.revenue} chartKey={`${chartPeriod}-${chartRange.from}-${chartRange.to}`} hasRevenue={revenueRows.length > 0} />
      </Panel>
      <CustomerSourcesChart branchId={user.branchId} className="bm-panel bm-sources-panel" />
      <Panel title="Khung giờ cao điểm" note="Số lượt phòng được sử dụng theo thứ và giờ trong kỳ" className="bm-heat-panel"><div className="bm-heatmap" aria-label="Lượt sử dụng phòng theo khung giờ"><span className="bm-heat-empty" />{days.map((day) => <span className="bm-heat-day" key={day}>{day}</span>)}{Array.from({ length: 12 }, (_, index) => <div className="bm-heat-row" key={index}><span>{`${String(index + 8).padStart(2, '0')}:00`}</span>{days.map((day, dayIndex) => { const value = hourly.get(`${dayIndex + 1}-${index + 8}`) || 0; return <i key={day} className={`heat-${heatLevel(dayIndex + 1, index + 8)}`} title={`${day} ${String(index + 8).padStart(2, '0')}:00 · ${value} lượt phòng`} />; })}</div>)}</div><div className="bm-heat-foot"><span>Ít</span>{[0,1,2,3,4].map((level) => <i key={level} className={`heat-${level}`} />)}<span>Nhiều</span></div></Panel>
    </div>
    <div className="bm-grid bm-grid-tables">
      <Panel title="Hiệu suất theo hạng phòng" note="Số liệu các lượt sử dụng kết thúc trong kỳ"><DataTable headings={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} labels={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} rows={dataView.roomTypes} /></Panel>
      <Panel title="Tồn kho cần chú ý" note="Mặt hàng đang dưới định mức tối thiểu" className="bm-inventory-panel" trailing={<span className="bm-panel-count">{inventory.length} mặt hàng</span>}>
        {inventory.length ? <PaginatedList as="ul" pageSize={5} className="bm-attention-list">{inventory.map((item) => {
          const quantity = Number(item.quantity) || 0;
          const minimum = Number(item.minimum) || 0;
          const level = minimum ? Math.max(0, Math.min(100, quantity / minimum * 100)) : 0;
          return <li key={item.id || item.name}>
            <span className="bm-attention-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 2.8 19h18.4L12 3Z"/><path d="M12 9v4m0 3h.01"/></svg></span>
            <div className="bm-attention-content"><strong>{item.name}</strong><span>Còn {quantity} {item.unit} · Định mức {minimum} {item.unit}</span><div className="bm-stock-track" role="meter" aria-label={`Tồn kho ${item.name}`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={level}><i style={{ width: `${level}%` }} /></div></div>
            <span className="bm-attention-badge">Cần nhập</span>
          </li>;
        })}</PaginatedList> : <p className="bm-panel-empty">Không có mặt hàng nào dưới định mức.</p>}
      </Panel>
      <Panel title="Sự cố thiết bị" note="Các sự cố đang cần theo dõi" className="bm-incidents-panel" trailing={<span className={`bm-panel-count${incidents.length ? ' is-attention' : ''}`}>{incidents.length} đang mở</span>}>
        {incidents.length ? <PaginatedList as="ul" pageSize={5} className="bm-incident-list">{incidents.map((incident) => <li key={incident.id}>
          <span className={`bm-incident-mark is-${incidentTone(incident.status)}`} aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 8v4m0 4h.01M10.3 3.9 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3l-7.6-13.1a2 2 0 0 0-3.4 0Z"/></svg></span>
          <div className="bm-incident-copy"><strong>{incident.assetCode || incident.id} · {incident.assetName}</strong><span>{incident.location || 'Chưa có vị trí'} · {localDate(incident.createdAt).split('-').reverse().join('/')}</span></div>
          <span className={`bm-incident-status is-${incidentTone(incident.status)}`}>{incidentStatus(incident.status)}</span>
        </li>)}</PaginatedList> : <p className="bm-panel-empty is-success">Không có sự cố nào đang mở.</p>}
      </Panel>
      <Panel title="Ca làm hôm nay & ngày mai" note="Lịch ca đã xác nhận và tình trạng chấm công" className="bm-shifts-panel" trailing={<span className="bm-panel-count">{shiftRows.length} ca</span>}>
        {shiftRows.length ? <PaginatedList as="ul" pageSize={5} className="bm-shift-list">{shiftRows.map((shift, index) => <li key={`${shift.date}-${shift.shift}-${shift.employee}-${index}`}>
          <div className="bm-shift-time"><span>{shift.dayLabel}</span><strong>{localTime(shift.startsAt)}<i aria-hidden="true">→</i>{localTime(shift.endsAt)}</strong><small>{shift.dateLabel}</small></div>
          <div className="bm-shift-person"><strong>{shift.employee}</strong><span>{shiftNames[shift.shift] || shift.shiftLabel}</span></div>
          <span className={`bm-attendance-status is-${attendanceTone(shift.attendance)}`}>{shift.attendance}</span>
        </li>)}</PaginatedList> : <p className="bm-panel-empty">Chưa có ca làm được xác nhận trong hai ngày tới.</p>}
      </Panel>
    </div>
    <p className="bm-disclaimer">Biểu đồ và bảng hiển thị dữ liệu từ API; các mục chưa được API cung cấp được để trống rõ ràng.</p>
  </main>;
}
