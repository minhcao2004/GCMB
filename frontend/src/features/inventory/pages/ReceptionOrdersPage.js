import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import { formatVnd } from '../../booking/data/bookingData';
import { foodOrderApi } from '../services/foodOrderApi';
import './food-orders.css';

const filters = [['PENDING', 'Chờ xác nhận'], ['ACCEPTED', 'Đang chuẩn bị'], ['SERVED', 'Đã phục vụ'], ['REJECTED', 'Đã từ chối'], ['ALL', 'Tất cả']];
const statusLabel = { PENDING: 'Chờ xác nhận', ACCEPTED: 'Đang chuẩn bị', SERVED: 'Đã phục vụ', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy' };
const errorText = (error) => Object.values(apiErrors(error))[0];
const newKey = () => window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const orderPollInterval = 5000;

export default function ReceptionOrdersPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('PENDING');
  const [orders, setOrders] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [menu, setMenu] = useState([]);
  const [bookingId, setBookingId] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [menuItemId, setMenuItemId] = useState('');
  const [draft, setDraft] = useState({});
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
    applyOrderRows(await foodOrderApi.staffOrders(filter));
  }, [applyOrderRows, status]);

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
      const [rows, activeBookings, servingEmployees, menuItems] = await Promise.all([
        foodOrderApi.staffOrders(status), foodOrderApi.activeBookings(), foodOrderApi.servingEmployees(), foodOrderApi.staffMenu(),
      ]);
      if (knownPendingOrderIds.current === null && status === 'PENDING') {
        knownPendingOrderIds.current = new Set(rows.map((order) => order.id));
      }
      applyOrderRows(rows);
      setBookings(activeBookings);
      setEmployees(servingEmployees);
      setMenu(menuItems);
      setBookingId((current) => current || activeBookings[0]?.id || '');
      setEmployeeId((current) => current || servingEmployees[0]?.id || '');
      setMenuItemId((current) => current || menuItems.find((item) => item.available)?.id || '');
    } catch (loadError) {
      if (loadError.response?.status === 401) navigate('/dang-nhap', { replace: true });
      else setError(errorText(loadError));
    } finally {
      setLoading(false);
    }
  }, [applyOrderRows, navigate, status]);

  useEffect(() => { loadPage(); }, [loadPage]);

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
      if (knownIds === null) {
        knownPendingOrderIds.current = new Set(pendingOrders.map((order) => order.id));
      } else {
        const newOrders = pendingOrders.filter((order) => !knownIds.has(order.id));
        pendingOrders.forEach((order) => knownIds.add(order.id));
        if (newOrders.length > 0) {
          const roomCodes = [...new Set(newOrders.map((order) => order.roomCode).filter(Boolean))];
          setIncomingOrderAlert((current) => ({
            count: (current?.count || 0) + newOrders.length,
            roomCodes: [...new Set([...(current?.roomCodes || []), ...roomCodes])],
          }));
          playIncomingOrderSound();
        }
      }
      if (status === 'PENDING') applyOrderRows(pendingOrders);
    } catch {
      // Polling retries automatically; keep transient network errors out of action feedback.
    } finally {
      orderPollInProgress.current = false;
    }
  }, [applyOrderRows, playIncomingOrderSound, status]);

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
  const draftTotal = draftLines.reduce((sum, item) => sum + Number(item.salePrice || 0) * draft[item.id], 0);
  const draftAddItem = () => {
    if (!menuItemId) return;
    setDraft((current) => ({ ...current, [menuItemId]: (current[menuItemId] || 0) + 1 }));
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
        items: draftLines.map((item) => ({ menuItemId: item.id, quantity: draft[item.id] })),
      });
      setDraft({});
      await loadOrders(status);
      setNotice('Đã thêm món vào booking và ghi nhận phí.');
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

  return <AppShell user={user} branchName={user.branchName} busy={busy} onLogout={logout} breadcrumb="Order & yêu cầu món" activeTab="orders" hideSearch>
    <div className="food-orders-staff">
      <header className="food-staff-heading"><div><h1>Order &amp; yêu cầu món</h1><p>Xác nhận yêu cầu QR, thêm món vào booking và cập nhật tiến độ phục vụ.</p></div><div className="food-staff-heading-actions"><button type="button" className={`food-secondary food-sound-toggle${soundEnabled ? ' is-enabled' : ''}`} aria-pressed={soundEnabled} onClick={toggleSound}>{soundEnabled ? 'Tắt âm báo' : 'Bật âm báo'}</button><button type="button" className="food-secondary" disabled={busy} onClick={() => loadPage()}>Làm mới danh sách</button></div></header>
      {soundError && <div className="food-sound-note" role="status">{soundError}</div>}
      {error && <div className="food-alert" role="alert">{error}</div>}
      {notice && <div className="food-alert food-alert--success" role="status">{notice}</div>}
      {incomingOrderAlert && <div className="food-alert food-alert--incoming" role="alert" aria-live="assertive"><div><strong>Khách vừa gửi {incomingOrderAlert.count} yêu cầu món mới.</strong><span>{incomingOrderAlert.roomCodes.length > 0 ? `Phòng ${incomingOrderAlert.roomCodes.slice(0, 3).join(', ')}${incomingOrderAlert.roomCodes.length > 3 ? ` và ${incomingOrderAlert.roomCodes.length - 3} phòng khác` : ''}.` : 'Vui lòng kiểm tra danh sách chờ xác nhận.'}</span></div><button type="button" onClick={() => { setIncomingOrderAlert(null); setStatus('PENDING'); document.getElementById('food-staff-order-queue')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>Xem yêu cầu</button></div>}

      <section className="food-staff-add">
        <div className="food-section-title"><div><h2>Thêm món vào booking</h2><p>Đơn do lễ tân tạo được ghi nhận ngay và trừ kho theo thiết lập món.</p></div></div>
        {bookings.length === 0 ? <div className="food-empty-inline">Hiện không có booking đang ở phòng tại cơ sở này.</div> : employees.length === 0 ? <div className="food-empty-inline">Chưa có lễ tân được xác nhận trong ca hiện tại. Chọn ca đang mở trong lịch phân ca rồi tải lại.</div> : <form onSubmit={addOrder}>
          <div className="food-form-row">
            <label>Booking đang sử dụng<select value={bookingId} onChange={(event) => setBookingId(event.target.value)} required>{bookings.map((booking) => <option key={booking.id} value={booking.id}>{booking.code} · {booking.roomCode} · {booking.customerName}</option>)}</select></label>
            <label>Nhân viên phụ trách<select value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} required>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label>
            <label>Chọn món<select value={menuItemId} onChange={(event) => setMenuItemId(event.target.value)}>{menu.filter((item) => item.available).map((item) => <option key={item.id} value={item.id}>{item.name} · {formatVnd(item.salePrice)}</option>)}</select></label>
            <button type="button" className="food-secondary" disabled={!menuItemId} onClick={draftAddItem}>Thêm món</button>
          </div>
          {draftLines.length > 0 && <div className="food-draft-lines">{draftLines.map((item) => <div key={item.id}><span>{item.name}</span><div className="food-quantity"><button type="button" aria-label={`Giảm ${item.name}`} onClick={() => changeDraft(item.id, draft[item.id] - 1)}>−</button><span>{draft[item.id]}</span><button type="button" aria-label={`Thêm ${item.name}`} onClick={() => changeDraft(item.id, draft[item.id] + 1)}>+</button></div><strong>{formatVnd(Number(item.salePrice || 0) * draft[item.id])}</strong></div>)}</div>}
          <footer className="food-add-footer"><span>Tạm tính <strong>{formatVnd(draftTotal)}</strong></span><button className="food-primary" disabled={busy || draftLines.length === 0}>{busy ? 'Đang lưu…' : 'Ghi nhận order'}</button></footer>
        </form>}
      </section>

      <section className="food-staff-queue" id="food-staff-order-queue">
        <div className="food-section-title"><div><h2>Danh sách order</h2><p>{orders.length} order trong bộ lọc</p></div><div className="food-filter-tabs" role="group" aria-label="Lọc trạng thái order">{filters.map(([value, label]) => <button key={value} className={status === value ? 'is-active' : ''} onClick={() => setStatus(value)}>{label}</button>)}</div></div>
        {orders.length === 0 ? <div className="food-empty"><h3>Không có order trong mục này</h3><p>Yêu cầu mới từ mã MR sẽ xuất hiện ở đây.</p></div> : <div className="food-staff-order-list">{orders.map((order) => <article className="food-staff-order" key={order.id}>
          <header><div><h3>{order.code} <span>· {order.roomCode}</span></h3><p>{order.bookingCode} · {order.customerName} · {new Date(order.createdAt).toLocaleString('vi-VN')}</p></div><span className={`food-order-status status-${order.status.toLowerCase()}`}>{statusLabel[order.status] || order.status}</span></header>
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
        </article>)}</div>}
      </section>
    </div>
  </AppShell>;
}
