import { useEffect, useState } from 'react';
import api from '../../../api/client';
import './branch-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
const days = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const shiftNames = { NIGHT: 'Ca đêm', MORNING: 'Ca sáng', AFTERNOON: 'Ca chiều', EVENING: 'Ca tối' };
const localDate = (value) => String(value || '').slice(0, 10);
const localTime = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(date); };

function Panel({ title, note, children, className = '' }) {
  return <section className={`bm-panel ${className}`}><header className="bm-panel-head"><div><h2>{title}</h2>{note && <p>{note}</p>}</div></header>{children}</section>;
}

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
  return <div className="bm-table-wrap"><table className="bm-table"><thead><tr>{headings.map((heading) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead><tbody>{rows.length ? rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, index) => <td key={index} data-label={labels[index]}>{cell}</td>)}</tr>) : <tr><td colSpan={headings.length}>API hiện chưa có dữ liệu cho bảng này.</td></tr>}</tbody></table></div>;
}

export default function BranchOverviewDashboard({ user, onNavigate }) {
  const period = 7;
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
    api.get('/branch-management/dashboard', { params: { month }, headers: { 'X-GCMB-Branch-Id': user.branchId } })
      .then(({ data: result }) => { if (active) setData(result); })
      .catch((cause) => { if (active) setError(cause.response?.data?.detail || cause.response?.data?.message || 'Không tải được dữ liệu cơ sở.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user.branchId]);
  const result = data?.result || {};
  const ops = data?.operations || {};
  const occupied = Number(ops.occupiedRooms) || 0;
  const maintenance = Number(ops.maintenanceRooms) || 0;
  const totalRooms = Number(ops.roomCount) || 0;
  const revenue = Number(result.revenue) || 0;
  const booked = Number(ops.bookedRooms) || 0;
  const dailySeries = (data?.dailyRevenue || []).map((row) => {
    const date = localDate(row.date);
    return { label: `${date.slice(8, 10)}/${date.slice(5, 7)}`, value: Number(row.revenue) || 0 };
  });
  const rooms = { occupied, maintenance, booked, vacant: Math.max(0, totalRooms - occupied - maintenance - booked) };
  const topItems = (data?.topItems || []).map((row) => [row.name, Number(row.quantity) || 0]);
  const roomTypes = (data?.roomTypePerformance || []).map((row) => [row.name, row.count, money(row.revenue), `${Math.round(Number(row.minutes) || 0)} phút`]);
  const inventory = (data?.lowStockItems || []).map((row) => [row.name, `${row.quantity} ${row.unit}`, `${row.minimum} ${row.unit}`, 'Dưới định mức']);
  const incidents = (data?.openIncidents || []).map((row) => [row.assetCode || row.id, row.assetName, row.location, row.status, localDate(row.createdAt)]);
  const shifts = (data?.todayShifts || []).map((row) => [localDate(row.date).split('-').reverse().join('/'), `${shiftNames[row.shift] || row.shift} · ${localTime(row.startsAt)}–${localTime(row.endsAt)}`, row.employee, row.attendance]);
  const fnbRevenue = (data?.dailyRevenue || []).reduce((sum, row) => sum + Number(row.fnbRevenue || 0), 0);
  const hourly = new Map((data?.hourlyOccupancy || []).map((row) => [`${row.weekday}-${row.hour}`, Number(row.occupied_room_hours) || 0]));
  const peakMax = Math.max(0, ...hourly.values());
  const heatLevel = (weekday, hour) => {
    const value = hourly.get(`${weekday}-${hour}`) || 0;
    return value === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(value / peakMax * 4)));
  };
  const dataView = {
    kpis: { todayRevenue: revenue, bookings: result.billCount, occupied, roomTotal: totalRooms, fnbRevenue, lowStock: ops.lowStockItems, staffPresent: ops.staffPresent, staffTotal: ops.staffTotal },
    rooms,
    revenue: { 7: dailySeries.slice(-7), 30: dailySeries.slice(-30) },
    peakHours: hourly, topItems, roomTypes, inventory, incidents, shifts,
    tasks: [
      ...(maintenance ? [['Phòng đang bảo trì', `${maintenance} phòng · số liệu vận hành hiện tại`, 'Phòng']] : []),
      ...(Number(ops.openIncidents) ? [['Sự cố thiết bị đang mở', `${ops.openIncidents} sự cố cần theo dõi`, 'Thiết bị & sự cố']] : []),
    ],
  };
  if (!data) return <main className="bm-dashboard" aria-live="polite"><h1>Tổng quan cơ sở</h1>{loading && <p>Đang tải dữ liệu…</p>}{error && <p role="alert">{error}</p>}</main>;
  const { kpis } = dataView;
  const total = rooms.vacant + rooms.booked + rooms.occupied + rooms.maintenance;
  const occupiedShare = total ? rooms.occupied / total * 100 : 0;
  const slices = [rooms.vacant, rooms.booked, rooms.occupied, rooms.maintenance].map((n) => n / Math.max(1, total) * 100);
  const gradient = `conic-gradient(#16855b 0 ${slices[0]}%, #2586a0 ${slices[0]}% ${slices[0] + slices[1]}%, #a91669 ${slices[0] + slices[1]}% ${slices[0] + slices[1] + slices[2]}%, #bf6744 ${slices[0] + slices[1] + slices[2]}% 100%)`;
  const itemMax = Math.max(1, ...dataView.topItems.map(([, amount]) => amount));
  const shiftRows = dataView.shifts;
  return <main className="bm-dashboard" aria-labelledby="bm-title">
    <div className="bm-heading"><div><h1 id="bm-title">Tổng quan cơ sở</h1><p>{user.branchName || 'Cơ sở đang quản lý'} · Báo cáo kỳ {data.month?.slice(5)}/{data.month?.slice(0, 4)}</p></div><span className="bm-live"><i /> Dữ liệu thật</span></div>
    {error && <p role="alert">{error}</p>}{loading && <p role="status">Đang cập nhật…</p>}
    <div className="bm-kpis">{[
      ['Doanh thu kỳ', money(kpis.todayRevenue), `${result.billCount || 0} bill đã hoàn tất`], ['Tổng bill', kpis.bookings ?? '—', 'Trong kỳ báo cáo'],
      ['Phòng đang sử dụng', `${kpis.occupied} / ${kpis.roomTotal}`, 'Theo trạng thái phòng hiện tại'], ['Doanh thu F&B', money(kpis.fnbRevenue), 'Bill đã hoàn tất trong kỳ'],
      ['Mặt hàng tồn thấp', ops.lowStockItems ?? '—', 'Tồn thấp hơn định mức tối thiểu'], ['Nhân viên đi làm hôm nay', `${ops.staffPresent ?? '—'} / ${ops.staffTotal ?? '—'}`, 'Theo dữ liệu chấm công trong ngày'],
    ].map(([label, value, note]) => <article className="bm-kpi" key={label}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>)}</div>

    <div className="bm-grid bm-grid-lead">
      <Panel title="Trạng thái phòng" note={`${total} phòng trong cơ sở`} className="bm-room-panel">
        <div className="bm-room-content"><div className="bm-donut" style={{ '--bm-donut': gradient }} role="img" aria-label={`Đang sử dụng ${rooms.occupied} trên ${total} phòng`}><div><strong>{Math.round(occupiedShare)}%</strong><span>đang dùng</span></div></div>
          <ul className="bm-legend">{[['Trống', rooms.vacant, 'vacant'], ['Đã đặt', rooms.booked, 'booked'], ['Đang sử dụng', rooms.occupied, 'occupied'], ['Bảo trì', rooms.maintenance, 'maintenance']].map(([name, count, tone]) => <li key={name}><i className={`is-${tone}`} /><span>{name}</span><strong>{count == null ? '—' : count}</strong></li>)}</ul></div>
      </Panel>
      <Panel title="Doanh thu" note="Theo kỳ tài chính hiện tại" className="bm-revenue-panel"><RevenueChart series={dataView.revenue[period]} /></Panel>
      <Panel title="Khung giờ cao điểm" note="Số lượt phòng được sử dụng theo thứ và giờ trong kỳ" className="bm-heat-panel"><div className="bm-heatmap" aria-label="Lượt sử dụng phòng theo khung giờ"><span className="bm-heat-empty" />{days.map((day) => <span className="bm-heat-day" key={day}>{day}</span>)}{Array.from({ length: 12 }, (_, index) => <div className="bm-heat-row" key={index}><span>{`${String(index + 8).padStart(2, '0')}:00`}</span>{days.map((day, dayIndex) => { const value = hourly.get(`${dayIndex + 1}-${index + 8}`) || 0; return <i key={day} className={`heat-${heatLevel(dayIndex + 1, index + 8)}`} title={`${day} ${String(index + 8).padStart(2, '0')}:00 · ${value} lượt phòng`} />; })}</div>)}</div><div className="bm-heat-foot"><span>Ít</span>{[0,1,2,3,4].map((level) => <i key={level} className={`heat-${level}`} />)}<span>Nhiều</span></div></Panel>
      <Panel title="Top 10 món F&B bán chạy" note="Số lượng bán trong kỳ" className="bm-items-panel">{dataView.topItems.length ? <ol className="bm-bars">{dataView.topItems.map(([name, count], index) => <li key={name}><span className="bm-rank">{String(index + 1).padStart(2, '0')}</span><span className="bm-item-name">{name}</span><span className="bm-bar-track"><i style={{ width: `${count / itemMax * 100}%` }} /></span><strong>{count}</strong></li>)}</ol> : <p>Không có món F&amp;B đã bán trong kỳ.</p>}</Panel>
    </div>
    <div className="bm-grid bm-grid-tables">
      <Panel title="Hiệu suất theo hạng phòng" note="Số liệu các lượt sử dụng kết thúc trong kỳ"><DataTable headings={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} labels={['Hạng phòng', 'Số lượt', 'Doanh thu', 'Thời gian dùng TB']} rows={dataView.roomTypes} /></Panel>
      <Panel title="Tồn kho cần chú ý" note="Tồn hiện tại thấp hơn mức tối thiểu"><DataTable headings={['Mặt hàng', 'Tồn hiện tại', 'Mức tối thiểu', '']} labels={['Mặt hàng', 'Tồn hiện tại', 'Mức tối thiểu', 'Trạng thái']} rows={dataView.inventory} /></Panel>
      <Panel title="Sự cố thiết bị" note={`${(data.openIncidents || []).length} sự cố chưa xử lý xong`}><DataTable headings={['Mã sự cố', 'Thiết bị', 'Vị trí', 'Trạng thái', 'Tạo lúc']} labels={['Mã sự cố', 'Thiết bị', 'Vị trí', 'Trạng thái', 'Tạo lúc']} rows={dataView.incidents} /></Panel>
      <Panel title="Ca làm hôm nay và ngày mai" note="Nhân sự chưa chấm công được đánh dấu"><DataTable headings={['Ngày', 'Khung giờ', 'Nhân viên', 'Chấm công']} labels={['Ngày', 'Khung giờ', 'Nhân viên', 'Chấm công']} rows={shiftRows} /></Panel>
      <Panel title="Việc cần làm" note="Các mục cần xử lý tại cơ sở" className="bm-tasks-panel"><ul className="bm-tasks">{dataView.tasks.map(([title, detail, destination]) => <li key={title}><span className="bm-task-mark" /><div><strong>{title}</strong><small>{detail}</small></div><button type="button" aria-label={`Mở ${destination}`} onClick={() => onNavigate?.(destination)}>Mở <span aria-hidden="true">↗</span></button></li>)}</ul></Panel>
    </div>
    <p className="bm-disclaimer">Biểu đồ và bảng hiển thị dữ liệu từ API; các mục chưa được API cung cấp được để trống rõ ràng.</p>
  </main>;
}
