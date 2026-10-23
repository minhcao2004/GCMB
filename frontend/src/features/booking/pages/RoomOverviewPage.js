import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { clearBookingDraft } from '../services/bookingDraft';
import { formatVnd, toLocalDateInput } from '../data/bookingData';
import { AppShell } from './BookingManagementPage';
import BookingStayDialog from './BookingStayDialog';
import '../styles/booking.css';
import '../styles/rooms.css';

function errorText(error) {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || 'Không tải được dữ liệu phòng.';
}

function clockTime(date) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function dateTimeText(value, timezone) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('vi-VN', { timeZone: timezone, day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));
}

function timeText(value, timezone) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('vi-VN', { timeZone: timezone, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));
}

function dateInputText(value) {
  const [year, month, day] = String(value || '').split('-');
  return year && month && day ? `${day}/${month}/${year}` : 'ngày đã chọn';
}

function dateInputInZone(value, timezone) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(value));
  const part = (type) => parts.find((item) => item.type === type)?.value || '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function bookingStatusText(status) {
  return ({ CONFIRMED: 'Đã đặt', IN_HOUSE: 'Đang sử dụng', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', NO_SHOW: 'No-show', DRAFT: 'Nháp' })[status] || status;
}

function floorOf(code) {
  const match = String(code || '').match(/\d/);
  return match ? match[0] : null;
}

function statusText(status) {
  return ({ occupied: 'Đang sử dụng', reserved: 'Đã đặt', vacant: 'Trống', maintenance: 'Bảo trì', inactive: 'Ngừng hoạt động' })[status];
}

async function bookingsForDate(date) {
  const [daily, inHouse] = await Promise.all([
    bookingApi.listAll({ dateFrom: date, dateTo: date }),
    bookingApi.listAll({ status: 'IN_HOUSE' }),
  ]);
  return [...new Map([...daily, ...inHouse].map((booking) => [booking.id, booking])).values()];
}

export default function RoomOverviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState('');
  const [retry, setRetry] = useState(0);
  const [options, setOptions] = useState(null);
  const [selectedDate, setSelectedDate] = useState('');
  const [search, setSearch] = useState('');
  const [tier, setTier] = useState('all');
  const [floor, setFloor] = useState('all');
  const [status, setStatus] = useState('all');
  const [bookings, setBookings] = useState([]);
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [selectedRoomId, setSelectedRoomId] = useState(() => location.state?.roomId || '');
  const [detailOpen, setDetailOpen] = useState(true);
  const [preferredRoomId, setPreferredRoomId] = useState('');
  const [detail, setDetail] = useState(null);
  const [stayMode, setStayMode] = useState(null);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const scheduleDialogRef = useRef(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => { document.title = 'Phòng | GenZ Cinema & Music Box'; }, []);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else { setUser(identity); setAuthError(''); }
    }).catch((error) => {
      if (!active) return;
      if (error.response?.status === 401) navigate('/dang-nhap', { replace: true });
      else setAuthError(errorText(error));
    });
    return () => { active = false; };
  }, [navigate, retry]);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    bookingApi.options().then((value) => { if (active) setOptions(value); })
      .catch((error) => { if (active) setLoadError(errorText(error)); });
    return () => { active = false; };
  }, [user]);

  const currentMinute = Math.floor(now / 60000);
  const date = selectedDate || toLocalDateInput(new Date((currentMinute + 5) * 60000));
  // Dùng giờ hiện tại cộng 5 phút cho ngày đã chọn, thống nhất với bộ lọc không có ô giờ.
  const slot = useMemo(() => new Date(`${date}T${clockTime(new Date((currentMinute + 5) * 60000))}:00`), [date, currentMinute]);
  useEffect(() => {
    if (!user || Number.isNaN(slot.getTime())) return undefined;
    let active = true;
    setLoading(true);
    setLoadError('');
    Promise.all([bookingsForDate(date), bookingApi.rooms()]).then(([list, roomList]) => {
      if (!active) return;
      setBookings(list);
      setInventory(roomList);
    }).catch((error) => { if (active) setLoadError(errorText(error)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, date, slot, reload]);

  const rooms = useMemo(() => {
    const byRoom = new Map();
    inventory.forEach((room) => byRoom.set(room.id, { ...room, status: ({ AVAILABLE: 'vacant', UNDER_MAINTENANCE: 'maintenance', INACTIVE: 'inactive' })[room.operatingStatus] || 'inactive' }));
    bookings.forEach((booking) => {
      if (!booking.roomId || !['IN_HOUSE', 'CONFIRMED'].includes(booking.status)) return;
      const overlaps = new Date(booking.plannedStart) <= slot && new Date(booking.plannedEnd) > slot;
      const upcoming = booking.status === 'CONFIRMED' && new Date(booking.plannedStart) > slot;
      if (!overlaps && !upcoming && booking.status !== 'IN_HOUSE') return;
      const existing = byRoom.get(booking.roomId);
      if (existing && !['vacant', 'reserved', 'occupied'].includes(existing.status) && booking.status !== 'IN_HOUSE') return;
      if (existing?.status === 'occupied' && booking.status !== 'IN_HOUSE') return;
      if (existing?.status === 'reserved' && booking.status === 'CONFIRMED'
          && new Date(existing.booking.plannedStart) <= new Date(booking.plannedStart)) return;
      byRoom.set(booking.roomId, {
        id: booking.roomId, code: booking.roomCode, name: booking.roomName,
        roomTypeName: booking.roomTypeName, capacity: existing?.capacity || options?.roomTypes?.find((type) => type.name === booking.roomTypeName)?.capacity,
        operatingStatus: existing?.operatingStatus,
        status: booking.status === 'IN_HOUSE' ? 'occupied' : 'reserved', booking,
      });
    });
    return [...byRoom.values()].sort((a, b) => String(a.code).localeCompare(String(b.code), 'vi-VN', { numeric: true }));
  }, [inventory, bookings, slot, options]);

  useEffect(() => {
    if (!rooms.length) { setSelectedRoomId(''); return; }
    if (preferredRoomId) {
      if (rooms.some((room) => room.id === preferredRoomId)) { setSelectedRoomId(preferredRoomId); setPreferredRoomId(''); }
      return;
    }
    if (!rooms.some((room) => room.id === selectedRoomId)) setSelectedRoomId((rooms.find((room) => room.status === 'occupied') || rooms[0]).id);
  }, [rooms, selectedRoomId, preferredRoomId]);

  const selected = rooms.find((room) => room.id === selectedRoomId) || null;
  const selectedBooking = selected?.booking;
  const selectedRoomBookings = useMemo(() => bookings
    .filter((booking) => booking.roomId === selected?.id)
    .sort((a, b) => new Date(a.plannedStart) - new Date(b.plannedStart)), [bookings, selected?.id]);
  useEffect(() => {
    if (!selectedBooking) { setDetail(null); return undefined; }
    let active = true;
    setDetail(null);
    bookingApi.detail(selectedBooking.id).then((value) => { if (active) setDetail(value); })
      .catch((error) => { if (active) setLoadError(errorText(error)); });
    return () => { active = false; };
  }, [selectedBooking, reload]);

  useEffect(() => {
    const dialog = scheduleDialogRef.current;
    if (!dialog) return;
    if (scheduleOpen && !dialog.open) dialog.showModal();
    if (!scheduleOpen && dialog.open) dialog.close();
  }, [scheduleOpen]);

  const floors = [...new Set(rooms.map((room) => floorOf(room.code)).filter(Boolean))].sort();
  const visible = rooms.filter((room) => {
    const keyword = search.trim().toLocaleLowerCase('vi-VN');
    return (floor === 'all' || floorOf(room.code) === floor)
      && (status === 'all' || room.status === status)
      && (tier === 'all' || room.roomTypeName === tier)
      && (!keyword || [room.code, room.name, room.booking?.contactName, room.booking?.code].some((part) => String(part || '').toLocaleLowerCase('vi-VN').includes(keyword)));
  });
  const counts = Object.fromEntries(['vacant', 'reserved', 'occupied', 'maintenance', 'inactive'].map((key) => [key, rooms.filter((room) => room.status === key).length]));
  const timezone = options?.timezone || 'Asia/Ho_Chi_Minh';
  const selectedDetail = selectedBooking && detail?.booking?.id === selectedBooking.id ? detail : null;
  const currentBooking = selectedDetail?.booking || selectedBooking || null;
  const detailReady = !selectedBooking || Boolean(selectedDetail);
  const roomBookingStart = currentBooking?.plannedStart ? new Date(currentBooking.plannedStart).getTime() : NaN;
  const roomBookingEnd = currentBooking?.plannedEnd ? new Date(currentBooking.plannedEnd).getTime() : NaN;
  const canCheckIn = selected?.status === 'reserved' && currentBooking?.status === 'CONFIRMED'
    && Number.isFinite(roomBookingStart) && now >= roomBookingStart
    && Number.isFinite(roomBookingEnd) && now < roomBookingEnd
    && selected?.operatingStatus === 'AVAILABLE' && detailReady && !busy;
  const scheduleDateLabel = date === dateInputInZone(now, timezone) ? 'Hôm nay' : dateInputText(date);
  const meter = selectedDetail?.meter || null;
  const activeCombo = selectedDetail?.charges?.find((charge) => charge.status === 'ACTIVE' && (charge.comboId || charge.kind === 'COMBO'))?.description;
  const checkinMs = currentBooking?.actualCheckin ? new Date(currentBooking.actualCheckin).getTime() : NaN;
  const plannedEndMs = currentBooking?.plannedEnd ? new Date(currentBooking.plannedEnd).getTime() : NaN;
  const usageStopped = Number.isFinite(plannedEndMs) && now >= plannedEndMs;
  const clockEndMs = Number.isFinite(plannedEndMs) ? Math.min(now, plannedEndMs) : now;
  const elapsedSeconds = Number.isFinite(checkinMs) ? Math.max(0, Math.floor((clockEndMs - checkinMs) / 1000)) : null;
  const elapsedText = elapsedSeconds == null ? '—' : [Math.floor(elapsedSeconds / 3600), Math.floor(elapsedSeconds % 3600 / 60), elapsedSeconds % 60].map((part) => String(part).padStart(2, '0')).join(':');

  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); clearBookingDraft(user); navigate('/dang-nhap', { replace: true }); }
    catch (error) { setLoadError(errorText(error)); }
    finally { setBusy(false); }
  };

  const openRoomBookings = () => {
    if (!selected) return;
    navigate('/le-tan/bookings', { state: { bookingDate: date, search: selected.code } });
  };

  const openSelectedBooking = (booking = currentBooking) => {
    if (!booking?.id) return;
    navigate('/le-tan/bookings', { state: { bookingDate: date, bookingId: booking.id } });
  };

  const openCheckIn = () => {
    if (!canCheckIn || !currentBooking?.id) return;
    navigate(`/le-tan/bookings/${currentBooking.id}/check-in`, { state: { bookingDate: date } });
  };

  const saveStay = async (action, payload) => {
    if (!currentBooking) return { error: 'Chưa chọn phòng đang sử dụng.' };
    setBusy(true);
    try {
      const result = action === 'change' ? await bookingApi.changeRoom(currentBooking.id, payload)
        : action === 'extend' ? await bookingApi.extendStay(currentBooking.id, payload)
          : action === 'meterStart' ? await bookingApi.startHourlyMeter(currentBooking.id)
            : await bookingApi.stopHourlyMeter(currentBooking.id);
      if (action === 'change') setPreferredRoomId(result.booking.booking.roomId);
      if (action === 'change' || action === 'extend') setStayMode(null);
      setNotice(action === 'change' ? `Đã đổi sang phòng ${result.booking.booking.roomCode}. Điều chỉnh ${formatVnd(result.adjustmentAmount)}.`
        : action === 'extend' ? `Đã gia hạn đến ${dateTimeText(result.endsAt, timezone)}. Phụ thu ${formatVnd(result.quote.expectedAmount)}.`
          : action === 'meterStart' ? 'Đã bật đồng hồ giờ lẻ.' : `Đã dừng đồng hồ; chốt ${result.meter.billedMinutes} phút và ${formatVnd(result.meter.amount)}.`);
      setReload((value) => value + 1);
      return { ok: true, detail: ['meterStart', 'meterStop'].includes(action) ? result : undefined };
    } catch (error) { return { error: errorText(error) }; }
    finally { setBusy(false); }
  };

  if (!user) return <main className="booking-auth-state">{authError ? <div className="booking-auth-message" role="alert">{authError} <button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button></div> : <p role="status">Đang kiểm tra phiên đăng nhập…</p>}</main>;

  return <AppShell user={user} branchName={options?.branchName} busy={busy} onLogout={logout} breadcrumb="Phòng" search={search} onSearchChange={setSearch} activeTab="rooms" hideSearch>
    <div className="booking-page-heading room-page-heading"><div><h1>Phòng</h1><p>Theo dõi trạng thái và thao tác nhanh tại quầy</p></div></div>
    {notice && <div className="booking-live-notice" role="status"><span>✓</span>{notice}<button type="button" aria-label="Đóng thông báo" onClick={() => setNotice('')}>×</button></div>}
    {loadError && <div className="booking-inline-error" role="alert">{loadError}<button type="button" onClick={() => setReload((value) => value + 1)}>Thử lại</button></div>}
    <div className={`room-layout${detailOpen ? '' : ' room-layout--compact'}`}>
      <section className="room-board" aria-label="Sơ đồ phòng">
        <div className="room-toolbar">
          <label className="room-search"><span className="sr-only">Tìm phòng</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm phòng, booking, khách…" /></label>
          <label className="room-date"><span className="sr-only">Ngày xem phòng</span><input type="date" value={date} onChange={(event) => setSelectedDate(event.target.value)} /></label>
          <label className="room-tier"><span className="sr-only">Hạng phòng</span><select value={tier} onChange={(event) => setTier(event.target.value)}><option value="all">Hạng phòng</option>{[...new Set(rooms.map((room) => room.roomTypeName).filter(Boolean))].sort().map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
        </div>
        <p className="room-view-note">Hiển thị danh sách phòng của cơ sở. Lịch đặt theo ngày đã chọn; trạng thái vận hành và khách đang sử dụng lấy từ hiện tại.</p>
        <div className="room-board-heading"><h2>Sơ đồ phòng</h2><span>Hiển thị {visible.length}/{rooms.length} phòng</span></div>
        <div className="room-floors" role="group" aria-label="Lọc tầng"><button type="button" className={floor === 'all' ? 'is-active' : ''} onClick={() => setFloor('all')}>Tất cả</button>{floors.map((value) => <button type="button" key={value} className={floor === value ? 'is-active' : ''} onClick={() => setFloor(value)}>Tầng {value}</button>)}</div>
        <div className="room-status-filters" role="group" aria-label="Lọc trạng thái"><button type="button" className={status === 'all' ? 'is-active' : ''} onClick={() => setStatus('all')}>Tất cả <b>{rooms.length}</b></button>{['vacant', 'reserved', 'occupied', 'maintenance', 'inactive'].map((key) => <button type="button" key={key} className={`${key}${status === key ? ' is-active' : ''}`} onClick={() => setStatus(key)}>{statusText(key)} <b>{counts[key]}</b></button>)}</div>
        {loading ? <p className="room-empty" role="status">Đang tải phòng…</p> : !visible.length ? <p className="room-empty">{loadError ? 'Chưa tải được danh sách phòng. Vui lòng thử lại.' : 'Không có phòng phù hợp với bộ lọc này.'}</p> : <div className="room-grid">{visible.map((room) => <button type="button" key={room.id} className={`room-card room-card-${room.status}${selectedRoomId === room.id ? ' is-selected' : ''}`} aria-pressed={selectedRoomId === room.id} onClick={() => setSelectedRoomId(room.id)}><strong>{room.code} {room.name}</strong><small>{room.roomTypeName || 'Phòng'}{room.capacity ? ` · ${room.capacity} người` : ''}</small><span>{statusText(room.status)}</span><em>{room.status === 'occupied' ? `${dateTimeText(room.booking?.actualCheckin || room.booking?.plannedStart, timezone)} · ${room.booking?.contactName || room.booking?.code}` : room.status === 'reserved' ? `${dateTimeText(room.booking?.plannedStart, timezone)} · ${room.booking?.contactName || room.booking?.code}` : room.status === 'vacant' ? 'Sẵn sàng' : statusText(room.status)}</em></button>)}</div>}
      </section>
      <aside className={`room-detail${detailOpen ? '' : ' room-detail--compact'}`} aria-label="Phòng đang chọn">
        <div className="room-detail-header">
          {detailOpen && <span className="room-detail-label">Phòng đang chọn</span>}
          <button type="button" className="room-panel-toggle" aria-label={detailOpen ? 'Thu gọn chi tiết phòng' : 'Mở chi tiết phòng'} aria-expanded={detailOpen} aria-controls="room-detail-content" onClick={() => setDetailOpen((open) => !open)}>{detailOpen ? '›' : '‹'}</button>
        </div>
        <div id="room-detail-content" hidden={!detailOpen}>
        {selected ? <><h2>{selected.code} · {selected.name}</h2><span className={`room-detail-status ${selected.status}`}>{statusText(selected.status)}</span><p>{selected.roomTypeName || 'Phòng'}{selected.capacity ? ` · tối đa ${selected.capacity} người` : ''}</p>
          {currentBooking && selected.status === 'reserved' && <>
            <div className="room-detail-divider" />
            <section className="room-reservation-facts" aria-label="Thông tin booking">
              <div><span>Lịch đặt gần nhất</span><strong>{scheduleDateLabel} · {timeText(currentBooking.plannedStart, timezone)}–{timeText(currentBooking.plannedEnd, timezone)}</strong><small>{now < roomBookingStart ? 'Sắp có khách · chưa nhận phòng' : 'Đã đến giờ · chưa nhận phòng'}</small></div>
              <div className="room-detail-divider" />
              <div><span>Thông tin booking</span><strong>{currentBooking.contactName} · {currentBooking.code}</strong><small>{currentBooking.guestCount || 0} khách · {Number(currentBooking.depositReceivedAmount ?? currentBooking.depositBalance) > 0 ? 'đã ghi nhận tiền cọc' : 'chưa ghi nhận tiền cọc'}</small></div>
            </section>
            <button type="button" className="room-reservation-link" onClick={openRoomBookings}>
              <strong>Xem mọi booking của {selected.code}</strong>
              <span>Mở Booking ngày {dateInputText(date)} với bộ lọc phòng {selected.code} được chọn sẵn.</span>
            </button>
            <h3>Thao tác</h3>
            <button type="button" className="room-checkout room-booking-primary" onClick={() => openSelectedBooking()}>Xem booking phòng này</button>
            <div className="room-booking-actions">
              <button type="button" onClick={() => setScheduleOpen(true)}>Xem lịch phòng</button>
              <button type="button" className="is-available" disabled={!canCheckIn} onClick={openCheckIn}>Nhận phòng</button>
            </div>
            {!canCheckIn && currentBooking.status === 'CONFIRMED' && now < roomBookingStart && <small className="room-checkout-note">Có thể nhận phòng từ {timeText(currentBooking.plannedStart, timezone)}.</small>}
          </>}
          {currentBooking && selected.status !== 'reserved' && <><div className="room-detail-divider" /><dl className="room-detail-facts"><div><dt>Booking</dt><dd>{currentBooking.code}</dd></div><div><dt>Khách hàng</dt><dd>{currentBooking.contactName}</dd></div><div><dt>Nhận phòng</dt><dd>{dateTimeText(currentBooking.actualCheckin || currentBooking.plannedStart, timezone)}</dd></div><div><dt>Giờ trả dự kiến</dt><dd>{dateTimeText(currentBooking.plannedEnd, timezone)}</dd></div></dl>{selected.status === 'occupied' && <section className="room-usage"><span>Thời gian sử dụng</span><strong>{elapsedText}</strong>{usageStopped && <small className="is-ended">Đã dừng tại giờ trả dự kiến</small>}<small>Combo đang dùng: {activeCombo || 'Theo giờ'}</small></section>}<div className="room-detail-total"><span>Tạm tính</span><strong>{formatVnd(currentBooking.expectedAmount)}</strong></div></>}
          {meter && <p className="room-meter-note">Giờ lẻ: {meter.status === 'RUNNING' ? 'đang chạy' : `đã chốt ${meter.billedMinutes} phút`}</p>}
          {selected.status !== 'reserved' && <>
            <h3>Thao tác</h3><div className="room-actions"><button type="button" disabled>+ Order dịch vụ</button><button type="button" className="is-available" disabled={selected.status !== 'occupied' || !detailReady} onClick={() => setStayMode('extend')}>+ Thêm giờ/combo</button><button type="button" className="is-available" disabled={selected.status !== 'occupied' || !detailReady || Boolean(meter)} onClick={() => setStayMode('change')}>Đổi phòng</button><button type="button" disabled>Báo sự cố</button></div><button type="button" className="room-checkout" disabled={selected.status !== 'occupied' || !currentBooking?.id} onClick={() => navigate(`/le-tan/bookings/${currentBooking.id}/checkout`)}>Checkout &amp; lập bill</button><small className="room-checkout-note">Kiểm tra phòng và chốt bill trước khi thanh toán.</small>
          </>}
        </> : <p className="room-empty">Chọn một phòng để xem thông tin.</p>}
        </div>
      </aside>
    </div>
    <dialog ref={scheduleDialogRef} className="room-schedule-dialog" aria-labelledby="room-schedule-title" onClose={() => setScheduleOpen(false)}>
      <header><div><h2 id="room-schedule-title">Lịch phòng {selected?.code}</h2><p>{dateInputText(date)} · các booking trong ngày</p></div><button type="button" aria-label="Đóng lịch phòng" onClick={() => setScheduleOpen(false)}>×</button></header>
      {selectedRoomBookings.length ? <ol className="room-schedule-list">{selectedRoomBookings.map((booking) => <li key={booking.id}>
        <div className="room-schedule-row"><strong>{timeText(booking.plannedStart, timezone)}–{timeText(booking.plannedEnd, timezone)}</strong><span className={`room-schedule-status status-${String(booking.status || '').toLowerCase().replace('_', '-')}`}>{bookingStatusText(booking.status)}</span></div>
        <p>{booking.contactName || 'Chưa có tên khách'} · {booking.code}</p>
        <button type="button" onClick={() => openSelectedBooking(booking)}>Xem booking</button>
      </li>)}</ol> : <p className="room-schedule-empty">Chưa có booking nào của phòng này trong ngày {dateInputText(date)}.</p>}
      <button type="button" className="room-schedule-close" onClick={() => setScheduleOpen(false)}>Đóng</button>
    </dialog>
    {stayMode && currentBooking && <BookingStayDialog key={`${currentBooking.id}-${stayMode}`} booking={currentBooking} detail={detail} bookingOptions={options} initialMode={stayMode} onClose={() => setStayMode(null)} onSave={saveStay} busy={busy} timezone={timezone} />}
  </AppShell>;
}
