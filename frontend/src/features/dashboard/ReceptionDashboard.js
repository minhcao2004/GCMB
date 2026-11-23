import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { bookingApi } from '../booking/services/bookingApi';

const statusNames = { occupied: 'Đang sử dụng', reserved: 'Đã đặt', vacant: 'Trống', maintenance: 'Bảo trì', inactive: 'Ngừng hoạt động' };
const bookingNames = { CONFIRMED: 'Đã đặt', IN_HOUSE: 'Đang sử dụng', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', NO_SHOW: 'No-show', DRAFT: 'Nháp' };
const localDate = () => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

export default function ReceptionDashboard({ user }) {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    setError('');
    const today = localDate();
    Promise.all([
      bookingApi.rooms(),
      Promise.all([bookingApi.listAll({ dateFrom: today, dateTo: today }), bookingApi.listAll({ status: 'IN_HOUSE' })]),
    ]).then(([roomList, bookingSets]) => {
      setRooms(roomList);
      setBookings([...new Map(bookingSets.flat().map((booking) => [booking.id, booking])).values()]);
    }).catch((cause) => setError(cause.response?.data?.detail || cause.response?.data?.message || 'Không tải được số liệu hiện tại.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, 90_000);
    window.addEventListener('focus', refresh);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [refresh, user.branchId]);

  const currentRooms = useMemo(() => {
    const now = Date.now();
    const current = new Map(rooms.map((room) => [room.id, {
      ...room,
      status: ({ AVAILABLE: 'vacant', UNDER_MAINTENANCE: 'maintenance', INACTIVE: 'inactive' })[room.operatingStatus] || 'inactive',
      booking: null,
    }]));
    bookings.forEach((booking) => {
      if (!booking.roomId || !['IN_HOUSE', 'CONFIRMED'].includes(booking.status)) return;
      const start = new Date(booking.plannedStart).getTime();
      const end = new Date(booking.plannedEnd).getTime();
      const inUse = booking.status === 'IN_HOUSE';
      if (!inUse && !(start <= now && now < end)) return;
      const room = current.get(booking.roomId);
      if (!room || ['maintenance', 'inactive'].includes(room.status) || (room.status === 'occupied' && !inUse)) return;
      current.set(booking.roomId, { ...room, status: inUse ? 'occupied' : 'reserved', booking });
    });
    return [...current.values()];
  }, [rooms, bookings]);

  const roomStats = useMemo(() => Object.keys(statusNames).map((status) => ({
    key: status, label: statusNames[status], items: currentRooms.filter((room) => room.status === status),
  })), [currentRooms]);
  const bookingStats = useMemo(() => Object.keys(bookingNames).map((status) => ({
    key: status, label: bookingNames[status], items: bookings.filter((booking) => booking.status === status),
  })), [bookings]);
  const detailItems = selected?.kind === 'rooms' ? selected.group.items : selected?.kind === 'bookings' ? selected.group.items : [];

  return <section className="manager-content performance-dashboard is-reception" aria-labelledby="reception-dashboard-title">
    <header className="performance-heading"><div><h1 id="reception-dashboard-title">Tổng quan lễ tân</h1></div>
      <div className="performance-dashboard-actions"><span>Số liệu hiện tại · tự cập nhật mỗi 90 giây</span><button type="button" onClick={refresh} disabled={loading}>Làm mới</button></div>
    </header>
    {loading && <p className="performance-state" role="status">Đang tải số liệu hiện tại…</p>}
    {error && <p className="performance-state performance-error" role="alert">{error}</p>}
    {!loading && !error && <>
      <div className="performance-grid performance-grid-reception">
        <article className="performance-panel"><div className="performance-panel-heading"><div><h2>Tình trạng phòng</h2><p>Bấm vào trạng thái để xem danh sách phòng tương ứng.</p></div><strong>{currentRooms.length} phòng</strong></div>
          <div className="performance-chart" aria-label="Biểu đồ tình trạng phòng">
            {roomStats.map((group) => <button key={group.key} type="button" className={`performance-chart-row${selected?.kind === 'rooms' && selected.group.key === group.key ? ' is-selected' : ''}`} onClick={() => setSelected({ kind: 'rooms', group })} aria-pressed={selected?.kind === 'rooms' && selected.group.key === group.key}>
              <span>{group.label}</span><span className="performance-bar" aria-hidden="true"><i style={{ width: `${currentRooms.length ? group.items.length / currentRooms.length * 100 : 0}%` }} /></span><strong>{group.items.length}</strong>
            </button>)}
          </div>
        </article>
        <article className="performance-panel"><div className="performance-panel-heading"><div><h2>Booking hôm nay và đang sử dụng</h2><p>Bấm vào trạng thái để xem booking tương ứng.</p></div><strong>{bookings.length} booking</strong></div>
          <div className="performance-chart" aria-label="Biểu đồ booking hôm nay">
            {bookingStats.map((group) => <button key={group.key} type="button" className={`performance-chart-row${selected?.kind === 'bookings' && selected.group.key === group.key ? ' is-selected' : ''}`} onClick={() => setSelected({ kind: 'bookings', group })} aria-pressed={selected?.kind === 'bookings' && selected.group.key === group.key}>
              <span>{group.label}</span><span className="performance-bar" aria-hidden="true"><i style={{ width: `${bookings.length ? group.items.length / bookings.length * 100 : 0}%` }} /></span><strong>{group.items.length}</strong>
            </button>)}
          </div>
        </article>
      </div>
      {selected && <section className="performance-panel performance-chart-results" aria-live="polite"><div className="performance-panel-heading"><div><h2>{selected.group.label} · {detailItems.length}</h2><p>{selected.kind === 'rooms' ? 'Danh sách phòng hiện tại' : 'Danh sách booking trong ngày và booking đang sử dụng'}</p></div><button type="button" onClick={() => setSelected(null)}>Đóng chi tiết</button></div>
        {detailItems.length ? <div className="performance-detail-chips">{detailItems.map((item) => <span key={item.id}>{selected.kind === 'rooms' ? `${item.code} · ${item.name}` : `${item.code} · ${item.contactName || 'Khách'} · ${bookingNames[item.status]}`}</span>)}</div> : <p className="performance-empty">Hiện không có mục nào ở trạng thái này.</p>}
      </section>}
      <div className="performance-dashboard-actions performance-quick-links"><button type="button" onClick={() => navigate('/le-tan/phong')}>Mở sơ đồ phòng</button><button type="button" onClick={() => navigate('/le-tan/bookings')}>Quản lý booking</button></div>
    </>}
  </section>;
}
