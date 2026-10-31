import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { apiAssetUrl } from '../../../api/client';
import { formatVnd } from '../../booking/data/bookingData';
import { foodOrderApi } from '../services/foodOrderApi';
import './food-orders.css';

const sessionKey = (code) => `gcmb:mr:${code}:session`;
const cartKey = (code) => `gcmb:mr:${code}:cart`;
const errorText = (error) => error.response?.data?.message || error.response?.data?.detail || error.message || 'Không thể tải thực đơn.';
const savedCart = (code) => {
  try {
    const value = JSON.parse(window.sessionStorage.getItem(cartKey(code)) || '{}');
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    return Object.fromEntries(Object.entries(value).filter(([, quantity]) => Number.isInteger(quantity) && quantity > 0));
  } catch {
    return {};
  }
};
const statusText = {
  PENDING: 'Đang chờ lễ tân xác nhận', ACCEPTED: 'Đã xác nhận · Đang chuẩn bị',
  SERVED: 'Đã phục vụ', REJECTED: 'Không thể phục vụ yêu cầu này', CANCELLED: 'Đã hủy',
};
const newKey = () => window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;

function MenuItemImage({ item }) {
  const src = apiAssetUrl(item.imageUrl);
  const [failedSrc, setFailedSrc] = useState('');
  return src && failedSrc !== src
    ? <img src={src} alt="" loading="lazy" onError={() => setFailedSrc(src)} />
    : <div className="food-menu-placeholder" aria-hidden="true"><span>F&amp;B</span></div>;
}

