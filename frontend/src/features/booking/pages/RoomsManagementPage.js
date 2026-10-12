import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { formatVnd } from '../data/bookingData';
import { bookingApi } from '../services/bookingApi';
import ReceptionLayout from '../components/ReceptionLayout';
import '../styles/booking.css';
import '../styles/rooms.css';

const orders = [
  { name: 'Coca Cola', quantity: 2, status: 'Đã phục vụ', price: 50000 },
  { name: 'Khoai tây chiên', quantity: 1, status: 'Chờ phục vụ', price: 49000 },
  { name: 'Bắp rang', quantity: 1, status: 'Đã phục vụ', price: 27000 },
];

const availableRooms = [
  { code: 'P.302', name: 'Football', tier: 'King', capacity: 6, floor: 'Tầng 3', price: 326000 },
  { code: 'P.403', name: 'Cinema', tier: 'Queen', capacity: 4, floor: 'Tầng 4', price: 296000 },
  { code: 'P.601', name: 'Galaxy', tier: 'VIP', capacity: 8, floor: 'Tầng 6', price: 396000 },
];

const changeReasons = ['Thiết bị gặp sự cố', 'Khách yêu cầu đổi phòng', 'Phòng không đáp ứng nhu cầu', 'Lý do khác'];

function formatDuration(milliseconds) {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = String(Math.floor(seconds / 3600)).padStart(2, '0');
  const minutes = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0');
  const remainder = String(seconds % 60).padStart(2, '0');
  return `${hours}:${minutes}:${remainder}`;
}

function roomMoney(value) {
  return formatVnd(value).replace(/\s+đ$/, 'đ');
}

function DetailPair({ label, value, secondary }) {
  return <div className="room-detail-pair"><span>{label}</span><strong>{value}</strong>{secondary && <small>{secondary}</small>}</div>;
}

function RoomChangeDialog({ onClose, onConfirm }) {
  const [reason, setReason] = useState(changeReasons[0]);
  const [note, setNote] = useState('Điều hòa phòng cũ hoạt động yếu');
  const [floor, setFloor] = useState('all');
  const [tier, setTier] = useState('all');
  const [minGuests, setMinGuests] = useState(4);
  const [selectedRoom, setSelectedRoom] = useState(availableRooms[0].code);
  const [error, setError] = useState('');
  const [branchName, setBranchName] = useState('');

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const rooms = useMemo(() => availableRooms.filter((room) =>
    (floor === 'all' || room.floor === floor)
    && (tier === 'all' || room.tier === tier)
    && room.capacity >= Number(minGuests)), [floor, tier, minGuests]);
  const targetRoom = rooms.find((room) => room.code === selectedRoom) || rooms[0];
  const priceDifference = (targetRoom?.price || 0) - 296000;

  useEffect(() => {
    if (!rooms.some((room) => room.code === selectedRoom)) setSelectedRoom(rooms[0]?.code || '');
  }, [rooms, selectedRoom]);

  const submit = () => {
    if (!reason || (reason === 'Lý do khác' && !note.trim())) {
      setError('Vui lòng nhập lý do đổi phòng.');
      return;
    }
    if (!targetRoom) {
      setError('Chọn một phòng còn trống phù hợp với số khách.');
      return;
    }
    onConfirm(reason, note, targetRoom);
  };

  return <div className="room-change-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="room-change-dialog" role="dialog" aria-modal="true" aria-labelledby="room-change-title">
      <header className="room-change-heading">
        <div><h2 id="room-change-title">Đổi phòng</h2><p>Phòng hiện tại: P.201 · Barbie · Queen</p></div>
        <button type="button" className="room-change-close" onClick={onClose} aria-label="Đóng">×</button>
      </header>

      <section className="room-change-section">
        <h3><span>1</span> Lý do đổi phòng <b>*</b></h3>
        <div className="room-change-reason-fields">
          <select aria-label="Lý do đổi phòng" value={reason} onChange={(event) => { setReason(event.target.value); setError(''); }}>
            {changeReasons.map((item) => <option key={item}>{item}</option>)}
          </select>
          <input aria-label="Mô tả lý do" value={note} onChange={(event) => { setNote(event.target.value); setError(''); }} placeholder="Mô tả thêm lý do đổi phòng" />
        </div>
      </section>

      <section className="room-change-section room-pick-section">
        <div className="room-change-subheading"><h3><span>2</span> Chọn phòng mới</h3><b>Chỉ phòng đang trống</b></div>
        <div className="room-change-filters">
          <label><span className="sr-only">Tầng</span><select value={floor} onChange={(event) => setFloor(event.target.value)}><option value="all">Tất cả tầng</option>{['Tầng 3', 'Tầng 4', 'Tầng 6'].map((item) => <option key={item}>{item}</option>)}</select></label>
          <label><span className="sr-only">Hạng phòng</span><select value={tier} onChange={(event) => setTier(event.target.value)}><option value="all">Hạng phòng</option>{['Queen', 'King', 'VIP'].map((item) => <option key={item}>{item}</option>)}</select></label>
          <label><span className="sr-only">Số khách tối thiểu</span><select value={minGuests} onChange={(event) => setMinGuests(event.target.value)}><option value={2}>Từ 2 người</option><option value={4}>Từ 4 người</option><option value={6}>Từ 6 người</option><option value={8}>Từ 8 người</option></select></label>
        </div>
        <div className="room-change-options">
          {rooms.length ? rooms.map((room) => <button type="button" key={room.code} className={`room-option${targetRoom?.code === room.code ? ' is-selected' : ''}`} onClick={() => { setSelectedRoom(room.code); setError(''); }} aria-pressed={targetRoom?.code === room.code}>
            <span className="room-option-title">{room.code} · {room.name}<b>{targetRoom?.code === room.code ? '✓' : ''}</b></span>
            <span className="room-option-meta">{room.tier} · {room.capacity} người · Trống</span>
            <strong>{roomMoney(room.price)} / gói</strong>
          </button>) : <p className="room-no-options">Không có phòng trống phù hợp với bộ lọc.</p>}
        </div>
      </section>

      <section className="room-price-section">
        <h3>So sánh giá tự động</h3>
        <div className="room-price-comparison">
          <div><span>Phòng hiện tại · Queen</span><strong>{roomMoney(296000)} / gói</strong></div>
          <span className="room-price-arrow" aria-hidden="true">→</span>
          <div><span>Phòng mới · {targetRoom?.tier || '—'}</span><strong>{targetRoom ? `${roomMoney(targetRoom.price)} / gói` : '—'}</strong></div>
          <div className="room-price-difference"><span>Chênh lệch</span><strong>{targetRoom ? `${priceDifference > 0 ? '+' : ''}${roomMoney(priceDifference)}` : '—'}</strong></div>
        </div>
        <p className="room-price-note">Hệ thống cập nhật giá theo hạng mới và lưu lịch sử phòng cũ → phòng mới.</p>
      </section>

      <section className="room-change-summary">
        <h3>Tóm tắt</h3>
        <p>P.201 → {targetRoom?.code || 'Chưa chọn'} · {reason}{targetRoom ? ` · Chênh lệch ${priceDifference > 0 ? '+' : ''}${roomMoney(priceDifference)}` : ''}</p>
        <small>Giữ nguyên giờ check-in, order và tiền cọc đang gắn với booking.</small>
      </section>
      {error && <p className="booking-inline-error" role="alert">{error}</p>}
      <footer className="room-change-actions"><button type="button" onClick={onClose}>Hủy</button><button type="button" className="booking-button booking-button-primary" onClick={submit} disabled={!targetRoom}>Xác nhận đổi phòng</button></footer>
    </section>
  </div>;
}

