import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import Brand from '../../../shared/components/Brand';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import BookingCreatePage from './BookingCreatePage';
import { formatVnd, toLocalDateInput } from '../data/bookingData';
import '../styles/booking.css';

const navigation = [
  ['Tổng quan', 'overview'], ['Phòng', 'rooms'], ['Booking', 'booking'], ['Order', 'orders'],
  ['Thanh toán', 'payments'], ['Tiền cọc', 'deposit'], ['Kho hàng', 'inventory'], ['Báo sự cố', 'issues'],
];

const iconPaths = {
  overview: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  rooms: <><path d="M3 20V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14M3 13h18M6 13V9a2 2 0 0 1 2-2h3v6m2 0V7h3a2 2 0 0 1 2 2v4M2 20h20"/></>,
  booking: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M8 14h3m-3 3h7"/></>,
  orders: <><path d="M4 5h16M4 12h16M4 19h16M7 3v4m10-4v4M7 10v4m10-4v4M7 17v4m10-4v4"/></>,
  payments: <><circle cx="12" cy="12" r="9"/><path d="M8 12h8m-4-4v8"/></>,
  deposit: <><path d="m12 3 9 9-9 9-9-9 9-9Z"/></>,
  inventory: <><path d="M4 4h16v16H4zM9 4v16m6-16v16"/></>,
  issues: <><path d="M5 21V4m0 0c5-4 9 4 14 0v11c-5 4-9-4-14 0"/></>,
  search: <><circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4.4 4.4"/></>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></>,
  plus: <><path d="M12 5v14M5 12h14"/></>,
  logout: <><path d="M10 17l5-5-5-5m5 5H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/></>,
};

function Icon({ name, size = 18 }) {
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{iconPaths[name]}</svg>;
}

function localToday() { return toLocalDateInput(new Date()); }
function localTomorrow() { const date = new Date(); date.setDate(date.getDate() + 1); return toLocalDateInput(date); }
function displayPart(instant, options, timezone = 'Asia/Ho_Chi_Minh') {
  if (!instant) return '—';
  try { return new Intl.DateTimeFormat('vi-VN', { ...options, timeZone: timezone }).format(new Date(instant)); }
  catch { return new Intl.DateTimeFormat('vi-VN', options).format(new Date(instant)); }
}
function statusId(value) {
  return ({ CONFIRMED: 'pending', IN_HOUSE: 'checkedIn', CANCELLED: 'cancelled', NO_SHOW: 'noShow', COMPLETED: 'completed', DRAFT: 'draft' })[value] || String(value || '').toLowerCase();
}
function statusLabel(value) {
  return ({ CONFIRMED: 'Chờ check-in', IN_HOUSE: 'Đã check-in', CANCELLED: 'Đã hủy', NO_SHOW: 'No-show', COMPLETED: 'Hoàn tất', DRAFT: 'Nháp' })[value] || 'Chờ check-in';
}
function timeText(value, timezone) { return displayPart(value, { hour: '2-digit', minute: '2-digit', hour12: false }, timezone); }
function dateText(value, timezone) { return displayPart(value, { day: '2-digit', month: '2-digit', year: 'numeric' }, timezone); }
function initials(user) {
  const value = user?.fullName || user?.username || 'MC';
  return value.split(/[\s._-]+/).filter(Boolean).slice(-2).map((part) => part[0]?.toUpperCase()).join('') || 'MC';
}

