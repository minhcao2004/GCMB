import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { formatVnd, isLocalDateInput, toLocalDateInput } from '../data/bookingData';
import { depositApi } from '../services/depositApi';
import { AppShell } from './BookingManagementPage';
import '../styles/booking.css';
import '../styles/deposit.css';

const PAGE_SIZE = 20;
const statusLabels = {
  HELD: 'Đang giữ',
  PENDING_REFUND: 'Chờ hoàn',
  REFUNDED: 'Đã hoàn',
  FORFEITED: 'Cọc không hoàn',
};
const statusPriority = { PENDING_REFUND: 0, REFUNDED: 1, HELD: 2, FORFEITED: 3 };
const methodLabels = {
  BANK_TRANSFER: 'Chuyển khoản Tingbox',
  MANUAL_TRANSFER: 'Chuyển khoản',
  CASH: 'Tiền mặt',
  CARD: 'Thẻ',
  QR: 'QR',
  MULTIPLE: 'Nhiều phương thức',
};
const refundRequestLabels = {
  REQUESTED: 'Chờ Kế toán xử lý',
  APPROVED: 'Đã duyệt',
  PROCESSING: 'Đang xử lý',
  PAID: 'Đã hoàn',
  REJECTED: 'Bị từ chối',
  FAILED: 'Thất bại',
};

function errorText(error) {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || 'Không tải được dữ liệu tiền cọc.';
}

function dateTimeText(value) {
  if (!value) return 'Chưa có thời gian';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value));
}

function shortDateTime(value) {
  if (!value) return 'Chưa gán';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value));
}

function statusClass(status) {
  return `deposit-status deposit-status--${String(status || '').toLowerCase().replaceAll('_', '-')}`;
}

function dateRangeStart() {
  const date = new Date();
  date.setDate(date.getDate() - 30);
  return toLocalDateInput(date);
}

function SummaryCard({ title, amount, count, tone, suffix, unavailable = false }) {
  return <article className={`deposit-summary-card deposit-summary-card--${tone}`}>
    <span className="deposit-summary-accent" />
    <div className="deposit-summary-copy">
      <span className="deposit-summary-title">{title}</span>
      <strong>{unavailable ? '—' : formatVnd(amount)}</strong>
    </div>
    <span className="deposit-summary-count">{unavailable ? '—' : count} {suffix}</span>
  </article>;
}

