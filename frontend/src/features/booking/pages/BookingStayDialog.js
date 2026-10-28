import { useEffect, useMemo, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { checkoutApi } from '../services/checkoutApi';
import { bookingApi } from '../services/bookingApi';
import { formatVnd } from '../data/bookingData';

// Lấy thông báo lỗi API phù hợp để hiển thị trong hộp thoại.
function requestError(error) {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || 'Không thể tải báo giá.';
}

// Hiển thị ngày giờ theo múi giờ của chi nhánh.
function dateTimeText(value, timezone) {
  if (!value) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false, timeZone: timezone,
    }).format(new Date(value));
  } catch {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).format(new Date(value));
  }
}

// Đổi tổng số phút thành thời lượng dễ đọc.
function durationText(minutes) {
  if (minutes == null || !Number.isFinite(Number(minutes)) || Number(minutes) < 0) return '—';
  const hours = Math.floor(Number(minutes) / 60);
  const remainder = Number(minutes) % 60;
  return [hours ? `${hours} giờ` : '', remainder ? `${remainder} phút` : ''].filter(Boolean).join(' ') || 'Dưới 1 phút';
}

// Chỉ hiện combo ngày/đêm khi giờ bắt đầu đáp ứng quy tắc backend.
function comboCanStartAt(combo, value, timezone) {
  const name = String(combo?.name || '').toLocaleLowerCase('vi-VN');
  if (!name.includes('ngày') && !name.includes('ngay') && !name.includes('day')
      && !name.includes('đêm') && !name.includes('dem') && !name.includes('night')) return true;
  if (!value) return false;
  const localTime = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(value));
  return name.includes('ngày') || name.includes('ngay') || name.includes('day')
    ? localTime === '07:00'
    : localTime === '23:00';
}

