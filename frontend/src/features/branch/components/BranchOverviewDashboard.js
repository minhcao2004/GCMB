import { useEffect, useState } from 'react';
import api from '../../../api/client';
import './branch-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
const days = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const shiftNames = { NIGHT: 'Ca đêm', MORNING: 'Ca sáng', AFTERNOON: 'Ca chiều', EVENING: 'Ca tối' };
const localDate = (value) => String(value || '').slice(0, 10);
const localTime = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(date); };
const todayDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const currentMonth = () => todayDate().slice(0, 7);

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

function RevenueChart({ series }) {
  if (!series.length) return <p className="bm-chart-empty">Chưa có doanh thu theo ngày trong kỳ.</p>;
  const max = Math.max(...series.map((point) => point.value));
  const min = Math.min(...series.map((point) => point.value));
  const points = series.map((point, index) => `${series.length === 1 ? 50 : index / (series.length - 1) * 100},${94 - ((point.value - min) / Math.max(1, max - min)) * 68}`).join(' ');
  return <div className="bm-revenue-chart"><div className="bm-chart-y"><span>{money(max)}</span><span>{money(Math.round((max + min) / 2))}</span><span>{money(min)}</span></div>
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="Biểu đồ doanh thu theo ngày"><polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />{series.map((point, index) => <circle key={point.label + index} cx={series.length === 1 ? 50 : index / (series.length - 1) * 100} cy={94 - ((point.value - min) / Math.max(1, max - min)) * 68} r="1.6" />)}</svg>
    <div className="bm-chart-x">{series.map((point, index) => <span key={point.label + index}>{point.label}</span>)}</div>
  </div>;
}

function DataTable({ headings, rows, labels }) {
  return <div className="bm-table-wrap"><table className="bm-table"><thead><tr>{headings.map((heading) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead><tbody>{rows.length ? rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, index) => <td key={index} data-label={labels[index]}>{cell}</td>)}</tr>) : <tr><td colSpan={headings.length}>Chưa có dữ liệu cho kỳ báo cáo này.</td></tr>}</tbody></table></div>;
}

