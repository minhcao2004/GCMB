import { PaginatedList } from '../../../shared/components/Pagination';
import { ToastStack } from '../../../shared/components/Feedback';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import MoneyInput from '../../../shared/components/MoneyInput';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import { formatVnd } from '../../booking/data/bookingData';
import { foodOrderApi } from '../services/foodOrderApi';
import './food-orders.css';

const filters = [['ALL', 'Tất cả trạng thái'], ['PENDING', 'Chờ xác nhận'], ['ACCEPTED', 'Đang chuẩn bị'], ['SERVED', 'Đã phục vụ'], ['REJECTED', 'Đã từ chối'], ['CANCELLED', 'Đã hủy']];
const statusLabel = { PENDING: 'Chờ xác nhận', ACCEPTED: 'Đang chuẩn bị', SERVED: 'Đã phục vụ', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' };
const errorText = (error) => Object.values(apiErrors(error))[0];
const newKey = () => window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const orderPollInterval = 5000;

export default function ReceptionOrdersPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const checkoutBookingId = searchParams.get('bookingId');
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('ALL');
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
  const [menuItemId, setMenuItemId] = useState('');
  const [draft, setDraft] = useState({});
  const [draftPrices, setDraftPrices] = useState({});
  const [quantities, setQuantities] = useState({});
  const [assignments, setAssignments] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [soundError, setSoundError] = useState('');
  const [incomingOrderAlert, setIncomingOrderAlert] = useState(null);
  const audioContext = useRef(null);
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

  const loadOrders = useCallback(async (filter = status) => {
    applyOrderRows(await foodOrderApi.staffOrders(filter, date, roomId));
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
        foodOrderApi.staffOrders(status, date, roomId),
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
          : 'Không tải được danh sách phòng. Vui lòng bấm Làm mới danh sách.'
        : '');
      if (roomResult.requestError && roomId) setRoomId('');
      setBookings(activeBookings);
      setEmployees(servingEmployees);
      setMenu(menuItems);
      setBookingId((current) => current || checkoutBookingId || activeBookings[0]?.id || '');
      setEmployeeId((current) => current || servingEmployees[0]?.id || '');
      setMenuItemId((current) => current || menuItems.find((item) => item.available)?.id || '');
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

  const playIncomingOrderSound = useCallback(() => {
    const context = audioContext.current;
    if (!soundEnabled || !context || context.state !== 'running') return;

    const now = context.currentTime;
    [[880, 0], [660, 0.16], [880, 0.32]].forEach(([frequency, offset]) => {
      const startAt = now + offset;
      const oscillator = context.createOscillator();
      const volume = context.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.setValueAtTime(frequency, startAt);
      volume.gain.setValueAtTime(0.0001, startAt);
      volume.gain.exponentialRampToValueAtTime(0.16, startAt + 0.015);
      volume.gain.exponentialRampToValueAtTime(0.0001, startAt + 0.14);
      oscillator.connect(volume);
      volume.connect(context.destination);
      oscillator.start(startAt);
      oscillator.stop(startAt + 0.15);
    });
  }, [soundEnabled]);

  const pollPendingOrders = useCallback(async () => {
    if (orderPollInProgress.current) return;
    orderPollInProgress.current = true;
    try {
      const pendingOrders = await foodOrderApi.staffOrders('PENDING');
      const knownIds = knownPendingOrderIds.current;
      let hasNewPending = false;
      if (knownIds === null) {
        knownPendingOrderIds.current = new Set(pendingOrders.map((order) => order.id));
      } else {
        const newOrders = pendingOrders.filter((order) => !knownIds.has(order.id));
        hasNewPending = newOrders.length > 0;
        pendingOrders.forEach((order) => knownIds.add(order.id));
        if (newOrders.length > 0) {
          const roomNames = [...new Set(newOrders.map((order) => order.roomName).filter(Boolean))];
          setIncomingOrderAlert((current) => ({
            count: (current?.count || 0) + newOrders.length,
            roomNames: [...new Set([...(current?.roomNames || []), ...roomNames])],
          }));
          playIncomingOrderSound();
        }
      }
      if (status === 'PENDING') applyOrderRows(date || roomId
        ? await foodOrderApi.staffOrders('PENDING', date, roomId) : pendingOrders);
      else if (status === 'ALL' && hasNewPending) applyOrderRows(await foodOrderApi.staffOrders('ALL', date, roomId));
    } catch {
      // Polling retries automatically; keep transient network errors out of action feedback.
    } finally {
      orderPollInProgress.current = false;
    }
  }, [applyOrderRows, date, playIncomingOrderSound, roomId, status]);

  useEffect(() => {
    if (!user || loading) return undefined;
    pollPendingOrders();
    const timer = window.setInterval(pollPendingOrders, orderPollInterval);
    return () => window.clearInterval(timer);
  }, [loading, pollPendingOrders, user]);

  const toggleSound = async () => {
    setSoundError('');
    if (soundEnabled) {
      setSoundEnabled(false);
      await audioContext.current?.suspend();
      return;
    }

    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) {
      setSoundError('Trình duyệt này không hỗ trợ âm báo.');
      return;
    }
    try {
      audioContext.current ||= new AudioContextClass();
      await audioContext.current.resume();
      if (audioContext.current.state !== 'running') throw new Error('Audio context is not running.');
      setSoundEnabled(true);
    } catch {
      setSoundError('Chưa bật được âm báo. Hãy kiểm tra âm lượng và quyền âm thanh của trình duyệt.');
    }
  };

  const draftLines = useMemo(() => menu.filter((item) => draft[item.id] > 0), [menu, draft]);
  const draftTotal = draftLines.reduce((sum, item) => sum + Number(draftPrices[item.id] ?? item.salePrice ?? 0) * draft[item.id], 0);
  const draftAddItem = () => {
    if (!menuItemId) return;
    setDraft((current) => ({ ...current, [menuItemId]: (current[menuItemId] || 0) + 1 }));
    const item = menu.find((candidate) => candidate.id === menuItemId);
    setDraftPrices((current) => ({ ...current, [menuItemId]: current[menuItemId] ?? Number(item?.salePrice || 0) }));
  };
  const changeDraft = (itemId, amount) => setDraft((current) => {
    const next = { ...current };
    if (amount <= 0) delete next[itemId]; else next[itemId] = amount;
    return next;
  });
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
      catch { setError('Order đã được ghi nhận nhưng chưa tải lại danh sách. Vui lòng bấm Làm mới danh sách.'); }
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

  const visibleOrders = checkoutBookingId
    ? orders.filter((order) => order.bookingId === checkoutBookingId)
    : orders;

  return <AppShell user={user} branchName={user.branchName} busy={busy} onLogout={logout} breadcrumb="Order & yêu cầu món" activeTab="orders" hideSearch>
    <div className="food-orders-staff">
      <header className="food-staff-heading"><div><h1>Order &amp; yêu cầu món</h1></div><div className="food-staff-heading-actions"><button type="button" className="food-primary" onClick={() => { setError(''); setOrderDialogOpen(true); }}>Ghi nhận order</button><button type="button" className={`food-secondary food-sound-toggle${soundEnabled ? ' is-enabled' : ''}`} aria-pressed={soundEnabled} onClick={toggleSound}>{soundEnabled ? 'Tắt âm báo' : 'Bật âm báo'}</button><button type="button" className="food-secondary" disabled={busy} onClick={() => loadPage()}>Làm mới danh sách</button></div></header>
      {soundError && <div className="food-sound-note" role="status">{soundError}</div>}
    <ToastStack error={error} notice={notice} onCloseError={() => setError('')} onCloseNotice={() => setNotice('')} />
      {incomingOrderAlert && <div className="food-alert food-alert--incoming" role="alert" aria-live="assertive"><div><strong>Khách vừa gửi {incomingOrderAlert.count} yêu cầu món mới.</strong><span>{incomingOrderAlert.roomNames.length > 0 ? `Phòng ${incomingOrderAlert.roomNames.slice(0, 3).join(', ')}${incomingOrderAlert.roomNames.length > 3 ? ` và ${incomingOrderAlert.roomNames.length - 3} phòng khác` : ''}.` : 'Vui lòng kiểm tra danh sách chờ xác nhận.'}</span></div><button type="button" onClick={() => { setIncomingOrderAlert(null); setStatus('PENDING'); setDate(''); setRoomId(''); document.getElementById('food-staff-order-queue')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>Xem yêu cầu</button></div>}

      <dialog ref={orderDialogRef} className="food-order-dialog" aria-labelledby="food-order-dialog-title" onClose={() => setOrderDialogOpen(false)} onCancel={(event) => { if (busy) event.preventDefault(); }}>
      <section className="food-staff-add">
        <div className="food-section-title"><div><h2 id="food-order-dialog-title">Thêm món vào booking</h2><p>Đơn do lễ tân tạo được ghi nhận ngay và trừ kho theo thiết lập món. Với món tặng kèm combo, đặt giá áp dụng là 0 đ.</p></div><button type="button" className="food-dialog-close" aria-label="Đóng cửa sổ order" disabled={busy} onClick={() => setOrderDialogOpen(false)}>×</button></div>
        {error && <div className="food-order-dialog-error" role="alert">{error}</div>}
        {bookings.length === 0 ? <div className="food-empty-inline">Hiện không có booking đang ở phòng tại cơ sở này.</div> : employees.length === 0 ? <div className="food-empty-inline">Chưa có lễ tân được xác nhận trong ca hiện tại. Chọn ca đang mở trong lịch phân ca rồi tải lại.</div> : <form onSubmit={addOrder}>
          <div className="food-form-row">
            <label>Booking đang sử dụng<select autoFocus value={bookingId} onChange={(event) => setBookingId(event.target.value)} required>{bookings.map((booking) => <option key={booking.id} value={booking.id}>{booking.roomName} · {booking.customerName}</option>)}</select></label>
            <label>Nhân viên phụ trách<select value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} required>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label>
            <label>Chọn món<select value={menuItemId} onChange={(event) => setMenuItemId(event.target.value)}>{menu.filter((item) => item.available).map((item) => <option key={item.id} value={item.id}>{item.name} · {formatVnd(item.salePrice)}</option>)}</select></label>
            <button type="button" className="food-secondary" disabled={!menuItemId} onClick={draftAddItem}>Thêm món</button>
          </div>
          {draftLines.length > 0 && <div className="food-draft-lines">{draftLines.map((item) => <div key={item.id}><span>{item.name}</span><div className="food-quantity"><button type="button" aria-label={`Giảm ${item.name}`} onClick={() => changeDraft(item.id, draft[item.id] - 1)}>−</button><span>{draft[item.id]}</span><button type="button" aria-label={`Thêm ${item.name}`} onClick={() => changeDraft(item.id, draft[item.id] + 1)}>+</button></div><label className="food-draft-price">Giá áp dụng<MoneyInput aria-label={`Giá ${item.name}`}  min="0"  value={draftPrices[item.id] ?? item.salePrice ?? 0} onValueChange={value => setDraftPrices((current) => ({ ...current, [item.id]: value }))} /></label><strong>{formatVnd(Number(draftPrices[item.id] ?? item.salePrice ?? 0) * draft[item.id])}</strong></div>)}</div>}
          <footer className="food-add-footer"><span>Tạm tính <strong>{formatVnd(draftTotal)}</strong></span><button className="food-primary" disabled={busy || draftLines.length === 0}>{busy ? 'Đang lưu…' : 'Ghi nhận order'}</button></footer>
        </form>}
      </section>
      </dialog>

      <section className="food-staff-queue" id="food-staff-order-queue">
        <div className="food-section-title"><div><h2>Danh sách order</h2><p>{checkoutBookingId ? `Hiển thị ${visibleOrders.length} order của booking đang checkout` : `Hiển thị ${visibleOrders.length} order`}</p></div><search className="food-queue-filters" aria-label="Lọc danh sách order"><label htmlFor="food-order-status-filter">Trạng thái<select id="food-order-status-filter" value={status} onChange={(event) => setStatus(event.target.value)}>{filters.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label htmlFor="food-order-room-filter">Phòng<select id="food-order-room-filter" value={roomId} disabled={Boolean(roomFilterError)} onChange={(event) => setRoomId(event.target.value)}><option value="">Tất cả phòng</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.name}</option>)}</select></label><label htmlFor="food-order-date-filter">Ngày tạo<input id="food-order-date-filter" type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>{date && <button type="button" className="food-secondary" onClick={() => setDate('')}>Xóa ngày</button>}</search></div>
        {roomFilterError && <p className="food-room-filter-note" role="status">{roomFilterError}</p>}
        {visibleOrders.length === 0 ? <div className="food-empty"><h3>Không có order trong mục này</h3><p>Yêu cầu mới từ mã MR sẽ xuất hiện ở đây.</p></div> : <PaginatedList as="div" pageSize={5} className="food-staff-order-list">{visibleOrders.map((order) => <article className="food-staff-order" key={order.id}>
          <header><div><h3>{order.roomName || 'Phòng chưa xác định'}</h3><p>{order.customerName} · {new Date(order.createdAt).toLocaleString('vi-VN')}</p>{order.status !== 'PENDING' && <p>Người phụ trách: {order.assignedEmployeeName || 'Chưa phân công'}</p>}</div><span className={`food-order-status status-${order.status.toLowerCase()}`}>{statusLabel[order.status] || order.status}</span></header>
          <div className="food-staff-lines">{order.items.map((item) => {
            const quantity = quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity);
            return <div key={item.id}><span>{item.name}{item.includedInCombo && <small> · Trong combo</small>}</span>
              {order.status === 'PENDING' ? <input type="number" min="0" max={Number(item.quantity)} step="1" aria-label={`Số lượng xác nhận ${item.name}`} value={quantity} onChange={(event) => changeOrderQty(order.id, item.menuItemId, Number(event.target.value))} /> : <b>× {Number(item.quantity)}</b>}
              <strong>{formatVnd(order.status === 'PENDING' ? Number(item.unitPrice) * quantity : item.amount)}</strong>
            </div>;
          })}</div>
          <footer><strong>Tạm tính: {formatVnd(order.status === 'PENDING'
            ? order.items.reduce((sum, item) => sum + Number(item.unitPrice) * (quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity)), 0)
            : order.total)}</strong>
            {order.status === 'PENDING' && <div className="food-order-actions"><label>Phân công<select value={assignments[order.id] || ''} onChange={(event) => setAssignments((current) => ({ ...current, [order.id]: event.target.value }))}><option value="">Chọn lễ tân đang trong ca</option>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label><button className="food-primary" disabled={busy || !assignments[order.id]} onClick={() => withAction(() => foodOrderApi.confirmRoomOrder(order.id, { assignedEmployeeId: assignments[order.id], items: order.items.map((item) => ({ menuItemId: item.menuItemId, quantity: quantities[order.id]?.[item.menuItemId] ?? Number(item.quantity) })) }), 'Đã xác nhận order, ghi nhận phí và xử lý tồn kho.')}>Xác nhận</button><button className="food-danger-button" disabled={busy} onClick={() => withAction(() => foodOrderApi.rejectRoomOrder(order.id), 'Đã từ chối yêu cầu món.')}>Từ chối</button></div>}
            {order.status === 'ACCEPTED' && <button className="food-primary" disabled={busy} onClick={() => withAction(() => foodOrderApi.markServed(order.id), 'Đã cập nhật order thành đã phục vụ.')}>Đánh dấu đã phục vụ</button>}
          </footer>
        </article>)}</PaginatedList>}
      </section>
    </div>
  </AppShell>;
}