// Báo giá và thực hiện đổi phòng hoặc gia hạn khi khách đang sử dụng.
export default function BookingStayDialog({ booking, detail, bookingOptions, initialMode = 'change', onClose, onSave, busy = false, timezone = 'Asia/Ho_Chi_Minh' }) {
  const [mode, setMode] = useState(initialMode);
  const [options, setOptions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [targetRoomId, setTargetRoomId] = useState('');
  const [cause, setCause] = useState('');
  const [reason, setReason] = useState('');
  const [roomQuote, setRoomQuote] = useState(null);
  const [pricingMode, setPricingMode] = useState('COMBO');
  const [comboId, setComboId] = useState('');
  const [hourlyHours, setHourlyHours] = useState('1');
  const [extensionQuote, setExtensionQuote] = useState(null);
  const [meter, setMeter] = useState(detail?.meter || null);
  const [meterQuote, setMeterQuote] = useState(null);
  const [clockNow, setClockNow] = useState(() => Date.now());
  const [submitting, setSubmitting] = useState(false);
  const bookingSummary = detail?.booking?.id === booking.id ? detail.booking : booking;
  const scheduledEnd = options?.plannedEnd || bookingSummary.plannedEnd;
  const scheduledEndAt = scheduledEnd ? new Date(scheduledEnd).getTime() : NaN;
  const reservationExpired = bookingSummary.status === 'IN_HOUSE'
    && Number.isFinite(scheduledEndAt) && clockNow >= scheduledEndAt;
  const meterVisible = Boolean(meter) || reservationExpired;
  const meterAction = meter?.status === 'RUNNING' ? 'meterStop'
    : !meter && reservationExpired ? 'meterStart' : null;

  // BR-169/170: tiền tạm tính lấy từ cùng báo giá checkout, không tự nhân một đơn giá cố định.
  useEffect(() => {
    if (!meterVisible || mode !== 'extend') return undefined;
    let active = true;
    const refreshQuote = () => checkoutApi.preview(booking.id).then(value => {
      if (active) { setMeterQuote(value); setError(''); }
    }).catch(failure => { if (active) setError(requestError(failure)); });
    refreshQuote();
    const timer = window.setInterval(refreshQuote, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [booking.id, meterVisible, mode, meter?.status]);

  useEffect(() => {
    const timer = window.setInterval(() => setClockNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (detail?.booking?.id === booking.id && detail.meter) {
      setMeter(detail.meter);
      if (detail.meter.status === 'RUNNING' || detail.meter.status === 'STOPPED') { setMode('extend'); setPricingMode('HOURLY'); }
    }
  }, [booking.id, detail]);

  useEffect(() => {
    setMode(meter ? 'extend' : initialMode);
  }, [initialMode, meter]);

  useEffect(() => {
    if (meter) { setLoading(false); return undefined; }
    let active = true;
    setLoading(true);
    setError('');
    bookingApi.stayOptions(booking.id).then((result) => {
      if (!active) return;
      setOptions(result);
      setTargetRoomId(result.availableRooms?.[0]?.id || '');
      const roomType = bookingOptions?.roomTypes?.find((type) =>
        (result.currentRoomTypeId && type.id === result.currentRoomTypeId)
        || (result.currentRoomTypeName && type.name === result.currentRoomTypeName)
        || (booking.roomTypeName && type.name === booking.roomTypeName));
      const configuredCombos = result.extensionCombos?.length ? result.extensionCombos : roomType?.combos || [];
      const eligibleCombos = configuredCombos.filter((combo) => comboCanStartAt(combo, result.plannedEnd || booking.plannedEnd, timezone));
      setComboId(eligibleCombos[0]?.id || '');
      setPricingMode(meter?.status ? 'HOURLY' : eligibleCombos.length ? 'COMBO' : 'HOURLY');
    }).catch((requestErrorValue) => { if (active) setError(requestError(requestErrorValue)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [booking.id, bookingOptions, booking.plannedEnd, booking.roomTypeName, timezone, meter]);

  useEffect(() => {
    if (meter || !targetRoomId || !cause || mode !== 'change') { setRoomQuote(null); return undefined; }
    let active = true;
    setRoomQuote(null);
    bookingApi.roomChangeQuote(booking.id, { targetRoomId, cause })
      .then((result) => { if (active) { setRoomQuote(result); setError(''); } })
      .catch((requestErrorValue) => { if (active) setError(requestError(requestErrorValue)); });
    return () => { active = false; };
  }, [booking.id, targetRoomId, cause, mode, meter]);

  const extensionRequest = useMemo(() => pricingMode === 'HOURLY'
    ? { pricingMode: 'HOURLY', comboId: null, hours: Number(hourlyHours) }
    : { pricingMode: 'COMBO', comboId: comboId || null, hours: null }, [pricingMode, comboId, hourlyHours]);

  useEffect(() => {
    const requestReady = pricingMode === 'COMBO' ? Boolean(comboId)
      : Number.isInteger(Number(hourlyHours)) && Number(hourlyHours) >= 1 && Number(hourlyHours) <= 12;
    if (mode !== 'extend' || !requestReady || meter || reservationExpired) { setExtensionQuote(null); return undefined; }
    let active = true;
    setExtensionQuote(null);
    bookingApi.extensionQuote(booking.id, extensionRequest)
      .then((result) => { if (active) { setExtensionQuote(result); setError(''); } })
      .catch((requestErrorValue) => { if (active) setError(requestError(requestErrorValue)); });
    return () => { active = false; };
  }, [booking.id, extensionRequest, mode, pricingMode, comboId, hourlyHours, meter, reservationExpired]);

  // Gửi thao tác được chọn; tiền đồng hồ hiển thị chỉ là tạm tính.
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      let result;
      if (mode === 'change') {
        result = await onSave('change', { targetRoomId, cause, reason: reason.trim() || null });
      } else if (meterAction) {
        result = await onSave(meterAction);
        if (result?.detail) {
          setMeter(result.detail.meter);
        }
      } else {
        result = await onSave('extend', extensionRequest);
      }
      if (result?.error) setError(result.error);
    } finally { setSubmitting(false); }
  };

  const active = submitting || busy;
  const selectedRoom = options?.availableRooms?.find((room) => room.id === targetRoomId);
  const currentDetail = detail?.booking?.id === booking.id ? detail : null;
  const currentAllocation = currentDetail?.allocations?.find((allocation) => allocation.state === 'OCCUPIED');
  const activeCharges = currentDetail?.charges?.filter((charge) => charge.status === 'ACTIVE') || [];
  const detailComboName = activeCharges
    .filter((charge) => charge.comboId || charge.kind === 'COMBO' || charge.pricingSnapshot?.pricingMode === 'COMBO')
    .map((charge) => charge.description)
    .filter(Boolean)
    .filter((name, index, names) => names.indexOf(name) === index)
    .join(' · ');
  const currentComboName = options?.currentComboName || detailComboName || 'Theo giờ';
  const allocationDuration = currentAllocation?.startsAt && currentAllocation?.endsAt
    ? Math.max(0, Math.floor((new Date(currentAllocation.endsAt) - new Date(currentAllocation.startsAt)) / 60000))
    : null;
  const currentDuration = options?.currentDurationMinutes ?? allocationDuration;
  const currentBooking = currentDetail?.booking || bookingSummary;
  const configuredRoomType = bookingOptions?.roomTypes?.find((type) =>
    (options?.currentRoomTypeId && type.id === options.currentRoomTypeId)
    || (options?.currentRoomTypeName && type.name === options.currentRoomTypeName)
    || (booking.roomTypeName && type.name === booking.roomTypeName));
  const comboChoices = options?.extensionCombos?.length ? options.extensionCombos : configuredRoomType?.combos || [];
  const extensionCombos = comboChoices.filter((combo) => comboCanStartAt(combo, options?.plannedEnd || booking.plannedEnd, timezone));
  const selectedCombo = extensionCombos.find((combo) => combo.id === comboId);
  const running = meter?.status === 'RUNNING';
  const stopped = meter?.status === 'STOPPED';
  const elapsedSeconds = meter ? Math.max(0, Math.floor(((stopped ? new Date(meter.stoppedAt).getTime() : clockNow) - new Date(meter.billedFrom).getTime()) / 1000)) : 0;
  const clockText = `${String(Math.floor(elapsedSeconds / 3600)).padStart(2, '0')}:${String(Math.floor(elapsedSeconds % 3600 / 60)).padStart(2, '0')}:${String(elapsedSeconds % 60).padStart(2, '0')}`;
  const estimatedAmount = meterQuote?.overtimeAmount ?? (stopped ? meter?.amount : null);

  return <div className="booking-stay-backdrop" role="presentation">
    <form className="booking-stay-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-stay-title" onSubmit={submit}>
      <header className="booking-stay-heading">
        <div className="booking-stay-heading-info"><span className="booking-stay-booking-code">Booking {booking.code}</span><h2 id="booking-stay-title">Đổi phòng hoặc gia hạn</h2>
          <p className="booking-stay-current-room"><span>Đang ở</span><strong>{options?.currentRoomCode || booking.roomCode || 'phòng hiện tại'} · {options?.currentRoomTypeName || booking.roomTypeName}</strong></p>
        </div>
        <button type="button" className="booking-panel-close" onClick={onClose} aria-label="Đóng">×</button>
      </header>
      <div className="booking-stay-tabs" role="tablist" aria-label="Thao tác phòng đang sử dụng">
        <button type="button" role="tab" aria-selected={mode === 'change'} className={mode === 'change' ? 'is-active' : ''} disabled={Boolean(meter)} onClick={() => { setMode('change'); setError(''); }}>Đổi phòng</button>
        <button type="button" role="tab" aria-selected={mode === 'extend'} className={mode === 'extend' ? 'is-active' : ''} onClick={() => { setMode('extend'); setError(''); }}>Gia hạn</button>
      </div>
      {loading ? <p className="booking-stay-loading" role="status">Đang tải thông tin phòng và giá…</p> : <>
        {mode === 'change' ? <>
          <label className="booking-stay-field"><span>Lý do đổi phòng</span>
            <select value={cause} onChange={(event) => setCause(event.target.value)} required>
              <option value="">Chọn lý do đổi phòng</option>
              <option value="BRANCH_FAULT">Lỗi phòng/thiết bị từ phía cơ sở</option>
              <option value="CUSTOMER">Khách muốn đổi theo nhu cầu</option>
            </select>
            <small>{cause === 'BRANCH_FAULT'
              ? 'Giữ giá đã chốt cho phần còn lại, không thu thêm do đổi sang hạng phòng cao hơn.'
              : cause === 'CUSTOMER'
                ? 'Giá phần còn lại áp dụng theo hạng phòng mới; chênh lệch (nếu có) được ghi vào bill thanh toán.'
                : 'Chọn lỗi từ phía cơ sở hoặc yêu cầu đổi theo nhu cầu của khách.'}</small>
          </label>
          <label className="booking-stay-field"><span>Chuyển sang phòng</span>
            <select value={targetRoomId} onChange={(event) => setTargetRoomId(event.target.value)} required>
              <option value="">Chọn phòng còn trống</option>
              {(options?.availableRooms || []).map((room) => <option key={room.id} value={room.id}>{room.code} · {room.name} · {room.operatingStatus}</option>)}
            </select>
            {!options?.availableRooms?.length && <small>Hiện không có phòng phù hợp còn trống đến giờ trả.</small>}
          </label>
          <label className="booking-stay-field"><span>Ghi chú (không bắt buộc)</span>
            <textarea aria-label="Ghi chú đổi phòng" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} rows={2} placeholder="Thêm ghi chú cho lần đổi phòng này…" />
          </label>
          {roomQuote && <section className="booking-stay-quote" aria-live="polite">
            <div><span>Đã dùng ở phòng hiện tại</span><strong>{formatVnd(roomQuote.usedSegmentAmount)}</strong></div>
            <div><span>{cause === 'BRANCH_FAULT' ? 'Phần còn lại theo giá đã chốt' : `Phần còn lại ở ${selectedRoom?.code || 'phòng mới'}`}</span><strong>{formatVnd(roomQuote.nextSegmentAmount)}</strong></div>
            <div><span>Điều chỉnh</span><strong>{Number(roomQuote.adjustmentAmount) > 0 ? '+' : ''}{formatVnd(roomQuote.adjustmentAmount)}</strong></div>
            <small>{cause === 'BRANCH_FAULT'
              ? 'Lỗi từ cơ sở: không tính chênh lệch giá phòng cho phần còn lại.'
              : 'Khách yêu cầu đổi: chênh lệch giá phòng (nếu có) được ghi vào bill thanh toán.'}</small>
            <div className="booking-stay-quote-total"><span>Tổng tiền sau đổi phòng</span><strong>{formatVnd(roomQuote.projectedAmount)}</strong></div>
          </section>}
        </> : <>
          <section className="booking-stay-current-summary" aria-label="Thông tin booking hiện tại">
            <h3>Booking hiện tại</h3>
            <dl>
              <div><dt>Combo hiện tại</dt><dd>{currentComboName}</dd></div>
              <div><dt>Thời lượng đang đặt</dt><dd>{durationText(meter ? Math.max(0, Math.floor((new Date(currentBooking.plannedEnd) - new Date(currentBooking.plannedStart)) / 60000)) : currentDuration)}</dd></div>
              <div><dt>Giờ hẹn</dt><dd>{dateTimeText(currentBooking.plannedStart, timezone)}</dd></div>
              <div><dt>Check-in thực tế</dt><dd>{dateTimeText(currentBooking.actualCheckin, timezone)}</dd></div>
              <div><dt>Giờ trả hiện tại</dt><dd>{dateTimeText(options?.plannedEnd || currentBooking.plannedEnd, timezone)}</dd></div>
            </dl>
            <p>{running
              ? 'Đồng hồ giờ lẻ đang chạy; phụ thu sẽ được chốt khi dừng đồng hồ.'
              : reservationExpired
                ? 'Giờ trả đã qua; booking vẫn đang chờ check-out.'
              : 'Giờ hẹn ban đầu được giữ nguyên. Đồng hồ giờ lẻ chỉ bật khi hết thời lượng đặt mà khách chưa check-out.'}</p>
          </section>
          {!meter && !reservationExpired && <fieldset className="booking-stay-cause"><legend>Chọn cách gia hạn</legend>
            <label><input type="radio" name="extension-mode" value="COMBO" checked={pricingMode === 'COMBO'} onChange={() => setPricingMode('COMBO')} />Mua thêm combo thời gian</label>
            <label><input type="radio" name="extension-mode" value="HOURLY" checked={pricingMode === 'HOURLY'} onChange={() => setPricingMode('HOURLY')} />Gia hạn giờ lẻ</label>
          </fieldset>}
          {!meter && !reservationExpired && pricingMode === 'COMBO' && extensionCombos.length > 0 ? <label className="booking-stay-field"><span>Combo áp dụng cho {options?.currentRoomTypeName || booking.roomTypeName}</span>
            <select value={comboId} onChange={(event) => setComboId(event.target.value)} required>
              <option value="">Chọn combo</option>
              {extensionCombos.map((combo) => <option key={combo.id} value={combo.id}>{combo.name} · {combo.durationMinutes / 60} giờ · {formatVnd(combo.price)}</option>)}
            </select>
          </label> : !meter && !reservationExpired && pricingMode === 'COMBO' ? <p className="booking-stay-empty-combos">Không có combo phù hợp với hạng phòng và giờ trả hiện tại.</p>
            : null}
          {!meter && !reservationExpired && pricingMode === 'HOURLY' && <label className="booking-stay-field"><span>Số giờ lẻ muốn gia hạn</span>
            <select value={hourlyHours} onChange={(event) => setHourlyHours(event.target.value)}>
              {Array.from({ length: 12 }, (_, index) => index + 1).map((hours) => <option key={hours} value={hours}>{hours} giờ</option>)}
            </select>
            <small>Phụ thu tính theo số giờ đã chọn; giá cụ thể hiển thị ở phần báo giá.</small>
          </label>}
          {meterVisible && <section className={`booking-stay-meter${running ? ' is-running' : ''}`} aria-live="polite">
              <span className="booking-stay-meter-label">{running ? 'Đang tính giờ lẻ' : stopped ? 'Đồng hồ đã dừng' : 'Tính theo thời gian thực dùng'}</span>
              <strong className="booking-stay-meter-clock" role="timer">{clockText}</strong>
              {meter ? <><div><span>Đơn giá lúc bắt đầu</span><strong>{formatVnd(meter.hourlyPrice)}</strong></div>
                <div><span>{stopped ? 'Đã chốt' : 'Tiền quá giờ tạm tính'}</span><strong>{estimatedAmount == null ? 'Đang tải giá…' : formatVnd(estimatedAmount)}</strong></div>
                <div><span>Giữ phòng đến</span><strong>{dateTimeText(meter.reservedUntil, timezone)}</strong></div>
                {running && clockNow > new Date(meter.reservedUntil).getTime() && <p className="booking-stay-meter-warning" role="alert">Đã quá hạn giữ phòng. Dừng đồng hồ và kiểm tra booking kế tiếp.</p>}
                <small>{stopped ? 'Thời lượng và phụ thu đã được chốt vào booking.' : 'Miễn phí tối đa 5 phút; vượt ngưỡng tính đủ số giây theo từng khung giá và làm tròn tiền lên đồng.'}</small>
              </> : <p>Phụ thu tính từ giờ trả dự kiến theo từng khung giá. Miễn phí nếu quá giờ không quá 5 phút.</p>}
            </section>}
          {!meter && !reservationExpired && extensionQuote && <section className="booking-stay-quote" aria-live="polite">
            <div><span>Phòng được giữ thêm đến</span><strong>{dateTimeText(extensionQuote.endsAt, timezone)}</strong></div>
            <div><span>{pricingMode === 'HOURLY' ? `Gia hạn ${hourlyHours} giờ lẻ` : selectedCombo?.name || 'Combo gia hạn'}</span><strong>{formatVnd(extensionQuote.quote.expectedAmount)}</strong></div>
            <small>{pricingMode === 'HOURLY' ? 'Áp dụng giá giờ theo thời điểm sử dụng; phòng sẽ được kiểm tra trùng lịch khi xác nhận.' : 'Áp dụng giá combo đã cấu hình. Phòng sẽ được kiểm tra trùng lịch khi lưu.'}</small>
          </section>}
        </>}
      </>}
      {error && <p className="booking-inline-error" role="alert">{error}</p>}
      <footer className="booking-stay-actions">
        <button type="button" className="booking-button booking-button-secondary" onClick={onClose} disabled={active}>Đóng</button>
        {!stopped && <button type="submit" className="booking-button booking-button-primary" disabled={active || loading || (mode === 'change' ? !targetRoomId || !roomQuote || Boolean(meter) : meterAction ? false : !extensionQuote)}>{active ? 'Đang xử lý…' : mode === 'change' ? 'Xác nhận đổi phòng' : meterAction === 'meterStop' ? 'Dừng tính giờ' : meterAction === 'meterStart' ? 'Bắt đầu tính giờ' : 'Xác nhận gia hạn'}</button>}
      </footer>
    </form>
  </div>;
}
