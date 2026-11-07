import { formatVnd } from '../data/bookingData';
import '../styles/checkout.css';

const dateTime = (value) =>
  value
    ? new Date(value).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : '—';

export default function CheckoutReceipt({ data, className = 'co-card' }) {
  const amount = Number(data.amount);
  const depositAmount = Number(data.depositApplied || 0);
  const settled = data.status === 'SETTLED';
  const paymentDue = Number(data.remaining ?? amount - depositAmount - Number(data.paid || 0));
  return (
    <aside className={className}>
      <h2 className="co-invoice-title">
        Hóa đơn
        <small>Mã hóa đơn: {data.number}</small>
      </h2>
      <p>
        {data.room} · {data.customer}
      </p>
      <p>
        Check-in:
        {dateTime(data.checkin)}
        <br />
        Chốt sử dụng:
        {dateTime(data.checkoutAt)}
      </p>
      <Lines lines={data.lines} />
      <Summary
        lines={data.lines}
        amount={amount}
        depositAmount={depositAmount}
        paid={Number(data.paid || 0)}
        paymentDue={paymentDue}
      />
      <small>
        {settled ? 'Đã hoàn tất thanh toán' : 'Hóa đơn đang chờ hoàn tất thanh toán'}
      </small>
    </aside>
  );
}

export function Info({ label, value }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
export function Lines({ lines }) {
  return lines?.length ? (
    <table className="co-lines">
      <thead>
        <tr>
          <th>Nội dung</th>
          <th>SL</th>
          <th>Thành tiền</th>
        </tr>
      </thead>
      <tbody>
        {lines.map((line, index) => (
          <tr key={index}>
            <td>{line.description}</td>
            <td>{Number(line.quantity)}</td>
            <td>{formatVnd(line.amount)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  ) : (
    <p>Không có khoản phát sinh.</p>
  );
}
export function Summary({
  lines,
  extra = 0,
  amount,
  depositAmount,
  paid = 0,
  paymentDue,
  preview = false,
}) {
  return (
    <>
      <dl className="co-totals">
        {[
          ['ROOM', 'Tiền phòng'],
          ['FNB', 'F&B'],
          ['SURCHARGE', 'Phụ phí'],
        ].map(([category, label]) => (
          <Info
            key={category}
            label={label}
            value={formatVnd(
              lines
                .filter((line) => line.category === category)
                .reduce(
                  (sum, line) => sum + Number(line.amount),
                  category === 'SURCHARGE' ? extra : 0
                )
            )}
          />
        ))}
        <Info label="Tổng hóa đơn (gồm phụ phí)" value={formatVnd(amount)} />
        <Info
          label={preview ? 'Tiền cọc sẽ áp dụng' : 'Tiền cọc đã áp dụng'}
          value={depositAmount > 0 ? `−${formatVnd(depositAmount)}` : formatVnd(0)}
        />
        {paid > 0 && <Info label="Trừ khoản đã thanh toán" value={`−${formatVnd(paid)}`} />}
      </dl>
      <div className="co-total">
        <small>CÒN PHẢI THANH TOÁN</small>
        <strong>{formatVnd(paymentDue)}</strong>
      </div>
    </>
  );
}