function AppShell({ user, branchName, busy, onLogout, children, breadcrumb, search, onSearchChange }) {
  const now = new Date();
  const currentDate = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(now);
  const currentTime = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(now);
  return <div className="booking-app">
    <aside className="booking-sidebar">
      <div className="booking-brand"><Brand /></div>
      <span className="booking-role-chip">LỄ TÂN</span>
      <nav className="booking-navigation" aria-label="Điều hướng lễ tân">
        {navigation.map(([label, icon]) => <button type="button" key={label} className={`booking-nav-item${icon === 'booking' ? ' is-active' : ''}`} aria-current={icon === 'booking' ? 'page' : undefined} aria-disabled={icon !== 'booking'}>
          <Icon name={icon} /><span>{label}</span>
        </button>)}
      </nav>
      <div className="booking-sidebar-footer">
        <strong>{branchName || 'Cơ sở theo tài khoản'}</strong>
        <small>Đang làm việc · Lễ tân</small>
        <button type="button" className="booking-logout-link" disabled={busy} onClick={onLogout}>Đăng xuất</button>
      </div>
    </aside>
    <div className="booking-workspace">
      <header className="booking-topbar">
        <p><span>Lễ tân</span><b>/</b><strong>{breadcrumb}</strong></p>
        <div className="booking-topbar-tools">
          <label className="booking-global-search"><Icon name="search" /><input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Tìm phòng, booking, khách hàng..." aria-label="Tìm kiếm booking" /></label>
          <span className="booking-topbar-datetime">{currentDate} {currentTime}</span>
          <span className="booking-user-avatar" aria-label={user?.username}>{initials(user)}</span>
        </div>
      </header>
      <main className="booking-main" id="main">{children}</main>
    </div>
  </div>;
}

function statusClass(id) { return `booking-status booking-status-${id}`; }

function BookingDetails({ booking, detail, timezone, onCheckIn, onEdit, onCancel }) {
  if (!booking) return <aside className="booking-details booking-details-empty"><h2>Chi tiết booking</h2><p>Chọn một booking để xem thông tin khách, phòng và tiền cọc.</p></aside>;
  const canCheckIn = booking.status === 'CONFIRMED';
  const room = booking.roomCode ? `${booking.roomCode}${booking.roomName ? ` · ${booking.roomName}` : ''}` : 'Chưa gán phòng';
  return <aside className="booking-details">
    <h2>Chi tiết booking</h2>
    <strong className="booking-detail-code">{booking.code}</strong>
    <span className={statusClass(statusId(booking.status))}>{statusLabel(booking.status)}</span>
    <div className="booking-detail-divider" />
    <dl className="booking-detail-list">
      <div><dt>Khách hàng</dt><dd>{booking.contactName || '—'}</dd></div>
      <div><dt>Số điện thoại</dt><dd>{booking.contactPhone || '—'}</dd></div>
      <div><dt>Thời gian</dt><dd>{timeText(booking.plannedStart, timezone)} · {dateText(booking.plannedStart, timezone)}</dd></div>
      <div><dt>Số khách</dt><dd>{booking.guestCount || '—'}</dd></div>
      <div><dt>Hạng phòng</dt><dd>{booking.roomTypeName || booking.roomName || '—'}</dd></div>
      <div><dt>Phòng</dt><dd>{room}</dd></div>
      <div><dt>Tiền cọc</dt><dd>{Number(booking.depositBalance) > 0 ? formatVnd(booking.depositBalance) : 'Không cọc'}</dd></div>
      <div><dt>Giá dự kiến</dt><dd>{formatVnd(booking.expectedAmount)}</dd></div>
    </dl>
    {detail?.note && <p className="booking-detail-note">{detail.note}</p>}
    <div className="booking-detail-room-ready"><i />{booking.roomCode ? `Phòng ${booking.roomCode} đã được giữ cho booking này` : 'Booking chưa được gán phòng'}</div>
    <button type="button" className="booking-button booking-button-primary booking-detail-checkin" onClick={onCheckIn} disabled={!canCheckIn}>Check-in</button>
    <div className="booking-detail-actions"><button type="button" disabled={!canCheckIn} onClick={() => onEdit(booking)}>Sửa booking</button><button type="button" className="is-danger" disabled={!canCheckIn} onClick={() => onCancel(booking)}>Hủy booking</button></div>
  </aside>;
}

