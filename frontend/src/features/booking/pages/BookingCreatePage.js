import { useEffect, useMemo, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { clearBookingDraft, readBookingDraft, saveBookingDraft } from '../services/bookingDraft';
import TimeInput from '../components/TimeInput';
import { formatDate, formatVnd, isValidTimeInput } from '../data/bookingData';

// Định dạng ngày nhập của biểu mẫu theo giờ máy người dùng.
function localDate(value = new Date()) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

// Chuyển ngày và giờ nhập trên máy thành mốc ISO gửi backend.
function dateTimeIso(date, time) {
  return new Date(`${date}T${time}:00`).toISOString();
}

// Bọc nhãn và trường nhập thống nhất trong biểu mẫu.
function Field({ label, children, className = '' }) {
  return <label className={`booking-form-field ${className}`}><span>{label}</span>{children}</label>;
}

// Thu thông tin khách, phòng, giá và xác nhận thu cọc trước khi tạo booking.
export default function BookingCreatePage({ user, options, optionsLoading, optionsError, onBack, onCreate }) {
  const now = new Date();
  now.setMinutes(Math.ceil((now.getMinutes() + 30) / 15) * 15, 0, 0);
  const initialDate = localDate(now);
  const initialTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const savedDraft = useRef(readBookingDraft(user));
  const [form, setForm] = useState(() => ({
    contactName: '', contactPhone: '', channel: 'PHONE', date: initialDate, time: initialTime,
    guestCount: '2', roomTypeId: '', roomId: '', pricingMode: 'COMBO', comboId: '',
    durationHours: '2', note: '', depositEnabled: false, depositAmount: '',
    paymentMethod: 'BANK_TRANSFER', transaction: '',
    ...savedDraft.current?.form,
  }));
  const [availability, setAvailability] = useState(null);
  const [availabilityError, setAvailabilityError] = useState('');
  const [loadingAvailability, setLoadingAvailability] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [touched, setTouched] = useState({});
  const paymentReferenceInput = useRef(null);
  const depositAmountEdited = useRef(Boolean(savedDraft.current?.depositAmountEdited));
  const autoSelectRoom = useRef(!savedDraft.current?.form?.roomId);

  useEffect(() => { saveBookingDraft(user, form, depositAmountEdited.current); }, [user, form]);

  const cancelDraft = () => {
    clearBookingDraft(user);
    onBack();
  };

  const roomTypes = useMemo(() => options?.roomTypes || [], [options?.roomTypes]);
  const selectedType = roomTypes.find((item) => item.id === form.roomTypeId) || roomTypes[0] || null;
  const combos = selectedType?.combos || [];
  const selectedCombo = combos.find((item) => item.id === form.comboId) || combos[0] || null;
  const hourlyDurationValid = /^(?:[1-9]|1\d|2[0-4])$/.test(String(form.durationHours));
  const durationMinutes = form.pricingMode === 'COMBO' ? Number(selectedCombo?.durationMinutes || 120) : hourlyDurationValid ? Number(form.durationHours) * 60 : null;
  const plannedStart = useMemo(() => {
    if (!form.date || !isValidTimeInput(form.time)) return null;
    const date = new Date(`${form.date}T${form.time}:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }, [form.date, form.time]);
  const plannedEnd = useMemo(() => {
    if (!plannedStart || !durationMinutes) return null;
    return new Date(plannedStart.getTime() + durationMinutes * 60000);
  }, [plannedStart, durationMinutes]);
  const availableRooms = useMemo(() => availability?.rooms || [], [availability?.rooms]);
  const chosenRoom = availableRooms.find((room) => room.id === form.roomId) || null;
  const quote = availability?.quote;
  const estimatedAmount = Number(quote?.expectedAmount || selectedCombo?.price || 0);
  const depositAmount = form.depositEnabled ? Number(form.depositAmount || 0) : 0;
  const paymentMethod = String(form.paymentMethod || '').trim().toUpperCase();
  const cashPayment = paymentMethod === 'CASH';
  const guestCount = Number(form.guestCount);
  const guestCountValid = Number.isInteger(guestCount) && guestCount >= 1 && (!chosenRoom || guestCount <= chosenRoom.capacity);
  const validSchedule = Boolean(plannedStart && plannedEnd && plannedStart > new Date() && plannedEnd > plannedStart);
  const pricingSelectionValid = form.pricingMode === 'COMBO' ? Boolean(selectedCombo) : hourlyDurationValid;
  const depositAmountValid = Number.isFinite(depositAmount) && depositAmount > 0;
 const contactPhoneError = !form.contactPhone.trim() ? 'Nhập số điện thoại.'
  : !/^0[0-9]{6,14}$/.test(form.contactPhone.trim())
    ? 'Số điện thoại phải bắt đầu bằng 0 và gồm 7–15 chữ số, không chứa dấu hoặc khoảng trắng.' : '';

const paymentReferenceRequired = form.depositEnabled && depositAmountValid && !cashPayment;
const paymentReferenceValid = !paymentReferenceRequired || Boolean(form.transaction.trim());
  const bookingBlockers = [];
  if (!form.contactName.trim()) bookingBlockers.push('Nhập họ tên khách.');
  if (contactPhoneError) bookingBlockers.push(contactPhoneError);
  if (!selectedType) bookingBlockers.push('Chọn hạng phòng.');
  if (!validSchedule) bookingBlockers.push(plannedStart && plannedStart <= new Date() ? 'Chọn ngày giờ nhận phòng trong tương lai.' : 'Chọn ngày giờ nhận phòng hợp lệ.');
  if (!guestCountValid) bookingBlockers.push(chosenRoom && guestCount > chosenRoom.capacity ? `Giảm số khách còn tối đa ${chosenRoom.capacity} người.` : 'Nhập số khách từ 1 trở lên.');
  if (!pricingSelectionValid) bookingBlockers.push(form.pricingMode === 'COMBO' ? 'Chọn một combo dịch vụ.' : 'Nhập thời lượng thuê từ 1 đến 24 giờ nguyên.');
  if (!loadingAvailability && !chosenRoom) {
    bookingBlockers.push(availabilityError ? 'Không xác nhận được phòng trống; hãy đổi lịch hoặc hạng phòng để thử lại.' : availableRooms.length ? 'Chọn một phòng còn trống.' : 'Đổi thời gian hoặc hạng phòng để tìm phòng trống.');
  }
  if (form.depositEnabled && !depositAmountValid) bookingBlockers.push('Nhập số tiền cọc đã thực sự nhận.');
  if (!paymentReferenceValid) bookingBlockers.push('Nhập mã giao dịch / ghi chú khi thu tiền cọc.');
  const bookingReady = !optionsLoading && !loadingAvailability && !availabilityError && bookingBlockers.length === 0 && Boolean(chosenRoom);
  const showContactNameError = Boolean(touched.contactName && !form.contactName.trim());
  const showContactPhoneError = Boolean((touched.contactPhone || form.contactPhone) && contactPhoneError);
  const showScheduleError = Boolean((touched.date || touched.time) && !validSchedule);
  const showGuestCountError = Boolean(touched.guestCount && !guestCountValid);
  const showRoomTypeError = Boolean(touched.roomTypeId && !selectedType);
  const showRoomError = Boolean(validSchedule && selectedType && !loadingAvailability && !chosenRoom && (touched.roomId || touched.date || touched.time || touched.roomTypeId));
  const showComboError = Boolean(form.pricingMode === 'COMBO' && !selectedCombo && (touched.pricingMode || touched.comboId));
  const showDurationError = Boolean(form.pricingMode === 'HOURLY' && !hourlyDurationValid && touched.durationHours);
  const showDepositAmountError = Boolean(form.depositEnabled && !depositAmountValid && (touched.depositEnabled || touched.depositAmount));
  const showPaymentReferenceError = Boolean(paymentReferenceRequired && !paymentReferenceValid && touched.transaction);
  const paymentReferenceServerError = /mã giao dịch\s*\/\s*ghi chú/i.test(formError) ? formError : '';
  const paymentReferenceFieldError = showPaymentReferenceError || Boolean(paymentReferenceServerError);
  const paymentReferenceFieldErrorText = paymentReferenceServerError || 'Nhập mã giao dịch / ghi chú khi thu tiền cọc.';
  const scheduleErrorMessage = plannedStart && plannedStart <= new Date()
    ? 'Thời gian nhận phòng cần ở tương lai.'
    : 'Vui lòng chọn ngày và giờ nhận phòng hợp lệ.';
  const roomErrorMessage = availabilityError
    ? 'Không xác nhận được phòng trống. Hãy đổi lịch hoặc hạng phòng để thử lại.'
    : availableRooms.length
      ? 'Chọn một phòng còn trống.'
      : 'Không có phòng trống; hãy đổi ngày giờ hoặc hạng phòng.';

  useEffect(() => {
    if (paymentReferenceServerError) {
      paymentReferenceInput.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
    }
  }, [paymentReferenceServerError]);

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
      setLoadingAvailability(false);
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
    setAvailability(null);
    setAvailabilityError('');
    Promise.all([bookingApi.availability(params), bookingApi.listAll({ status: 'IN_HOUSE' })]).then(([result, inHouse]) => {
      if (!active) return;
      const occupiedRoomIds = new Set(inHouse.map((booking) => booking.roomId));
      setAvailability({ ...result, rooms: (result.rooms || []).filter((room) => !occupiedRoomIds.has(room.id)) });
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
    if (!availability) return;
    if (form.roomId && !availableRooms.some((room) => room.id === form.roomId)) {
      autoSelectRoom.current = false;
      setForm((current) => ({ ...current, roomId: '' }));
      setFormError('Phòng đã chọn không còn trống trong khung giờ này. Vui lòng chọn lại phòng.');
      return;
    }
    if (!form.roomId && availableRooms.length && autoSelectRoom.current) {
      setForm((current) => ({ ...current, roomId: availableRooms[0].id }));
    }
  }, [availability, availableRooms, form.roomId]);

  useEffect(() => {
    if (depositAmountEdited.current || quote?.depositRequired == null) return;
    const suggestedAmount = Number(quote.depositRequired) > 0 ? String(Math.trunc(Number(quote.depositRequired))) : '';
    setForm((current) => current.depositAmount === suggestedAmount ? current : { ...current, depositAmount: suggestedAmount });
  }, [quote?.depositRequired]);

  // Đánh dấu trường đã được người dùng kiểm tra để chỉ hiện lỗi sau tương tác.
  const markTouched = (field) => setTouched((current) => current[field] ? current : { ...current, [field]: true });

  // Cập nhật biểu mẫu; đánh dấu khi lễ tân tự sửa tiền cọc gợi ý.
  const change = (event) => {
    const { name, value, checked, type } = event.target;
    if (name === 'depositAmount') depositAmountEdited.current = true;
    if (name === 'depositEnabled') markTouched(name);
    const nextValue = name === 'depositAmount' ? value.replace(/\D/g, '') : value;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : nextValue }));
    setFormError('');
  };

  // Kiểm tra lựa chọn và gửi dữ liệu chuẩn hóa cho callback tạo booking.
  const submit = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!bookingReady) return setFormError(bookingBlockers[0] || 'Đang kiểm tra phòng trống. Vui lòng đợi hoàn tất.');

    setSubmitting(true);
    try {
      const inHouse = await bookingApi.listAll({ status: 'IN_HOUSE' });
      if (inHouse.some((booking) => booking.roomId === chosenRoom.id)) {
        setFormError('Phòng vẫn đang được sử dụng. Vui lòng chọn phòng khác hoặc chờ checkout.');
        setAvailability((current) => current ? { ...current, rooms: current.rooms.filter((room) => room.id !== chosenRoom.id) } : null);
        return;
      }
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
        guestCount: Number(form.guestCount),
        depositAmount: form.depositEnabled ? depositAmount : 0,
        paymentMethod: form.depositEnabled && depositAmount > 0 ? paymentMethod : null,
        paymentReference: form.depositEnabled && depositAmount > 0 ? form.transaction.trim() || null : null,
      });
      if (result?.ok) clearBookingDraft(user);
      if (result?.error) setFormError(result.error);
    } catch (error) {
      const errors = apiErrors(error);
      setFormError(errors.form || Object.values(errors)[0] || 'Không kiểm tra được trạng thái phòng. Vui lòng thử lại.');
    } finally { setSubmitting(false); }
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
          <Field label="Họ tên khách *" className={showContactNameError ? 'is-invalid' : ''}>
            <input name="contactName" value={form.contactName} onChange={change} onBlur={() => markTouched('contactName')} autoComplete="name" placeholder="Nhập họ tên khách" required aria-invalid={showContactNameError} aria-describedby={showContactNameError ? 'booking-contact-name-error' : undefined} />
            {showContactNameError && <small id="booking-contact-name-error" className="booking-field-error" aria-live="polite">Nhập họ tên khách.</small>}
          </Field>
          <Field label="Số điện thoại *" className={showContactPhoneError ? 'is-invalid' : ''}>
            <input type="tel" name="contactPhone" maxLength="30" value={form.contactPhone} onChange={change} onBlur={() => markTouched('contactPhone')} inputMode="numeric" autoComplete="tel" placeholder="Nhập số điện thoại" required aria-invalid={showContactPhoneError} aria-describedby={showContactPhoneError ? 'booking-contact-phone-error' : undefined} />
            {showContactPhoneError && <small id="booking-contact-phone-error" className="booking-field-error" aria-live="polite">{contactPhoneError}</small>}
          </Field>
        </div>
        <div className="booking-form-grid booking-schedule-grid">
          <Field label="Ngày nhận phòng *" className={showScheduleError ? 'is-invalid' : ''}>
            <input type="date" name="date" value={form.date} onChange={change} onBlur={() => markTouched('date')} required aria-invalid={showScheduleError} aria-describedby={showScheduleError ? 'booking-schedule-error' : undefined} />
          </Field>
          <Field label="Giờ nhận phòng *" className={showScheduleError ? 'is-invalid' : ''}>
            <TimeInput name="time" value={form.time} onChange={change} onBlur={() => markTouched('time')} required aria-invalid={showScheduleError} aria-describedby={showScheduleError ? 'booking-time-help booking-schedule-error' : 'booking-time-help'} />
            <small id="booking-time-help" className="booking-field-hint">Nhập theo định dạng 24 giờ, ví dụ 14:30.</small>
          </Field>
          <Field label="Số lượng khách *" className={showGuestCountError ? 'is-invalid' : ''}>
            <input type="number" name="guestCount" min="1" value={form.guestCount} onChange={change} onBlur={() => markTouched('guestCount')} required aria-invalid={showGuestCountError} aria-describedby={showGuestCountError ? 'booking-guest-count-error' : undefined} />
            {showGuestCountError && <small id="booking-guest-count-error" className="booking-field-error" aria-live="polite">{chosenRoom && guestCount > chosenRoom.capacity ? `Phòng này nhận tối đa ${chosenRoom.capacity} khách.` : 'Nhập ít nhất 1 khách.'}</small>}
          </Field>
          <Field label="Hạng phòng *" className={showRoomTypeError ? 'is-invalid' : ''}>
            <select name="roomTypeId" value={selectedType?.id || ''} onChange={(event) => { markTouched('roomTypeId'); autoSelectRoom.current = false; setFormError(''); setForm((current) => ({ ...current, roomTypeId: event.target.value, roomId: '' })); }} required aria-invalid={showRoomTypeError} aria-describedby={showRoomTypeError ? 'booking-room-type-error' : undefined}>
              <option value="" disabled>Chọn hạng phòng</option>{roomTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select>
            {showRoomTypeError && <small id="booking-room-type-error" className="booking-field-error" aria-live="polite">Chọn hạng phòng.</small>}
          </Field>
        </div>
        {showScheduleError && <small id="booking-schedule-error" className="booking-field-error booking-schedule-error" aria-live="polite">{scheduleErrorMessage}</small>}

        <div className={`booking-availability-banner${availabilityError ? ' is-error' : ''}`} role="status">
          {loadingAvailability ? 'Đang kiểm tra phòng trống…' : availabilityError || `Còn ${availableRooms.length} phòng ${selectedType?.name || ''} phù hợp lúc ${form.time}, ${formatDate(form.date)}`}
        </div>

        <div className={`booking-section-title-row${showRoomError ? ' is-invalid' : ''}`}><h3>Chọn phòng cụ thể *</h3><span>Bắt buộc chọn phòng trống</span></div>
        <div className={`booking-room-choice-grid${showRoomError ? ' is-invalid' : ''}`} role="group" aria-label="Chọn phòng cụ thể" aria-describedby={showRoomError ? 'booking-room-error' : undefined} onClick={() => markTouched('roomId')}>
          {availableRooms.length ? availableRooms.map((room) => <button key={room.id} type="button" className={`booking-room-choice${form.roomId === room.id ? ' is-selected' : ''}`} onClick={() => { autoSelectRoom.current = false; setFormError(''); setForm((current) => ({ ...current, roomId: room.id })); }}>
            <strong>{room.code} · {room.name}</strong><small>{selectedType?.name} · tối đa {room.capacity} khách</small>{form.roomId === room.id && <span className="booking-choice-check">✓</span>}
          </button>) : <div className="booking-room-empty">{loadingAvailability ? 'Đang tải phòng…' : 'Không có phòng trống trong khung giờ đã chọn.'}</div>}
        </div>
        {showRoomError && <small id="booking-room-error" className="booking-field-error booking-room-error" role="status">{roomErrorMessage}</small>}

        <div className="booking-form-divider" />
        <h3 className="booking-section-heading">Gói dịch vụ</h3>
        <div className="booking-pricing-options">
          <div className="booking-pricing-mode" role="group" aria-label="Hình thức tính giá">
            <button type="button" className={form.pricingMode === 'HOURLY' ? 'is-selected' : ''} onClick={() => { markTouched('pricingMode'); setFormError(''); setForm((current) => ({ ...current, pricingMode: 'HOURLY' })); }}>Tính giờ</button>
            <button type="button" className={form.pricingMode === 'COMBO' ? 'is-selected' : ''} onClick={() => { markTouched('pricingMode'); setFormError(''); setForm((current) => ({ ...current, pricingMode: 'COMBO' })); }}>Combo</button>
          </div>
          {form.pricingMode === 'COMBO' ? <div className={`booking-package-grid${showComboError ? ' is-invalid' : ''}`}>
            {combos.map((combo) => <button key={combo.id} type="button" className={`booking-package-choice${form.comboId === combo.id ? ' is-selected' : ''}`} onClick={() => { markTouched('comboId'); setFormError(''); setForm((current) => ({ ...current, comboId: combo.id })); }}>
              <strong>{combo.name}</strong><b>{formatVnd(combo.price)}</b>
            </button>)}
            {!combos.length && <div className="booking-room-empty">Hạng phòng này chưa có combo đang áp dụng.</div>}
          </div> : <Field label="Thời lượng thuê (giờ)" className={showDurationError ? 'is-invalid' : ''}>
            <input type="number" name="durationHours" min="1" max="24" step="1" inputMode="numeric" required value={form.durationHours} onChange={change} onBlur={() => markTouched('durationHours')} aria-invalid={showDurationError} aria-describedby="booking-duration-help" />
            <small id="booking-duration-help" className={showDurationError ? 'booking-field-error' : 'booking-field-hint'} aria-live={showDurationError ? 'polite' : undefined}>
              {showDurationError ? 'Thời lượng thuê phải là số giờ nguyên từ 1 đến 24.' : 'Nhập số giờ nguyên từ 1 đến 24.'}
            </small>
          </Field>}
          {showComboError && <small className="booking-field-error booking-combo-error" role="status">Hãy chọn một combo đang áp dụng cho hạng phòng này.</small>}
        </div>

        <div className="booking-form-divider" />
        <div className="booking-section-title-row booking-deposit-heading"><h3>Tiền cọc</h3><label className="booking-toggle"><span>Có thu cọc</span><input type="checkbox" name="depositEnabled" checked={form.depositEnabled} onChange={change} /><i /></label></div>
        <div className="booking-form-grid booking-deposit-grid">
          <Field label="Số tiền cọc" className={showDepositAmountError ? 'is-invalid' : ''}>
            <div className="booking-money-input"><input name="depositAmount" inputMode="numeric" value={formatMoneyInput(form.depositAmount)} onChange={change} onBlur={() => markTouched('depositAmount')} disabled={!form.depositEnabled} placeholder="0" aria-label="Số tiền cọc" aria-invalid={showDepositAmountError} aria-describedby={showDepositAmountError ? 'booking-deposit-error' : undefined} /><span>đ</span></div>
            {showDepositAmountError && <small id="booking-deposit-error" className="booking-field-error" aria-live="polite">Nhập số tiền cọc đã thực sự nhận.</small>}
          </Field>
          <Field label="Phương thức"><select name="paymentMethod" value={form.paymentMethod} onChange={change} disabled={!form.depositEnabled}><option value="BANK_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option></select></Field>
          <Field label={`Mã giao dịch / ghi chú${paymentReferenceRequired ? ' *' : ''}`} className={paymentReferenceFieldError ? 'is-invalid' : ''}>
            <input ref={paymentReferenceInput} name="transaction" value={form.transaction} onChange={change} onBlur={() => markTouched('transaction')} disabled={!form.depositEnabled} required={paymentReferenceRequired} maxLength={120} placeholder="Ví dụ: CK-245781" aria-invalid={paymentReferenceFieldError} aria-describedby={paymentReferenceFieldError ? 'booking-deposit-reference-error' : undefined} />
            {paymentReferenceFieldError && <small id="booking-deposit-reference-error" className="booking-field-error" aria-live="polite">{paymentReferenceFieldErrorText}</small>}
          </Field>
        </div>
        <p className="booking-feature-note">Chỉ bật “Có thu cọc” khi đã thực sự nhận tiền. Tiền mặt không cần mã giao dịch / ghi chú; với chuyển khoản, hãy nhập thông tin để đối chiếu và kiểm tra tiền đã vào tài khoản trước khi tạo booking.</p>
        <Field label="Ghi chú booking" className="booking-note-field"><textarea name="note" rows="2" value={form.note} onChange={change} placeholder="Thông tin khách cần lưu ý" /></Field>
      </section>

      <aside className="booking-summary-card">
        <h2>Tóm tắt booking</h2><p>Kiểm tra trước khi giữ phòng</p>
        <div className={`booking-readiness${bookingReady ? ' is-ready' : ''}`}>
          <div className="booking-readiness-status" role="status" aria-live="polite">
            {bookingReady ? 'Đã đủ thông tin để tạo booking' : bookingBlockers.length ? `Chưa thể tạo booking · Còn ${bookingBlockers.length} mục` : 'Đang kiểm tra phòng trống…'}
          </div>
          <p className="booking-readiness-description">
            {bookingReady ? 'Bạn có thể tạo booking.' : loadingAvailability ? 'Nút tạo sẽ khả dụng sau khi xác nhận được phòng trống.' : availabilityError ? 'Điều chỉnh ngày giờ hoặc hạng phòng để kiểm tra lại.' : 'Hoàn thành các mục dưới đây để mở nút tạo booking.'}
          </p>
          {bookingBlockers.length > 0 && <ul className="booking-readiness-list">{bookingBlockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>}
        </div>
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
        <div className="booking-summary-money booking-deposit-money"><span>Tiền cọc đã nhận</span><strong>{form.depositEnabled ? `−${formatVnd(depositAmount)}` : formatVnd(0)}</strong></div>
        <div className="booking-summary-total"><strong>Còn lại dự kiến</strong><b>{formatVnd(Math.max(estimatedAmount - depositAmount, 0))}</b></div>
        <div className="booking-summary-hint">Booking giữ phòng theo ngày/giờ đã chọn.<br />Giá cuối có thể thay đổi khi checkout.</div>
        <button className="booking-button booking-button-primary booking-submit-button" type="submit" disabled={submitting || !bookingReady}>{submitting ? 'Đang tạo booking…' : 'Tạo booking'}</button>
        <button className="booking-summary-cancel" type="button" onClick={cancelDraft}>Hủy</button>
      </aside>
    </form>}
  </div>;
}