function DepositRequestDialog({ eligibleBookings, loading, error, busy, onClose, onSubmit }) {
  const [bookingId, setBookingId] = useState('');
  const [beneficiaryName, setBeneficiaryName] = useState('');
  const [bankName, setBankName] = useState('');
  const [bankAccount, setBankAccount] = useState('');
  const [reason, setReason] = useState('');
  const selected = eligibleBookings.find((booking) => booking.bookingId === bookingId);

  return <div className="deposit-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="deposit-request-dialog" role="dialog" aria-modal="true" aria-labelledby="deposit-request-title">
      <header className="deposit-dialog-heading">
        <div><span className="deposit-dialog-kicker">Gửi Kế toán xử lý</span><h2 id="deposit-request-title">Tạo yêu cầu hoàn cọc</h2></div>
        <button className="deposit-dialog-close" type="button" onClick={onClose} aria-label="Đóng">×</button>
      </header>
      <p className="deposit-dialog-description">Chỉ booking đã hủy, còn số dư cọc và đủ điều kiện hoàn mới xuất hiện ở đây.</p>
      {error && <div className="deposit-inline-error" role="alert">{error}</div>}
      {loading ? <div className="deposit-dialog-loading" role="status">Đang tải booking đủ điều kiện…</div>
        : eligibleBookings.length === 0 ? <div className="deposit-no-eligible">
          <span aria-hidden="true">✓</span><strong>Không có booking cần gửi yêu cầu</strong>
          <p>Các khoản đủ điều kiện đã có yêu cầu hoàn, hoặc hiện chưa có booking phù hợp.</p>
        </div> : <form onSubmit={(event) => { event.preventDefault(); onSubmit({ bookingId, beneficiaryName, bankName, bankAccount, reason }); }}>
          <label className="deposit-form-field"><span>Booking đủ điều kiện <b>*</b></span>
            <select value={bookingId} onChange={(event) => setBookingId(event.target.value)} required>
              <option value="">Chọn booking</option>
              {eligibleBookings.map((booking) => <option key={booking.bookingId} value={booking.bookingId}>
                {booking.bookingCode} · {booking.customerName} · {formatVnd(booking.amount)}
              </option>)}
            </select>
          </label>
          {selected && <div className="deposit-request-amount"><span>Số tiền backend xác định theo số dư đủ điều kiện</span><strong>{formatVnd(selected.amount)}</strong></div>}
          <div className="deposit-form-grid">
            <label className="deposit-form-field"><span>Chủ tài khoản nhận <b>*</b></span>
              <input maxLength={200} value={beneficiaryName} onChange={(event) => setBeneficiaryName(event.target.value)} autoComplete="name" required />
            </label>
            <label className="deposit-form-field"><span>Ngân hàng <b>*</b></span>
              <input maxLength={200} value={bankName} onChange={(event) => setBankName(event.target.value)} required />
            </label>
          </div>
          <label className="deposit-form-field"><span>Số tài khoản <b>*</b></span>
            <input maxLength={60} value={bankAccount} onChange={(event) => setBankAccount(event.target.value)} inputMode="numeric" required />
          </label>
          <label className="deposit-form-field"><span>Lý do hoàn cọc <b>*</b></span>
            <textarea maxLength={1000} rows={3} value={reason} onChange={(event) => setReason(event.target.value)} required />
          </label>
          <div className="deposit-dialog-note">Yêu cầu được chuyển cho Kế toán. Lễ tân không xác nhận đã hoàn tiền.</div>
          <footer className="deposit-dialog-actions">
            <button type="button" className="deposit-button deposit-button--secondary" onClick={onClose}>Đóng</button>
            <button type="submit" className="deposit-button deposit-button--primary" disabled={busy || !bookingId}>
              {busy ? 'Đang gửi…' : 'Gửi yêu cầu'}
            </button>
          </footer>
        </form>}
      {!loading && eligibleBookings.length === 0 && <footer className="deposit-dialog-actions"><button type="button" className="deposit-button deposit-button--secondary" onClick={onClose}>Đóng</button></footer>}
    </section>
  </div>;
}

function DepositDetails({ detail, loading, error, onOpenBooking }) {
  if (loading) return <aside className="deposit-details-card"><div className="deposit-details-empty">Đang tải chi tiết tiền cọc…</div></aside>;
  if (error) return <aside className="deposit-details-card"><div className="deposit-details-empty deposit-details-empty--error">{error}</div></aside>;
  if (!detail?.deposit) return <aside className="deposit-details-card"><div className="deposit-details-empty">Chọn một booking để xem chi tiết tiền cọc.</div></aside>;

  const item = detail.deposit;
  const amountLabel = item.status === 'PENDING_REFUND' ? 'Số tiền chờ hoàn'
    : item.status === 'REFUNDED' ? 'Số tiền đã hoàn'
      : item.status === 'FORFEITED' ? 'Số tiền cọc không hoàn' : 'Số dư đang giữ';

  return <aside className="deposit-details-card" aria-label="Chi tiết tiền cọc">
    <div className="deposit-details-scroll">
      <header className="deposit-details-heading">
        <div><h2>Chi tiết tiền cọc</h2><strong>{item.bookingCode} · {item.customerName}</strong></div>
        <span className={statusClass(item.status)}>{statusLabels[item.status] || item.status}</span>
      </header>
      <section className="deposit-details-amount">
        <span>{amountLabel}</span><strong>{formatVnd(item.amount)}</strong>
        <small>Đã nhận {formatVnd(item.receivedAmount)}</small>
      </section>
      <dl className="deposit-detail-list">
        <div><dt>Phòng / nhận</dt><dd>{item.roomCode || 'Chưa gán'}{item.checkInAt ? ` · ${shortDateTime(item.checkInAt)}` : ''}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{item.phone || '—'}</dd></div>
        <div><dt>Phương thức</dt><dd>{methodLabels[item.paymentMethod] || item.paymentMethod || '—'}</dd></div>
        <div><dt>Trạng thái xử lý</dt><dd>{item.refundRequestStatus ? refundRequestLabels[item.refundRequestStatus] || item.refundRequestStatus : statusLabels[item.status] || item.status}</dd></div>
        {item.status === 'PENDING_REFUND' && <div><dt>Còn giữ tại ví cọc</dt><dd>{formatVnd(item.balance)}</dd></div>}
      </dl>
      <section className="deposit-history">
        <h3>Lịch sử xử lý</h3>
        {detail.history?.length ? <ol>{detail.history.map((event, index) => <li key={`${event.happenedAt}-${event.type}-${index}`}>
          <span className={`deposit-history-dot deposit-history-dot--${event.type.toLowerCase()}`} />
          <div><strong>{event.label}</strong><time>{dateTimeText(event.happenedAt)}</time>{event.details && <small>{event.details}</small>}</div>
        </li>)}</ol> : <p className="deposit-history-empty">Chưa có lịch sử xử lý.</p>}
      </section>
      <div className="deposit-accounting-note"><strong>Kế toán xác nhận sau khi chuyển khoản hoàn.</strong><p>Lễ tân chỉ theo dõi, không đánh dấu đã hoàn.</p></div>
    </div>
    <footer className="deposit-details-footer">
      <button type="button" onClick={() => onOpenBooking(item)}>Xem booking <span aria-hidden="true">→</span></button>
    </footer>
  </aside>;
}