function CheckInPanel({ booking, detail, timezone, onClose, onConfirm }) {
  const [confirmationMessage, setConfirmationMessage] = useState('');
  const actualTime = displayPart(new Date(), { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', hour12: false }, timezone);
  const charge = Number(booking?.expectedAmount || 0);
  const deposit = Number(booking?.depositBalance || 0);
  if (!booking) return <div className="booking-checkin-overlay"><button className="booking-overlay-dismiss" type="button" onClick={onClose} aria-label="Đóng" /><aside className="booking-checkin-panel"><button className="booking-panel-close" type="button" onClick={onClose}>×</button><p role="status">Đang tải thông tin booking…</p></aside></div>;
  return <div className="booking-checkin-overlay">
    <button className="booking-overlay-dismiss" type="button" onClick={onClose} aria-label="Đóng check-in" />
    <aside className="booking-checkin-panel" aria-labelledby="booking-checkin-title">
      <div className="booking-checkin-heading"><div><h2 id="booking-checkin-title">Check-in booking</h2><strong>{booking.code}</strong></div><button className="booking-panel-close" type="button" onClick={onClose} aria-label="Đóng">×</button></div>
      <section className="booking-checkin-section"><h3>Khách hàng</h3><strong className="booking-checkin-customer">{booking.contactName}</strong><p>{booking.contactPhone || 'Chưa có số điện thoại'} · {booking.guestCount ? `${booking.guestCount} khách` : 'Chưa cập nhật số khách'}</p></section>
      <section className="booking-checkin-section"><h3>Thông tin đặt phòng</h3><div className="booking-checkin-grid">
        <div><span>Ngày nhận</span><strong>{dateText(booking.plannedStart, timezone)}</strong></div><div><span>Giờ hẹn</span><strong>{timeText(booking.plannedStart, timezone)}</strong></div>
        <div><span>Hạng phòng</span><strong>{booking.roomTypeName || booking.roomName || '—'}</strong></div><div><span>Phòng</span><strong>{booking.roomCode ? `${booking.roomCode} · ${booking.roomName || ''}` : 'Chưa gán'}</strong></div>
      </div></section>
      <div className="booking-checkin-ready"><i /><div><strong>{booking.roomCode ? `${booking.roomCode} đã được giữ cho booking này` : 'Booking chưa được gán phòng'}</strong><p>Kiểm tra phòng sẵn sàng trước khi nhận khách.</p></div></div>
      <section className="booking-checkin-deposit"><div><strong>Tiền cọc đã nhận</strong><b>{formatVnd(deposit)}</b></div><span>{deposit > 0 ? 'Đang giữ' : 'Chưa thu cọc'}</span></section>
      <label className="booking-checkin-time"><span>Giờ check-in thực tế</span><div><input value={actualTime} readOnly /><small>Theo giờ hệ thống</small></div></label>
      <section className="booking-checkin-receipt"><strong>Tóm tắt</strong><div><span>{detail?.charges?.[0]?.description || 'Dịch vụ phòng'}</span><b>{formatVnd(charge)}</b></div><p>Tiền cọc {formatVnd(deposit)} sẽ được đối chiếu khi checkout.</p></section>
      <p className="booking-feature-note">Backend hiện chưa có API xác nhận check-in; nút xác nhận sẽ báo trạng thái tích hợp.</p>
      {confirmationMessage && <p className="booking-inline-error" role="alert">{confirmationMessage}</p>}
      <button type="button" className="booking-button booking-button-primary booking-confirm-checkin" onClick={() => setConfirmationMessage(onConfirm(booking))} disabled={booking.status !== 'CONFIRMED'}>Xác nhận check-in</button>
    </aside>
  </div>;
}

function CancelDialog({ booking, onClose, onConfirm, busy, error }) {
  const [reason, setReason] = useState('');
  useEffect(() => setReason(''), [booking?.id]);
  if (!booking) return null;
  return <div className="booking-cancel-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="booking-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="cancel-title">
      <div className="booking-cancel-title"><div><h2 id="cancel-title">Hủy booking</h2><p>{booking.code} · {booking.contactName}</p></div><button type="button" className="booking-panel-close" onClick={onClose} aria-label="Đóng">×</button></div>
      <label className="booking-form-field"><span>Lý do hủy *</span><textarea rows="3" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Nhập lý do hủy booking" /></label>
      {error && <p className="booking-inline-error" role="alert">{error}</p>}
      <div className="booking-cancel-buttons"><button className="booking-button booking-button-danger" type="button" disabled={!reason.trim() || busy} onClick={() => onConfirm(booking, reason.trim())}>{busy ? 'Đang hủy…' : 'Xác nhận hủy'}</button><button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button></div>
    </section>
  </div>;
}

export default function BookingManagementPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { bookingId } = useParams();
  const createMode = location.pathname.endsWith('/new');
  const checkinMode = location.pathname.endsWith('/check-in');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [options, setOptions] = useState(null);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionsError, setOptionsError] = useState('');
  const [selectedDate, setSelectedDate] = useState(localToday);
  const [activeFilter, setActiveFilter] = useState('all');
  const [roomTier, setRoomTier] = useState('all');
  const [search, setSearch] = useState('');
  const [rows, setRows] = useState([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [detail, setDetail] = useState(null);
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelError, setCancelError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { document.title = `${createMode ? 'Tạo booking' : 'Booking'} | GenZ Cinema & Music Box`; }, [createMode]);

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else { setUser(identity); setError(''); }
    }).catch((requestError) => {
      if (!active) return;
      if (requestError.response?.status === 401) navigate('/dang-nhap', { replace: true, state: { message: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' } });
      else { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    });
    return () => { active = false; };
  }, [navigate, retry]);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    setOptionsLoading(true);
    bookingApi.options().then((value) => { if (active) { setOptions(value); setOptionsError(''); } })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setOptionsError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setOptionsLoading(false); });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    if (!user || createMode) return undefined;
    let active = true;
    setListLoading(true);
    bookingApi.list({ dateFrom: selectedDate, dateTo: selectedDate, search: search.trim() || undefined, page: 0, size: 100 })
      .then((result) => {
        if (!active) return;
        const items = result.items || [];
        setRows(items);
        setListError('');
        if (!selectedId || !items.some((item) => item.id === selectedId)) setSelectedId(items[0]?.id || '');
      }).catch((requestError) => { if (active) { const errors = apiErrors(requestError); setListError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setListLoading(false); });
    return () => { active = false; };
  }, [user, createMode, selectedDate, search, reload]);

  const activeId = checkinMode ? bookingId : selectedId;
  useEffect(() => {
    if (!activeId || !user) { setDetail(null); return undefined; }
    let active = true;
    bookingApi.detail(activeId).then((value) => { if (active) setDetail(value); })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setListError(errors.form || Object.values(errors)[0]); } });
    return () => { active = false; };
  }, [activeId, user, reload]);

  const selected = (detail?.booking?.id === activeId ? detail.booking : null) || rows.find((item) => item.id === activeId) || null;
  const timezone = options?.timezone || 'Asia/Ho_Chi_Minh';
  const counts = useMemo(() => ({
    all: rows.length,
    pending: rows.filter((item) => item.status === 'CONFIRMED').length,
    checkedIn: rows.filter((item) => item.status === 'IN_HOUSE').length,
    cancelled: rows.filter((item) => item.status === 'CANCELLED').length,
    noShow: rows.filter((item) => item.status === 'NO_SHOW').length,
    paid: rows.filter((item) => Number(item.depositBalance) > 0).length,
    unpaid: rows.filter((item) => Number(item.depositBalance) <= 0).length,
  }), [rows]);
  const visibleRows = useMemo(() => rows.filter((item) => {
    const id = statusId(item.status);
    if (['pending', 'checkedIn', 'cancelled', 'noShow'].includes(activeFilter) && id !== activeFilter) return false;
    if (activeFilter === 'paid' && Number(item.depositBalance) <= 0) return false;
    if (activeFilter === 'unpaid' && Number(item.depositBalance) > 0) return false;
    if (roomTier !== 'all' && !(item.roomTypeName || item.roomName || '').toLocaleLowerCase('vi-VN').includes(roomTier.toLocaleLowerCase('vi-VN'))) return false;
    return true;
  }), [rows, activeFilter, roomTier]);

  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (requestError) { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  const createBooking = async (payload, depositInfo = {}) => {
    setBusy(true);
    try {
      const result = await bookingApi.create(payload);
      const booking = result.booking;
      setSelectedDate(toLocalDateInput(new Date(booking.plannedStart)));
      setSelectedId(booking.id);
      setActiveFilter('all');
      setRoomTier('all');
      setSearch('');
      setReload((value) => value + 1);
      const unsupportedFields = [
        depositInfo.guestCount ? 'số khách' : '',
        depositInfo.depositEnabled && depositInfo.depositAmount > 0 ? 'tiền cọc' : '',
      ].filter(Boolean);
      setNotice(`Đã tạo booking ${booking.code}.${unsupportedFields.length ? ` API hiện chưa lưu ${unsupportedFields.join(' và ')}.` : ''}`);
      navigate('/le-tan/bookings', { replace: true });
      return { ok: true };
    } catch (requestError) { const errors = apiErrors(requestError); return { error: errors.form || Object.values(errors)[0] }; }
    finally { setBusy(false); }
  };

  const cancelBooking = async (booking, reason) => {
    setBusy(true);
    setCancelError('');
    try {
      await bookingApi.cancel(booking.id, { reason });
      setCancelTarget(null);
      setNotice(`Đã hủy booking ${booking.code}.`);
      setReload((value) => value + 1);
    } catch (requestError) { const errors = apiErrors(requestError); setCancelError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  if (!user) return <main className="booking-auth-state">{error ? <div className="booking-auth-message" role="alert"><p>{error}</p><button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button></div> : <p role="status">Đang kiểm tra phiên đăng nhập…</p>}</main>;

  const tabs = [
    ['all', 'Tất cả', counts.all], ['pending', 'Chờ check-in', counts.pending], ['checkedIn', 'Đã check-in', counts.checkedIn],
    ['cancelled', 'Đã hủy', counts.cancelled], ['noShow', 'No-show', counts.noShow], ['paid', 'Có cọc', counts.paid], ['unpaid', 'Không cọc', counts.unpaid],
  ];

  const openCheckIn = (booking) => navigate(`/le-tan/bookings/${booking.id}/check-in`);
  const closeCheckIn = () => navigate('/le-tan/bookings');
  const confirmCheckIn = (booking) => `Backend chưa hỗ trợ xác nhận check-in cho ${booking.code}.`;
  const mainContent = createMode ? <BookingCreatePage options={options} optionsLoading={optionsLoading} optionsError={optionsError} onBack={() => navigate('/le-tan/bookings')} onCreate={createBooking} /> : <>
    <div className="booking-page-heading">
      <div><h1>Booking</h1><p>Theo dõi lịch đặt và check-in khách trong ca</p></div>
      <button className="booking-button booking-button-primary booking-create-button" type="button" onClick={() => navigate('/le-tan/bookings/new')}><Icon name="plus" /> Tạo booking</button>
    </div>
    {notice && <div className="booking-live-notice" role="status"><span>✓</span>{notice}<button type="button" aria-label="Đóng thông báo" onClick={() => setNotice('')}>×</button></div>}
    {listError && <div className="booking-inline-error" role="alert">{listError}<button type="button" onClick={() => setReload((value) => value + 1)}>Thử lại</button></div>}
    <section className="booking-filters" aria-label="Bộ lọc booking">
      <div className="booking-filter-date-group"><span className="booking-filter-label">Ngày nhận phòng</span><div className="booking-date-buttons">
        <button type="button" className={selectedDate === localToday() ? 'is-selected' : ''} onClick={() => setSelectedDate(localToday())}>Hôm nay</button>
        <button type="button" className={selectedDate === localTomorrow() ? 'is-selected' : ''} onClick={() => setSelectedDate(localTomorrow())}>Ngày mai</button>
        <label className="booking-date-picker"><Icon name="calendar" /><input aria-label="Chọn ngày booking" type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} /></label>
      </div></div>
      <label className="booking-search-field"><Icon name="search" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm tên, SĐT, mã booking, phòng..." /></label>
      <label className="booking-room-filter"><select aria-label="Hạng phòng" value={roomTier} onChange={(event) => setRoomTier(event.target.value)}><option value="all">Hạng phòng · Tất cả</option>{(options?.roomTypes || []).map((type) => <option key={type.id} value={type.name}>{type.name}</option>)}</select></label>
    </section>
    <section className="booking-status-tabs" aria-label="Lọc theo trạng thái booking">
      {tabs.map(([id, label, count]) => <button type="button" key={id} className={`booking-filter-chip chip-${id}${activeFilter === id ? ' is-active' : ''}`} aria-pressed={activeFilter === id} onClick={() => setActiveFilter(id)}><span>{label}</span>{id === 'all' && <small>{count}</small>}</button>)}
      <span className="booking-result-count">Hiển thị {visibleRows.length}/{rows.length}</span>
    </section>
    <div className="booking-content-grid">
      <section className="booking-table-card" aria-label="Danh sách booking">
        <div className="booking-table-scroll"><table className="booking-table">
          <thead><tr><th>Mã booking</th><th>Giờ</th><th>Khách hàng / SĐT</th><th>Hạng / Phòng</th><th>Khách</th><th>Tiền cọc</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{visibleRows.map((item) => {
            const selectedRow = item.id === selected?.id;
            const lateMinutes = item.status === 'CONFIRMED' ? Math.floor((Date.now() - new Date(item.plannedStart).getTime()) / 60000) : 0;
            return <tr key={item.id} className={selectedRow ? 'is-selected' : ''} onClick={() => setSelectedId(item.id)}>
              <td><strong>{item.code}</strong></td><td><b>{timeText(item.plannedStart, timezone)}</b></td>
              <td><strong>{item.contactName}</strong><small>{item.contactPhone || '—'}</small></td>
              <td><span>{item.roomTypeName || item.roomName || '—'}{item.roomCode ? ` · ${item.roomCode}` : ' · Chưa gán'}</span></td>
              <td>{item.guestCount || '—'}</td><td>{Number(item.depositBalance) > 0 ? formatVnd(item.depositBalance) : '0đ'}</td>
              <td>{lateMinutes > 0 ? <span className="booking-status booking-status-late">Quá giờ {lateMinutes} phút</span> : <span className={statusClass(statusId(item.status))}>{statusLabel(item.status)}</span>}</td>
              <td><button type="button" className="booking-row-action" onClick={(event) => { event.stopPropagation(); setSelectedId(item.id); }}>Xem</button></td>
            </tr>;
          })}</tbody>
        </table>
        {listLoading && <div className="booking-table-message" role="status">Đang tải booking…</div>}
        {!listLoading && !visibleRows.length && <div className="booking-table-message">Không có booking trong ngày này.</div>}
        </div>
      </section>
      <BookingDetails booking={selected} detail={detail} timezone={timezone} onCheckIn={() => selected && openCheckIn(selected)} onEdit={() => setNotice('Chỉnh sửa booking chưa có trong phạm vi giao diện hiện tại.')} onCancel={(booking) => { setCancelError(''); setCancelTarget(booking); }} />
    </div>
  </>;

  return <AppShell user={user} branchName={options?.branchName} busy={busy} onLogout={logout} breadcrumb={createMode ? 'Booking / Tạo booking' : 'Booking'} search={search} onSearchChange={setSearch}>
    {mainContent}
    {checkinMode && <CheckInPanel booking={selected} detail={detail} timezone={timezone} onClose={closeCheckIn} onConfirm={confirmCheckIn} />}
    <CancelDialog booking={cancelTarget} onClose={() => setCancelTarget(null)} onConfirm={cancelBooking} busy={busy} error={cancelError} />
  </AppShell>;
}
