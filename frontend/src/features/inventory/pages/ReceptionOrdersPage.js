import { PaginatedList } from '../../../shared/components/Pagination';
import { ToastStack } from '../../../shared/components/Feedback';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import MoneyInput from '../../../shared/components/MoneyInput';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import { formatVnd } from '../../booking/data/bookingData';
import { foodOrderApi } from '../services/foodOrderApi';
import '../../booking/styles/rooms.css';
import './food-orders.css';

const filters = [['ACTIVE', 'Cần xử lý'], ['ALL', 'Tất cả trạng thái'], ['PENDING', 'Chờ xác nhận'], ['ACCEPTED', 'Đang chuẩn bị'], ['SERVED', 'Đã phục vụ'], ['REJECTED', 'Đã từ chối'], ['CANCELLED', 'Đã hủy']];
const statusLabel = { PENDING: 'Chờ xác nhận', ACCEPTED: 'Đang chuẩn bị', SERVED: 'Đã phục vụ', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' };
const orderTime = new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const errorText = (error) => Object.values(apiErrors(error))[0];
const newKey = () => window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const orderPollInterval = 5000;
const todayInVietnam = () => {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const value = (type) => parts.find((part) => part.type === type)?.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
};
const normalizeOrderSearch = (value) => String(value || '').trim().toLocaleLowerCase('vi-VN')
  .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');

export default function ReceptionOrdersPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const checkoutBookingId = searchParams.get('bookingId');
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('ACTIVE');
  const [date, setDate] = useState('');
  const [roomId, setRoomId] = useState('');
  const [orderDialogOpen, setOrderDialogOpen] = useState(false);
  const orderDialogRef = useRef(null);
  const [orders, setOrders] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [roomFilterError, setRoomFilterError] = useState('');
  const [bookings, setBookings] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [menu, setMenu] = useState([]);
  const [bookingId, setBookingId] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [menuSearch, setMenuSearch] = useState('');
  const [menuCategory, setMenuCategory] = useState('all');
  const [draft, setDraft] = useState({});
  const [draftPrices, setDraftPrices] = useState({});
  const [quantities, setQuantities] = useState({});
  const [assignments, setAssignments] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [incomingOrderAlert, setIncomingOrderAlert] = useState(null);
  const knownPendingOrderIds = useRef(null);
  const orderPollInProgress = useRef(false);

  const applyOrderRows = useCallback((rows) => {
    setOrders(rows);
    setQuantities((current) => {
      const next = { ...current };
      rows.forEach((order) => {
        if (!next[order.id]) next[order.id] = Object.fromEntries(order.items.map((item) => [item.menuItemId, Number(item.quantity)]));
      });
      return next;
    });
    setAssignments((current) => {
      const next = { ...current };
      rows.forEach((order) => { if (!next[order.id]) next[order.id] = order.assignedEmployeeId || ''; });
      return next;
    });
  }, []);

  // shortcut: ALL returns at most 200 active-first orders; add server paging if the active queue exceeds that.
  const loadOrders = useCallback(async (filter = status) => {
    applyOrderRows(await foodOrderApi.staffOrders(filter === 'ACTIVE' ? 'ALL' : filter, date, roomId));
  }, [applyOrderRows, date, roomId, status]);

  const loadPage = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const identity = await authApi.me();
      if (identity.role !== 'RECEPTIONIST') {
        navigate(identity.destination || '/dang-nhap', { replace: true });
        return;
      }
      setUser(identity);
      const [rows, roomResult, activeBookings, servingEmployees, menuItems] = await Promise.all([
        foodOrderApi.staffOrders(status === 'ACTIVE' ? 'ALL' : status, date, roomId),
        foodOrderApi.orderRooms().then((items) => ({ items })).catch((requestError) => ({ requestError })),
        foodOrderApi.activeBookings(), foodOrderApi.servingEmployees(), foodOrderApi.staffMenu(),
      ]);
      if (knownPendingOrderIds.current === null && !date) {
        knownPendingOrderIds.current = new Set(rows.filter((order) => order.status === 'PENDING').map((order) => order.id));
      }
      applyOrderRows(rows);
      setRooms(roomResult.items || []);
      setRoomFilterError(roomResult.requestError
        ? roomResult.requestError.response?.status === 404
          ? 'Bộ lọc phòng chưa sẵn sàng. Vui lòng thử lại sau khi hệ thống được cập nhật.'
          : 'Không tải được danh sách phòng. Vui lòng tải lại trang.'
        : '');
      if (roomResult.requestError && roomId) setRoomId('');
      setBookings(activeBookings);
      setEmployees(servingEmployees);
      setMenu(menuItems);
      setBookingId((current) => current || checkoutBookingId || activeBookings[0]?.id || '');
      setEmployeeId((current) => current || servingEmployees[0]?.id || '');
    } catch (loadError) {
      if (loadError.response?.status === 401) navigate('/dang-nhap', { replace: true });
      else setError(errorText(loadError));
    } finally {
      setLoading(false);
    }
  }, [applyOrderRows, checkoutBookingId, date, navigate, roomId, status]);

  useEffect(() => { loadPage(); }, [loadPage]);

  useEffect(() => {
    const dialog = orderDialogRef.current;
    if (!dialog) return;
    if (orderDialogOpen && !dialog.open) dialog.showModal();
    if (!orderDialogOpen && dialog.open) dialog.close();
  }, [orderDialogOpen, loading]);

  const pollPendingOrders = useCallback(async () => {
    if (orderPollInProgress.current) return;
    orderPollInProgress.current = true;
    try {
      const pendingOrders = await foodOrderApi.staffOrders('PENDING');
      const knownIds = knownPendingOrderIds.current;
      if (knownIds === null) {
        knownPendingOrderIds.current = new Set(pendingOrders.map((order) => order.id));
      } else {
        const newOrders = pendingOrders.filter((order) => !knownIds.has(order.id));
        pendingOrders.forEach((order) => knownIds.add(order.id));
        if (newOrders.length > 0) {
          const roomNames = [...new Set(newOrders.map((order) => order.roomName).filter(Boolean))];
          setIncomingOrderAlert((current) => ({
            count: (current?.count || 0) + newOrders.length,
            roomNames: [...new Set([...(current?.roomNames || []), ...roomNames])],
          }));
        }
      }
      if (status === 'PENDING' && !date && !roomId) applyOrderRows(pendingOrders);
      else applyOrderRows(await foodOrderApi.staffOrders(status === 'ACTIVE' ? 'ALL' : status, date, roomId));
    } catch {
      // Polling retries automatically; keep transient network errors out of action feedback.
    } finally {
      orderPollInProgress.current = false;
    }
  }, [applyOrderRows, date, roomId, status]);

  useEffect(() => {
    if (!user || loading) return undefined;
    pollPendingOrders();
    const timer = window.setInterval(pollPendingOrders, orderPollInterval);
    return () => window.clearInterval(timer);
  }, [loading, pollPendingOrders, user]);

  const availableMenu = useMemo(() => menu.filter((item) => item.available), [menu]);
  const visibleMenu = useMemo(() => {
    const keyword = normalizeOrderSearch(menuSearch);
    return availableMenu.filter((item) =>
      (menuCategory === 'all' || normalizeOrderSearch(item.category) === normalizeOrderSearch(menuCategory))
      && (!keyword || normalizeOrderSearch(item.name).includes(keyword)));
  }, [availableMenu, menuSearch, menuCategory]);
  // BR-117: chỉ gửi món có số lượng nguyên dương, kể cả khi đang bị bộ lọc ẩn.
  const draftLines = useMemo(() => availableMenu.filter((item) => draft[item.id] > 0), [availableMenu, draft]);
  const draftTotal = draftLines.reduce((sum, item) => sum + Number(draftPrices[item.id] ?? item.salePrice ?? 0) * draft[item.id], 0);
  const selectedOrderBooking = bookings.find((booking) => booking.id === bookingId);
  const changeDraft = (item, amount) => {
    if (amount > 0) setDraftPrices((current) => ({ ...current, [item.id]: current[item.id] ?? Number(item.salePrice || 0) }));
    setDraft((current) => {
    const next = { ...current };
    if (amount <= 0) delete next[item.id]; else next[item.id] = amount;
    return next;
    });
  };
  const changeOrderQty = (orderId, itemId, amount) => setQuantities((current) => ({
    ...current,
    [orderId]: { ...(current[orderId] || {}), [itemId]: amount },
  }));

  const withAction = async (action, successMessage) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      await loadOrders(status);
      setNotice(successMessage);
    } catch (actionError) { setError(errorText(actionError)); }
    finally { setBusy(false); }
  };

  const addOrder = async (event) => {
    event.preventDefault();
    if (!bookingId || !employeeId || draftLines.length === 0 || busy) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await foodOrderApi.addStaffOrder({
        bookingId,
        assignedEmployeeId: employeeId,
        idempotencyKey: newKey(),
        items: draftLines.map((item) => ({ menuItemId: item.id, quantity: draft[item.id], unitPrice: Number(draftPrices[item.id] ?? item.salePrice) })),
      });
      setDraft({});
      setDraftPrices({});
      setOrderDialogOpen(false);
      try { await loadOrders(status); setNotice('Đã thêm món vào booking và cập nhật tồn kho.'); }
      catch { setError('Order đã được ghi nhận nhưng chưa tải lại danh sách. Hệ thống sẽ tự thử lại.'); }
    } catch (actionError) { setError(errorText(actionError)); }
    finally { setBusy(false); }
  };

  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (logoutError) { setError(errorText(logoutError)); }
    finally { setBusy(false); }
  };

  if (loading) return <div className="booking-auth-state" role="status">Đang tải yêu cầu món…</div>;
  if (!user) return <div className="booking-auth-state" role="alert">{error || 'Không thể tải màn hình order.'}</div>;

  const visibleOrders = orders.filter((order) =>
    (!checkoutBookingId || order.bookingId === checkoutBookingId)
    && (status !== 'ACTIVE' || ['PENDING', 'ACCEPTED'].includes(order.status)));

  return <AppShell user={user} branchName={user.branchName} busy={busy} onLogout={logout} breadcrumb="Order & yêu cầu món" activeTab="orders" hideSearch>
    <div className="food-orders-staff">
       <header className="food-staff-heading"><div><span className="food-staff-eyebrow">VẬN HÀNH F&amp;B</span><h1>Order &amp; yêu cầu món</h1><p>Theo dõi yêu cầu từ phòng và xử lý từng order tại quầy.</p></div><div className="food-staff-heading-actions"><button type="button" className="food-primary" onClick={() => { setError(''); setOrderDialogOpen(true); }}>+ Ghi nhận order</button></div></header>
    <ToastStack error={error} notice={notice} onCloseError={() => setError('')} onCloseNotice={() => setNotice('')} />
      {incomingOrderAlert && <div className="food-alert food-alert--incoming" role="alert" aria-live="assertive"><div><strong>Khách vừa gửi {incomingOrderAlert.count} yêu cầu món mới.</strong><span>{incomingOrderAlert.roomNames.length > 0 ? `Phòng ${incomingOrderAlert.roomNames.slice(0, 3).join(', ')}${incomingOrderAlert.roomNames.length > 3 ? ` và ${incomingOrderAlert.roomNames.length - 3} phòng khác` : ''}.` : 'Vui lòng kiểm tra danh sách chờ xác nhận.'}</span></div><button type="button" onClick={() => { setIncomingOrderAlert(null); setStatus('PENDING'); setDate(''); setRoomId(''); document.getElementById('food-staff-order-queue')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>Xem yêu cầu</button></div>}

      <dialog ref={orderDialogRef} className="room-order-dialog food-staff-order-dialog" aria-labelledby="food-order-dialog-title" onClose={() => setOrderDialogOpen(false)} onCancel={(event) => { if (busy) event.preventDefault(); }}>
        <header><div><h2 id="food-order-dialog-title">Ghi nhận order{selectedOrderBooking?.roomName ? ` · ${selectedOrderBooking.roomName}` : ''}</h2><p>{selectedOrderBooking?.customerName || 'Chọn booking đang sử dụng'}</p></div><button type="button" aria-label="Đóng cửa sổ order" disabled={busy} onClick={() => setOrderDialogOpen(false)}>×</button></header>
        <p className="room-order-guidance">Chọn món và số lượng. Order sẽ được ghi nhận vào booking, cập nhật phí và tồn kho ngay. Với món tặng kèm combo, đặt giá áp dụng là 0 đ.</p>
        {error && <div className="room-order-error" role="alert">{error}</div>}
        {bookings.length === 0 ? <p className="room-order-empty">Hiện không có booking đang ở phòng tại cơ sở này.</p> : employees.length === 0 ? <p className="room-order-empty">Chưa có lễ tân được xác nhận trong ca hiện tại. Chọn ca đang mở trong lịch phân ca rồi tải lại.</p> : availableMenu.length === 0 ? <p className="room-order-empty">Chưa có món khả dụng để order.</p> : <form aria-labelledby="food-order-dialog-title" onSubmit={addOrder}>
          <div className="food-staff-order-context">
            <label className="room-order-employee">Booking đang sử dụng<select autoFocus value={bookingId} onChange={(event) => setBookingId(event.target.value)} required>{bookings.map((booking) => <option key={booking.id} value={booking.id}>{booking.roomName} · {booking.customerName}</option>)}</select></label>
            {/* BR-123: chỉ phân công cho lễ tân đang làm ca tại cơ sở của booking. */}
            <label className="room-order-employee">Nhân viên phụ trách<select value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} required>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label>
          </div>
          <search className="room-order-filters" aria-label="Lọc món">
            <label className="room-order-search">Tìm món<span><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></svg><input type="search" name="menuSearch" placeholder="Nhập tên món…" value={menuSearch} disabled={busy} aria-controls="food-staff-order-items" onChange={(event) => setMenuSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') event.preventDefault(); }} /></span></label>
            <label className="room-order-category">Danh mục<select name="menuCategory" value={menuCategory} disabled={busy} aria-controls="food-staff-order-items" onChange={(event) => setMenuCategory(event.target.value)}><option value="all">Tất cả</option><option value="Đồ ăn">Đồ ăn</option><option value="Đồ uống">Đồ uống</option></select></label>
          </search>
          <p className="room-order-result-count" role="status">Hiển thị {visibleMenu.length}/{availableMenu.length} món · Đã chọn {draftLines.length} món</p>
          <PaginatedList as="div" pageSize={4} className="room-order-items" id="food-staff-order-items">{visibleMenu.length === 0 ? <div className="room-order-no-results">
            {/* MSG01: không có món khớp từ khóa và bộ lọc. */}
            <p>Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</p><button type="button" disabled={busy} onClick={() => { setMenuSearch(''); setMenuCategory('all'); }}>Đặt lại bộ lọc</button>
          </div> : visibleMenu.map((item) => {
            const quantity = draft[item.id] || 0;
            return <article className="room-order-item food-staff-order-item" key={item.id}>
              <div><strong>{item.name}</strong><span>{formatVnd(item.salePrice)}</span>{quantity > 0 && <label className="food-staff-order-price">Giá áp dụng<MoneyInput aria-label={`Giá ${item.name}`} min="0" value={draftPrices[item.id] ?? item.salePrice ?? 0} onValueChange={(value) => setDraftPrices((current) => ({ ...current, [item.id]: value }))} /></label>}</div>
              <div className="room-order-quantity"><button type="button" aria-label={`Giảm ${item.name}`} disabled={quantity === 0 || busy} onClick={() => changeDraft(item, quantity - 1)}>−</button><span>{quantity}</span><button type="button" aria-label={`Tăng ${item.name}`} disabled={busy} onClick={() => changeDraft(item, quantity + 1)}>+</button></div>
            </article>;
          })}</PaginatedList>
          <footer className="room-order-footer"><span>Tạm tính <strong>{formatVnd(draftTotal)}</strong></span><button type="submit" disabled={busy || !bookingId || !employeeId || draftLines.length === 0}>{busy ? 'Đang lưu…' : 'Ghi nhận order'}</button></footer>
        </form>}
      </dialog>

      <section className="food-staff-queue" id="food-staff-order-queue">
         <div className="food-queue-heading"><div><span className="food-staff-eyebrow">{status === 'ACTIVE' ? 'HÀNG ĐỢI PHỤC VỤ' : 'TRA CỨU ORDER'}</span><h2>Danh sách order <span className="food-order-count">{visibleOrders.length}</span></h2><p>{checkoutBookingId ? 'Order của booking đang checkout' : status === 'ACTIVE' ? 'Ưu tiên order cần xử lý. Chọn Đã phục vụ để xem lịch sử.' : 'Các order theo bộ lọc hiện tại'}</p></div></div>
        <search className={`food-queue-filters${status === 'ACTIVE' ? ' food-queue-filters--active' : ''}`} aria-label="Lọc danh sách order"><label htmlFor="food-order-status-filter">Hiển thị<select id="food-order-status-filter" value={status} onChange={(event) => { const next = event.target.value; setStatus(next); setDate(next === 'SERVED' ? todayInVietnam() : ''); }}>{filters.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label htmlFor="food-order-room-filter">Phòng<select id="food-order-room-filter" value={roomId} disabled={Boolean(roomFilterError)} onChange={(event) => setRoomId(event.target.value)}><option value="">Tất cả phòng</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.name}</option>)}</select></label>{status !== 'ACTIVE' && <label htmlFor="food-order-date-filter">{status === 'SERVED' ? 'Ngày phục vụ' : 'Ngày tạo'}<input id="food-order-date-filter" type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>}{date && <button type="button" className="food-secondary" onClick={() => setDate('')}>Xóa ngày</button>}</search>
        {roomFilterError && <p className="food-room-filter-note" role="status">{roomFilterError}</p>}
         {visibleOrders.length === 0 ? <div className="food-empty"><h3>{status === 'ACTIVE' ? 'Không có order cần xử lý' : status === 'SERVED' && date ? 'Chưa có order phục vụ trong ngày này' : 'Không có order trong mục này'}</h3><p>{status === 'ACTIVE' ? 'Chọn Đã phục vụ để xem các order đã hoàn tất.' : status === 'SERVED' && date ? 'Chọn ngày khác hoặc xóa ngày để xem tất cả.' : 'Thử đổi bộ lọc hoặc chờ yêu cầu món mới từ phòng.'}</p></div> : <PaginatedList as="div" pageSize={6} className="food-staff-order-list">{visibleOrders.map((order) => <article className={`food-staff-order food-staff-order--${order.status.toLowerCase()}`} key={order.id}>
          <header><div className="food-order-identity"><span className="food-order-room-label">PHÒNG</span><h3>{order.roomName || 'Phòng chưa xác định'}</h3><p>{order.customerName || 'Chưa có tên khách'}</p></div><div className="food-order-meta"><span className={`food-order-status status-${order.status.toLowerCase()}`}>{statusLabel[order.status] || order.status}</span><time dateTime={order.status === 'SERVED' ? order.servedAt || order.createdAt : order.createdAt}>{orderTime.format(new Date(order.status === 'SERVED' ? order.servedAt || order.createdAt : order.createdAt))}</time></div></header>
          {order.status !== 'PENDING' && <p className="food-order-assignee">Phụ trách <strong>{order.assignedEmployeeName || 'Chưa phân công'}</strong></p>}
          <div className="food-staff-lines">{order.items.map((item) => {
            const quantity = quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity);
            return <div key={item.id}><span>{item.name}{item.includedInCombo && <small> · Trong combo</small>}</span>
              {order.status === 'PENDING' ? <input type="number" min="0" max={Number(item.quantity)} step="1" aria-label={`Số lượng xác nhận ${item.name}`} value={quantity} onChange={(event) => changeOrderQty(order.id, item.menuItemId, Number(event.target.value))} /> : <b>× {Number(item.quantity)}</b>}
              <strong>{formatVnd(order.status === 'PENDING' ? Number(item.unitPrice) * quantity : item.amount)}</strong>
            </div>;
          })}</div>
          <footer><div className="food-order-total"><span>Tạm tính</span><strong>{formatVnd(order.status === 'PENDING'
            ? order.items.reduce((sum, item) => sum + Number(item.unitPrice) * (quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity)), 0)
            : order.total)}</strong></div>
            {order.status === 'PENDING' && <div className="food-order-actions"><label>Phân công<select value={assignments[order.id] || ''} onChange={(event) => setAssignments((current) => ({ ...current, [order.id]: event.target.value }))}><option value="">Chọn lễ tân đang trong ca</option>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label><button className="food-primary" disabled={busy || !assignments[order.id]} onClick={() => withAction(() => foodOrderApi.confirmRoomOrder(order.id, { assignedEmployeeId: assignments[order.id], items: order.items.map((item) => ({ menuItemId: item.menuItemId, quantity: quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity) })) }), 'Đã xác nhận order, ghi nhận phí và xử lý tồn kho.')}>Xác nhận</button><button className="food-danger-button" disabled={busy} onClick={() => withAction(() => foodOrderApi.rejectRoomOrder(order.id), 'Đã từ chối yêu cầu món.')}>Từ chối</button></div>}
            {order.status === 'ACCEPTED' && <button className="food-primary" disabled={busy} onClick={() => withAction(() => foodOrderApi.markServed(order.id), 'Đã cập nhật order thành đã phục vụ.')}>Đánh dấu đã phục vụ</button>}
          </footer>
        </article>)}</PaginatedList>}
      </section>
    </div>
  </AppShell>;
}