export default function RoomsManagementPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState('overview');
  const [changeOpen, setChangeOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [startedAt] = useState(() => Date.now() - (2 * 60 + 38) * 60 * 1000);
  const [elapsed, setElapsed] = useState(Date.now() - startedAt);

  useEffect(() => {
    document.title = 'Phòng đang sử dụng | GenZ Cinema & Music Box';
    const timer = window.setInterval(() => setElapsed(Date.now() - startedAt), 1000);
    return () => window.clearInterval(timer);
  }, [startedAt]);

  useEffect(() => {
    let active = true;
    setUser(null);
    setError('');
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else setUser(identity);
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
    bookingApi.options().then((options) => { if (active) setBranchName(options.branchName || ''); }).catch(() => undefined);
    return () => { active = false; };
  }, [user]);

  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (requestError) { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  const filteredOrders = useMemo(() => orders.filter((order) => order.name.toLocaleLowerCase('vi-VN').includes(search.toLocaleLowerCase('vi-VN'))), [search]);
  const notifyUnavailable = (action) => setNotice(`${action}: giao diện đang dùng dữ liệu minh họa; backend chưa có API để lưu thao tác này.`);
  const confirmRoomChange = (reason, note, room) => {
    setChangeOpen(false);
    setNotice(`Đã chọn ${room.code} trong bản xem trước. Lý do: ${reason}${note ? ` — ${note}` : ''}. Backend chưa có API đổi phòng nên thay đổi chưa được lưu.`);
  };

  if (!user) return <main className="booking-auth-state">{error ? <div className="booking-auth-message" role="alert"><p>{error}</p><button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button></div> : <p role="status">Đang kiểm tra phiên đăng nhập…</p>}</main>;

  return <ReceptionLayout user={user} branchName={branchName || 'Cơ sở Hải Phòng'} busy={busy} onLogout={logout} breadcrumb="Phòng / P.201" search={search} onSearchChange={setSearch} activeSection="rooms">
    <div className="room-page-heading">
      <div><span className="room-prototype-tag">Dữ liệu minh họa</span><h1>P.201 · Barbie <span>Đang sử dụng</span></h1><p>Queen · 4 khách · Check-in 12:32, 29/09/2026</p></div>
      <div className="room-heading-actions">
        <button type="button" className="room-action-order" onClick={() => notifyUnavailable('Tạo order')}>＋ Order</button>
        <button type="button" onClick={() => { setNotice(''); setChangeOpen(true); }}>Đổi phòng</button>
        <button type="button" onClick={() => notifyUnavailable('Báo sự cố')}>Báo sự cố</button>
        <button type="button" className="room-action-checkout" onClick={() => notifyUnavailable('Checkout')}>Checkout</button>
      </div>
    </div>
    {notice && <div className="booking-live-notice room-page-notice" role="status"><span>i</span>{notice}<button type="button" aria-label="Đóng thông báo" onClick={() => setNotice('')}>×</button></div>}

    <div className="room-detail-layout">
      <div className="room-detail-main">
        <nav className="room-tabs" aria-label="Thông tin phiên phòng">
          {[['overview', 'Tổng quan'], ['orders', 'Order'], ['history', 'Lịch sử']].map(([id, label]) => <button type="button" key={id} className={tab === id ? 'is-active' : ''} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</button>)}
        </nav>

        {tab === 'overview' && <>
          <section className="room-info-card room-usage-card">
            <h2>Sử dụng phòng</h2>
            <div className="room-usage-grid">
              <div className="room-duration"><span>Thời gian đã dùng</span><strong>{formatDuration(elapsed)}</strong></div>
              <DetailPair label="Bắt đầu" value="12:32 · 29/09/2026" />
              <DetailPair label="Hình thức" value="Combo 3H Queen" />
              <DetailPair label="Đơn giá hiện tại" value="Theo gói đã chọn" />
              <DetailPair label="Tiền phòng tạm tính" value={roomMoney(320000)} />
            </div>
          </section>
          <section className="room-info-card room-customer-card">
            <h2>Khách hàng</h2>
            <div className="room-customer-grid"><div><strong>Nguyễn Thu Hà</strong><span>0914 206 388</span></div><div><span>4 khách</span><span>Booking: BK260929001</span></div></div>
          </section>
          <OrdersCard orders={filteredOrders} onViewAll={() => setTab('orders')} />
        </>}

        {tab === 'orders' && <OrdersCard orders={filteredOrders} onViewAll={() => setTab('overview')} showAll />}
        {tab === 'history' && <section className="room-info-card room-history-card"><h2>Lịch sử phòng</h2><div><b>12:32 · 29/09/2026</b><span>P.201 · Barbie · Check-in booking BK260929001</span><small>Khách Nguyễn Thu Hà · 4 khách · Combo 3H Queen</small></div><div><b>12:32 · 29/09/2026</b><span>Booking được chuyển thành phiên sử dụng</span></div><p>Lịch sử đổi phòng sẽ xuất hiện tại đây sau khi backend hỗ trợ thao tác.</p></section>}
      </div>

      <aside className="room-bill-card">
        <div><h2>Tạm tính</h2><p>Cập nhật theo thời gian sử dụng và order</p></div>
        <div className="room-bill-lines">
          <div><span>Tiền phòng</span><strong>{roomMoney(320000)}</strong></div>
          <div><span>Order F&amp;B</span><strong>{roomMoney(126000)}</strong></div>
          <div><span>Phụ phí</span><strong>0đ</strong></div>
          <div className="room-bill-total"><span>Tổng tạm tính</span><strong>{roomMoney(446000)}</strong></div>
          <div><span>Tiền cọc đã nhận</span><strong className="room-deposit-value">-{roomMoney(200000)}</strong></div>
        </div>
        <div className="room-balance-card"><span>Còn dự kiến</span><strong>{roomMoney(246000)}</strong></div>
        <div className="room-pending-note"><strong>1 món đang chờ phục vụ</strong><span>Kiểm tra phát sinh trước khi lập bill.</span></div>
        <button type="button" className="booking-button booking-button-primary room-checkout-button" onClick={() => notifyUnavailable('Checkout & thanh toán')}>Checkout &amp; thanh toán</button>
      </aside>
    </div>
    {changeOpen && <RoomChangeDialog onClose={() => setChangeOpen(false)} onConfirm={confirmRoomChange} />}
  </ReceptionLayout>;
}

function OrdersCard({ orders: visibleOrders, onViewAll, showAll = false }) {
  return <section className={`room-info-card room-orders-card${showAll ? ' is-expanded' : ''}`}>
    <div className="room-card-heading"><h2>Order F&amp;B</h2><button type="button" onClick={onViewAll}>{showAll ? 'Quay lại tổng quan' : 'Xem tất cả →'}</button></div>
    <div className="room-order-table-scroll"><table className="room-order-table"><thead><tr><th>Sản phẩm</th><th>SL</th><th>Trạng thái</th><th>Tiền</th></tr></thead>
      <tbody>{visibleOrders.map((order) => <tr key={order.name}><td>{order.name}</td><td>{order.quantity}</td><td><span className={order.status === 'Chờ phục vụ' ? 'is-waiting' : 'is-served'}>{order.status}</span></td><td>{roomMoney(order.price)}</td></tr>)}</tbody>
    </table>{!visibleOrders.length && <p className="room-no-orders">Không tìm thấy sản phẩm phù hợp.</p>}</div>
  </section>;
}
