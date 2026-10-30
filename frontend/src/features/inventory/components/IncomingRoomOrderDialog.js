import { useEffect, useRef, useState } from 'react';
import './IncomingRoomOrderDialog.css';

const formatVnd = (value) => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + 'đ';

export default function IncomingRoomOrderDialog({
  orders,
  employees,
  busy,
  error,
  notice,
  onConfirm,
  onReject,
  onDefer,
}) {
  const order = orders[0];
  const dialogRef = useRef(null);
  const [assignedEmployeeId, setAssignedEmployeeId] = useState('');
  const [quantities, setQuantities] = useState({});

  useEffect(() => {
    if (!order) return;
    setAssignedEmployeeId(order.assignedEmployeeId || '');
    setQuantities(Object.fromEntries(order.items.map((item) => [item.menuItemId, Number(item.quantity)])));
  }, [order?.id]);

  useEffect(() => {
    if (order && !assignedEmployeeId && employees.length > 0) {
      setAssignedEmployeeId(employees[0].id);
    }
  }, [assignedEmployeeId, employees, order]);

  useEffect(() => {
    const initialFocus = dialogRef.current?.querySelector('.incoming-order-assignee select:not(:disabled)')
      || dialogRef.current?.querySelector('button:not(:disabled)');
    initialFocus?.focus();
  }, [employees.length, order?.id]);

  if (!order) return null;

  const acceptedTotal = order.items.reduce((total, item) => (
    total + Number(item.unitPrice || 0) * (quantities[item.menuItemId] ?? Number(item.quantity))
  ), 0);
  const acceptedCount = order.items.reduce((total, item) => (
    total + (quantities[item.menuItemId] ?? Number(item.quantity))
  ), 0);

  const changeQuantity = (item, value) => {
    // BR-117: accepted quantities stay whole and cannot exceed the guest's requested amount; zero omits that line.
    const parsed = value === '' ? 0 : Number.parseInt(value, 10);
    const quantity = Number.isFinite(parsed) ? Math.min(Number(item.quantity), Math.max(0, parsed)) : 0;
    setQuantities((current) => ({ ...current, [item.menuItemId]: quantity }));
  };

  const submitConfirmation = () => onConfirm(
    order,
    assignedEmployeeId,
    order.items.map((item) => ({
      menuItemId: item.menuItemId,
      quantity: quantities[item.menuItemId] ?? Number(item.quantity),
    })),
  );

  const keepDialogAccessible = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onDefer(order.id);
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = [...event.currentTarget.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled)')];
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  };

  return <div className="incoming-order-backdrop" role="presentation">
    <section ref={dialogRef} className="incoming-order-dialog" role="dialog" aria-modal="true" aria-labelledby="incoming-order-title" onKeyDown={keepDialogAccessible}>
      <header>
        <div>
          <h2 id="incoming-order-title">Khách gửi yêu cầu món</h2>
          <p>{orders.length > 1 ? `Còn ${orders.length} order đang chờ xử lý` : 'Order QR mới đang chờ xác nhận'}</p>
        </div>
        <button type="button" className="incoming-order-close" aria-label="Để order xử lý sau" disabled={busy} onClick={() => onDefer(order.id)}>×</button>
      </header>

      {notice && <div className="incoming-order-notice" role="status">{notice}</div>}

      <div className="incoming-order-room">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
        <div><strong>Phòng {order.roomCode} · {order.roomName}</strong><span>{order.code} · {order.bookingCode}</span><small>{order.customerName || 'Khách đang sử dụng phòng'}</small></div>
      </div>

      {error && <div className="incoming-order-error" role="alert">{error}</div>}

      {/* BR-123: lựa chọn nhân viên chỉ lấy từ danh sách lễ tân đang làm ca tại cơ sở. */}
      <label className="incoming-order-assignee">Nhân viên phục vụ
        <select value={assignedEmployeeId} onChange={(event) => setAssignedEmployeeId(event.target.value)} disabled={busy || employees.length === 0}>
          <option value="">Chọn nhân viên đang làm ca</option>
          {employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}
        </select>
      </label>
      {employees.length === 0 && <p className="incoming-order-empty-staff">Chưa có lễ tân đang làm ca tại cơ sở của booking để phụ trách order.</p>}

      <div className="incoming-order-lines" aria-label="Món khách đã gọi">
        {order.items.map((item) => {
          const quantity = quantities[item.menuItemId] ?? Number(item.quantity);
          return <div className="incoming-order-line" key={item.id}>
            <span><strong>{item.name}</strong><small>{formatVnd(item.unitPrice)} / món</small></span>
            <input type="number" min="0" max={Number(item.quantity)} step="1" aria-label={`Số lượng xác nhận ${item.name}`} value={quantity} disabled={busy} onChange={(event) => changeQuantity(item, event.target.value)} />
            <b>{formatVnd(Number(item.unitPrice) * quantity)}</b>
          </div>;
        })}
      </div>

      <footer>
        <div className="incoming-order-total"><span>Tạm tính</span><strong>{formatVnd(acceptedTotal)}</strong></div>
        <div className="incoming-order-actions">
          <button type="button" className="incoming-order-decline" disabled={busy} onClick={() => onReject(order)}>Từ chối</button>
          <button type="button" className="incoming-order-defer" disabled={busy} onClick={() => onDefer(order.id)}>Để sau</button>
          <button type="button" className="incoming-order-confirm" disabled={busy || !assignedEmployeeId || acceptedCount <= 0} onClick={submitConfirmation}>{busy ? 'Đang lưu…' : 'Xác nhận order'}</button>
        </div>
      </footer>
    </section>
  </div>;
}
