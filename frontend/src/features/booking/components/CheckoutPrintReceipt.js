import { createPortal } from 'react-dom';
import logo from '../../../assets/images/logo.png';
import { formatVnd } from '../data/bookingData';
import '../styles/checkout-print.css';

const money = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });
const quantity = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 });
const paymentNames = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản' };

function receiptDate(value, timezone, options) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', {
    ...options, timeZone: timezone,
  }).format(date);
}

function ReceiptAmount({ label, value, className }) {
  return <div className={className}><dt>{label}</dt><dd>{value}</dd></div>;
}

export default function CheckoutPrintReceipt({ data, fallbackBranchName }) {
  const timezone = data.branchTimezone || 'Asia/Ho_Chi_Minh';
  const dateOptions = { day: '2-digit', month: '2-digit', year: 'numeric' };
  const timeOptions = { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' };
  const checkInDate = receiptDate(data.checkin, timezone, dateOptions);
  const checkoutDate = receiptDate(data.checkoutAt, timezone, dateOptions);
  const checkInTime = receiptDate(data.checkin, timezone, timeOptions);
  const checkoutTime = receiptDate(data.checkoutAt, timezone, timeOptions);
  const rounding = Number(data.roundingAdjustment || 0);
  const discount = Number(data.discountAmount || 0);
  const deposit = Number(data.depositApplied || 0);
  const payments = new Map();
  if (deposit > 0) payments.set('Tiền cọc đã áp dụng', deposit);
  for (const payment of data.payments || []) {
    const label = paymentNames[payment.method] || 'Khoản đã thanh toán';
    payments.set(label, (payments.get(label) || 0) + Number(payment.amount));
  }

  return createPortal(
    <article className="co-print-receipt" aria-label="Phiếu tính tiền">
      <header className="co-print-brand">
        <img src={logo} alt="" width="386" height="274" />
        <strong>GENZ CINEMA</strong>
        {data.branchPhone && <span>Hotline {data.branchPhone}</span>}
        <strong className="co-print-branch">{data.branchName || fallbackBranchName}</strong>
      </header>

      <section className="co-print-details" aria-label="Thông tin hóa đơn">
        {data.customer && <p className="co-print-customer">Khách hàng <strong>{data.customer}</strong></p>}
        <div className="co-print-id">
          <strong className="co-print-room">{data.room || '—'}</strong>
          <div><h2>PHIẾU TÍNH TIỀN</h2><span>{data.number}</span></div>
        </div>
        <div className="co-print-times">
          <span>Giờ vào {checkInTime}{checkInDate !== '—' && checkInDate !== checkoutDate && <small>{checkInDate}</small>}</span>
          <span>Giờ ra {checkoutTime}{checkoutDate !== '—' && <small>{checkoutDate}</small>}</span>
        </div>
      </section>

      <table className="co-print-lines">
        <colgroup><col /><col /><col /><col /></colgroup>
        <thead><tr><th scope="col">Tên hàng</th><th scope="col">Đơn giá</th><th scope="col">SL</th><th scope="col">Thành tiền</th></tr></thead>
        <tbody>{(data.lines || []).map((line, index) => (
          <tr key={index}>
            <td>{line.description}</td>
            <td>{line.unitPrice == null ? '—' : money.format(line.unitPrice)}</td>
            <td>{quantity.format(line.quantity)}</td>
            <td>{money.format(line.amount)}</td>
          </tr>
        ))}</tbody>
      </table>

      <dl className="co-print-amounts">
        <ReceiptAmount label="Tổng tiền hàng" value={formatVnd(data.grossAmount ?? data.amount)} />
        <ReceiptAmount label="Chiết khấu" value={discount > 0 ? `−${formatVnd(discount)}` : formatVnd(0)} />
        {rounding !== 0 && <ReceiptAmount label="Điều chỉnh làm tròn" value={`${rounding > 0 ? '+' : '−'}${formatVnd(Math.abs(rounding))}`} />}
        <ReceiptAmount label="Tổng cộng" value={formatVnd(Number(data.amount) + rounding)} className="co-print-total" />
      </dl>

      {payments.size > 0 && <section className="co-print-payment">
        <h3>Phương thức thanh toán</h3>
        <dl className="co-print-amounts">{Array.from(payments, ([label, amount]) => (
          <ReceiptAmount key={label} label={label} value={formatVnd(amount)} />
        ))}</dl>
      </section>}

      <footer className="co-print-footer">
        <strong>LƯU Ý QUAN TRỌNG</strong>
        <p>Nếu được yêu cầu chuyển tiền vào tài khoản cá nhân, vui lòng gọi <strong>0876 583 333</strong> để phản ánh.</p>
        <p>Xin cảm ơn quý khách và hẹn gặp lại!</p>
      </footer>
    </article>,
    document.body,
  );
}
