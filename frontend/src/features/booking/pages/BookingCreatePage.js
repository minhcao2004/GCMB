import { useEffect, useMemo, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { formatDate, formatVnd } from '../data/bookingData';

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

function dateTimeIso(date, time) {
  return new Date(`${date}T${time}:00`).toISOString();
}

function Field({ label, children, className = '' }) {
  return <label className={`booking-form-field ${className}`}><span>{label}</span>{children}</label>;
}

export default function BookingCreatePage({ options, optionsLoading, optionsError, onBack, onCreate }) {
  const now = new Date();
  now.setMinutes(Math.ceil((now.getMinutes() + 30) / 15) * 15, 0, 0);
  const initialDate = localDate(now);
  const initialTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const [form, setForm] = useState({
    contactName: '', contactPhone: '', channel: 'PHONE', date: initialDate, time: initialTime,
    guestCount: '2', roomTypeId: '', roomId: '', pricingMode: 'COMBO', comboId: '',
    durationHours: '2', note: '', depositEnabled: true, depositAmount: '200000',
    paymentMethod: 'BANK_TRANSFER', transaction: '',
  });
  const [availability, setAvailability] = useState(null);
  const [availabilityError, setAvailabilityError] = useState('');
  const [loadingAvailability, setLoadingAvailability] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  const roomTypes = options?.roomTypes || [];
  const selectedType = roomTypes.find((item) => item.id === form.roomTypeId) || roomTypes[0] || null;
  const combos = selectedType?.combos || [];
  const selectedCombo = combos.find((item) => item.id === form.comboId) || combos[0] || null;
  const durationMinutes = form.pricingMode === 'COMBO' ? Number(selectedCombo?.durationMinutes || 120) : Number(form.durationHours) * 60;
  const plannedStart = useMemo(() => {
    if (!form.date || !form.time) return null;
    const date = new Date(`${form.date}T${form.time}:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }, [form.date, form.time]);
  const plannedEnd = useMemo(() => {
    if (!plannedStart || !durationMinutes) return null;
    return new Date(plannedStart.getTime() + durationMinutes * 60000);
  }, [plannedStart, durationMinutes]);
  const availableRooms = availability?.rooms || [];
  const chosenRoom = availableRooms.find((room) => room.id === form.roomId) || null;
  const quote = availability?.quote;
  const estimatedAmount = Number(quote?.expectedAmount || selectedCombo?.price || 0);
  const depositAmount = form.depositEnabled ? Number(form.depositAmount || 0) : 0;

  useEffect(() => {
    if (!form.roomTypeId && roomTypes[0]) setForm((current) => ({ ...current, roomTypeId: roomTypes[0].id }));
  }, [form.roomTypeId, roomTypes]);

  useEffect(() => {
    if (!selectedType) return;
    if (!selectedType.combos?.some((item) => item.id === form.comboId)) {
      setForm((current) => ({ ...current, comboId: selectedType.combos?.[0]?.id || '' }));
    }
  }, [selectedType, form.comboId]);

  useEffect(() => {
    if (!selectedType || !plannedStart || !plannedEnd || plannedStart <= new Date() || plannedEnd <= plannedStart) {
      setAvailability(null);
      setAvailabilityError(plannedStart && plannedStart <= new Date() ? 'Thời gian nhận phòng cần ở tương lai.' : '');
      return undefined;
    }
    let active = true;
    const params = {
      roomTypeId: selectedType.id,
      plannedStart: plannedStart.toISOString(),
      plannedEnd: plannedEnd.toISOString(),
      pricingMode: form.pricingMode,
      ...(form.pricingMode === 'COMBO' && selectedCombo ? { comboId: selectedCombo.id } : {}),
    };
    setLoadingAvailability(true);
    setAvailabilityError('');
    bookingApi.availability(params).then((result) => {
      if (active) setAvailability(result);
    }).catch((error) => {
      if (active) {
        setAvailability(null);
        const errors = apiErrors(error);
        setAvailabilityError(errors.form || Object.values(errors)[0]);
      }
    }).finally(() => { if (active) setLoadingAvailability(false); });
    return () => { active = false; };
  }, [selectedType, selectedCombo, plannedStart, plannedEnd, form.pricingMode]);

  useEffect(() => {
    if (availableRooms.length && !availableRooms.some((room) => room.id === form.roomId)) {
      setForm((current) => ({ ...current, roomId: availableRooms[0].id }));
    }
    if (!availableRooms.length && form.roomId) setForm((current) => ({ ...current, roomId: '' }));
  }, [availableRooms, form.roomId]);

  useEffect(() => {
    if (quote?.depositRequired && Number(quote.depositRequired) > 0) {
      setForm((current) => ({ ...current, depositEnabled: true, depositAmount: String(quote.depositRequired) }));
    }
  }, [quote?.depositRequired]);

  const change = (event) => {
    const { name, value, checked, type } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
    setFormError('');
  };

  const submit = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!form.contactName.trim()) return setFormError('Vui lòng nhập họ tên khách.');
    if (!form.contactPhone.trim()) return setFormError('Vui lòng nhập số điện thoại.');
    if (!plannedStart || !plannedEnd) return setFormError('Vui lòng chọn thời gian nhận phòng hợp lệ.');
    if (!chosenRoom) return setFormError('Chọn một phòng đang trống trong khung giờ này.');
    if (Number(form.guestCount) < 1 || Number(form.guestCount) > chosenRoom.capacity) return setFormError(`Phòng này nhận tối đa ${chosenRoom.capacity} khách.`);
    if (form.depositEnabled && (!Number.isFinite(depositAmount) || depositAmount < 0)) return setFormError('Kiểm tra lại số tiền cọc.');

    setSubmitting(true);
    const result = await onCreate({
      roomId: chosenRoom.id,
      plannedStart: dateTimeIso(form.date, form.time),
      plannedEnd: plannedEnd.toISOString(),
      pricingMode: form.pricingMode,
      comboId: form.pricingMode === 'COMBO' ? selectedCombo?.id : null,
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      channel: form.channel,
      note: form.note.trim() || null,
    }, { depositAmount, depositEnabled: form.depositEnabled, guestCount: Number(form.guestCount) });
    setSubmitting(false);
    if (result?.error) setFormError(result.error);
  };

  return <div className="booking-create-page">
    <div className="booking-page-heading">
      <div><h1>Tạo booking</h1><p>Đặt phòng qua điện thoại, Facebook hoặc Zalo</p></div>
      <button type="button" className="booking-button booking-button-secondary" onClick={onBack}>← Quay lại</button>
    </div>
    {(optionsError || formError) && <div className="booking-inline-error" role="alert">{formError || optionsError}</div>}
    {optionsLoading ? <div className="booking-empty-state">Đang tải hạng phòng và gói dịch vụ…</div> : <form className="booking-create-layout" onSubmit={submit}>
      <section className="booking-create-card">
        <header className="booking-card-heading"><h2>Thông tin khách &amp; lịch đặt</h2><p>Các trường có dấu * là bắt buộc</p></header>

        <div className="booking-form-grid booking-customer-grid">
          <Field label="Họ tên khách *"><input name="contactName" value={form.contactName} onChange={change} autoComplete="name" placeholder="Nhập họ tên khách" required /></Field>
          <Field label="Số điện thoại *"><input name="contactPhone" value={form.contactPhone} onChange={change} inputMode="tel" autoComplete="tel" placeholder="Nhập số điện thoại" required /></Field>
        </div>
        <div className="booking-form-grid booking-schedule-grid">
          <Field label="Ngày nhận phòng *"><input type="date" name="date" value={form.date} onChange={change} required /></Field>
          <Field label="Giờ nhận phòng *"><input type="time" name="time" value={form.time} onChange={change} required /></Field>
          <Field label="Số lượng khách *"><input type="number" name="guestCount" min="1" value={form.guestCount} onChange={change} required /></Field>
          <Field label="Hạng phòng *"><select name="roomTypeId" value={selectedType?.id || ''} onChange={(event) => setForm((current) => ({ ...current, roomTypeId: event.target.value, roomId: '' }))} required><option value="" disabled>Chọn hạng phòng</option>{roomTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></Field>
        </div>

        <div className={`booking-availability-banner${availabilityError ? ' is-error' : ''}`} role="status">
          {loadingAvailability ? 'Đang kiểm tra phòng trống…' : availabilityError || `Còn ${availableRooms.length} phòng ${selectedType?.name || ''} phù hợp lúc ${form.time}, ${formatDate(form.date)}`}
        </div>

        <div className="booking-section-title-row"><h3>Chọn phòng cụ thể</h3><span>Có thể gán sau khi tạo</span></div>
        <div className="booking-room-choice-grid">
          {availableRooms.length ? availableRooms.map((room) => <button key={room.id} type="button" className={`booking-room-choice${form.roomId === room.id ? ' is-selected' : ''}`} onClick={() => setForm((current) => ({ ...current, roomId: room.id }))}>
            <strong>{room.code} · {room.name}</strong><small>{selectedType?.name} · tối đa {room.capacity} khách</small>{form.roomId === room.id && <span className="booking-choice-check">✓</span>}
          </button>) : <div className="booking-room-empty">{loadingAvailability ? 'Đang tải phòng…' : 'Không có phòng trống trong khung giờ đã chọn.'}</div>}
        </div>

        <div className="booking-form-divider" />
        <h3 className="booking-section-heading">Gói dịch vụ</h3>
        <div className="booking-pricing-options">
          <div className="booking-pricing-mode" role="group" aria-label="Hình thức tính giá">
            <button type="button" className={form.pricingMode === 'HOURLY' ? 'is-selected' : ''} onClick={() => setForm((current) => ({ ...current, pricingMode: 'HOURLY' }))}>Tính giờ</button>
            <button type="button" className={form.pricingMode === 'COMBO' ? 'is-selected' : ''} onClick={() => setForm((current) => ({ ...current, pricingMode: 'COMBO' }))}>Combo</button>
          </div>
          {form.pricingMode === 'COMBO' ? <div className="booking-package-grid">
            {combos.map((combo) => <button key={combo.id} type="button" className={`booking-package-choice${form.comboId === combo.id ? ' is-selected' : ''}`} onClick={() => setForm((current) => ({ ...current, comboId: combo.id }))}>
              <strong>{combo.name}</strong><b>{formatVnd(combo.price)}</b>
            </button>)}
            {!combos.length && <div className="booking-room-empty">Hạng phòng này chưa có combo đang áp dụng.</div>}
          </div> : <Field label="Thời lượng thuê"><select name="durationHours" value={form.durationHours} onChange={change}><option value="2">2 giờ</option><option value="3">3 giờ</option><option value="4">4 giờ</option><option value="6">6 giờ</option></select></Field>}
        </div>

        <div className="booking-form-divider" />
        <div className="booking-section-title-row booking-deposit-heading"><h3>Tiền cọc</h3><label className="booking-toggle"><span>Có thu cọc</span><input type="checkbox" name="depositEnabled" checked={form.depositEnabled} onChange={change} /><i /></label></div>
        <div className="booking-form-grid booking-deposit-grid">
          <Field label="Số tiền cọc"><input name="depositAmount" inputMode="numeric" value={form.depositAmount} onChange={change} disabled={!form.depositEnabled} placeholder="0đ" /></Field>
          <Field label="Phương thức"><select name="paymentMethod" value={form.paymentMethod} onChange={change} disabled={!form.depositEnabled}><option value="BANK_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option><option value="CARD">Thẻ</option><option value="QR">QR</option></select></Field>
          <Field label="Mã giao dịch / ghi chú"><input name="transaction" value={form.transaction} onChange={change} disabled={!form.depositEnabled} placeholder="Ví dụ: CK-245781" /></Field>
        </div>
        <p className="booking-feature-note">API booking hiện tại chưa lưu số khách hoặc ghi sổ khoản cọc; các trường này chỉ dùng để đối chiếu tại quầy.</p>
        <Field label="Ghi chú booking" className="booking-note-field"><textarea name="note" rows="2" value={form.note} onChange={change} placeholder="Thông tin khách cần lưu ý" /></Field>
      </section>

      <aside className="booking-summary-card">
        <h2>Tóm tắt booking</h2><p>Kiểm tra trước khi giữ phòng</p>
        <div className="booking-summary-separator" />
        <dl className="booking-summary-list">
          <div><dt>Ngày giờ</dt><dd>{form.time} · {formatDate(form.date)}</dd></div>
          <div><dt>Khách</dt><dd>{form.contactName || '—'}</dd></div>
          <div><dt>Số khách</dt><dd>{form.guestCount} người</dd></div>
          <div><dt>Phòng</dt><dd>{chosenRoom ? `${chosenRoom.code} · ${chosenRoom.name}` : 'Chưa chọn'}</dd></div>
          <div><dt>Hạng phòng</dt><dd>{selectedType?.name || '—'}</dd></div>
          <div><dt>Gói</dt><dd>{form.pricingMode === 'COMBO' ? selectedCombo?.name || 'Chưa chọn combo' : `Tính giờ · ${form.durationHours} giờ`}</dd></div>
        </dl>
        <div className="booking-summary-separator" />
        <div className="booking-summary-money"><span>Giá dự kiến</span><strong>{formatVnd(estimatedAmount)}</strong></div>
        <div className="booking-summary-money booking-deposit-money"><span>Tiền cọc dự kiến</span><strong>{form.depositEnabled ? `−${formatVnd(depositAmount)}` : formatVnd(0)}</strong></div>
        <div className="booking-summary-total"><strong>Còn lại dự kiến</strong><b>{formatVnd(Math.max(estimatedAmount - depositAmount, 0))}</b></div>
        <div className="booking-summary-hint">Booking giữ phòng theo ngày/giờ đã chọn.<br />Giá cuối có thể thay đổi khi checkout.</div>
        <button className="booking-button booking-button-primary booking-submit-button" type="submit" disabled={submitting || loadingAvailability || !chosenRoom}>{submitting ? 'Đang tạo booking…' : 'Tạo booking'}</button>
        <button className="booking-summary-cancel" type="button" onClick={onBack}>Hủy</button>
      </aside>
    </form>}
  </div>;
}