export default function DepositManagementPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState('');
  const [retryAuth, setRetryAuth] = useState(0);
  const [dateFrom, setDateFrom] = useState(dateRangeStart);
  const [dateTo, setDateTo] = useState(() => toLocalDateInput(new Date()));
  const [status, setStatus] = useState('');
  const [method, setMethod] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [reload, setReload] = useState(0);
  const [notice, setNotice] = useState('');
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [eligibleBookings, setEligibleBookings] = useState([]);
  const [eligibleLoading, setEligibleLoading] = useState(false);
  const [requestError, setRequestError] = useState('');
  const [requestBusy, setRequestBusy] = useState(false);

  useEffect(() => { document.title = 'Tiền cọc | GenZ Cinema & Music Box'; }, []);

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else { setUser(identity); setAuthError(''); }
    }).catch((error) => {
      if (!active) return;
      if (error.response?.status === 401) navigate('/dang-nhap', { replace: true, state: { message: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' } });
      else setAuthError(errorText(error));
    });
    return () => { active = false; };
  }, [navigate, retryAuth]);

  useEffect(() => {
    if (!user) return undefined;
    if (dateFrom !== '' && !isLocalDateInput(dateFrom)) {
      setDateFrom(dateRangeStart());
      return undefined;
    }
    if (dateTo !== '' && !isLocalDateInput(dateTo)) {
      setDateTo(toLocalDateInput(new Date()));
      return undefined;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setLoadError('');
      depositApi.list({
        dateFrom: isLocalDateInput(dateFrom) ? dateFrom : undefined,
        dateTo: isLocalDateInput(dateTo) ? dateTo : undefined,
        status: status || undefined,
        method: method || undefined,
        search: search.trim() || undefined,
        page,
        size: PAGE_SIZE,
      }).then((result) => {
        if (!active) return;
        setData(result);
        setSelectedId((current) => current && result.items?.some((item) => item.bookingId === current)
          ? current : result.items?.[0]?.bookingId || '');
      }).catch((error) => {
        if (!active) return;
        setLoadError(errorText(error));
        setData(null);
        setSelectedId('');
        setDetail(null);
      })
        .finally(() => { if (active) setLoading(false); });
    }, 160);
    return () => { active = false; window.clearTimeout(timer); };
  }, [user, dateFrom, dateTo, status, method, search, page, reload]);

  useEffect(() => {
    if (!selectedId || !user) { setDetail(null); return undefined; }
    let active = true;
    setDetail(null);
    setDetailLoading(true);
    setDetailError('');
    depositApi.detail(selectedId).then((result) => { if (active) setDetail(result); })
      .catch((error) => { if (active) setDetailError(errorText(error)); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, user, reload]);

  useEffect(() => {
    if (!dialogOpen) return undefined;
    let active = true;
    setEligibleLoading(true);
    setRequestError('');
    depositApi.eligibleRefundBookings().then((items) => { if (active) setEligibleBookings(items || []); })
      .catch((error) => { if (active) setRequestError(errorText(error)); })
      .finally(() => { if (active) setEligibleLoading(false); });
    return () => { active = false; };
  }, [dialogOpen]);

  const items = [...(data?.items || [])].sort((left, right) =>
    (statusPriority[left.status] ?? 4) - (statusPriority[right.status] ?? 4));
  const summary = data?.summary || {};
  const summaryUnavailable = loading || Boolean(loadError) || !data;
  const detailsUnavailable = loadError ? 'Danh sách chưa tải được nên chưa thể xem chi tiết.' : detailError;

  async function logout() {
    setLogoutBusy(true);
    try { await authApi.logout(); } catch { /* Phiên có thể đã hết hạn; vẫn chuyển về đăng nhập. */ }
    navigate('/dang-nhap', { replace: true });
  }

  async function submitRefundRequest(body) {
    setRequestBusy(true);
    setRequestError('');
    try {
      const result = await depositApi.createRefundRequest({
        ...body,
        bookingId: body.bookingId,
      });
      setDialogOpen(false);
      setNotice(`Đã gửi yêu cầu hoàn ${formatVnd(result.amount)} cho Kế toán.`);
      setReload((value) => value + 1);
    } catch (error) { setRequestError(errorText(error)); }
    finally { setRequestBusy(false); }
  }

  function openBooking(item) {
    const bookingDate = item.checkInAt ? toLocalDateInput(new Date(item.checkInAt)) : undefined;
    navigate('/le-tan/bookings', { state: { bookingId: item.bookingId, bookingDate } });
  }

  if (authError) return <main className="booking-auth-state"><p>{authError}</p><button type="button" onClick={() => setRetryAuth((value) => value + 1)}>Thử lại</button></main>;

  return <AppShell user={user} branchName={data?.branchName} busy={logoutBusy} onLogout={logout}
    breadcrumb="Tiền cọc" activeTab="deposit" search={search} onSearchChange={(value) => { setSearch(value); setPage(0); }} fitViewport>
    <div className="deposit-page">
      <header className="deposit-page-heading">
        <div><h1>Tiền cọc</h1><p>Theo dõi tiền cọc gắn với booking tại {data?.branchName || 'chi nhánh hiện tại'}</p></div>
        <button type="button" className="deposit-button deposit-button--primary deposit-create-button" onClick={() => setDialogOpen(true)}>
          <span aria-hidden="true">＋</span> Tạo yêu cầu hoàn cọc
        </button>
      </header>

      {notice && <div className="deposit-notice" role="status"><span aria-hidden="true">✓</span>{notice}<button type="button" onClick={() => setNotice('')} aria-label="Đóng thông báo">×</button></div>}
      {loadError && <div className="deposit-inline-error" role="alert">{loadError}<button type="button" onClick={() => setReload((value) => value + 1)}>Tải lại</button></div>}

      <section className="deposit-summary-grid" aria-label="Tổng hợp tiền cọc">
        <SummaryCard title="Đang giữ" amount={summary.heldAmount} count={summary.heldBookings} suffix="booking" tone="held" unavailable={summaryUnavailable} />
        <SummaryCard title="Chờ hoàn" amount={summary.pendingRefundAmount} count={summary.pendingRequests} suffix="yêu cầu" tone="pending" unavailable={summaryUnavailable} />
        <SummaryCard title="Đã hoàn" amount={summary.refundedAmount} count={summary.refundTransactions} suffix="giao dịch" tone="refunded" unavailable={summaryUnavailable} />
        <SummaryCard title="Cọc không hoàn" amount={summary.forfeitedAmount} count={summary.forfeitedBookings} suffix="booking" tone="forfeited" unavailable={summaryUnavailable} />
      </section>

      <section className="deposit-filter-card" aria-label="Lọc giao dịch tiền cọc">
        <h2>Lọc giao dịch</h2>
        <div className="deposit-filter-row">
          <label className="deposit-filter-date"><span className="sr-only">Từ ngày</span><input type="date" value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); setPage(0); }} /></label>
          <span className="deposit-date-separator" aria-hidden="true">—</span>
          <label className="deposit-filter-date"><span className="sr-only">Đến ngày</span><input type="date" value={dateTo} onChange={(event) => { setDateTo(event.target.value); setPage(0); }} /></label>
          <label className="deposit-filter-select"><span className="sr-only">Trạng thái</span><select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">Trạng thái · Tất cả</option><option value="HELD">Đang giữ</option><option value="PENDING_REFUND">Chờ hoàn</option><option value="REFUNDED">Đã hoàn</option><option value="FORFEITED">Cọc không hoàn</option>
          </select></label>
          <label className="deposit-filter-select"><span className="sr-only">Phương thức</span><select value={method} onChange={(event) => { setMethod(event.target.value); setPage(0); }}>
            <option value="">Phương thức · Tất cả</option><option value="BANK_TRANSFER">Chuyển khoản Tingbox</option><option value="MANUAL_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option><option value="CARD">Thẻ</option><option value="QR">QR</option><option value="MULTIPLE">Nhiều phương thức</option>
          </select></label>
          <label className="deposit-filter-search"><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Tìm mã booking, khách hàng, số điện thoại, phòng…" aria-label="Tìm giao dịch tiền cọc" /></label>
        </div>
      </section>

      <div className="deposit-content-grid">
        <section className="deposit-table-card" aria-label="Danh sách tiền cọc">
          <div className="deposit-table-scroll">
            <table className="deposit-table">
              <thead><tr><th>Booking</th><th>Khách hàng</th><th>Phòng / nhận</th><th>Số tiền</th><th>Phương thức</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {loading ? <tr><td colSpan="6" className="deposit-table-message">Đang tải giao dịch tiền cọc…</td></tr>
                  : loadError ? <tr><td colSpan="6" className="deposit-table-message deposit-table-message--error">Không tải được danh sách. Dùng nút “Tải lại” phía trên để thử lại.</td></tr>
                    : items.length === 0 ? <tr><td colSpan="6" className="deposit-table-message">Không tìm thấy giao dịch tiền cọc phù hợp.</td></tr>
                    : items.map((item) => <tr key={item.bookingId} className={item.bookingId === selectedId ? 'is-selected' : ''} onClick={() => setSelectedId(item.bookingId)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedId(item.bookingId); } }} aria-selected={item.bookingId === selectedId}>
                      <td><strong>{item.bookingCode}</strong></td>
                      <td><strong>{item.customerName}</strong><small>{item.phone || 'Không có số điện thoại'}</small></td>
                      <td><span>{item.roomCode || 'Chưa gán'}</span><small>{shortDateTime(item.checkInAt)}</small></td>
                      <td><strong>{formatVnd(item.amount)}</strong></td>
                      <td>{methodLabels[item.paymentMethod] || item.paymentMethod || '—'}</td>
                      <td><span className={statusClass(item.status)}>{statusLabels[item.status] || item.status}</span></td>
                    </tr>)}
              </tbody>
            </table>
          </div>
          <footer className="deposit-table-footer">
            <span>{loading ? 'Đang cập nhật…' : loadError ? 'Danh sách chưa tải được' : `Hiển thị ${items.length} / ${data?.total ?? 0} giao dịch`}</span>
            <div className="deposit-pagination">
              <button type="button" disabled={page <= 0 || loading || Boolean(loadError)} onClick={() => setPage((value) => Math.max(0, value - 1))} aria-label="Trang trước">‹</button>
              <span>{loadError ? '—' : data?.totalPages ? `${page + 1} / ${data.totalPages}` : '0 / 0'}</span>
              <button type="button" disabled={!data?.totalPages || page + 1 >= data.totalPages || loading || Boolean(loadError)} onClick={() => setPage((value) => value + 1)} aria-label="Trang sau">›</button>
            </div>
          </footer>
        </section>

        <DepositDetails detail={detail} loading={detailLoading} error={detailsUnavailable} onOpenBooking={openBooking} />
      </div>
    </div>
    {dialogOpen && <DepositRequestDialog eligibleBookings={eligibleBookings} loading={eligibleLoading} error={requestError} busy={requestBusy}
      onClose={() => { if (!requestBusy) setDialogOpen(false); }} onSubmit={submitRefundRequest} />}
  </AppShell>;
}
