import { useEffect, useMemo, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { formatVnd } from '../data/bookingData';

function partsInZone(value, timezone) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(value));
  const fields = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
  return { date: `${fields.year}-${fields.month}-${fields.day}`, time: `${fields.hour}:${fields.minute}` };
}

function instantInZone(date, time, timezone) {
  if (!date || !time) return null;
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

export default function BookingEditDialog({ bookingId, timezone, onClose, onUpdate }) {
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(null);
  const [rooms, setRooms] = useState([]);
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
  const periodValid = Boolean(plannedStart && plannedEnd && new Date(plannedStart) > new Date() && new Date(plannedEnd) > new Date(plannedStart));
  const formReady = Boolean(form?.startDate && form?.startTime && form?.endDate && form?.endTime);
  const booking = detail?.booking;

  useEffect(() => {
    if (!bookingId || !formReady || !periodValid) {
      setLoadingRooms(false);
      setRooms([]);
      setRoomError(formReady && !periodValid ? 'Thời gian phải hợp lệ, ở tương lai và giờ kết thúc sau giờ bắt đầu.' : '');
      return undefined;
    }
    let active = true;
    setLoadingRooms(true);
    setRooms([]);
    setRoomError('');
    const timer = window.setTimeout(() => {
      bookingApi.editOptions(bookingId, { plannedStart, plannedEnd }).then((value) => {
        if (active) setRooms(value.rooms || []);
      }).catch((error) => {
        if (active) {
          const errors = apiErrors(error);
          setRooms([]);
          setRoomError(errors.form || Object.values(errors)[0] || 'Không tải được danh sách phòng trống.');
        }
      }).finally(() => { if (active) setLoadingRooms(false); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [bookingId, formReady, form?.startDate, form?.startTime, form?.endDate, form?.endTime, plannedStart, plannedEnd, periodValid]);

  const change = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
    setFormError('');
  };

  const save = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!form?.contactName.trim()) return setFormError('Vui lòng nhập tên khách.');
    if (!periodValid) return setFormError('Kiểm tra lại thời gian booking.');
    if (!form.roomId) return setFormError('Vui lòng chọn phòng.');
    const selectedRoom = rooms.find((room) => room.id === form.roomId);
    if (selectedRoom && selectedRoom.capacity < Number(booking.guestCount)) {
      return setFormError(`Phòng này nhận tối đa ${selectedRoom.capacity} khách, booking đang có ${booking.guestCount} khách.`);
    }

    setSubmitting(true);
    const result = await onUpdate(bookingId, {
      roomId: form.roomId,
      plannedStart,
      plannedEnd,
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim() || null,
      note: form.note.trim() || null,
    });
    setSubmitting(false);
    if (result?.error) setFormError(result.error);
  };

  const canEdit = booking?.status === 'CONFIRMED' && new Date(booking.plannedStart) > new Date();
  const hasRoomOption = rooms.some((room) => room.id === form?.roomId);

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
          <label className="booking-form-field"><span>Giờ nhận phòng</span><input type="time" name="startTime" value={form.startTime} onChange={change} /></label>
          <label className="booking-form-field"><span>Ngày trả phòng</span><input type="date" name="endDate" value={form.endDate} onChange={change} /></label>
          <label className="booking-form-field"><span>Giờ trả phòng</span><input type="time" name="endTime" value={form.endTime} onChange={change} /></label>
        </div>
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
        <div className="booking-cancel-deposit-summary booking-edit-summary"><span>Giá đã chốt: <strong>{formatVnd(booking.expectedAmount)}</strong></span><span>Tiền cọc đã ghi: <strong>{formatVnd(detail?.deposit?.balance ?? booking.depositBalance)}</strong></span></div>
        {formError && <p className="booking-inline-error" role="alert">{formError}</p>}
        <div className="booking-cancel-buttons"><button className="booking-button booking-button-primary" type="submit" disabled={submitting || loadingRooms || !periodValid || Boolean(roomError)}>{submitting ? 'Đang lưu…' : 'Lưu thay đổi'}</button><button className="booking-button booking-button-secondary" type="button" onClick={onClose} disabled={submitting}>Quay lại</button></div>
      </form>}
      {!loadingDetail && (pageError || !canEdit) && <div className="booking-cancel-buttons"><button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button></div>}
    </section>
  </div>;
}
