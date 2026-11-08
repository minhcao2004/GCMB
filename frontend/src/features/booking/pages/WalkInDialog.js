import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { formatVnd } from '../data/bookingData';

export default function WalkInDialog({ room, options, onClose, onOpened }) {
  const dialog = useRef(null);
  const type = options?.roomTypes?.find((item) => item.id === room.roomTypeId);
  const combos = type?.combos || [];
  const [form, setForm] = useState({ contactName: '', contactPhone: '', guestCount: '1', note: '', pricingMode: 'HOURLY', durationHours: '2', comboId: combos[0]?.id || '' });
  const [quote, setQuote] = useState(null);
  const [quoteError, setQuoteError] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [uncertain, setUncertain] = useState(false);
  const [giftMenu, setGiftMenu] = useState([]);
  const [giftLoading, setGiftLoading] = useState(false);
  const [giftError, setGiftError] = useState('');
  const [drinkSelections, setDrinkSelections] = useState([]);
  const [foodSelections, setFoodSelections] = useState([]);
  const combo = combos.find((item) => item.id === form.comboId);
  const drinkCount = form.pricingMode === 'COMBO' ? combo?.includedFnbQuantity || 0 : 0;
  const foodCount = form.pricingMode === 'COMBO' ? combo?.includedFoodQuantity || 0 : 0;
  const giftRequired = drinkCount + foodCount > 0;
  const giftsReady = !giftRequired || (!giftLoading && !giftError && giftMenu.length > 0
    && drinkSelections.length === drinkCount && foodSelections.length === foodCount
    && drinkSelections.every(Boolean) && foodSelections.every(Boolean));
  const hours = Number(form.durationHours);
  // BR-150: thời lượng tính giờ từ 1 đến 24 giờ; giao diện chọn theo bước 30 phút.
  const duration = form.pricingMode === 'COMBO' ? combo?.durationMinutes : (hours >= 1 && hours <= 24 && Number.isInteger(hours * 2) ? hours * 60 : null);

  useEffect(() => { dialog.current?.showModal(); }, []);
  useEffect(() => {
    setDrinkSelections(Array(drinkCount).fill(''));
    setFoodSelections(Array(foodCount).fill(''));
    setGiftMenu([]);
    setGiftError('');
    if (!giftRequired) return undefined;
    let active = true;
    setGiftLoading(true);
    bookingApi.comboGiftMenu().then((menu) => { if (active) setGiftMenu(menu); })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setGiftError(errors.form || Object.values(errors)[0] || 'Không tải được danh sách quà combo.'); } })
      .finally(() => { if (active) setGiftLoading(false); });
    return () => { active = false; };
  }, [form.pricingMode, combo?.id, drinkCount, foodCount, giftRequired]);
  useEffect(() => {
    if (!type || !duration) { setQuote(null); return undefined; }
    let active = true;
    // BR-04: báo giá chỉ tham khảo; máy chủ kiểm tra lại toàn bộ khoảng dùng khi xác nhận.
    const start = new Date(Date.now() + 30000);
    setLoading(true); setQuote(null); setQuoteError('');
    bookingApi.availability({ roomTypeId: type.id, plannedStart: start.toISOString(), plannedEnd: new Date(start.getTime() + duration * 60000).toISOString(), pricingMode: form.pricingMode, ...(form.pricingMode === 'COMBO' ? { comboId: form.comboId } : {}) })
      .then((result) => { if (active) { setQuote(result.quote); if (!result.rooms.some((item) => item.id === room.id)) setQuoteError('Phòng không còn trống trong khoảng thời gian này. Vui lòng chọn thời lượng khác hoặc kiểm tra lịch phòng.'); } })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setQuoteError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [type, room.id, duration, form.pricingMode, form.comboId]);

  const change = (event) => { const { name, value } = event.target; setForm((current) => ({ ...current, [name]: value })); if (!uncertain) setError(''); };
  const submit = async (event) => {
    event.preventDefault();
    const count = Number(form.guestCount);
    // BR-128, MSG54: số khách không vượt quá sức chứa phòng.
    if (!Number.isInteger(count) || count < 1) return setError('Số lượng khách phải là số nguyên lớn hơn 0.');
    if (count > room.capacity) return setError(`Phòng ${room.code} chỉ phù hợp tối đa ${room.capacity} khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.`);
    if (!duration || !quote || quoteError || loading || busy || uncertain || !giftsReady) return;
    setBusy(true); setError('');
    try {
      const result = await bookingApi.openWalkIn({ roomId: room.id, pricingMode: form.pricingMode, comboId: form.pricingMode === 'COMBO' ? form.comboId : null, durationMinutes: duration, contactName: form.contactName.trim(), contactPhone: form.contactPhone.trim(), guestCount: count, note: form.note.trim() || null, drinkItems: drinkSelections.map((menuItemId) => ({ menuItemId, quantity: 1 })), foodItems: foodSelections.map((menuItemId) => ({ menuItemId, quantity: 1 })) });
      onOpened(result.booking);
    } catch (requestError) {
      if (requestError.walkInBeforeSubmit) {
        const errors = apiErrors(requestError);
        setError(requestError.response?.status < 500 ? errors.form || Object.values(errors)[0] : 'Chưa gửi yêu cầu mở phòng vì không thể chuẩn bị kết nối. Vui lòng kiểm tra máy chủ hoặc kết nối và thử lại.');
      } else if (!requestError.response || requestError.response.status >= 500) {
        setUncertain(true);
        const reason = requestError.response ? `Máy chủ báo lỗi (${requestError.response.status}).` : 'Mất kết nối khi gửi yêu cầu.';
        setError(`${reason} Chưa xác nhận được kết quả mở phòng. Hãy đóng hộp thoại và kiểm tra trạng thái phòng hoặc booking trước khi thử lại.`);
      }
      else { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    } finally { setBusy(false); }
  };

  return <dialog ref={dialog} className="walk-in-dialog" aria-labelledby="walk-in-title" onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}>
    <form onSubmit={submit}>
      <header className="walk-in-header"><div><h2 id="walk-in-title">Nhận khách tại quầy</h2><p>{room.code} · {room.name} · tối đa {room.capacity} khách</p></div><button type="button" className="walk-in-close" aria-label="Đóng" disabled={busy} onClick={onClose}>×</button></header>
      <div className="walk-in-fields">
        <label>Họ tên khách <span>*</span><input autoFocus name="contactName" value={form.contactName} onChange={change} maxLength="200" autoComplete="name" required /></label>
        <label>Số điện thoại <span>*</span><input name="contactPhone" type="tel" value={form.contactPhone} onChange={change} pattern="\+?[0-9]+" maxLength="30" title="Chỉ gồm chữ số và có thể có dấu + ở đầu" autoComplete="tel" required /></label>
        <label>Số khách <span>*</span><input name="guestCount" type="number" min="1" max={room.capacity} step="1" value={form.guestCount} onChange={change} required /></label>
        <label>Hình thức sử dụng<select name="pricingMode" value={form.pricingMode} onChange={change}><option value="HOURLY">Tính giờ</option>{combos.length > 0 && <option value="COMBO">Combo</option>}</select></label>
        {form.pricingMode === 'HOURLY' ? <label>Thời lượng (giờ)<input name="durationHours" type="number" min="1" max="24" step="0.5" value={form.durationHours} onChange={change} required /></label> : <label>Gói dịch vụ<select name="comboId" value={form.comboId} onChange={change}>{combos.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.durationMinutes} phút · {formatVnd(item.price)}</option>)}</select></label>}
        {giftRequired && <div className="walk-in-gifts"><strong>Quà tặng theo combo</strong>{giftLoading && <small>Đang tải món F&amp;B…</small>}{giftError && <small role="alert">{giftError}</small>}{!giftLoading && !giftError && !giftMenu.length && <small role="alert">Cơ sở chưa có món quà combo khả dụng.</small>}{[['Đồ uống', drinkSelections, setDrinkSelections], ['Đồ ăn', foodSelections, setFoodSelections]].map(([label, selections, setSelections]) => selections.map((selectedId, index) => <label key={`${label}-${index}`}>{label} {index + 1}<select required value={selectedId} onChange={(event) => setSelections((current) => current.map((id, i) => i === index ? event.target.value : id))}><option value="">Chọn {label.toLowerCase()}</option>{giftMenu.map((item) => <option key={item.id} value={item.id} disabled={Number(item.stockQuantity || 0) <= [...drinkSelections, ...foodSelections].filter((id) => id === item.id).length - (selectedId === item.id ? 1 : 0)}>{item.name} · tồn {item.stockQuantity} {item.unitName}</option>)}</select></label>))}</div>}
        <label className="walk-in-note">Ghi chú <textarea name="note" value={form.note} onChange={change} maxLength="2000" rows="2" placeholder="Nhập ghi chú (nếu có)" /></label>
      </div>
      <div className="walk-in-summary"><span>Giá dự kiến <small>Bắt đầu khi xác nhận · giá cuối có thể thay đổi</small></span><strong>{loading ? 'Đang tính…' : quote ? formatVnd(quote.expectedAmount) : '—'}</strong></div>
      {(error || quoteError) && <p className="walk-in-error" role="alert">{error || quoteError}</p>}
      <div className="walk-in-actions"><button type="button" disabled={busy} onClick={onClose}>Quay lại</button><button type="submit" disabled={busy || uncertain || loading || !quote || Boolean(quoteError) || !duration || !giftsReady}>{busy ? 'Đang mở phòng…' : 'Check-in & mở phòng'}</button></div>
    </form>
  </dialog>;
}