export default function BranchOverviewDashboard({ user }) {
  const [revenuePeriod, setRevenuePeriod] = useState('MONTH');
  const [revenueDate, setRevenueDate] = useState(currentMonth);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const month = currentMonth();
    api.get('/branch-management/dashboard', { params: { month, revenuePeriod, revenueDate }, headers: { 'X-GCMB-Branch-Id': user.branchId } })
      .then(({ data: result }) => { if (active) setData(result); })
      .catch((cause) => { if (active) setError(cause.response?.data?.detail || cause.response?.data?.message || 'Không tải được dữ liệu cơ sở.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user.branchId, revenuePeriod, revenueDate]);
  const result = data?.result || {};
  const ops = data?.operations || {};
  const occupied = Number(ops.occupiedRooms) || 0;
  const maintenance = Number(ops.maintenanceRooms) || 0;
  const totalRooms = Number(ops.roomCount) || 0;
  const booked = Number(ops.bookedRooms) || 0;
  const revenueRows = data?.dailyRevenue || [];
  const dailySeries = (() => {
    if (revenuePeriod !== 'YEAR') return revenueRows.map((row) => {
      const date = localDate(row.date);
      return { label: `${date.slice(8, 10)}/${date.slice(5, 7)}`, value: Number(row.revenue) || 0 };
    });
    const byMonth = new Map();
    revenueRows.forEach((row) => {
      const key = localDate(row.date).slice(0, 7);
      byMonth.set(key, (byMonth.get(key) || 0) + (Number(row.revenue) || 0));
    });
    return [...byMonth].sort(([left], [right]) => left.localeCompare(right)).map(([month, value]) => ({ label: month.slice(5) + '/' + month.slice(0, 4), value }));
  })();
  const rooms = { occupied, maintenance, booked, vacant: Math.max(0, totalRooms - occupied - maintenance - booked) };
  const roomTypes = (data?.roomTypePerformance || []).map((row) => [row.name, row.count, money(row.revenue), `${Math.round(Number(row.minutes) || 0)} phút`]);
  const inventory = data?.lowStockItems || [];
  const incidents = data?.openIncidents || [];
  const today = todayDate();
  const currentYear = today.slice(0, 4);
  const availableYears = Array.from({ length: Math.max(1, Number(currentYear) - 2019) }, (_, index) => String(Number(currentYear) - index));
  const shifts = (data?.todayShifts || []).map((row) => {
    const date = localDate(row.date);
    return { ...row, date, dateLabel: date.split('-').reverse().join('/'), dayLabel: date === today ? 'Hôm nay' : 'Ngày mai', shiftLabel: shiftNames[row.shift] || row.shift };
  });
  const revenueSummary = data?.revenueSummary || {};
  const fnbRevenue = Number(revenueSummary.fnbRevenue ?? revenueRows.reduce((sum, row) => sum + Number(row.fnbRevenue || 0), 0));
  const selectedRevenue = Number(revenueSummary.revenue ?? result.revenue) || 0;
  const selectedTransactions = Number(revenueSummary.billCount ?? result.billCount) || 0;
  const rangeLabel = revenuePeriod === 'DAY'
    ? `Ngày ${revenueDate.slice(8, 10)}/${revenueDate.slice(5, 7)}/${revenueDate.slice(0, 4)}`
    : revenuePeriod === 'YEAR' ? `Năm ${revenueDate}` : `Tháng ${revenueDate.slice(5, 7)}/${revenueDate.slice(0, 4)}`;
  const hourly = new Map((data?.hourlyOccupancy || []).map((row) => [`${row.weekday}-${row.hour}`, Number(row.occupied_room_hours) || 0]));
  const peakMax = Math.max(0, ...hourly.values());
  const heatLevel = (weekday, hour) => {
    const value = hourly.get(`${weekday}-${hour}`) || 0;
    return value === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(value / peakMax * 4)));
  };
  const dataView = {
    kpis: { revenue: selectedRevenue, transactions: selectedTransactions, occupied, roomTotal: totalRooms, fnbRevenue },
    rooms,
    revenue: dailySeries,
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
    <div className="bm-heading"><div><span className="bm-eyebrow">QUẢN TRỊ VẬN HÀNH</span><h1 id="bm-title">Tổng quan cơ sở</h1><p>{user.branchName || 'Cơ sở đang quản lý'} · Báo cáo vận hành tháng {data.month?.slice(5)}/{data.month?.slice(0, 4)}</p></div><span className="bm-live"><i /> Dữ liệu trực tiếp</span></div>
    {error && <p role="alert">{error}</p>}{loading && <p role="status">Đang cập nhật…</p>}
    <div className="bm-kpis" aria-label="Các chỉ số vận hành chính">{[
      ['revenue', 'Doanh thu', money(kpis.revenue), rangeLabel], ['transactions', 'Tổng giao dịch', new Intl.NumberFormat('vi-VN').format(kpis.transactions), 'Hóa đơn đã hoàn tất'],
      ['rooms', 'Phòng đang sử dụng', `${kpis.occupied} / ${kpis.roomTotal}`, 'Trạng thái phòng hiện tại'], ['fnb', 'Doanh thu F&B', money(kpis.fnbRevenue), rangeLabel],
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
      <Panel title="Doanh thu" note={`Thu nhập vận hành · ${rangeLabel}`} className="bm-revenue-panel">
        <div className="bm-revenue-controls">
          <label>Thống kê theo<select value={revenuePeriod} onChange={(event) => {
            const next = event.target.value;
            setRevenuePeriod(next);
            setRevenueDate((current) => next === 'DAY' ? (current.length === 10 ? current : current.length === 7 ? `${current}-01` : `${current}-01-01`) : next === 'MONTH' ? (current.length >= 7 ? current.slice(0, 7) : `${current}-01`) : current.slice(0, 4));
          }}><option value="DAY">Ngày</option><option value="MONTH">Tháng</option><option value="YEAR">Năm</option></select></label>
          <label>{revenuePeriod === 'DAY' ? 'Chọn ngày' : revenuePeriod === 'YEAR' ? 'Chọn năm' : 'Chọn tháng'}
            {revenuePeriod === 'YEAR' ? <select value={revenueDate} onChange={(event) => setRevenueDate(event.target.value)}>{availableYears.map((year) => <option value={year} key={year}>{year}</option>)}</select>
              : <input type={revenuePeriod === 'DAY' ? 'date' : 'month'} max={revenuePeriod === 'DAY' ? today : currentMonth()} value={revenueDate} onChange={(event) => setRevenueDate(event.target.value)} />}
          </label>
        </div>
        <RevenueChart series={dataView.revenue} />
      </Panel>
      <Panel title="Khung giờ cao điểm" note="Số lượt phòng được sử dụng theo thứ và giờ trong kỳ" className="bm-heat-panel"><div className="bm-heatmap" aria-label="Lượt sử dụng phòng theo khung giờ"><span className="bm-heat-empty" />{days.map((day) => <span className="bm-heat-day" key={day}>{day}</span>)}{Array.from({ length: 12 }, (_, index) => <div className="bm-heat-row" key={index}><span>{`${String(index + 8).padStart(2, '0')}:00`}</span>{days.map((day, dayIndex) => { const value = hourly.get(`${dayIndex + 1}-${index + 8}`) || 0; return <i key={day} className={`heat-${heatLevel(dayIndex + 1, index + 8)}`} title={`${day} ${String(index + 8).padStart(2, '0')}:00 · ${value} lượt phòng`} />; })}</div>)}</div><div className="bm-heat-foot"><span>Ít</span>{[0,1,2,3,4].map((level) => <i key={level} className={`heat-${level}`} />)}<span>Nhiều</span></div></Panel>
    </div>
    <div className="bm-grid bm-grid-tables">
      <Panel title="Hiệu suất theo hạng phòng" note="Số liệu các lượt sử dụng kết thúc trong kỳ"><DataTable headings={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} labels={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} rows={dataView.roomTypes} /></Panel>
      <Panel title="Tồn kho cần chú ý" note="Mặt hàng đang dưới định mức tối thiểu" className="bm-inventory-panel" trailing={<span className="bm-panel-count">{inventory.length} mặt hàng</span>}>
        {inventory.length ? <ul className="bm-attention-list">{inventory.map((item) => {
          const quantity = Number(item.quantity) || 0;
          const minimum = Number(item.minimum) || 0;
          const level = minimum ? Math.max(0, Math.min(100, quantity / minimum * 100)) : 0;
          return <li key={item.id || item.name}>
            <span className="bm-attention-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 2.8 19h18.4L12 3Z"/><path d="M12 9v4m0 3h.01"/></svg></span>
            <div className="bm-attention-content"><strong>{item.name}</strong><span>Còn {quantity} {item.unit} · Định mức {minimum} {item.unit}</span><div className="bm-stock-track" role="meter" aria-label={`Tồn kho ${item.name}`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={level}><i style={{ width: `${level}%` }} /></div></div>
            <span className="bm-attention-badge">Cần nhập</span>
          </li>;
        })}</ul> : <p className="bm-panel-empty">Không có mặt hàng nào dưới định mức.</p>}
      </Panel>
      <Panel title="Sự cố thiết bị" note="Các sự cố đang cần theo dõi" className="bm-incidents-panel" trailing={<span className={`bm-panel-count${incidents.length ? ' is-attention' : ''}`}>{incidents.length} đang mở</span>}>
        {incidents.length ? <ul className="bm-incident-list">{incidents.slice(0, 6).map((incident) => <li key={incident.id}>
          <span className={`bm-incident-mark is-${incidentTone(incident.status)}`} aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 8v4m0 4h.01M10.3 3.9 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3l-7.6-13.1a2 2 0 0 0-3.4 0Z"/></svg></span>
          <div className="bm-incident-copy"><strong>{incident.assetCode || incident.id} · {incident.assetName}</strong><span>{incident.location || 'Chưa có vị trí'} · {localDate(incident.createdAt).split('-').reverse().join('/')}</span></div>
          <span className={`bm-incident-status is-${incidentTone(incident.status)}`}>{incidentStatus(incident.status)}</span>
        </li>)}</ul> : <p className="bm-panel-empty is-success">Không có sự cố nào đang mở.</p>}
      </Panel>
      <Panel title="Ca làm hôm nay & ngày mai" note="Lịch ca đã xác nhận và tình trạng chấm công" className="bm-shifts-panel" trailing={<span className="bm-panel-count">{shiftRows.length} ca</span>}>
        {shiftRows.length ? <ul className="bm-shift-list">{shiftRows.map((shift, index) => <li key={`${shift.date}-${shift.shift}-${shift.employee}-${index}`}>
          <div className="bm-shift-time"><span>{shift.dayLabel}</span><strong>{localTime(shift.startsAt)}<i aria-hidden="true">→</i>{localTime(shift.endsAt)}</strong><small>{shift.dateLabel}</small></div>
          <div className="bm-shift-person"><strong>{shift.employee}</strong><span>{shiftNames[shift.shift] || shift.shiftLabel}</span></div>
          <span className={`bm-attendance-status is-${attendanceTone(shift.attendance)}`}>{shift.attendance}</span>
        </li>)}</ul> : <p className="bm-panel-empty">Chưa có ca làm được xác nhận trong hai ngày tới.</p>}
      </Panel>
    </div>
    <p className="bm-disclaimer">Biểu đồ và bảng hiển thị dữ liệu từ API; các mục chưa được API cung cấp được để trống rõ ràng.</p>
  </main>;
}