export default function GuestRoomOrderPage() {
  const { mrCode = '' } = useParams();
  const normalizedCode = mrCode.toUpperCase();
  const [room, setRoom] = useState(null);
  const [items, setItems] = useState([]);
  const [orders, setOrders] = useState([]);
  const [cart, setCart] = useState({});
  const [cartCode, setCartCode] = useState(null);
  const [category, setCategory] = useState('Tất cả');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [ordersError, setOrdersError] = useState('');
  const pendingKey = useRef(newKey());
  const token = useRef('');

  useEffect(() => {
    setCart(savedCart(normalizedCode));
    setCartCode(normalizedCode);
  }, [normalizedCode]);
  useEffect(() => {
    if (cartCode !== normalizedCode) return;
    try {
      if (Object.keys(cart).length > 0) window.sessionStorage.setItem(cartKey(normalizedCode), JSON.stringify(cart));
      else window.sessionStorage.removeItem(cartKey(normalizedCode));
    } catch { /* giỏ vẫn dùng được trong trang hiện tại */ }
  }, [cart, cartCode, normalizedCode]);

  const refreshOrders = useCallback(async (sessionToken = token.current) => {
    if (!sessionToken) return;
    try {
      const list = await foodOrderApi.roomOrders(normalizedCode, sessionToken);
      setOrders(list);
      setOrdersError('');
    } catch (ordersLoadError) {
      setOrdersError(errorText(ordersLoadError));
    }
  }, [normalizedCode]);

  const load = useCallback(async (forceNew = false) => {
    setLoading(true);
    setError('');
    setOrdersError('');
    setMessage('');
    try {
      let savedToken = '';
      if (!forceNew) {
        try { savedToken = window.sessionStorage.getItem(sessionKey(normalizedCode)) || ''; } catch { savedToken = ''; }
      }
      if (savedToken) {
        try {
          const current = await foodOrderApi.roomMenu(normalizedCode, savedToken);
          token.current = savedToken;
          setRoom(current.room);
          setItems(current.items || []);
          await refreshOrders(savedToken);
          return;
        } catch (sessionError) {
          if (sessionError.response?.status !== 401) throw sessionError;
        }
      }
      const opened = await foodOrderApi.openRoomSession(normalizedCode);
      token.current = opened.sessionToken;
      try { window.sessionStorage.setItem(sessionKey(normalizedCode), opened.sessionToken); } catch { /* phiên vẫn dùng được trong trang hiện tại */ }
      setRoom(opened.room);
      setItems(opened.items || []);
      setOrders([]);
      setOrdersError('');
    } catch (loadError) {
      setError(errorText(loadError));
      setRoom(null);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [normalizedCode, refreshOrders]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (!token.current) return undefined;
    const timer = window.setInterval(() => refreshOrders(), 12000);
    return () => window.clearInterval(timer);
  }, [room, refreshOrders]);

  const categories = useMemo(() => ['Tất cả', ...new Set(items.map((item) => item.category).filter(Boolean))], [items]);
  const visibleItems = useMemo(() => items.filter((item) => category === 'Tất cả' || item.category === category), [items, category]);
  const cartLines = useMemo(() => items.filter((item) => cart[item.id] > 0), [items, cart]);
  const cartTotal = cartLines.reduce((sum, item) => sum + Number(item.salePrice || 0) * cart[item.id], 0);
  const setQuantity = (id, amount) => setCart((current) => {
    const next = { ...current };
    if (amount <= 0) delete next[id]; else next[id] = amount;
    return next;
  });

  const submit = async () => {
    if (!token.current || cartLines.length === 0 || sending) return;
    setSending(true);
    setError('');
    setMessage('');
    try {
      const result = await foodOrderApi.submitRoomOrder(normalizedCode, token.current, {
        idempotencyKey: pendingKey.current,
        items: cartLines.map((item) => ({ menuItemId: item.id, quantity: cart[item.id] })),
      });
      pendingKey.current = newKey();
      setCart({});
      setMessage(`Đã gửi yêu cầu ${result.code}. Lễ tân sẽ xác nhận món và số lượng.`);
      await refreshOrders();
    } catch (sendError) {
      setError(errorText(sendError));
    } finally {
      setSending(false);
    }
  };

  return <main className="mr-order-page">
    <header className="mr-order-header">
      <span className="mr-brand" aria-label="GenZ Cinema & Music Box">GENZ <span>F&amp;B</span></span>
      {room && <p>{room.roomName} <span>·</span> {room.branchName}</p>}
    </header>
    <div className="mr-order-content">
      <section className="mr-order-intro">
        <div><h1>Thực đơn tại phòng</h1><p>Chọn món bạn muốn dùng. Yêu cầu sẽ được lễ tân xác nhận trước khi tính phí.</p></div>
        {room && <div className="mr-room-tag"><span>Mã phòng</span><strong>{room.roomCode}</strong></div>}
      </section>
      {error && <div className="food-alert" role="alert"><span>{error}</span><button onClick={() => load(true)}>Quét lại mã MR</button></div>}
      {message && <div className="food-alert food-alert--success" role="status">{message}</div>}
      {loading ? <div className="food-loading" role="status">Đang tải thực đơn phòng…</div> : room && <>
        {items.length === 0 ? <div className="food-empty"><h2>Chưa có món đang bán</h2><p>Vui lòng gọi lễ tân nếu bạn cần hỗ trợ.</p></div> : <>
          <nav className="food-categories" aria-label="Nhóm món">
            {categories.map((entry) => <button key={entry} className={category === entry ? 'is-active' : ''} onClick={() => setCategory(entry)}>{entry}</button>)}
          </nav>
          <div className="mr-order-layout">
            <section className="food-menu-list" aria-label="Danh sách món">
              {visibleItems.map((item) => <article className={`food-menu-item${item.available ? '' : ' is-unavailable'}`} key={item.id}>
                <MenuItemImage item={item} />
                <div className="food-menu-copy"><p className="food-menu-category">{item.category || 'Món ngon'}</p><h2>{item.name}</h2><p>{item.description || 'Món ngon được phục vụ tại phòng.'}</p><strong>{formatVnd(item.salePrice)}</strong></div>
                {item.available ? <div className="food-quantity" aria-label={`Số lượng ${item.name}`}>
                  <button aria-label={`Giảm ${item.name}`} disabled={!cart[item.id]} onClick={() => setQuantity(item.id, (cart[item.id] || 0) - 1)}>−</button>
                  <span>{cart[item.id] || 0}</span>
                  <button aria-label={`Thêm ${item.name}`} onClick={() => setQuantity(item.id, (cart[item.id] || 0) + 1)}>+</button>
                </div> : <span className="food-sold-out">Tạm hết</span>}
              </article>)}
            </section>
            <aside className="food-cart">
              <div className="food-cart-heading"><h2>Giỏ món</h2><span>{cartLines.reduce((sum, item) => sum + cart[item.id], 0)} món</span></div>
              {cartLines.length === 0 ? <p className="food-cart-empty">Thêm món từ thực đơn để bắt đầu yêu cầu.</p> : <ul>{cartLines.map((item) => <li key={item.id}><span>{item.name} <small>× {cart[item.id]}</small></span><strong>{formatVnd(Number(item.salePrice || 0) * cart[item.id])}</strong></li>)}</ul>}
              <div className="food-cart-total"><span>Tạm tính</span><strong>{formatVnd(cartTotal)}</strong></div>
              <p className="food-cart-note">Tổng tiền được chốt khi lễ tân xác nhận yêu cầu.</p>
              <button className="food-primary" disabled={cartLines.length === 0 || sending} onClick={submit}>{sending ? 'Đang gửi…' : 'Gửi yêu cầu món'}</button>
            </aside>
          </div>
        </>}
        <section className="food-order-history">
          <div className="food-section-title"><h2>Yêu cầu gần đây</h2><button onClick={() => refreshOrders()}>Làm mới</button></div>
          {ordersError ? <p role="status">Không tải được yêu cầu gần đây. {ordersError}</p>
            : orders.length === 0 ? <p>Chưa có yêu cầu món trong phiên này.</p> : <div className="food-history-list">{orders.map((order) => <article key={order.code}>
            <div><strong>{order.code} <span>{order.roomCode}</span></strong><small>{new Date(order.createdAt).toLocaleString('vi-VN')}</small></div>
            <ul>{order.items.map((line, index) => <li key={`${line.name}-${index}`}>{line.name} × {Number(line.quantity)}</li>)}</ul>
            <div className={`food-order-status status-${order.status.toLowerCase()}`}>{statusText[order.status] || order.status}</div>
          </article>)}</div>}
        </section>
      </>}
      {!loading && !room && !error && <div className="food-empty"><h2>Không mở được mã phòng</h2><p>Vui lòng quét lại mã MR hoặc gọi lễ tân.</p></div>}
    </div>
    <footer className="mr-order-footer">GenZ Cinema &amp; Music Box <span>·</span> Đặt món qua mã MR trong phòng</footer>
  </main>;
}
