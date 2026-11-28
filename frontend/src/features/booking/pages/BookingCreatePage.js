import { useEffect, useMemo, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { clearBookingDraft, readBookingDraft, saveBookingDraft } from '../services/bookingDraft';
import TimeInput from '../components/TimeInput';
import { formatDate, formatMoneyInput, formatVnd, hourlyRentalMinutes, isValidTimeInput } from '../data/bookingData';

// Định dạng ngày nhập của biểu mẫu theo giờ máy người dùng.
function localDate(value = new Date()) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

// Chuyển ngày và giờ nhập trên máy thành mốc ISO gửi backend.
function dateTimeIso(date, time) {
  return new Date(`${date}T${time}:00`).toISOString();
}

function normalizeSearch(value) {
  return String(value || '').trim().toLocaleLowerCase('vi-VN')
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
}

// Bọc nhãn và trường nhập thống nhất trong biểu mẫu.
function Field({ label, children, className = '' }) {
  return <label className={`booking-form-field ${className}`}><span>{label}</span>{children}</label>;
}

// Thu thông tin khách, hạng phòng, giá và xác nhận thu cọc trước khi tạo booking.
export default function BookingCreatePage({ user, options, optionsLoading, optionsError, onBack, onCreate }) {
  const now = new Date();
  now.setMinutes(Math.ceil((now.getMinutes() + 30) / 15) * 15, 0, 0);
  const initialDate = localDate(now);
  const initialTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const savedDraft = useRef(readBookingDraft(user));
  const [form, setForm] = useState(() => ({
    contactName: '', contactPhone: '', date: initialDate, time: initialTime,
    guestCount: '2', roomTypeId: '', pricingMode: 'COMBO', comboId: '',
    durationHours: '2', note: '', depositEnabled: false,
    paymentMethod: 'BANK_TRANSFER', transaction: '',
    ...savedDraft.current?.form,
    channel: ['FACEBOOK', 'TIKTOK', 'ZALO', 'INSTAGRAM', 'WALK_IN'].includes(savedDraft.current?.form?.channel) ? savedDraft.current.form.channel : '',
  }));
  const [availability, setAvailability] = useState(null);
  const [availabilityError, setAvailabilityError] = useState('');
  const [loadingAvailability, setLoadingAvailability] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [touched, setTouched] = useState({});
  const [comboSearch, setComboSearch] = useState('');
  const comboDialogRef = useRef(null);
  const paymentReferenceInput = useRef(null);

  useEffect(() => { saveBookingDraft(user, form, false); }, [user, form]);

  const cancelDraft = () => {
    clearBookingDraft(user);
    onBack();
  };

  const roomTypes = useMemo(() => options?.roomTypes || [], [options?.roomTypes]);
  const selectedType = roomTypes.find((item) => item.id === form.roomTypeId) || roomTypes[0] || null;
  const combos = selectedType?.combos || [];
  const selectedCombo = combos.find((item) => item.id === form.comboId) || combos[0] || null;
  const visibleCombos = combos.filter((combo) => normalizeSearch(combo.name).includes(normalizeSearch(comboSearch)));
  // BR-150: giờ thuê hỗ trợ bước 30 phút để chấp nhận booking 90 phút.
  const parsedHourlyDuration = Number(form.durationHours);
  const hourlyDurationValid = Number.isFinite(parsedHourlyDuration)
    && parsedHourlyDuration >= 1 && parsedHourlyDuration <= 24
    && Number.isInteger(parsedHourlyDuration * 2);
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
  const quote = availability?.quote;
  const estimatedAmount = Number(quote?.expectedAmount || selectedCombo?.price || 0);
  const depositAmount = form.depositEnabled ? Number(quote?.depositRequired ?? Math.ceil(estimatedAmount * 0.3 / 1000) * 1000) : 0;
  const paymentMethod = String(form.paymentMethod || '').trim().toUpperCase();
  const cashPayment = paymentMethod === 'CASH';
  const guestCount = Number(form.guestCount);
  // BR-128: kiểm tra sức chứa theo hạng phòng trước khi gán phòng cụ thể.
  const guestCountError = !Number.isInteger(guestCount) || guestCount < 1
    ? 'Số lượng khách phải là số nguyên lớn hơn 0.'
    : selectedType && guestCount > selectedType.capacity
      ? `Hạng phòng ${selectedType.name} chỉ phù hợp tối đa ${selectedType.capacity} khách. Vui lòng giảm số khách hoặc chọn hạng phòng khác.`
      : '';
  const guestCountValid = !guestCountError;
  const validSchedule = Boolean(plannedStart && plannedEnd && plannedStart > new Date() && plannedEnd > plannedStart);
  const pricingSelectionValid = form.pricingMode === 'COMBO' ? Boolean(selectedCombo) : hourlyDurationValid;
  const depositAmountValid = Number.isFinite(depositAmount) && depositAmount > 0;
  const contactPhoneError = !form.contactPhone.trim() ? 'Nhập số điện thoại.' : phoneFormatError(form.contactPhone);

const paymentReferenceRequired = form.depositEnabled && depositAmountValid && !cashPayment;
const paymentReferenceValid = !paymentReferenceRequired || Boolean(form.transaction.trim());
  const bookingBlockers = [];
  if (!['FACEBOOK', 'TIKTOK', 'ZALO', 'INSTAGRAM', 'WALK_IN'].includes(form.channel)) bookingBlockers.push('Chọn nguồn khách.');
  if (!form.contactName.trim()) bookingBlockers.push('Nhập họ tên khách.');
  if (contactPhoneError) bookingBlockers.push(contactPhoneError);
  if (!selectedType) bookingBlockers.push('Chọn hạng phòng.');
  if (!validSchedule) bookingBlockers.push(plannedStart && plannedStart <= new Date()
    ? 'Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.'
    : 'Nhập ngày hợp lệ và giờ theo định dạng HH:mm, từ 00:00 đến 23:59.');
  if (guestCountError) bookingBlockers.push(guestCountError);
  if (!pricingSelectionValid) bookingBlockers.push(form.pricingMode === 'COMBO' ? 'Chọn một combo dịch vụ.' : 'Nhập thời lượng thuê từ 1 đến 24 giờ, theo bước 0,5 giờ.');
  if (!loadingAvailability && !availableRooms.some((room) => room.capacity >= guestCount)) {
    bookingBlockers.push(availabilityError ? 'Không xác nhận được phòng trống; hãy đổi lịch hoặc hạng phòng để thử lại.' : 'Đổi thời gian hoặc hạng phòng để tìm phòng trống.');
  }
  if (form.depositEnabled && !depositAmountValid) bookingBlockers.push('Chưa tính được cọc 30%; hãy kiểm tra lại báo giá booking.');
  if (!paymentReferenceValid) bookingBlockers.push('Nhập mã giao dịch / ghi chú khi thu tiền cọc.');
  const bookingReady = !optionsLoading && !loadingAvailability && !availabilityError && bookingBlockers.length === 0;
  const showContactNameError = Boolean(touched.contactName && !form.contactName.trim());
  const showContactPhoneError = Boolean((touched.contactPhone || form.contactPhone) && contactPhoneError);
  const showScheduleError = Boolean((touched.date || touched.time) && !validSchedule);
  const showGuestCountError = Boolean(touched.guestCount && !guestCountValid);
  const showRoomTypeError = Boolean(touched.roomTypeId && !selectedType);
  const showComboError = Boolean(form.pricingMode === 'COMBO' && !selectedCombo && (touched.pricingMode || touched.comboId));
  const showDurationError = Boolean(form.pricingMode === 'HOURLY' && !hourlyDurationValid && touched.durationHours);
  const showDepositAmountError = Boolean(form.depositEnabled && !depositAmountValid && (touched.depositEnabled || touched.depositAmount));
  const showPaymentReferenceError = Boolean(paymentReferenceRequired && !paymentReferenceValid && touched.transaction);
  const paymentReferenceServerError = /mã giao dịch/i.test(formError) ? formError : '';
  const paymentReferenceFieldError = showPaymentReferenceError || Boolean(paymentReferenceServerError);
  const paymentReferenceFieldErrorText = paymentReferenceServerError || 'Nhập mã giao dịch của khoản tiền cọc đã nhận.';
  const scheduleErrorMessage = plannedStart && plannedStart <= new Date()
    ? 'Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.'
    : 'Nhập ngày hợp lệ và giờ theo định dạng HH:mm, từ 00:00 đến 23:59.';

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
      setAvailabilityError(plannedStart && plannedStart <= new Date()
        ? 'Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.'
        : '');
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
    // BR-04: backend kiểm tra toàn bộ khoảng đặt, kể cả lịch sau giờ trả của khách hiện tại.
    bookingApi.availability(params).then((result) => {
      if (!active) return;
      setAvailability(result);
    }).catch((error) => {
      if (active) {
        setAvailability(null);
        const errors = apiErrors(error);
        setAvailabilityError(errors.form || Object.values(errors)[0]);
      }
    }).finally(() => { if (active) setLoadingAvailability(false); });
    return () => { active = false; };
  }, [selectedType, selectedCombo, plannedStart, plannedEnd, form.pricingMode ]);

  // Đánh dấu trường đã được người dùng kiểm tra để chỉ hiện lỗi sau tương tác.
  const markTouched = (field) => setTouched((current) => current[field] ? current : { ...current, [field]: true });

  // Cập nhật thông tin booking; tiền cọc luôn lấy từ báo giá backend.
  const change = (event) => {
    const { name, value, checked, type } = event.target;
    if (name === 'depositEnabled') markTouched(name);
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
    setFormError('');
  };

  // Kiểm tra lựa chọn và gửi dữ liệu chuẩn hóa cho callback tạo booking.
  const submit = async (event) => {
    event.preventDefault();
    setFormError('');
    if (!bookingReady) return setFormError(bookingBlockers[0] || 'Đang kiểm tra phòng trống. Vui lòng đợi hoàn tất.');

    setSubmitting(true);
    try {
      // BR-02, BR-127: backend kiểm tra lại hạng phòng, giá và khung giờ trong giao dịch tạo booking.
      const result = await onCreate({
        roomTypeId: selectedType.id,
        plannedStart: dateTimeIso(form.date, form.time),
        plannedEnd: plannedEnd.toISOString(),
        pricingMode: form.pricingMode,
        comboId: form.pricingMode === 'COMBO' ? selectedCombo?.id : null,
        contactName: form.contactName.trim(),
        contactPhone: form.contactPhone.trim(),
        channel: form.channel,
        note: null,
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
      <div><h1>Tạo booking</h1></div>
      <button type="button" className="booking-button booking-button-secondary" onClick={onBack}>← Quay lại</button>
    </div>
    {(optionsError || formError) && <div className="booking-inline-error" role="alert">{formError || optionsError}</div>}
    {optionsLoading ? <div className="booking-empty-state">Đang tải hạng phòng và gói dịch vụ…</div> : <form className="booking-create-layout" onSubmit={submit}>
      <section className="booking-create-card">
        <header className="booking-card-heading"><h2>Thông tin khách &amp; lịch đặt</h2></header>

        <Field label="Nguồn khách *" className={`booking-source-field${touched.channel && !form.channel ? ' is-invalid' : ''}`}>
          <select name="channel" value={form.channel} onChange={change} onBlur={() => markTouched('channel')} required aria-invalid={Boolean(touched.channel && !form.channel)}>
            <option value="">Chọn nguồn khách</option>
            <option value="FACEBOOK">FB</option>
            <option value="TIKTOK">TikTok</option>
            <option value="ZALO">Zalo</option>
            <option value="INSTAGRAM">IG</option>
            <option value="WALK_IN">Khách ngoài</option>
          </select>
          {touched.channel && !form.channel && <small className="booking-field-error" aria-live="polite">Chọn nguồn khách.</small>}
        </Field>

        <div className="booking-form-grid booking-customer-grid">
          <Field label="Họ tên khách *" className={showContactNameError ? 'is-invalid' : ''}>
            <input name="contactName" value={form.contactName} onChange={change} onBlur={() => markTouched('contactName')} autoComplete="name" placeholder="Nhập họ tên khách" required aria-invalid={showContactNameError} aria-describedby={showContactNameError ? 'booking-contact-name-error' : undefined} />
            {showContactNameError && <small id="booking-contact-name-error" className="booking-field-error" aria-live="polite">Nhập họ tên khách.</small>}
          </Field>
          <Field label="Số điện thoại *" className={showContactPhoneError ? 'is-invalid' : ''}>
            <input type="tel" name="contactPhone" maxLength="30" value={form.contactPhone} onChange={change} onBlur={() => markTouched('contactPhone')} inputMode="tel" autoComplete="tel" placeholder="Nhập số điện thoại" required aria-invalid={showContactPhoneError} aria-describedby={showContactPhoneError ? 'booking-contact-phone-error' : undefined} />
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
            {showGuestCountError && <small id="booking-guest-count-error" className="booking-field-error" aria-live="polite">{guestCountError}</small>}
          </Field>
          <Field label="Hạng phòng *" className={showRoomTypeError ? 'is-invalid' : ''}>
             <select name="roomTypeId" value={selectedType?.id || ''} onChange={(event) => { markTouched('roomTypeId'); setFormError(''); setForm((current) => ({ ...current, roomTypeId: event.target.value })); }} required aria-invalid={showRoomTypeError} aria-describedby={showRoomTypeError ? 'booking-room-type-error' : undefined}>
              <option value="" disabled>Chọn hạng phòng</option>{roomTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select>
            {showRoomTypeError && <small id="booking-room-type-error" className="booking-field-error" aria-live="polite">Chọn hạng phòng.</small>}
          </Field>
        </div>
        {showScheduleError && <small id="booking-schedule-error" className="booking-field-error booking-schedule-error" aria-live="polite">{scheduleErrorMessage}</small>}

        <div className={`booking-availability-banner${availabilityError ? ' is-error' : ''}`} role="status">
           {loadingAvailability ? 'Đang kiểm tra phòng trống…' : availabilityError || `Còn ${availableRooms.filter((room) => room.capacity >= guestCount).length} phòng hạng ${selectedType?.name || ''} phù hợp lúc ${form.time}, ${formatDate(form.date)}. Chọn phòng cụ thể khi check-in.`}
        </div>

        <div className="booking-form-divider" />
        <h3 className="booking-section-heading">Gói dịch vụ</h3>
        <div className="booking-pricing-options">
          <div className="booking-pricing-mode" role="group" aria-label="Hình thức tính giá">
            <button type="button" className={form.pricingMode === 'HOURLY' ? 'is-selected' : ''} onClick={() => { markTouched('pricingMode'); setFormError(''); setForm((current) => ({ ...current, pricingMode: 'HOURLY' })); }}>Tính giờ</button>
            <button type="button" className={form.pricingMode === 'COMBO' ? 'is-selected' : ''} onClick={() => { markTouched('pricingMode'); setFormError(''); setForm((current) => ({ ...current, pricingMode: 'COMBO' })); }}>Combo</button>
          </div>
          {form.pricingMode === 'COMBO' ? <div className="booking-package-picker">
            <span className="booking-package-label">Combo sử dụng</span>
            <button type="button" className={`booking-package-trigger${showComboError ? ' is-invalid' : ''}`} onClick={() => { setComboSearch(''); comboDialogRef.current?.showModal(); }}>
              <span>{selectedCombo?.name || 'Chọn combo'}</span><strong>{selectedCombo ? formatVnd(selectedCombo.price) : 'Xem danh sách'}</strong>
            </button>
            <dialog ref={comboDialogRef} className="booking-package-dialog" aria-labelledby="booking-package-dialog-title" onClose={() => setComboSearch('')}>
              <header><h2 id="booking-package-dialog-title">Chọn combo</h2><button type="button" aria-label="Đóng danh sách combo" onClick={() => comboDialogRef.current?.close()}>×</button></header>
              <search aria-label="Tìm combo"><label className="booking-package-search">Tìm combo
                <input autoFocus type="search" name="comboSearch" value={comboSearch} onChange={(event) => setComboSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') event.preventDefault(); }} placeholder="Nhập tên combo…" aria-controls="booking-package-list" />
              </label></search>
              <div className="booking-package-grid" id="booking-package-list" role="group" aria-label="Danh sách combo">
                {visibleCombos.map((combo) => <button key={combo.id} type="button" aria-pressed={form.comboId === combo.id} className={`booking-package-choice${form.comboId === combo.id ? ' is-selected' : ''}`} onClick={() => { markTouched('comboId'); setFormError(''); setForm((current) => ({ ...current, comboId: combo.id })); comboDialogRef.current?.close(); }}>
                  <span><strong>{combo.name}</strong><b>{formatVnd(combo.price)}</b></span>{(combo.includedFnbQuantity > 0 || combo.includedFoodQuantity > 0) && <small>Tặng {combo.includedFnbQuantity || 0} đồ uống · {combo.includedFoodQuantity || 0} đồ ăn (chọn lúc check-in)</small>}
                </button>)}
                {!combos.length && <div className="booking-room-empty">Hạng phòng này chưa có combo đang áp dụng.</div>}
                {combos.length > 0 && !visibleCombos.length && <div className="booking-room-empty">Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</div>}
              </div>
            </dialog>
          </div> : <Field label="Thời lượng thuê (giờ)" className={showDurationError ? 'is-invalid' : ''}>
            <input type="number" name="durationHours" min="1" max="24" step="0.5" inputMode="decimal" required value={form.durationHours} onChange={change} onBlur={() => markTouched('durationHours')} aria-invalid={showDurationError} aria-describedby="booking-duration-help" />
            <small id="booking-duration-help" className={showDurationError ? 'booking-field-error' : 'booking-field-hint'} aria-live={showDurationError ? 'polite' : undefined}>
              {showDurationError ? 'Thời lượng phải tương ứng số phút nguyên từ 1 phút đến 24 giờ.' : 'Ví dụ: nhập 0,5 giờ để đặt 30 phút.'}
            </small>
          </Field>}
          {showComboError && <small className="booking-field-error booking-combo-error" role="status">Hãy chọn một combo đang áp dụng cho hạng phòng này.</small>}
        </div>

        <div className="booking-form-divider" />
        <div className="booking-section-title-row booking-deposit-heading"><h3>Tiền cọc</h3><label className="booking-toggle"><span>Có thu cọc</span><input type="checkbox" name="depositEnabled" checked={form.depositEnabled} onChange={change} /><i /></label></div>
        <div className="booking-form-grid booking-deposit-grid">
          <Field label="Số tiền cọc (30%)" className={showDepositAmountError ? 'is-invalid' : ''}>
            <div className="booking-money-input"><input name="depositAmount" inputMode="numeric" value={formatMoneyInput(String(depositAmount))} readOnly onBlur={() => markTouched('depositAmount')} disabled={!form.depositEnabled} placeholder="0" aria-label="Số tiền cọc" aria-invalid={showDepositAmountError} aria-describedby={showDepositAmountError ? 'booking-deposit-error' : undefined} /><span>đ</span></div>
            {showDepositAmountError && <small id="booking-deposit-error" className="booking-field-error" aria-live="polite">Chưa tính được cọc 30%; hãy kiểm tra lại báo giá booking.</small>}
          </Field>
          <Field label="Phương thức"><select name="paymentMethod" value={form.paymentMethod} onChange={change} disabled={!form.depositEnabled}><option value="BANK_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option></select></Field>
          <Field label={`${cashPayment ? 'Ghi chú' : 'Mã giao dịch'}${paymentReferenceRequired ? ' *' : ''}`} className={paymentReferenceFieldError ? 'is-invalid' : ''}>
            <input ref={paymentReferenceInput} name="transaction" value={form.transaction} onChange={change} onBlur={() => markTouched('transaction')} disabled={!form.depositEnabled} required={paymentReferenceRequired} maxLength={120} placeholder={cashPayment ? 'Ghi chú khoản cọc' : 'Nhập mã giao dịch đã nhận cọc'} aria-invalid={paymentReferenceFieldError} aria-describedby={paymentReferenceFieldError ? 'booking-deposit-reference-error' : undefined} />
            {paymentReferenceFieldError && <small id="booking-deposit-reference-error" className="booking-field-error" aria-live="polite">{paymentReferenceFieldErrorText}</small>}
          </Field>
        </div>
        <p className="booking-feature-note">Cọc tự tính bằng 30% giá booking. Lễ tân kiểm tra đã nhận đủ tiền cọc trước khi bấm Tạo booking. Chuyển khoản nhập mã giao dịch / ghi chú.</p>
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
          <div><dt>Nguồn khách</dt><dd>{({ FACEBOOK: 'FB', TIKTOK: 'TikTok', ZALO: 'Zalo', INSTAGRAM: 'IG', WALK_IN: 'Khách ngoài' })[form.channel] || 'Chưa chọn'}</dd></div>
          <div><dt>Ngày giờ</dt><dd>{form.time} · {formatDate(form.date)}</dd></div>
          <div><dt>Khách</dt><dd>{form.contactName || '—'}</dd></div>
          <div><dt>Số khách</dt><dd>{form.guestCount} người</dd></div>
           <div><dt>Phòng</dt><dd>Chọn khi check-in</dd></div>
          <div><dt>Hạng phòng</dt><dd>{selectedType?.name || '—'}</dd></div>
          <div><dt>Gói</dt><dd>{form.pricingMode === 'COMBO' ? selectedCombo?.name || 'Chưa chọn combo' : `Tính giờ · ${form.durationHours} giờ`}</dd></div>
        </dl>
        <div className="booking-summary-separator" />
        <div className="booking-summary-money"><span>Giá dự kiến</span><strong>{formatVnd(estimatedAmount)}</strong></div>
        <div className="booking-summary-money booking-deposit-money"><span>Tiền cọc đã nhận</span><strong>{form.depositEnabled ? `−${formatVnd(depositAmount)}` : formatVnd(0)}</strong></div>
        <div className="booking-summary-total"><strong>Còn lại sau khi thu cọc</strong><b>{formatVnd(Math.max(estimatedAmount - depositAmount, 0))}</b></div>
        <button className="booking-button booking-button-primary booking-submit-button" type="submit" disabled={submitting || !bookingReady}>{submitting ? 'Đang tạo booking…' : 'Tạo booking'}</button>
        <button className="booking-summary-cancel" type="button" onClick={cancelDraft}>Hủy</button>
      </aside>
    </form>}
  </div>;
}
