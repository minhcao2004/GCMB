import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../../../api/client';
import './branch-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
const chartMoney = (value) => `${new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;
const days = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const shiftNames = { NIGHT: 'Ca đêm', MORNING: 'Ca sáng', AFTERNOON: 'Ca chiều', EVENING: 'Ca tối' };
const localDate = (value) => String(value || '').slice(0, 10);
const localTime = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(date); };
const todayVN = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const iso = (date) => date.toISOString().slice(0, 10);
const dateOf = (value) => new Date(`${value}T00:00:00Z`);
const addDays = (value, days) => { const date = dateOf(value); date.setUTCDate(date.getUTCDate() + days); return iso(date); };
const validDate = (value) => { if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false; const date = dateOf(value); return !Number.isNaN(date.getTime()) && iso(date) === value; };
const periodIds = ['day', 'week', 'month', 'quarter', 'year', 'custom'];
const periodNames = { day: 'Ngày', week: 'Tuần', month: 'Tháng', quarter: 'Quý', year: 'Năm', custom: 'Tùy chỉnh' };
const periodBounds = (period, anchor, today) => {
  const start = dateOf(anchor);
  if (period === 'week') start.setUTCDate(start.getUTCDate() - (start.getUTCDay() + 6) % 7);
  if (period === 'month') start.setUTCDate(1);
  if (period === 'quarter') start.setUTCMonth(Math.floor(start.getUTCMonth() / 3) * 3, 1);
  if (period === 'year') start.setUTCMonth(0, 1);
  const end = new Date(start);
  if (period === 'week') end.setUTCDate(end.getUTCDate() + 7);
  else if (period === 'month') end.setUTCMonth(end.getUTCMonth() + 1);
  else if (period === 'quarter') end.setUTCMonth(end.getUTCMonth() + 3);
  else if (period === 'year') end.setUTCFullYear(end.getUTCFullYear() + 1);
  else end.setUTCDate(end.getUTCDate() + 1);
  end.setUTCDate(end.getUTCDate() - 1);
  return { from: iso(start), to: iso(end) > today ? today : iso(end) };
};
const shiftPeriod = (period, from, to, step, today) => {
  if (period === 'custom') {
    const length = Math.round((dateOf(to) - dateOf(from)) / 86400000) + 1;
    const nextFrom = addDays(from, step * length);
    const nextTo = addDays(to, step * length);
    return { from: nextFrom, to: nextTo > today ? today : nextTo };
  }
  const start = dateOf(from);
  if (period === 'day') start.setUTCDate(start.getUTCDate() + step);
  if (period === 'week') start.setUTCDate(start.getUTCDate() + step * 7);
  if (period === 'month') start.setUTCMonth(start.getUTCMonth() + step);
  if (period === 'quarter') start.setUTCMonth(start.getUTCMonth() + step * 3);
  if (period === 'year') start.setUTCFullYear(start.getUTCFullYear() + step);
  return periodBounds(period, iso(start), today);
};
const displayDate = (value) => value.split('-').reverse().join('/');

function Panel({ title, note, children, className = '' }) {
  return <section className={`bm-panel ${className}`}><header className="bm-panel-head"><div><h2>{title}</h2>{note && <p>{note}</p>}</div></header>{children}</section>;
}

function RevenueChart({ series, label }) {
  if (!series.length) return <p className="bm-chart-empty">Chưa có doanh thu theo ngày trong kỳ.</p>;
  const max = Math.max(0, ...series.map((point) => point.value));
  const step = max ? 10 ** Math.floor(Math.log10(max / 2)) : 1;
  const half = max ? Math.max(1, Math.ceil(max / 2 / step) * step) : 0;
  const ceiling = half * 2;
  const x = (index) => series.length === 1 ? 50 : index / (series.length - 1) * 100;
  const y = (value) => 94 - value / (ceiling || 1) * 76;
  const points = series.map((point, index) => `${x(index)},${y(point.value)}`).join(' ');
  const dateTicks = series.map((point, index) => ({ point, index })).filter(({ index }) => index === 0 || index === series.length - 1 || index % Math.max(1, Math.ceil((series.length - 1) / 5)) === 0);
  return <div className="bm-revenue-chart"><div className="bm-chart-y">{[ceiling, half, 0].map((value, index) => <span key={index} title={ceiling || index === 2 ? money(value) : undefined}>{ceiling || index === 2 ? chartMoney(value) : ''}</span>)}</div>
    <div className="bm-chart-plot"><svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label={label}>{(ceiling ? [18, 56, 94] : [94]).map((tick) => <line key={tick} x1="0" x2="100" y1={tick} y2={tick} stroke="var(--bm-line)" strokeWidth="1" vectorEffect="non-scaling-stroke" />)}<polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" vectorEffect="non-scaling-stroke" /></svg>
      {series.length <= 31 && series.map((point, index) => <span key={point.label + index} className="bm-chart-point" aria-hidden="true" style={{ left: `${x(index)}%`, top: `${y(point.value)}%` }} />)}</div>
    <div className="bm-chart-x">{dateTicks.map(({ point, index }) => <span key={point.label + index} style={{ left: `${x(index)}%` }}>{point.label}</span>)}</div>
  </div>;
}

function DataTable({ headings, rows, labels, title, emptyMessage }) {
  return <div className="bm-table-wrap" role="region" aria-label={`Bảng ${title}, cuộn ngang khi cần`} tabIndex="0"><table className="bm-table"><thead><tr>{headings.map((heading) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead><tbody>{rows.length ? rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, index) => <td key={index} data-label={labels[index]}>{cell}</td>)}</tr>) : <tr><td colSpan={headings.length}>{emptyMessage}</td></tr>}</tbody></table></div>;
}

export default function BranchOverviewDashboard({ user, onNavigate }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const today = todayVN();
  const period = searchParams.get('period') || (searchParams.has('from') || searchParams.has('to') ? 'custom' : 'month');
  const defaultRange = periodBounds(periodIds.includes(period) && period !== 'custom' ? period : 'month', today, today);
  const from = searchParams.get('from') || (!searchParams.has('from') && !searchParams.has('to') ? defaultRange.from : '');
  const to = searchParams.get('to') || (!searchParams.has('from') && !searchParams.has('to') ? defaultRange.to : '');
  const [draftFrom, setDraftFrom] = useState(from);
  const [draftTo, setDraftTo] = useState(to);
  const [request, setRequest] = useState({ key: '', status: 'loading', data: null, error: '' });
  const [retry, setRetry] = useState(0);
  // BR-163 / MSG106: bộ lọc ngày bắt đầu phải bằng hoặc trước ngày kết thúc.
  const filterError = !user.branchId ? 'Chưa có cơ sở được phân công để xem tổng quan.'
    : !periodIds.includes(period) ? 'Kỳ lọc không hợp lệ.'
    : !validDate(from) || !validDate(to) ? 'Ngày lọc không hợp lệ. Vui lòng chọn lại Từ ngày và Đến ngày.'
      : from > to ? 'Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.'
        : to > today ? 'Không thể chọn ngày trong tương lai.'
          : Math.round((dateOf(to) - dateOf(from)) / 86400000) >= 366 ? 'Khoảng lọc tối đa 366 ngày.'
            : period !== 'custom' && (periodBounds(period, from, today).from !== from || periodBounds(period, from, today).to !== to) ? 'Ngày trên URL không khớp với kỳ đã chọn.' : '';
  const requestKey = `${user.branchId}|${from}|${to}`;
  const data = !filterError && request.key === requestKey && request.status === 'ready' ? request.data : null;
  const loading = !filterError && (!data && (request.key !== requestKey || request.status === 'loading'));
  const error = !filterError && request.key === requestKey && request.status === 'error' ? request.error : '';
  useEffect(() => {
    if (!searchParams.has('period') || (!searchParams.has('from') && !searchParams.has('to'))) {
      const next = new URLSearchParams(searchParams);
      next.set('period', period);
      if (!next.has('from') && !next.has('to')) { next.set('from', from); next.set('to', to); }
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams, period, from, to]);
  useEffect(() => { setDraftFrom(from); setDraftTo(to); }, [from, to]);
  useEffect(() => {
    if (filterError || !user.branchId) return undefined;
    let active = true;
    setRequest({ key: requestKey, status: 'loading', data: null, error: '' });
    api.get('/branch-management/dashboard', { params: { from, to }, headers: { 'X-GCMB-Branch-Id': user.branchId } })
      .then(({ data: result }) => {
        if (!active) return;
        if (result.from !== from || result.to !== to || String(result.result?.id) !== String(user.branchId)) throw new Error('Dữ liệu trả về không khớp kỳ hoặc cơ sở đã chọn.');
        setRequest({ key: requestKey, status: 'ready', data: result, error: '' });
      })
      .catch((cause) => { if (active) setRequest({ key: requestKey, status: 'error', data: null, error: cause.response?.data?.detail || cause.response?.data?.message || cause.message || 'Không tải được dữ liệu cơ sở.' }); });
    return () => { active = false; };
  }, [user.branchId, from, to, requestKey, filterError, retry]);
  const selectRange = (nextPeriod, range) => {
    const next = new URLSearchParams(searchParams);
    next.set('period', nextPeriod); next.set('from', range.from); next.set('to', range.to);
    setSearchParams(next);
  };
  const applyCustom = (event) => { event.preventDefault(); selectRange('custom', { from: draftFrom, to: draftTo }); };
  const nextRange = !filterError ? shiftPeriod(period, from, to, 1, today) : null;
  const canNext = !!nextRange && nextRange.from <= today && (nextRange.from !== from || nextRange.to !== to);
  const rangeLabel = validDate(from) && validDate(to) ? `${displayDate(from)}–${displayDate(to)}` : 'kỳ không hợp lệ';
  const result = data?.result || {};
  const ops = data?.operations || {};
  // BR-177: cơ sở tạm ngừng vẫn xem được lịch sử, nhưng các đường tác nghiệp chỉ để xem.
  const readOnly = user.readOnly || result.status === 'SUSPENDED';
  const branchName = data?.result?.name || user.branchName || 'Cơ sở đang quản lý';
  const year = from.slice(0, 4);
  const month = Number(from.slice(5, 7));
  const periodTitle = !validDate(from) ? 'Kỳ chưa hợp lệ' : period === 'month' ? `Tháng ${month}/${year}` : period === 'quarter' ? `Quý ${Math.ceil(month / 3)}/${year}`
    : period === 'year' ? `Năm ${year}` : period === 'week' ? 'Tuần' : period === 'day' ? 'Ngày' : 'Khoảng tùy chỉnh';
  const periodScope = `${branchName} · ${periodTitle} · ${rangeLabel}`;
  const currentScope = `${branchName} · hiện tại, không theo kỳ lọc`;
  const occupied = Number(ops.occupiedRooms) || 0;
  const maintenance = Number(ops.maintenanceRooms) || 0;
  const totalRooms = Number(ops.roomCount) || 0;
  const hasRoomStatus = [ops.roomCount, ops.occupiedRooms, ops.bookedRooms, ops.maintenanceRooms].every((value) => value != null);
  const revenue = result.revenue == null ? null : Number(result.revenue);
  const booked = Number(ops.bookedRooms) || 0;
  const dailyRows = Array.isArray(data?.dailyRevenue) ? data.dailyRevenue : null;
  const revenueByDate = new Map((dailyRows || []).map((row) => [localDate(row.date), Number(row.revenue) || 0]));
  const dailySeries = dailyRows?.length ? Array.from({ length: Math.round((dateOf(to) - dateOf(from)) / 86400000) + 1 }, (_, index) => {
    const date = addDays(from, index);
    return { label: `${date.slice(8, 10)}/${date.slice(5, 7)}`, value: revenueByDate.get(date) || 0 };
  }) : [];
  const billCount = dailyRows?.reduce((sum, row) => sum + Number(row.billCount || 0), 0) ?? null;
  const rooms = { occupied, maintenance, booked, vacant: Math.max(0, totalRooms - occupied - maintenance - booked) };
  const topItems = (data?.topItems || []).map((row) => [row.name, Number(row.quantity) || 0]);
  const roomTypes = (data?.roomTypePerformance || []).map((row) => [row.name, row.count, money(row.revenue), `${Math.round(Number(row.minutes) || 0)} phút`]);
  const inventory = (data?.lowStockItems || []).map((row) => [row.name, `${row.quantity} ${row.unit}`, `${row.minimum} ${row.unit}`, 'Dưới định mức']);
  const incidents = (data?.openIncidents || []).map((row) => [row.assetCode || row.id, row.assetName, row.location, row.status, localDate(row.createdAt)]);
  const shifts = (data?.todayShifts || []).map((row) => [localDate(row.date).split('-').reverse().join('/'), `${shiftNames[row.shift] || row.shift} · ${localTime(row.startsAt)}–${localTime(row.endsAt)}`, row.employee, row.attendance]);
  const fnbRevenue = dailyRows?.reduce((sum, row) => sum + Number(row.fnbRevenue || 0), 0) ?? null;
  const hourly = new Map((data?.hourlyOccupancy || []).map((row) => [`${row.weekday}-${row.hour}`, Number(row.occupied_room_hours) || 0]));
  const peakMax = Math.max(0, ...hourly.values());
  const heatLevel = (weekday, hour) => {
    const value = hourly.get(`${weekday}-${hour}`) || 0;
    return value === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(value / peakMax * 4)));
  };
  const dataView = {
    rooms,
    revenue: dailySeries,
    peakHours: hourly, topItems, roomTypes, inventory, incidents, shifts,
    tasks: [
      ...(maintenance > 0 ? [['Phòng đang bảo trì', `${maintenance} phòng cần theo dõi`, 'Phòng']] : []),
      ...(Number(ops.openIncidents) > 0 ? [['Sự cố thiết bị đang mở', `${ops.openIncidents} sự cố cần xử lý`, 'Thiết bị & sự cố']] : []),
      ...(Number(ops.lowStockItems) > 0 ? [['Mặt hàng tồn thấp', `${ops.lowStockItems} mặt hàng dưới định mức`, 'Kho hàng']] : []),
    ],
  };
  // BR-102/103/104/105/107/108/211: dùng các giá trị cùng kỳ do API trả; cọc và CAPEX không nhập vào lợi nhuận vận hành.
  const hasRevenue = revenue != null && revenue > 0;
  const margin = hasRevenue ? percent(result.margin) : '—';
  const hasCapacity = result.sellableRoomHours != null && Number(result.sellableRoomHours) > 0;
  const occupancy = hasCapacity ? percent(result.occupancyRate) : '—';
  const financialKpis = [
    ['Doanh thu hóa đơn', money(result.revenue), `Hóa đơn hoàn tất · ${rangeLabel}${result.revenue == null ? ' · API chưa cung cấp số liệu' : ''}`],
    ['Chi phí vận hành và lương', money(result.expenses), `Chi phí vận hành và lương theo ngày thanh toán · ${rangeLabel}${result.expenses == null ? ' · API chưa cung cấp số liệu' : ''}`],
    ['Lợi nhuận vận hành', money(result.profit), `${result.profit != null && Number(result.profit) < 0 ? 'Lỗ trong kỳ · ' : ''}doanh thu hóa đơn trừ chi phí vận hành và lương · ${rangeLabel}${result.profit == null ? ' · API chưa cung cấp số liệu' : ''} · Biên lợi nhuận: ${margin}${!hasRevenue ? ' (doanh thu bằng 0 hoặc chưa có)' : result.margin == null ? ' (API chưa cung cấp)' : ''}`],
    ['Lấp đầy ước tính', occupancy, `${hasCapacity ? 'Giờ sử dụng / giờ phòng khả dụng ước tính theo hiện trạng' : '— vì không có phòng khả dụng hoặc API chưa cung cấp mẫu số'} · ${rangeLabel}${hasCapacity && result.occupancyRate == null ? ' · API chưa cung cấp tỷ lệ' : ''}`],
  ];
  const total = rooms.vacant + rooms.booked + rooms.occupied + rooms.maintenance;
  const occupiedShare = total ? rooms.occupied / total * 100 : 0;
  const slices = [rooms.vacant, rooms.booked, rooms.occupied, rooms.maintenance].map((n) => n / Math.max(1, total) * 100);
  const gradient = total ? `conic-gradient(#16855b 0 ${slices[0]}%, #2586a0 ${slices[0]}% ${slices[0] + slices[1]}%, #a91669 ${slices[0] + slices[1]}% ${slices[0] + slices[1] + slices[2]}%, #bf6744 ${slices[0] + slices[1] + slices[2]}% 100%)` : 'var(--gcmb-color-bg)';
  const itemMax = Math.max(1, ...dataView.topItems.map(([, amount]) => amount));
  const shiftRows = dataView.shifts;
  return <main className="bm-dashboard" aria-labelledby="bm-title">
    <div className="bm-heading"><div><h1 id="bm-title">Tổng quan cơ sở</h1><p>{branchName}{data?.result?.status === 'SUSPENDED' ? ' · Tạm ngừng (xem lịch sử)' : ''} · {periodTitle} · {rangeLabel}</p></div></div>
    <div className="bm-filter" aria-label="Bộ lọc kỳ báo cáo">
      <div className="bm-filter-presets">{[['day', 'Hôm nay'], ['week', 'Tuần này'], ['month', 'Tháng này'], ['quarter', 'Quý này'], ['year', 'Năm nay']].map(([id, label]) => { const current = periodBounds(id, today, today); const selected = period === id && from === current.from && to === current.to; return <button key={id} type="button" className={selected ? 'is-active' : ''} aria-pressed={selected} onClick={() => selectRange(id, current)}>{label}</button>; })}</div>
      <div className="bm-filter-step"><button type="button" disabled={!!filterError} onClick={() => selectRange(period, shiftPeriod(period, from, to, -1, today))}>← Kỳ trước</button><strong>{periodNames[period] || 'Kỳ'} · {rangeLabel}</strong><button type="button" disabled={!canNext} onClick={() => selectRange(period, nextRange)}>Kỳ sau →</button></div>
      <form className="bm-filter-dates" onSubmit={applyCustom}><label>Từ ngày <input type="date" value={draftFrom} max={today} onChange={(event) => setDraftFrom(event.target.value)} /></label><label>Đến ngày <input type="date" value={draftTo} max={today} onChange={(event) => setDraftTo(event.target.value)} /></label><button type="submit">Áp dụng</button></form>
    </div>
    {filterError && <p role="alert" className="bm-message">{filterError}</p>}
    {loading && <p role="status" className="bm-message">Đang tải dữ liệu cho {user.branchName || 'cơ sở đang quản lý'} · {rangeLabel}…</p>}
    {error && <div className="bm-message" role="alert"><p>Không tải được dữ liệu cơ sở: {error}</p><button type="button" onClick={() => setRetry((count) => count + 1)}>Thử lại</button></div>}
    {data && <>
    <section className="bm-section" aria-labelledby="bm-financial-title">
      <div className="bm-section-heading"><h2 id="bm-financial-title">Kết quả kinh doanh trong kỳ</h2><p>{periodScope} · chi phí theo ngày thanh toán</p></div>
      {revenue === 0 && billCount === 0 && <p className="bm-message">Không có hóa đơn hoàn tất trong kỳ. Chi phí phát sinh, nếu có, vẫn được tính vào lợi nhuận.</p>}
      <div className="bm-kpis bm-kpis-primary">{financialKpis.map(([label, value, note]) =>
        <article className={`bm-kpi${label === 'Lợi nhuận vận hành' && result.profit != null && Number(result.profit) < 0 ? ' is-negative' : ''}`} key={label} aria-label={`${label} · ${periodScope}`}>
          <h3>{label}</h3><strong>{value}</strong><small>{note}</small>
        </article>)}</div>
      <div className="bm-support" aria-label="Chỉ số hỗ trợ trong kỳ"><div><span>Tổng bill hoàn tất</span><strong>{billCount ?? '—'}</strong><small>{rangeLabel}{billCount == null ? ' · API chưa cung cấp chi tiết theo ngày' : ''}</small></div><div><span>Doanh thu F&amp;B trong hóa đơn</span><strong>{money(fnbRevenue)}</strong><small>{rangeLabel} · đã nằm trong doanh thu hóa đơn{fnbRevenue == null ? ' · API chưa cung cấp chi tiết theo ngày' : ''}</small></div></div>
    </section>

    <section className="bm-section" aria-labelledby="bm-operations-title">
      <div className="bm-section-heading"><h2 id="bm-operations-title">Việc cần xử lý và vận hành hiện tại</h2><p>{currentScope}; nhân sự theo hôm nay</p></div>
      <div className="bm-grid bm-grid-lead">
      <Panel title="Việc cần làm" note={readOnly ? 'Cơ sở tạm ngừng · chỉ xem trạng thái hiện tại' : 'Từ trạng thái vận hành hiện tại, không theo kỳ lọc'} className="bm-tasks-panel">
        {dataView.tasks.length ? <ul className="bm-tasks">{dataView.tasks.map(([title, detail, destination]) => <li key={title}><span className="bm-task-mark" aria-hidden="true" /><div><strong>{title}</strong><small>{detail}</small></div><button type="button" aria-label={`${readOnly ? 'Xem' : 'Mở'} ${destination}${readOnly ? '' : ` để xử lý ${title.toLowerCase()}`}`} onClick={() => onNavigate?.(destination, destination === 'Thiết bị & sự cố' ? 'incidents' : undefined)}>{readOnly ? 'Xem' : 'Mở'} {destination}</button></li>)}</ul> : <p className="bm-empty">Không có phòng bảo trì, sự cố đang mở hoặc mặt hàng tồn thấp.</p>}
      </Panel>
      <Panel title="Trạng thái phòng hiện tại" note={currentScope} className="bm-room-panel">
        {hasRoomStatus ? <div className="bm-room-content"><div className="bm-donut" style={{ '--bm-donut': gradient }} role="img" aria-label={total ? `Đang sử dụng ${rooms.occupied} trên ${total} phòng hiện tại` : 'Chưa có phòng hoạt động hiện tại'}><div><strong>{total ? `${Math.round(occupiedShare)}%` : '—'}</strong><span>{total ? 'đang dùng' : 'chưa có phòng'}</span></div></div>
          <ul className="bm-legend">{[['Trống', rooms.vacant, 'vacant'], ['Đã đặt', rooms.booked, 'booked'], ['Đang sử dụng', rooms.occupied, 'occupied'], ['Bảo trì', rooms.maintenance, 'maintenance']].map(([name, count, tone]) => <li key={name}><i className={`is-${tone}`} /><span>{name}</span><strong>{count == null ? '—' : count}</strong></li>)}</ul></div>
          : <p className="bm-empty">— Không có dữ liệu trạng thái phòng hiện tại từ API.</p>}
      </Panel>
      </div>
      <div className="bm-support bm-support-current" aria-label="Chỉ số vận hành hỗ trợ"><div><span>Mặt hàng tồn thấp hiện tại</span><strong>{ops.lowStockItems ?? '—'}</strong><small>Dưới định mức tối thiểu</small></div><div><span>Nhân viên đi làm hôm nay</span><strong>{ops.staffPresent ?? '—'} / {ops.staffTotal ?? '—'}</strong><small>Theo chấm công hôm nay</small></div></div>
      <div className="bm-grid bm-grid-tables">
        <Panel title="Tồn kho cần chú ý" note={currentScope}><DataTable title="tồn kho cần chú ý" headings={['Mặt hàng', 'Tồn hiện tại', 'Mức tối thiểu', '']} labels={['Mặt hàng', 'Tồn hiện tại', 'Mức tối thiểu', 'Trạng thái']} rows={dataView.inventory} emptyMessage="Không có mặt hàng tồn thấp hiện tại." /></Panel>
        <Panel title="Sự cố thiết bị đang mở" note={currentScope}><DataTable title="sự cố thiết bị" headings={['Mã sự cố', 'Thiết bị', 'Vị trí', 'Trạng thái', 'Tạo lúc']} labels={['Mã sự cố', 'Thiết bị', 'Vị trí', 'Trạng thái', 'Tạo lúc']} rows={dataView.incidents} emptyMessage="Không có sự cố thiết bị đang mở." /></Panel>
        <Panel title="Ca làm hôm nay và ngày mai" note={`${branchName} · lịch hôm nay và ngày mai, không theo kỳ lọc`}><DataTable title="ca làm hôm nay và ngày mai" headings={['Ngày', 'Khung giờ', 'Nhân viên', 'Chấm công']} labels={['Ngày', 'Khung giờ', 'Nhân viên', 'Chấm công']} rows={shiftRows} emptyMessage="Không có ca làm hôm nay và ngày mai." /></Panel>
      </div>
    </section>

    <section className="bm-section" aria-labelledby="bm-analysis-title">
      <div className="bm-section-heading"><h2 id="bm-analysis-title">Xu hướng và phân tích trong kỳ</h2><p>{periodScope}</p></div>
      <div className="bm-grid bm-grid-lead">
        <Panel title={`Doanh thu hóa đơn theo ngày · ${periodTitle}`} note={`${branchName} · ${rangeLabel}`} className="bm-revenue-panel">{dailyRows ? <RevenueChart series={dataView.revenue} label={`Doanh thu hóa đơn theo ngày, ${periodScope}`} /> : <p className="bm-empty">— API chưa cung cấp doanh thu theo ngày.</p>}</Panel>
        <Panel title={`Giờ-phòng sử dụng theo khung giờ · ${periodTitle}`} note={`${branchName} · tổng giờ-phòng theo thứ và giờ · ${rangeLabel}`} className="bm-heat-panel">{Array.isArray(data.hourlyOccupancy) && data.hourlyOccupancy.length ? <><div className="bm-heatmap" aria-label={`Giờ-phòng sử dụng theo khung giờ · ${periodScope}`}><span className="bm-heat-empty" />{days.map((day) => <span className="bm-heat-day" key={day}>{day}</span>)}{Array.from({ length: 12 }, (_, index) => <div className="bm-heat-row" key={index}><span>{`${String(index + 8).padStart(2, '0')}:00`}</span>{days.map((day, dayIndex) => { const value = hourly.get(`${dayIndex + 1}-${index + 8}`) || 0; return <i key={day} className={`heat-${heatLevel(dayIndex + 1, index + 8)}`} title={`${day} ${String(index + 8).padStart(2, '0')}:00 · ${value} giờ-phòng`} />; })}</div>)}</div><div className="bm-heat-foot"><span>Ít</span>{[0,1,2,3,4].map((level) => <i key={level} className={`heat-${level}`} />)}<span>Nhiều</span></div></> : <p className="bm-empty">{Array.isArray(data.hourlyOccupancy) ? 'Không có giờ-phòng sử dụng trong kỳ.' : '— API chưa cung cấp khung giờ sử dụng phòng.'}</p>}</Panel>
        <Panel title={`Top 10 món F&B bán chạy · ${periodTitle}`} note={`${branchName} · số lượng bán · ${rangeLabel}`} className="bm-items-panel">{dataView.topItems.length ? <ol className="bm-bars">{dataView.topItems.map(([name, count], index) => <li key={name}><span className="bm-rank">{String(index + 1).padStart(2, '0')}</span><span className="bm-item-name">{name}</span><span className="bm-bar-track"><i style={{ width: `${count / itemMax * 100}%` }} /></span><strong>{count}</strong></li>)}</ol> : <p className="bm-empty">Không có món F&amp;B đã bán trong kỳ.</p>}</Panel>
        <Panel title={`Hiệu suất theo hạng phòng · ${periodTitle}`} note={`${branchName} · lượt sử dụng kết thúc và hóa đơn hoàn tất · ${rangeLabel}`} className="bm-roomtype-panel"><DataTable title="hiệu suất theo hạng phòng" headings={['Hạng phòng', 'Số lượt', 'Doanh thu phòng', 'Thời gian dùng TB']} labels={['Hạng phòng', 'Số lượt', 'Doanh thu phòng', 'Thời gian dùng TB']} rows={dataView.roomTypes} emptyMessage="Không có lượt sử dụng phòng kết thúc trong kỳ." /></Panel>
      </div>
    </section>
    </>}
  </main>;
}
