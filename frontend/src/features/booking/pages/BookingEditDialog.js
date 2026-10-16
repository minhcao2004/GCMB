import { useEffect, useMemo, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import TimeInput from '../components/TimeInput';
import { formatVnd, isValidTimeInput } from '../data/bookingData';

// Tách thời điểm ISO thành ngày/giờ nhập theo múi giờ chi nhánh.
function partsInZone(value, timezone) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(value));
  const fields = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
  return { date: `${fields.year}-${fields.month}-${fields.day}`, time: `${fields.hour}:${fields.minute}` };
}

// Đổi ngày/giờ tại chi nhánh về ISO, kể cả sai lệch múi giờ.
function instantInZone(date, time, timezone) {
  if (!date || !isValidTimeInput(time)) return null;
  const [year, month, day] = date.split('-').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  if (![year, month, day, hour, minute].every(Number.isFinite)) return null;
  const target = Date.UTC(year, month - 1, day, hour, minute);
  const fields = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(target)).filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
  const represented = Date.UTC(Number(fields.year), Number(fields.month) - 1, Number(fields.day), Number(fields.hour), Number(fields.minute), Number(fields.second));
  return new Date(target - (represented - target)).toISOString();
}

// Nạp booking và phòng trống để sửa thông tin trước giờ bắt đầu.
export default function BookingEditDialog({ bookingId, timezone, onClose, onUpdate }) {
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [combos, setCombos] = useState([]);
  const [quote, setQuote] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(true);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [pageError, setPageError] = useState('');
  const [roomError, setRoomError] = useState('');
  const [formError, setFormError] = useState('');

  useEffect(() => {
    if (!bookingId) return undefined;
    let active = true;
    setLoadingDetail(true);
    setPageError('');
    bookingApi.detail(bookingId).then((value) => {
      if (!active) return;
      const booking = value.booking;
      const start = partsInZone(booking.plannedStart, timezone);
      const end = partsInZone(booking.plannedEnd, timezone);
      const activeCombo = (value.charges || []).find((charge) => charge.status === 'ACTIVE' && charge.comboId);
      setDetail(value);
      setForm({
        contactName: booking.contactName || '',
        contactPhone: booking.contactPhone || '',
        note: value.note || '',
        startDate: start.date,
        startTime: start.time,
        endDate: end.date,
        endTime: end.time,
        roomId: booking.roomId || '',
        pricingMode: activeCombo ? 'COMBO' : 'HOURLY',
        comboId: activeCombo?.comboId || '',
        durationHours: String((new Date(booking.plannedEnd) - new Date(booking.plannedStart)) / 3600000),
      });
    }).catch((error) => {
      if (active) {
        const errors = apiErrors(error);
        setPageError(errors.form || Object.values(errors)[0] || 'Không tải được booking.');
      }
    }).finally(() => { if (active) setLoadingDetail(false); });
    return () => { active = false; };
  }, [bookingId, timezone]);

  const plannedStart = useMemo(() => form ? instantInZone(form.startDate, form.startTime, timezone) : null, [form, timezone]);
  const plannedEnd = useMemo(() => form ? instantInZone(form.endDate, form.endTime, timezone) : null, [form, timezone]);
  const durationMinutes = plannedStart && plannedEnd ? (new Date(plannedEnd) - new Date(plannedStart)) / 60000 : 0;
  const hourlyDurationValid = /^(?:[1-9]|1\d|2[0-4])$/.test(String(form?.durationHours || ''));
  const periodValid = Boolean(plannedStart && plannedEnd && new Date(plannedStart) > new Date()
    && Number.isInteger(durationMinutes) && durationMinutes >= 1 && durationMinutes <= 1440
    && (form?.pricingMode !== 'HOURLY' || (hourlyDurationValid && durationMinutes === Number(form.durationHours) * 60)));
  const formReady = Boolean(form?.startDate && form?.startTime && form?.endDate && form?.endTime);
  const booking = detail?.booking;
  const currentCombo = detail?.charges?.find((charge) => charge.status === 'ACTIVE' && charge.comboId);

  useEffect(() => {
    if (!bookingId || !formReady || !periodValid) {
      setLoadingRooms(false);
      setRooms([]);
      setQuote(null);
      setRoomError(formReady && !periodValid ? form?.pricingMode === 'HOURLY'
        ? 'Thời lượng tính giờ phải là số giờ nguyên từ 1 đến 24; giờ nhận phải ở tương lai.'
        : 'Giờ nhận phải ở tương lai; thời lượng từ 1 phút đến 24 giờ và giờ trả phải sau giờ nhận.' : '');
      return undefined;
    }
    let active = true;
    setLoadingRooms(true);
    setRooms([]);
    setQuote(null);
    setRoomError('');
    const timer = window.setTimeout(() => {
      bookingApi.editOptions(bookingId, { plannedStart, plannedEnd, pricingMode: form.pricingMode,
        ...(form.pricingMode === 'COMBO' && form.comboId ? { comboId: form.comboId } : {}) }).then((value) => {
        if (active) {
          setRooms(value.rooms || []);
          setCombos(value.combos || []);
          setQuote(value.quote || null);
        }
      }).catch((error) => {
        if (active) {
          const errors = apiErrors(error);
          setRooms([]);
          setRoomError(errors.form || Object.values(errors)[0] || 'Không tải được danh sách phòng trống.');
        }
      }).finally(() => { if (active) setLoadingRooms(false); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [bookingId, formReady, form?.startDate, form?.startTime, form?.endDate, form?.endTime, form?.pricingMode, form?.comboId, plannedStart, plannedEnd, periodValid]);

  // Cập nhật trường vừa sửa và xóa lỗi biểu mẫu cũ.
  const change = (event) => {
    const { name, value } = event.target;
    setForm((current) => {
      const next = { ...current, [name]: value };
      if (next.pricingMode === 'HOURLY' && (name === 'durationHours' || name === 'startDate' || name === 'startTime')
          && /^(?:[1-9]|1\d|2[0-4])$/.test(String(next.durationHours))) {
        const start = instantInZone(next.startDate, next.startTime, timezone);
        if (start) {
          const end = partsInZone(new Date(new Date(start).getTime() + Number(next.durationHours) * 3600000), timezone);
          next.endDate = end.date;
          next.endTime = end.time;
        }
      }
      return next;
    });
    setFormError('');
  };

  // Xác thực thời gian/phòng rồi gửi phần thay đổi tới backend.
  const save = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!form?.contactName.trim()) return setFormError('Vui lòng nhập tên khách.');
    if (!periodValid) return setFormError(
      !isValidTimeInput(form?.startTime) || !isValidTimeInput(form?.endTime)
        ? 'Nhập giờ nhận và giờ trả theo định dạng 24 giờ, ví dụ 14:30.'
        : 'Kiểm tra lại thời gian booking.',
    );
    if (form.pricingMode === 'COMBO' && !form.comboId) return setFormError('Vui lòng chọn combo.');
    if (!quote) return setFormError('Chưa có báo giá cho thời gian đã chọn.');
    if (!form.roomId) return setFormError('Vui lòng chọn phòng.');
    const selectedRoom = rooms.find((room) => room.id === form.roomId);
    if (!selectedRoom) return setFormError('Phòng đã chọn không còn trống trong khung giờ này.');
    if (selectedRoom && selectedRoom.capacity < Number(booking.guestCount)) {
      return setFormError(`Phòng này nhận tối đa ${selectedRoom.capacity} khách, booking đang có ${booking.guestCount} khách.`);
    }

    setSubmitting(true);
    const result = await onUpdate(bookingId, {
      roomId: form.roomId,
      plannedStart,
      plannedEnd,
      pricingMode: form.pricingMode,
      comboId: form.pricingMode === 'COMBO' ? form.comboId : null,
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim() || null,
      note: form.note.trim() || null,
    });
    setSubmitting(false);
    if (result?.error) setFormError(result.error);
  };

  const canEdit = booking?.status === 'CONFIRMED' && new Date(booking.plannedStart) > new Date();
  const hasRoomOption = rooms.some((room) => room.id === form?.roomId);
  const displayedCombos = currentCombo && !combos.some((combo) => combo.id === currentCombo.comboId)
    ? [{ id: currentCombo.comboId, name: currentCombo.description, durationMinutes: currentCombo.pricingSnapshot?.durationMinutes, price: currentCombo.unitPrice }, ...combos]
    : combos;

  const changeMode = (pricingMode) => {
    setForm((current) => {
      const next = { ...current, pricingMode };
      const start = instantInZone(current.startDate, current.startTime, timezone);
      if (pricingMode === 'HOURLY') {
        const hours = /^(?:[1-9]|1\d|2[0-4])$/.test(String(current.durationHours)) ? current.durationHours : '2';
        next.durationHours = hours;
        next.comboId = '';
        if (start) {
          const end = partsInZone(new Date(new Date(start).getTime() + Number(hours) * 3600000), timezone);
          next.endDate = end.date;
          next.endTime = end.time;
        }
      } else {
        const selected = displayedCombos.find((combo) => combo.id === current.comboId) || displayedCombos[0];
        next.comboId = selected?.id || '';
        if (start && selected?.durationMinutes) {
          const end = partsInZone(new Date(new Date(start).getTime() + Number(selected.durationMinutes) * 60000), timezone);
          next.endDate = end.date;
          next.endTime = end.time;
        }
      }
      return next;
    });
    setFormError('');
  };

  const changeCombo = (event) => {
    const comboId = event.target.value;
    const selected = displayedCombos.find((combo) => combo.id === comboId);
    setForm((current) => {
      const start = instantInZone(current.startDate, current.startTime, timezone);
      const end = start && selected?.durationMinutes
        ? partsInZone(new Date(new Date(start).getTime() + Number(selected.durationMinutes) * 60000), timezone) : null;
      return { ...current, comboId, ...(end ? { endDate: end.date, endTime: end.time } : {}) };
    });
    setFormError('');
  };

  return <div className="booking-cancel-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !submitting) onClose(); }}>
    <section className="booking-cancel-dialog booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-edit-title">
      <div className="booking-cancel-title"><div><h2 id="booking-edit-title">Sửa booking</h2><p>{booking?.code || 'Đang tải booking'}{booking?.contactName ? ` · ${booking.contactName}` : ''}</p></div><button type="button" className="booking-panel-close" onClick={onClose} disabled={submitting} aria-label="Đóng">×</button></div>
      {loadingDetail ? <p role="status">Đang tải thông tin booking…</p> : pageError ? <p className="booking-inline-error" role="alert">{pageError}</p> : !canEdit ? <p className="booking-inline-error" role="alert">Chỉ có thể sửa booking đã xác nhận và chưa đến giờ sử dụng.</p> : <form onSubmit={save}>
        <h3 className="booking-cancel-section-title">Thông tin booking</h3>
        <div className="booking-form-grid booking-customer-grid">
          <label className="booking-form-field"><span>Tên khách *</span><input name="contactName" maxLength="200" value={form.contactName} onChange={change} /></label>
          <label className="booking-form-field"><span>Số điện thoại</span><input name="contactPhone" maxLength="30" value={form.contactPhone} onChange={change} /></label>
        </div>
        <h3 className="booking-cancel-section-title booking-edit-section-title">Thời gian sử dụng</h3>
        <div className="booking-form-grid booking-schedule-grid">
          <label className="booking-form-field"><span>Ngày nhận phòng</span><input type="date" name="startDate" value={form.startDate} onChange={change} /></label>
          <label className="booking-form-field"><span>Giờ nhận phòng</span><TimeInput name="startTime" value={form.startTime} onChange={change} aria-invalid={!isValidTimeInput(form.startTime)} aria-describedby="booking-edit-start-time-hint" /><small id="booking-edit-start-time-hint" className="booking-field-hint">Định dạng 24 giờ, ví dụ 14:30.</small></label>
          <label className="booking-form-field"><span>Ngày trả phòng</span><input type="date" name="endDate" value={form.endDate} onChange={change} readOnly={form.pricingMode === 'HOURLY'} /></label>
          <label className="booking-form-field"><span>Giờ trả phòng</span><TimeInput name="endTime" value={form.endTime} onChange={change} readOnly={form.pricingMode === 'HOURLY'} aria-invalid={!isValidTimeInput(form.endTime)} aria-describedby="booking-edit-end-time-hint" /><small id="booking-edit-end-time-hint" className="booking-field-hint">{form.pricingMode === 'HOURLY' ? 'Tự động tính theo thời lượng thuê.' : 'Định dạng 24 giờ, ví dụ 14:30.'}</small></label>
        </div>
        <div className="booking-pricing-mode booking-edit-pricing-mode" role="group" aria-label="Hình thức tính giá">
          <button type="button" className={form.pricingMode === 'HOURLY' ? 'is-selected' : ''} onClick={() => changeMode('HOURLY')}>Tính giờ</button>
          <button type="button" className={form.pricingMode === 'COMBO' ? 'is-selected' : ''} onClick={() => changeMode('COMBO')}>Combo</button>
        </div>
        {form.pricingMode === 'HOURLY' ? <label className="booking-form-field booking-edit-combo"><span>Thời lượng thuê (giờ)</span>
          <input type="number" name="durationHours" min="1" max="24" step="1" inputMode="numeric" value={form.durationHours} onChange={change} aria-invalid={!hourlyDurationValid} />
          <small className={!hourlyDurationValid ? 'booking-inline-error' : ''}>Nhập số giờ nguyên từ 1 đến 24. Giờ trả được tính tự động.</small>
        </label> : <label className="booking-form-field booking-edit-combo"><span>Combo sử dụng</span>
          <select name="comboId" value={form.comboId} onChange={changeCombo}>
            {!form.comboId && <option value="">Chọn combo</option>}
            {displayedCombos.map((combo) => <option key={combo.id} value={combo.id}>{combo.name} · {combo.durationMinutes} phút · {formatVnd(combo.price)}</option>)}
          </select>
          <small>Đổi combo sẽ đặt lại giờ trả theo thời lượng gói. Có thể chỉnh giờ trả sau đó; thời gian vượt gói được tính phụ phí.</small>
        </label>}
        <div className="booking-section-title-row booking-edit-section-title"><h3>Phòng</h3><span>{loadingRooms ? 'Đang kiểm tra phòng…' : `${rooms.length} phòng còn trống`}</span></div>
        <label className="booking-form-field">
          <span>Phòng sử dụng</span>
          <select name="roomId" value={form.roomId} onChange={change}>
            {!hasRoomOption && form.roomId && <option value={form.roomId}>{booking.roomCode || booking.roomName || 'Phòng hiện tại'} · hiện không có trong danh sách trống</option>}
            {rooms.map((room) => <option key={room.id} value={room.id}>{room.code} · {room.name} · tối đa {room.capacity} khách</option>)}
            {!form.roomId && <option value="">Chọn phòng</option>}
          </select>
        </label>
        {roomError && <p className="booking-inline-error" role="alert">{roomError}</p>}
        <label className="booking-form-field booking-note-field"><span>Ghi chú booking</span><textarea name="note" rows="3" maxLength="2000" value={form.note} onChange={change} placeholder="Thông tin khách cần lưu ý" /></label>
        <div className="booking-cancel-deposit-summary booking-edit-summary"><span>Giá hiện tại: <strong>{formatVnd(booking.expectedAmount)}</strong></span><span>Giá sau sửa: <strong>{quote ? formatVnd(quote.expectedAmount) : 'Đang tính…'}</strong></span><span>Tiền cọc đã ghi: <strong>{formatVnd(detail?.deposit?.balance ?? booking.depositBalance)}</strong></span></div>
        {formError && <p className="booking-inline-error" role="alert">{formError}</p>}
        <div className="booking-cancel-buttons"><button className="booking-button booking-button-primary" type="submit" disabled={submitting || loadingRooms || !periodValid || !quote || !hasRoomOption || Boolean(roomError)}>{submitting ? 'Đang lưu…' : 'Lưu thay đổi'}</button><button className="booking-button booking-button-secondary" type="button" onClick={onClose} disabled={submitting}>Quay lại</button></div>
      </form>}
      {!loadingDetail && (pageError || !canEdit) && <div className="booking-cancel-buttons"><button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button></div>}
    </section>
  </div>;
}
