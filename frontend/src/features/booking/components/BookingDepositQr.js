import { useEffect, useState } from 'react';
import { bookingApi } from '../services/bookingApi';
import { apiErrors } from '../../auth/services/authApi';
import { formatVnd } from '../data/bookingData';

export default function BookingDepositQr({ bookingId, onBack }) {
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    let timer;
    setError('');
    const report = (failure) => {
      const errors = apiErrors(failure);
      if (active) setError(errors.form || Object.values(errors)[0] || 'Không tải được QR cọc.');
    };
    const poll = async () => {
      try {
        const result = await bookingApi.depositPayment(bookingId);
        if (!active) return;
        setPayment(result);
        setError('');
        if (result.status === 'PENDING') timer = setTimeout(poll, 4000);
      } catch (failure) { report(failure); if (active) timer = setTimeout(poll, 8000); }
    };
    bookingApi.planDeposit(bookingId).then((result) => {
      if (!active) return;
      setPayment(result);
      if (result.status === 'PENDING') timer = setTimeout(poll, 4000);
    }).catch(report);
    return () => { active = false; clearTimeout(timer); };
  }, [bookingId, refresh]);

  return <section className="booking-create-card booking-deposit-qr" aria-live="polite">
    <h2>Cọc chuyển khoản · {payment?.bookingCode || 'Booking đã tạo'}</h2>
    <p>Booking đã được giữ phòng. Cọc bằng 30% giá booking, làm tròn đến đồng.</p>
    {error && <p className="booking-inline-error" role="alert">{error}</p>}
    {!payment && !error && <p>Đang lấy QR cọc…</p>}
    {payment?.status === 'SETTLED' ? <p className="booking-readiness is-ready">Đã nhận cọc {formatVnd(payment.receivedAmount)}.</p> : payment && <>
      <h3>{formatVnd(payment.amount)}</h3>
      {payment.status === 'PENDING' && payment.qrUrl ? <>
        <img src={payment.qrUrl} alt={`QR chuyển khoản cọc ${formatVnd(payment.amount)}`} width="280" height="280" style={{ maxWidth: '100%', objectFit: 'contain' }} />
        <p>{payment.bank} · {payment.account}<br />{payment.holder}</p>
        <p>Nội dung: <strong>{payment.reference}</strong></p>
        <p>QR hết hạn lúc {new Date(payment.expiresAt).toLocaleString('vi-VN')}. Chuyển đúng số tiền và nội dung; hệ thống tự xác nhận khi nhận thông báo ngân hàng.</p>
      </> : <p>QR đã hết hạn hoặc giá booking đã thay đổi. Lấy QR mới trước khi chuyển tiền.</p>}
    </>}
    {(error || (payment && !['PENDING', 'SETTLED'].includes(payment.status))) && <button type="button" className="booking-button booking-button-primary" onClick={() => setRefresh((value) => value + 1)}>Lấy lại QR cọc</button>}
    <button type="button" className="booking-button booking-button-secondary" onClick={onBack}>Về danh sách booking</button>
    <p>Rời màn hình này không hủy booking. Có thể mở lại QR trong chi tiết booking chưa nhận cọc.</p>
  </section>;
}
