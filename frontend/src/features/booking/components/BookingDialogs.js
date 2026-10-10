import { useEffect, useMemo, useState } from 'react';
import {
  bookingRooms,
  calculateBookingPrice,
  comboLabels,
  formatDate,
  formatVnd,
  fromDateAndTime,
  hasRoomConflict,
} from '../data/bookingData';

const localDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const timeInput = (date) => `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;

function freshForm(selectedDate) {
  const today = localDate(new Date());
  const start = selectedDate === today ? new Date(Date.now() + 30 * 60 * 1000) : new Date(`${selectedDate}T15:00:00`);
  start.setMinutes(Math.ceil(start.getMinutes() / 15) * 15, 0, 0);
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);
  return {
    source: 'advance', customerName: '', phone: '', guestCount: '2',
    startDate: localDate(start), startTime: timeInput(start),
    endDate: localDate(end), endTime: timeInput(end),
    roomId: '', pricingMode: 'hourly', comboIndex: '0',
    depositAmount: '', depositReceived: false,
  };
}

function formFromBooking(booking) {
  return {
    source: booking.source === 'Khách đến trực tiếp' ? 'walkin' : 'advance',
    customerName: booking.customerName,
    phone: booking.phone,
    guestCount: String(booking.guestCount),
    startDate: booking.startDate,
    startTime: booking.startTime,
    endDate: booking.endDate,
    endTime: booking.endTime,
    roomId: booking.roomId,
    pricingMode: booking.pricingMode,
    comboIndex: String(booking.comboIndex || 0),
    depositAmount: booking.depositAmount ? String(booking.depositAmount) : '',
    depositReceived: booking.depositStatus === 'paid' && booking.depositAmount > 0,
  };
}

function TextField({ label, name, value, onChange, error, ...inputProps }) {
  return <label className="booking-field">
    <span>{label}</span>
    <input name={name} value={value} onChange={onChange} aria-invalid={Boolean(error)} {...inputProps} />
    {error && <small className="booking-field-error">{error}</small>}
  </label>;
}

export function BookingFormDialog({ open, mode, booking, bookings, selectedDate, onClose, onSubmit }) {
  const [form, setForm] = useState(() => freshForm(selectedDate));
  const [errors, setErrors] = useState({});
  const editing = mode === 'edit';

  useEffect(() => {
    if (!open) return;
    setForm(editing && booking ? formFromBooking(booking) : freshForm(selectedDate));
    setErrors({});
  }, [open, editing, booking, selectedDate]);

  const availableRooms = useMemo(() => bookingRooms.filter((room) => room.operational
    && room.capacity >= Number(form.guestCount || 0)
    && !hasRoomConflict(bookings, { ...form, roomId: room.id }, editing ? booking?.id : undefined)), [bookings, booking?.id, editing, form]);

  useEffect(() => {
    if (!open || editing || !availableRooms.length) return;
    if (!availableRooms.some((room) => room.id === form.roomId)) {
      setForm((current) => ({ ...current, roomId: availableRooms[0].id }));
    }
  }, [open, editing, availableRooms, form.roomId]);

  if (!open) return null;

  const updateField = (event) => {
    const { name, value, checked, type } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
    setErrors((current) => ({ ...current, [name]: '', form: '' }));
  };

  const room = bookingRooms.find((item) => item.id === form.roomId);
  const tier = room?.tier || bookingRooms.find((item) => item.id === booking?.roomId)?.tier || 'queen';
  const estimatedPrice = editing ? booking?.priceAmount || 0 : calculateBookingPrice({ ...form, tier });

  const save = (event) => {
    event.preventDefault();
    const nextErrors = {};
    const digits = form.phone.replace(/\D/g, '');
    const start = fromDateAndTime(form.startDate, form.startTime);
    const end = fromDateAndTime(form.endDate, form.endTime);
    const now = new Date();
    if (!form.customerName.trim()) nextErrors.customerName = 'Nhập tên khách hàng.';
    if (!digits) nextErrors.phone = 'Nhập số điện thoại.';
    else if (digits.length < 9 || digits.length > 11) nextErrors.phone = 'Số điện thoại cần có từ 9 đến 11 chữ số.';
    if (!form.startDate) nextErrors.startDate = 'Chọn ngày bắt đầu.';
    if (!form.startTime) nextErrors.startTime = 'Chọn giờ bắt đầu.';
    if (!form.endDate) nextErrors.endDate = 'Chọn ngày kết thúc.';
    if (!form.endTime) nextErrors.endTime = 'Chọn giờ kết thúc.';
    if (start && end && end <= start) nextErrors.endTime = 'Thời gian kết thúc phải sau thời gian bắt đầu.';
    if (!editing && form.source === 'advance' && start && start <= now) nextErrors.startTime = 'Booking đặt trước cần có giờ bắt đầu trong tương lai.';
    if (!room) nextErrors.roomId = 'Chọn một phòng đang hoạt động và còn trống.';
    if (room && Number(form.guestCount) > room.capacity) nextErrors.guestCount = `Phòng này tối đa ${room.capacity} khách.`;
    if (room && hasRoomConflict(bookings, { ...form, roomId: room.id }, editing ? booking?.id : undefined)) nextErrors.roomId = 'Phòng đã có booking trùng thời gian. Chọn phòng hoặc giờ khác.';
    if (!Number.isFinite(Number(form.guestCount)) || Number(form.guestCount) < 1) nextErrors.guestCount = 'Số khách phải từ 1 trở lên.';
    const deposit = Number(form.depositAmount || 0);
    if (form.depositReceived && (!Number.isFinite(deposit) || deposit <= 0)) nextErrors.depositAmount = 'Nhập số tiền cọc đã thực nhận.';
    if (form.depositReceived && !/^\d+$/.test(form.depositAmount)) nextErrors.depositAmount = 'Nhập số tiền nguyên hợp lệ.';
    if (!editing && estimatedPrice <= 0) nextErrors.form = 'Chọn loại giá và thời gian hợp lệ để tính giá dự kiến.';

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    onSubmit({
      ...(booking || {}),
      customerName: form.customerName.trim(),
      phone: form.phone.trim(),
      guestCount: Number(form.guestCount),
      roomId: room.id,
      startDate: form.startDate,
      startTime: form.startTime,
      endDate: form.endDate,
      endTime: form.endTime,
      pricingMode: form.pricingMode,
      comboIndex: Number(form.comboIndex),
      priceAmount: editing ? booking.priceAmount : estimatedPrice,
      priceLabel: editing ? booking.priceLabel : (form.pricingMode === 'combo' ? `Combo ${tier === 'king' ? 'King' : 'Queen'} ${Number(form.comboIndex) + 1}` : 'Theo giờ'),
      depositAmount: form.depositReceived ? deposit : 0,
      depositStatus: form.depositReceived ? 'paid' : 'unpaid',
      source: form.source === 'walkin' ? 'Khách đến trực tiếp' : 'Đặt trước',
    });
  };

  return <div className="booking-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="booking-modal booking-form-modal" role="dialog" aria-modal="true" aria-labelledby="booking-form-title" onKeyDown={(event) => { if (event.key === 'Escape') onClose(); }}>
      <div className="booking-modal-heading">
        <div>
          <p className="booking-eyebrow">QUẢN LÝ BOOKING</p>
          <h2 id="booking-form-title">{editing ? 'Cập nhật booking' : 'Tạo booking mới'}</h2>
          <p>{editing ? 'Thông tin giá đã chốt được giữ nguyên trong lần cập nhật này.' : 'Kiểm tra thời gian, phòng trống và giá dự kiến trước khi xác nhận.'}</p>
        </div>
        <button className="booking-icon-button" type="button" aria-label="Đóng" onClick={onClose}>×</button>
      </div>

      <form onSubmit={save} noValidate>
        <div className="booking-form-grid">
          <div className="booking-form-main">
            {!editing && <fieldset className="booking-fieldset">
              <legend>Hình thức booking</legend>
              <div className="booking-choice-row">
                <label className={form.source === 'walkin' ? 'booking-choice is-selected' : 'booking-choice'}>
                  <input type="radio" name="source" value="walkin" checked={form.source === 'walkin'} onChange={updateField} />
                  <span><strong>Khách đến trực tiếp</strong><small>Tạo booking trước khi check-in</small></span>
                </label>
                <label className={form.source === 'advance' ? 'booking-choice is-selected' : 'booking-choice'}>
                  <input type="radio" name="source" value="advance" checked={form.source === 'advance'} onChange={updateField} />
                  <span><strong>Đặt trước</strong><small>Giữ phòng theo khung giờ</small></span>
                </label>
              </div>
            </fieldset>}

            <fieldset className="booking-fieldset">
              <legend>Thông tin khách hàng</legend>
              <div className="booking-two-columns">
                <TextField label="Tên khách hàng *" name="customerName" value={form.customerName} onChange={updateField} error={errors.customerName} placeholder="Nguyễn Minh Anh" autoComplete="name" />
                <TextField label="Số điện thoại *" name="phone" value={form.phone} onChange={updateField} error={errors.phone} placeholder="0912 345 678" inputMode="tel" autoComplete="tel" />
              </div>
            </fieldset>

            <fieldset className="booking-fieldset">
              <legend>Thời gian và số khách</legend>
              <div className="booking-time-grid">
                <TextField label="Ngày bắt đầu *" name="startDate" type="date" value={form.startDate} onChange={updateField} error={errors.startDate} />
                <TextField label="Giờ bắt đầu *" name="startTime" type="time" value={form.startTime} onChange={updateField} error={errors.startTime} />
                <TextField label="Ngày kết thúc *" name="endDate" type="date" value={form.endDate} onChange={updateField} error={errors.endDate} />
                <TextField label="Giờ kết thúc *" name="endTime" type="time" value={form.endTime} onChange={updateField} error={errors.endTime} />
                <TextField label="Số khách" name="guestCount" type="number" min="1" max="12" value={form.guestCount} onChange={updateField} error={errors.guestCount} />
              </div>
            </fieldset>

            <fieldset className="booking-fieldset">
              <legend>Chọn phòng</legend>
              {errors.roomId && <p className="booking-inline-error" role="alert">{errors.roomId}</p>}
              {!availableRooms.length && <div className="booking-empty-rooms">Không có phòng phù hợp còn trống trong khung giờ này. Hãy đổi thời gian hoặc số khách.</div>}
              <div className="booking-room-options">
                {availableRooms.map((item) => <label key={item.id} className={form.roomId === item.id ? 'booking-room-option is-selected' : 'booking-room-option'}>
                  <input type="radio" name="roomId" value={item.id} checked={form.roomId === item.id} onChange={updateField} />
                  <img src={item.image} alt={`Không gian ${item.theme}`} />
                  <span className="booking-room-option-copy"><strong>{item.name}</strong><small>{item.theme} · tối đa {item.capacity} khách</small></span>
                  <span className="booking-room-check" aria-hidden="true">✓</span>
                </label>)}
                {bookingRooms.filter((item) => !item.operational).map((item) => <div key={item.id} className="booking-room-option is-disabled" aria-disabled="true">
                  <img src={item.image} alt="" />
                  <span className="booking-room-option-copy"><strong>{item.name}</strong><small>{item.theme} · chưa thể nhận booking</small></span>
                  <span className="booking-maintenance-label">Bảo trì</span>
                </div>)}
              </div>
            </fieldset>

            {!editing && <fieldset className="booking-fieldset">
              <legend>Giá và tiền cọc</legend>
              <div className="booking-segmented-control" role="radiogroup" aria-label="Hình thức tính giá">
                <label className={form.pricingMode === 'hourly' ? 'is-selected' : ''}><input type="radio" name="pricingMode" value="hourly" checked={form.pricingMode === 'hourly'} onChange={updateField} />Theo giờ</label>
                <label className={form.pricingMode === 'combo' ? 'is-selected' : ''}><input type="radio" name="pricingMode" value="combo" checked={form.pricingMode === 'combo'} onChange={updateField} />Combo</label>
              </div>
              {form.pricingMode === 'combo' && <label className="booking-field booking-combo-field">
                <span>Combo áp dụng</span>
                <select name="comboIndex" value={form.comboIndex} onChange={updateField}>
                  {comboLabels.map((label, index) => <option value={index} key={label}>{label}</option>)}
                </select>
              </label>}
              <label className="booking-deposit-toggle">
                <input type="checkbox" name="depositReceived" checked={form.depositReceived} onChange={updateField} />
                <span><strong>Đã thực nhận tiền cọc</strong><small>Chỉ ghi nhận khoản đã nhận xác nhận tại quầy.</small></span>
              </label>
              {form.depositReceived && <TextField label="Số tiền cọc đã nhận (VNĐ)" name="depositAmount" type="number" min="1" step="1000" value={form.depositAmount} onChange={updateField} error={errors.depositAmount} placeholder="150000" />}
            </fieldset>}
          </div>

          <aside className="booking-form-summary">
            <div className="booking-summary-room">
              {room ? <img src={room.image} alt={`Không gian ${room.theme}`} /> : <div className="booking-summary-placeholder">Chọn phòng để xem thông tin</div>}
              {room && <div><span className="booking-room-tier">{room.tier.toUpperCase()}</span><h3>{room.name}</h3><p>{room.theme} · tối đa {room.capacity} khách</p></div>}
            </div>
            <div className="booking-summary-values">
              <div><span>Thời gian</span><strong>{formatDate(form.startDate)} {form.startTime || '—'}</strong><small>đến {formatDate(form.endDate)} {form.endTime || '—'}</small></div>
              <div><span>Giá dự kiến</span><strong className="booking-summary-total">{formatVnd(estimatedPrice)}</strong><small>{editing ? 'Giá chốt ban đầu, không thay đổi' : form.pricingMode === 'hourly' ? 'Tính theo giờ và hạng phòng' : 'Theo combo đã chọn'}</small></div>
              {editing && <p className="booking-price-snapshot">Giá được lưu theo thỏa thuận tại thời điểm tạo booking. Thay đổi phòng hoặc giờ không áp dụng bảng giá mới.</p>}
            </div>
            {errors.form && <p className="booking-inline-error" role="alert">{errors.form}</p>}
            <div className="booking-dialog-actions">
              <button className="booking-button booking-button-primary" type="submit">{editing ? 'Lưu thay đổi' : 'Xác nhận booking'}</button>
              <button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button>
            </div>
          </aside>
        </div>
      </form>
    </section>
  </div>;
}

export function BookingCancelDialog({ booking, onClose, onConfirm }) {
  const [cancelType, setCancelType] = useState('customer');
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  useEffect(() => { setCancelType('customer'); setReason(''); setNote(''); }, [booking?.id]);
  if (!booking) return null;

  const start = fromDateAndTime(booking.startDate, booking.startTime);
  const minutesBeforeStart = start ? (start.getTime() - Date.now()) / 60000 : -1;
  const hasDeposit = booking.depositAmount > 0 && booking.depositStatus === 'paid';
  const refundEligible = hasDeposit && cancelType === 'customer' && minutesBeforeStart >= 40;
  const outcomeLabel = !hasDeposit ? 'Booking chưa ghi nhận tiền cọc.'
    : refundEligible ? 'Đủ điều kiện tạo yêu cầu hoàn cọc (hủy trước giờ bắt đầu ít nhất 40 phút).'
      : 'Tiền cọc không được hoàn theo điều kiện hủy booking này.';
  const canMarkNoShow = Boolean(start && start <= new Date());

  return <div className="booking-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="booking-modal booking-cancel-modal" role="dialog" aria-modal="true" aria-labelledby="booking-cancel-title" onKeyDown={(event) => { if (event.key === 'Escape') onClose(); }}>
      <div className="booking-modal-heading">
        <div><p className="booking-eyebrow">XÁC NHẬN THAY ĐỔI</p><h2 id="booking-cancel-title">Hủy booking</h2><p>{booking.id} · {booking.customerName}</p></div>
        <button className="booking-icon-button" type="button" aria-label="Đóng" onClick={onClose}>×</button>
      </div>
      <div className="booking-cancel-summary">
        <span>Khung giờ</span><strong>{formatDate(booking.startDate)} {booking.startTime} – {formatDate(booking.endDate)} {booking.endTime}</strong>
        <span>Tiền cọc đã nhận</span><strong>{formatVnd(hasDeposit ? booking.depositAmount : 0)}</strong>
      </div>
      <fieldset className="booking-fieldset booking-cancel-type">
        <legend>Loại ghi nhận</legend>
        <label className={cancelType === 'customer' ? 'booking-choice is-selected' : 'booking-choice'}>
          <input type="radio" name="cancelType" value="customer" checked={cancelType === 'customer'} onChange={() => { setCancelType('customer'); setReason(''); }} />
          <span><strong>Khách yêu cầu hủy</strong><small>GCMB ghi nhận yêu cầu hủy booking.</small></span>
        </label>
        <label className={`${cancelType === 'noShow' ? 'booking-choice is-selected' : 'booking-choice'}${canMarkNoShow ? '' : ' is-unavailable'}`}>
          <input type="radio" name="cancelType" value="noShow" checked={cancelType === 'noShow'} disabled={!canMarkNoShow} onChange={() => { setCancelType('noShow'); setReason(''); }} />
          <span><strong>Khách không đến (no-show)</strong><small>{canMarkNoShow ? 'Ghi nhận khách không có mặt theo giờ đặt.' : 'Chỉ ghi nhận no-show từ giờ bắt đầu booking.'}</small></span>
        </label>
      </fieldset>
      <div className="booking-cancel-reason-fields">
        <label className="booking-field"><span>Lý do ghi nhận *</span><select value={reason} onChange={(event) => setReason(event.target.value)}><option value="">Chọn lý do</option>{cancelType === 'customer' ? <><option>Khách thay đổi kế hoạch</option><option>Khách đổi thời gian đặt</option><option>Khách cung cấp nhầm thông tin</option><option>Lý do khác</option></> : <><option>Khách không có mặt</option><option>Không liên hệ được với khách</option><option>Khách báo không đến</option></>}</select></label>
        <label className="booking-field booking-reason-field"><span>Ghi chú thêm</span><textarea rows="2" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Thông tin cần lưu lại tại quầy (không bắt buộc)" /></label>
      </div>
      {!reason && <p className="booking-reason-hint">Chọn lý do để lưu cùng trạng thái hủy hoặc no-show.</p>}
      <div className={`booking-deposit-outcome${refundEligible ? ' is-refundable' : ''}`} role="status">
        <span className="booking-outcome-icon">{refundEligible ? '↗' : '!'}</span>
        <div><strong>{refundEligible ? 'Đủ điều kiện yêu cầu hoàn cọc' : hasDeposit ? 'Cọc không hoàn' : 'Không có tiền cọc'}</strong><p>{outcomeLabel}{refundEligible ? ' Khoản cọc chưa được hoàn tự động.' : ''}</p></div>
      </div>
      <div className="booking-dialog-actions booking-dialog-actions-inline">
        <button className="booking-button booking-button-danger" type="button" disabled={!reason} onClick={() => onConfirm({
          status: cancelType === 'noShow' ? 'noShow' : 'cancelled',
          depositOutcome: !hasDeposit ? '' : refundEligible ? 'eligible_refund' : 'forfeited',
          cancellationReason: `${reason}${note.trim() ? ` · ${note.trim()}` : ''}`,
        })}>{cancelType === 'noShow' ? 'Ghi nhận no-show' : 'Xác nhận hủy booking'}</button>
        <button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button>
      </div>
    </section>
  </div>;
}
