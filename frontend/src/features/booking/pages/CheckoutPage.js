/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Kiểm tra phòng, chốt bill và thanh toán tiền mặt/SePay/kết hợp.
 * Updated: 15/10/2026 - Hiển thị cọc đã áp dụng và phần tiền còn phải thu.
 */
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AppShell } from './BookingManagementPage';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { checkoutApi as api } from '../services/checkoutApi';
import { formatVnd, formatMoneyInput } from '../data/bookingData';
import '../styles/checkout.css';
const usedTime = (data) => {
  const minutes = Math.max(
    0,
    Math.floor((new Date(data.checkoutAt).getTime() - new Date(data.checkin).getTime()) / 60000)
  );
  return Number.isFinite(minutes)
    ? Math.floor(minutes / 60) + ' giờ ' + (minutes % 60) + ' phút'
    : '—';
};
const dateTime = (value) =>
  value
    ? new Date(value).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : '—';
const errorText = (error) => {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || 'Không thể xử lý yêu cầu.';
};
export default function CheckoutPage() {
  const { bookingId, billId } = useParams();
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [data, setData] = useState(null);
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [inspected, setInspected] = useState(false);
  const [overtimeConfirmed, setOvertimeConfirmed] = useState(false);
  const [unservedConfirmed, setUnservedConfirmed] = useState(false);
  const [inspectionNote, setInspectionNote] = useState('');
  const [fees, setFees] = useState([]);
  const [method, setMethod] = useState('CASH');
  const [cashTendered, setCashTendered] = useState('');
  useEffect(() => {
    let active = true;
    setData(null);
    setError('');
    setInspected(false);
    setOvertimeConfirmed(false);
    setUnservedConfirmed(false);
    setFees([]);
    setInspectionNote('');
    authApi
      .me()
      .then(async (identity) => {
        if (!active) return;
        if (identity.role !== 'RECEPTIONIST') {
          navigate(identity.destination || '/dang-nhap', {
            replace: true,
          });
          return;
        }
        setUser(identity);
        const result = bookingId
          ? await api.preview(bookingId)
          : billId
            ? await api.bill(billId)
            : await api.list();
        if (!active) return;
        if (!bookingId && !billId) setRows(result);
        else if (bookingId && result.id)
          navigate(`/le-tan/bills/${result.id}`, {
            replace: true,
          });
        else setData(result);
      })
      .catch((e) => {
        if (!active) return;
        if (e.response?.status === 401)
          navigate('/dang-nhap', {
            replace: true,
          });
        else setError(errorText(e));
      });
    return () => {
      active = false;
    };
  }, [bookingId, billId, navigate]);

  // Chỉ đọc trạng thái server, không tự xác nhận thanh toán dựa trên giao diện.
  useEffect(() => {
    if (!billId || !data?.request?.id || data.status === 'SETTLED') return undefined;
    let active = true;
    const timer = window.setInterval(() => {
      api
        .bill(billId)
        .then((result) => {
          if (active) setData(result);
        })
        .catch((e) => {
          if (active) setError(errorText(e));
        });
    }, 4000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [billId, data?.request?.id, data?.status]);
  async function run(action) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }
  function issue(event) {
    event.preventDefault();
    if (fees.some((fee) => !Number.isSafeInteger(Number(fee.amount)) || Number(fee.amount) <= 0)) {
      setError('Phụ phí phải là số tiền nguyên lớn hơn 0.');
      return;
    }
    run(async () => {
      let result;
      try {
        result = await api.issue(bookingId, {
          inspected,
          inspectionNote,
          overtimeConfirmed,
          overtimeAmount: data.overtimeAmount,
          unservedConfirmed,
          quotedAt: data.checkoutAt,
          quoteToken: data.quoteToken,
          surcharges: fees.map((fee) => ({
            reason: fee.reason,
            amount: Number(fee.amount),
          })),
        });
      } catch (failure) {
        if (failure.response?.status === 409) {
          setOvertimeConfirmed(false);
          setUnservedConfirmed(false);
          try {
            setData(await api.preview(bookingId));
          } catch {
            /* Giữ lỗi lập bill để lễ tân biết cần xử lý. */
          }
        }
        throw failure;
      }
      navigate(`/le-tan/bills/${result.id}`);
    });
  }
  function pay(event) {
    event.preventDefault();
    if (
      method !== 'BANK_TRANSFER' &&
      (!Number.isSafeInteger(Number(cashTendered)) ||
        Number(cashTendered) < 0 ||
        (method === 'CASH' && Number(cashTendered) < paymentDue))
    ) {
      setError(
        'Nhập số tiền mặt hợp lệ; nếu trả toàn bộ bằng tiền mặt thì tiền khách đưa phải đủ số cần thu.'
      );
      return;
    }
    run(async () => {
      const paymentMethod =
        method === 'MIXED'
          ? cashDue === 0
            ? 'BANK_TRANSFER'
            : bankDue === 0
              ? 'CASH'
              : 'MIXED'
          : method;
      const result = await api.plan(billId, {
        method: paymentMethod,
        cashAmount: paymentMethod === 'MIXED' ? cashDue : null,
        cashTendered: paymentMethod === 'BANK_TRANSFER' ? null : Number(cashTendered),
      });
      setData(result);
      if (result.readyToComplete) setData(await api.complete(billId));
    });
  }
  async function logout() {
    await run(async () => {
      await authApi.logout();
      navigate('/dang-nhap', {
        replace: true,
      });
    });
  }
  function changePaymentMethod() {
    run(async () => {
      // Hủy yêu cầu trên server trước khi mở lại lựa chọn, không chỉ ẩn QR ở giao diện.
      setData(await api.changeMethod(billId));
      setMethod('CASH');
      setCashTendered('');
    });
  }
  const planned = Boolean(data?.request || Number(data?.plannedCash) > 0 || data?.payments?.length);
  const settled = data?.status === 'SETTLED';
  const extra = fees.reduce((sum, fee) => sum + Number(fee.amount || 0), 0);
  const amount = Number(data?.amount || 0) + (bookingId ? extra : 0);
  const depositAmount = Number(data?.depositApplied ?? data?.depositBalance ?? 0);
  const paymentDue = Number(data?.remaining ?? Math.max(0, amount - depositAmount));
  const cashDue =
    method === 'BANK_TRANSFER'
      ? 0
      : method === 'MIXED'
        ? Math.min(Number(cashTendered || 0), paymentDue)
        : paymentDue;
  const bankDue = method === 'CASH' ? 0 : Math.max(0, paymentDue - cashDue);
  const change = Math.max(0, Number(cashTendered || 0) - cashDue);
  if (!user)
    return (
      <div className="checkout-page">
        <p role={error ? 'alert' : 'status'}>{error || 'Đang kiểm tra tài khoản…'}</p>
      </div>
    );
  return (
    <AppShell
      user={user}
      branchName={user.branchName}
      busy={busy}
      onLogout={logout}
      breadcrumb={bookingId ? 'Checkout' : 'Thanh toán'}
      activeTab="payments"
      hideSearch
    >
      <section className="checkout-page">
        <header className="co-heading">
          <div>
            <h1>{bookingId ? `Checkout · ${data?.room || ''}` : 'Thanh toán'}</h1>
            <p>
              {data
                ? `${data.number || data.bookingCode} · ${data.customer}`
                : 'Tra cứu bill của cơ sở'}
            </p>
          </div>
          <button onClick={() => navigate('/le-tan/phong')}>‹ Về phòng</button>
        </header>
        {data?.bookingId && (
          <section className="co-card">
            <h2>Yêu cầu hóa đơn thuế</h2>
            <p>Nếu khách yêu cầu hóa đơn, ghi nhận thông tin và gửi kế toán xử lý bên ngoài.</p>
            <button
              type="button"
              onClick={() => navigate(`/le-tan/hoa-don-thue?booking=${data.bookingId}`)}
            >
              Ghi nhận / theo dõi yêu cầu hóa đơn
            </button>
          </section>
        )}
        {error && (
          <div className="co-error" role="alert">
            {error}
          </div>
        )}
        {!bookingId && !billId ? (
          <div className="co-card co-list">
            <h2>Bill của cơ sở</h2>
            <table>
              <thead>
                <tr>
                  <th>Hóa đơn</th>
                  <th>Khách</th>
                  <th>Tổng tiền</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td>{row.number}</td>
                    <td>{row.customer}</td>
                    <td>{formatVnd(row.amount)}</td>
                    <td>{row.status === 'SETTLED' ? 'Đã thanh toán' : 'Chờ thanh toán'}</td>
                    <td>
                      <button onClick={() => navigate(`/le-tan/bills/${row.id}`)}>Chi tiết</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!rows.length && <p>Chưa có bill.</p>}
          </div>
        ) : !data ? (
          <p>Đang tải dữ liệu…</p>
        ) : bookingId ? (
          <form onSubmit={issue} className="co-grid">
            <div>
              <section className="co-card">
                <h2>1 Tiền phòng</h2>
                <div className="co-time-grid">
                  <Info label="Check-in" value={dateTime(data.checkin)} />
                  <Info label="Giờ trả dự kiến" value={dateTime(data.plannedEnd)} />
                  <Info label="Chốt sử dụng" value={dateTime(data.checkoutAt)} />
                  <Info label="Đã sử dụng" value={usedTime(data)} />
                </div>
                <Lines lines={data.lines.filter((line) => line.category === 'ROOM')} />
                {data.overtime?.length > 0 && (
                  <div className="co-warning">
                    <strong>Tiền quá giờ cần xác nhận</strong>
                    {data.overtime.map((line, index) => (
                      <p key={index}>
                        {dateTime(line.from)} → {dateTime(line.to)}: {line.seconds} giây ×{' '}
                        {formatVnd(line.hourlyPrice)}/giờ ={' '}
                        <strong>{formatVnd(line.amount)}</strong>
                      </p>
                    ))}
                    <p>
                      Miễn phí 15 phút đầu sau giờ trả. Sau đó tính thời gian thực dùng theo từng
                      khung giá, làm tròn tiền lên đồng. Khoản này đã được cộng vào tổng hóa đơn.
                    </p>
                    <label className="co-inspection">
                      <input
                        type="checkbox"
                        required
                        checked={overtimeConfirmed}
                        onChange={(e) => setOvertimeConfirmed(e.target.checked)}
                      />
                      Xác nhận tiền quá giờ {formatVnd(data.overtimeAmount)}
                    </label>
                  </div>
                )}
                <button
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      setOvertimeConfirmed(false);
                      setUnservedConfirmed(false);
                      setData(await api.preview(bookingId));
                    })
                  }
                >
                  Cập nhật tiền phòng
                </button>
              </section>
              <section className="co-card">
                <h2>2 Order F&B</h2>
                <Lines lines={data.lines.filter((line) => line.category === 'FNB')} />
                {data.pendingOrders?.length > 0 && (
                  <div className="co-warning">
                    <p>
                      Booking còn {data.unservedItemCount} món chưa phục vụ. Bạn vẫn có thể tiếp tục
                      trả phòng.
                    </p>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        setUnservedConfirmed(
                          window.confirm(
                            `Booking còn ${data.unservedItemCount} món chưa phục vụ. Bạn vẫn có thể tiếp tục trả phòng. Bạn có muốn tiếp tục?`
                          )
                        )
                      }
                    >
                      Xác nhận tiếp tục trả phòng
                    </button>
                    {unservedConfirmed && (
                      <p role="status">Đã xác nhận tiếp tục với các món chưa phục vụ.</p>
                    )}
                  </div>
                )}
              </section>
              <section className="co-card">
                <div className="co-section-title">
                  <h2>3 Phụ phí</h2>
                  <button
                    type="button"
                    disabled={fees.length >= 20}
                    onClick={() =>
                      setFees([
                        ...fees,
                        {
                          reason: '',
                          amount: '',
                        },
                      ])
                    }
                  >
                    + Thêm phụ phí
                  </button>
                </div>
                {!fees.length && <p>Chưa có phụ phí phát sinh.</p>}
                {fees.map((fee, index) => (
                  <div className="co-fee" key={index}>
                    <input
                      aria-label={`Lý do phụ phí ${index + 1}`}
                      required
                      maxLength="180"
                      placeholder="Lý do phụ phí"
                      value={fee.reason}
                      onChange={(e) =>
                        setFees(
                          fees.map((f, i) =>
                            i === index
                              ? {
                                  ...f,
                                  reason: e.target.value,
                                }
                              : f
                          )
                        )
                      }
                    />
                    <input
                      aria-label={`Số tiền phụ phí ${index + 1}`}
                      type="text"
                      inputMode="numeric"
                      required
                      placeholder="Số tiền"
                      value={formatMoneyInput(fee.amount)}
                      onChange={(e) =>
                        /^[0-9.]*$/.test(e.target.value) &&
                        setFees(
                          fees.map((f, i) =>
                            i === index
                              ? {
                                  ...f,
                                  amount: e.target.value.replace(/\./g, ''),
                                }
                              : f
                          )
                        )
                      }
                    />
                    <button
                      type="button"
                      aria-label="Xóa phụ phí"
                      onClick={() => setFees(fees.filter((_, i) => i !== index))}
                    >
                      ×
                    </button>
                  </div>
                ))}
                <small>Chỉ thêm phụ phí khi có lý do.</small>
              </section>
              <section className="co-card">
                <h2>4 Tình trạng phòng</h2>
                <label className="co-inspection">
                  <input
                    type="checkbox"
                    required
                    checked={inspected}
                    onChange={(e) => setInspected(e.target.checked)}
                  />
                  Đã kiểm tra phòng và xác nhận các khoản phí
                </label>
                <textarea
                  aria-label="Kết quả kiểm tra phòng"
                  maxLength="2000"
                  placeholder="Kết quả kiểm tra / ghi chú"
                  value={inspectionNote}
                  onChange={(e) => setInspectionNote(e.target.value)}
                />
                {data.hasPendingRefund && (
                  <div className="co-warning">
                    Cọc đang có yêu cầu hoàn xử lý. Cần giải quyết yêu cầu trước khi lập bill.
                  </div>
                )}
                {depositAmount > amount && (
                  <div className="co-warning">
                    Số dư cọc vượt tổng bill. Cần kiểm tra cọc trước khi thanh toán.
                  </div>
                )}
              </section>
            </div>
            <aside className="co-card co-summary">
              <h2>Tóm tắt thanh toán</h2>
              <Summary
                lines={data.lines}
                extra={extra}
                amount={amount}
                depositAmount={depositAmount}
                paymentDue={paymentDue}
                preview
              />
              <p className="co-note">
                Giữ đơn giá đã xác nhận. Lập bill chốt sử dụng đến thời điểm báo giá hiển thị, có
                hiệu lực 5 phút. Bấm Cập nhật tiền phòng nếu khách vẫn tiếp tục sử dụng.
              </p>
              <button
                className="co-primary"
                disabled={
                  user.readOnly ||
                  busy ||
                  !inspected ||
                  (data.overtime?.length > 0 && !overtimeConfirmed) ||
                  data.hasPendingRefund ||
                  depositAmount > amount ||
                  (!!data.pendingOrders?.length && !unservedConfirmed)
                }
              >
                {busy
                  ? 'Đang chốt bill…'
                  : data.overtime?.length > 0
                    ? 'Xác nhận tiền quá giờ & lập bill'
                    : 'Tiếp tục thanh toán'}
              </button>
            </aside>
          </form>
        ) : (
          <>
            <div className="co-amount-banner">
              <small>{settled ? 'ĐÃ THANH TOÁN' : 'SỐ TIỀN CẦN THANH TOÁN'}</small>
              <strong>{formatVnd(settled ? amount : data.remaining)}</strong>
            </div>
            <div className="co-grid">
              <section className="co-card">
                <h2>{settled ? 'Thanh toán hoàn tất' : 'Phương thức thanh toán'}</h2>
                {settled ? (
                  <>
                    <div className="co-success">
                      Đã hoàn tất hóa đơn. Phòng đã trở về Trống và sẵn sàng nhận khách tiếp theo.
                    </div>
                    <button onClick={() => window.print()}>In hóa đơn</button>
                  </>
                ) : paymentDue === 0 && !planned ? (
                  <>
                    <div className="co-success">
                      Bill không còn khoản phải thu sau khi áp dụng cọc.
                    </div>
                    <button
                      className="co-primary"
                      disabled={busy}
                      onClick={() => run(async () => setData(await api.complete(billId)))}
                    >
                      Hoàn tất bill
                    </button>
                  </>
                ) : !planned ? (
                  <form onSubmit={pay}>
                    <div className="co-methods">
                      {[
                        ['CASH', 'Tiền mặt'],
                        ['BANK_TRANSFER', 'SePay'],
                        ['MIXED', 'Kết hợp'],
                      ].map(([value, label]) => (
                        <button
                          key={value}
                          type="button"
                          className={method === value ? 'selected' : ''}
                          onClick={() => setMethod(value)}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                    {method === 'CASH' && (
                      <p className="co-note">
                        Tiền mặt cần thu: <strong>{formatVnd(paymentDue)}</strong> (đã trừ cọc và
                        khoản đã thanh toán).
                      </p>
                    )}
                    {method !== 'BANK_TRANSFER' && (
                      <>
                        <label className="co-field">
                          Tiền mặt khách đưa
                          <input
                            type="text"
                            inputMode="numeric"
                            required
                            value={formatMoneyInput(cashTendered)}
                            aria-describedby="co-cash-tendered-help"
                            onChange={(e) =>
                              /^[0-9.]*$/.test(e.target.value) &&
                              setCashTendered(e.target.value.replace(/\./g, ''))
                            }
                          />
                          <small id="co-cash-tendered-help">
                            {method === 'MIXED'
                              ? 'Hệ thống tự trừ tiền mặt khỏi số còn phải thanh toán. Phần thiếu sẽ thanh toán bằng QR; đưa dư thì trả lại tiền thừa.'
                              : 'Nhập tiền khách đưa để tính tiền thừa. Tiền khách đưa phải đủ số cần thu.'}
                          </small>
                        </label>
                        <div className="co-quick">
                          {[paymentDue, 250000, 300000, 500000]
                            .filter(
                              (value, index, all) =>
                                value > 0 && value >= paymentDue && all.indexOf(value) === index
                            )
                            .map((value) => (
                              <button
                                key={value}
                                type="button"
                                onClick={() => setCashTendered(String(value))}
                              >
                                {value === paymentDue
                                  ? `Đưa đủ ${formatVnd(value)}`
                                  : formatVnd(value)}
                              </button>
                            ))}
                        </div>
                        <div className="co-success">
                          <small>TIỀN THỪA TRẢ KHÁCH</small>
                          <strong>{formatVnd(change)}</strong>
                        </div>
                      </>
                    )}
                    {method !== 'CASH' && (
                      <p className="co-note">
                        {bankDue > 0 ? (
                          <>
                            Cần chuyển khoản: <strong>{formatVnd(bankDue)}</strong> qua QR SePay.
                            Khoản chuyển được ghi nhận sau khi hệ thống xác nhận nhận tiền.
                          </>
                        ) : (
                          'Tiền mặt đã đủ thanh toán, không cần tạo QR.'
                        )}
                      </p>
                    )}
                    {!data.sepayReady && bankDue > 0 && (
                      <div className="co-warning">
                        Chưa cấu hình SePay. Bạn có thể dùng tiền mặt hoặc cấu hình ngân hàng trước.
                      </div>
                    )}
                    <button
                      className="co-primary"
                      disabled={busy || (!data.sepayReady && bankDue > 0)}
                    >
                      {busy
                        ? 'Đang xử lý…'
                        : bankDue === 0
                          ? 'Hoàn tất thanh toán tiền mặt'
                          : 'Tạo mã QR SePay'}
                    </button>
                  </form>
                ) : (
                  <>
                    <div className="co-methods">
                      {[
                        ['CASH', 'Tiền mặt'],
                        ['BANK_TRANSFER', 'SePay'],
                        ['MIXED', 'Kết hợp'],
                      ].map(([value, label]) => (
                        <button
                          key={value}
                          type="button"
                          disabled
                          className={
                            value ===
                            (data.request
                              ? Number(data.plannedCash) > 0 ||
                                data.payments?.some((payment) => payment.method === 'CASH')
                                ? 'MIXED'
                                : 'BANK_TRANSFER'
                              : 'CASH')
                              ? 'selected'
                              : ''
                          }
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                    {!data.payments?.length &&
                      (Number(data.plannedCash) > 0 ||
                        ['PENDING', 'EXPIRED'].includes(data.request?.status)) && (
                        <>
                          <button type="button" disabled={busy} onClick={changePaymentMethod}>
                            Đổi phương thức
                          </button>
                          <p className="co-note">
                            Đổi phương thức sẽ xóa số tiền nhập tạm và hủy QR hiện tại. Không tiếp
                            tục chuyển khoản bằng mã cũ.
                          </p>
                        </>
                      )}
                    {Number(data.plannedCash) > 0 && (
                      <div className="co-note">
                        Tiền mặt dự kiến: {formatVnd(data.plannedCash)} · chưa ghi nhận khoản thu.
                        <p>
                          Khách đưa: {formatVnd(data.plannedCashTendered)} · tiền thừa dự kiến:{' '}
                          {formatVnd(Number(data.plannedCashTendered) - Number(data.plannedCash))}.
                        </p>
                      </div>
                    )}
                    {data.payments?.map((payment, index) => (
                      <div className="co-success" key={index}>
                        Đã nhận {formatVnd(payment.amount)} ·{' '}
                        {payment.method === 'CASH' ? 'Tiền mặt' : 'SePay'}
                        {payment.method === 'CASH' && (
                          <p>Tiền thừa: {formatVnd(payment.cashChange)}</p>
                        )}
                      </div>
                    ))}
                    {data.request && (
                      <>
                        <div className="co-transfer">
                          {data.request.expired && data.request.status !== 'SETTLED' ? (
                            <div className="co-warning">
                              Yêu cầu thanh toán này đã hết hạn hiển thị. Vui lòng kiểm tra trạng
                              thái thanh toán trước khi tạo yêu cầu mới.
                            </div>
                          ) : (
                            <img
                              src={data.request.qrUrl}
                              alt="QR chuyển khoản thanh toán bill"
                              referrerPolicy="no-referrer"
                            />
                          )}
                          <dl>
                            <Info label="Ngân hàng" value={data.request.bank} />
                            <Info label="Tài khoản nhận" value={data.request.account} />
                            <Info label="Chủ tài khoản" value={data.request.holder} />
                            <Info label="Số tiền" value={formatVnd(data.request.amount)} />
                            <Info label="Nội dung chuyển khoản" value={data.request.reference} />
                          </dl>
                        </div>
                        <div
                          className={
                            data.request.status === 'SETTLED' ? 'co-success' : 'co-warning'
                          }
                        >
                          {data.request.status === 'SETTLED'
                            ? '✓ SePay đã xác nhận đúng số tiền và nội dung.'
                            : data.request.expired
                              ? 'QR đã hết hạn. Chưa xác nhận thu tiền; cần kiểm tra giao dịch trước khi tiếp tục.'
                              : `Đang chờ xác nhận chuyển khoản ${formatVnd(data.request.amount).replace(/đ$/, '')} đ ` +
                                `cho booking ${data.bookingCode}.`}
                        </div>
                      </>
                    )}
                    {data.request?.expired && data.request.status === 'PENDING' && (
                      <button
                        disabled={busy}
                        onClick={() => run(async () => setData(await api.renew(billId)))}
                      >
                        Gia hạn mã QR (30 phút)
                      </button>
                    )}
                    <button
                      className="co-primary"
                      disabled={busy || !data.readyToComplete}
                      onClick={() => run(async () => setData(await api.complete(billId)))}
                    >
                      Hoàn tất thanh toán
                    </button>
                  </>
                )}
              </section>
              <aside className="co-card co-summary co-print">
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
            </div>
          </>
        )}
      </section>
    </AppShell>
  );
}
function Info({ label, value }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
function Lines({ lines }) {
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
function Summary({
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
