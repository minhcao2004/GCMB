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
  APPLIED: 'Đã trừ vào bill',
};
const statusPriority = { PENDING_REFUND: 0, REFUNDED: 1, HELD: 2, FORFEITED: 3 };
const methodLabels = {
  BANK_TRANSFER: 'Chuyển khoản',
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

function RefundEvidenceGallery({ evidence }) {
  const [images, setImages] = useState([]);
  const [error, setError] = useState('');
  const [copyingId, setCopyingId] = useState('');
  const [copyMessages, setCopyMessages] = useState({});

  async function copyEvidenceImage(image) {
    setCopyingId(image.requestId);
    setCopyMessages((current) => ({ ...current, [image.requestId]: null }));
    try {
      if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined')
        throw new Error('Clipboard image writing is unavailable.');

      const bitmap = await createImageBitmap(image.blob);
      let png;
      try {
        const canvas = document.createElement('canvas');
        canvas.width = bitmap.width;
        canvas.height = bitmap.height;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Could not prepare the image for copying.');
        context.drawImage(bitmap, 0, 0);
        png = await new Promise((resolve, reject) => {
          canvas.toBlob((result) => result ? resolve(result) : reject(new Error('Could not encode the image.')), 'image/png');
        });
      } finally {
        bitmap.close();
      }

      await navigator.clipboard.write([new ClipboardItem({ 'image/png': png })]);
      setCopyMessages((current) => ({
        ...current,
        [image.requestId]: { text: 'Đã sao chép ảnh. Có thể dán vào nơi cần dùng.', isError: false },
      }));
    } catch {
      setCopyMessages((current) => ({
        ...current,
        [image.requestId]: { text: 'Không thể sao chép ảnh từ trình duyệt này. Hãy tải ảnh về để sử dụng.', isError: true },
      }));
    } finally {
      setCopyingId('');
    }
  }

  useEffect(() => {
    let active = true;
    const objectUrls = [];
    setImages([]);
    setError('');
    setCopyMessages({});
    setCopyingId('');
    Promise.allSettled(evidence.map(async (item) => {
      const blob = await depositApi.receptionRefundEvidence(item.requestId);
      if (!active) return null;
      const url = URL.createObjectURL(blob);
      objectUrls.push(url);
      return { ...item, blob, url };
    })).then((results) => {
      if (!active) return;
      setImages(results.filter((result) => result.status === 'fulfilled' && result.value).map((result) => result.value));
      if (results.some((result) => result.status === 'rejected'))
        setError('Một số ảnh chứng từ chưa tải được. Hãy đóng rồi mở lại chi tiết để thử lại.');
    });
    return () => {
      active = false;
      objectUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [evidence]);

  return <section className="deposit-evidence">
    <h3>Ảnh chứng từ hoàn cọc</h3>
    {error && <p className="deposit-evidence-message deposit-evidence-message--error" role="alert">{error}</p>}
    {images.length === 0 && !error ? <p className="deposit-evidence-message" role="status">Đang tải ảnh chứng từ…</p>
      : images.map((image) => <article className="deposit-evidence-item" key={image.requestId}>
          <div className="deposit-evidence-meta">
            <strong>{formatVnd(image.amount)}</strong>
            <span>{dateTimeText(image.paidAt)}</span>
          </div>
          {image.externalReference && <small className="deposit-evidence-reference">Mã giao dịch: {image.externalReference}</small>}
          <a className="deposit-evidence-preview" href={image.url} target="_blank" rel="noreferrer" aria-label={`Xem ảnh chứng từ ${image.originalName || ''}`}>
            <img src={image.url} alt={`Ảnh chứng từ hoàn cọc ${image.originalName || ''}`} />
          </a>
          <div className="deposit-evidence-actions">
            <a href={image.url} target="_blank" rel="noreferrer">Xem ảnh</a>
            <a href={image.url} download={image.originalName || 'chung-tu-hoan-coc'}>Tải ảnh</a>
            <button type="button" onClick={() => copyEvidenceImage(image)} disabled={Boolean(copyingId)}>
              {copyingId === image.requestId ? 'Đang sao chép…' : 'Sao chép ảnh'}
            </button>
          </div>
          {copyMessages[image.requestId] && <small className={`deposit-evidence-copy-message${copyMessages[image.requestId].isError ? ' deposit-evidence-copy-message--error' : ''}`} role={copyMessages[image.requestId].isError ? 'alert' : 'status'}>
            {copyMessages[image.requestId].text}
          </small>}
        </article>)}
  </section>;
}

function DepositDetails({ detail, loading, error, onClose, onOpenBooking }) {
  const item = detail?.deposit;
  const amountLabel = item?.status === 'PENDING_REFUND' ? 'Số tiền chờ hoàn'
    : item?.status === 'REFUNDED' ? 'Số tiền đã hoàn'
      : item?.status === 'FORFEITED' ? 'Số tiền cọc không hoàn'
        : item?.status === 'APPLIED' ? 'Số tiền đã trừ bill' : 'Số dư đang giữ';

  return <div className="deposit-drawer-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <aside className="deposit-details-card" role="dialog" aria-modal="true" aria-label="Chi tiết tiền cọc">
      <div className="deposit-details-scroll">
        <header className="deposit-details-heading">
          <div><h2>Chi tiết tiền cọc</h2>{item && <strong>{item.bookingCode} · {item.customerName}</strong>}</div>
          {item && <span className={statusClass(item.status)}>{statusLabels[item.status] || item.status}</span>}
          <button type="button" className="deposit-details-close" onClick={onClose} aria-label="Đóng chi tiết tiền cọc" autoFocus>×</button>
        </header>
        {loading ? <div className="deposit-details-empty" role="status">Đang tải chi tiết tiền cọc…</div>
          : error ? <div className="deposit-details-empty deposit-details-empty--error" role="alert">{error}</div>
            : item && <>
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
              {detail.refundEvidence?.length > 0 && <RefundEvidenceGallery evidence={detail.refundEvidence} />}
              <div className="deposit-accounting-note"><strong>Kế toán xác nhận sau khi chuyển khoản hoàn.</strong><p>Lễ tân chỉ theo dõi, không đánh dấu đã hoàn.</p></div>
            </>}
      </div>
      {item && <footer className="deposit-details-footer">
        <button type="button" onClick={() => onOpenBooking(item)}>Xem booking <span aria-hidden="true">→</span></button>
      </footer>}
    </aside>
  </div>;
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
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [reload, setReload] = useState(0);
  const [logoutBusy, setLogoutBusy] = useState(false);

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
        setSelectedId((current) => current && result.items?.some((item) => item.bookingId === current) ? current : '');
      }).catch((error) => {
        if (!active) return;
        setLoadError(errorText(error));
        setData(null);
        setSelectedId('');
        setDetailsOpen(false);
        setDetail(null);
      })
        .finally(() => { if (active) setLoading(false); });
    }, 160);
    return () => { active = false; window.clearTimeout(timer); };
  }, [user, dateFrom, dateTo, status, method, search, page, reload]);

  useEffect(() => {
    if (data && selectedId && !data.items?.some((item) => item.bookingId === selectedId)) {
      setSelectedId('');
      setDetailsOpen(false);
      setDetail(null);
    }
  }, [data, selectedId]);

  useEffect(() => {
    if (!detailsOpen || !selectedId || !user) {
      if (!selectedId || !user) setDetail(null);
      return undefined;
    }
    let active = true;
    setDetail(null);
    setDetailLoading(true);
    setDetailError('');
    depositApi.detail(selectedId).then((result) => { if (active) setDetail(result); })
      .catch((error) => { if (active) setDetailError(errorText(error)); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [detailsOpen, selectedId, user, reload]);

  useEffect(() => {
    if (!detailsOpen) return undefined;
    function closeOnEscape(event) {
      if (event.key === 'Escape') setDetailsOpen(false);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [detailsOpen]);

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

  function openBooking(item) {
    const bookingDate = item.checkInAt ? toLocalDateInput(new Date(item.checkInAt)) : undefined;
    navigate('/le-tan/bookings', { state: { bookingId: item.bookingId, bookingDate } });
  }

  if (authError) return <main className="booking-auth-state"><p>{authError}</p><button type="button" onClick={() => setRetryAuth((value) => value + 1)}>Thử lại</button></main>;

  // Chờ xác thực trước khi dựng thanh tài khoản và các thao tác tiền cọc.
  if (!user) return <main className="booking-auth-state"><p role="status">Đang kiểm tra phiên đăng nhập…</p></main>;

  return <AppShell user={user} branchName={data?.branchName} busy={logoutBusy} onLogout={logout}
    breadcrumb="Tiền cọc" activeTab="deposit" search={search} onSearchChange={(value) => { setSearch(value); setPage(0); }} fitViewport>
    <div className="deposit-page">
      <header className="deposit-page-heading">
        <div><h1>Tiền cọc</h1><p>Theo dõi tiền cọc gắn với booking tại {data?.branchName || 'chi nhánh hiện tại'}</p></div>
      </header>

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
                    : items.map((item) => <tr key={item.bookingId} className={item.bookingId === selectedId ? 'is-selected' : ''} onClick={() => { setSelectedId(item.bookingId); setDetailsOpen(true); }} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedId(item.bookingId); setDetailsOpen(true); } }} aria-selected={item.bookingId === selectedId} aria-label={`Xem chi tiết tiền cọc ${item.bookingCode}`}>
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

      </div>
    </div>
    {detailsOpen && <DepositDetails detail={detail} loading={detailLoading} error={detailsUnavailable} onClose={() => setDetailsOpen(false)} onOpenBooking={openBooking} />}
  </AppShell>;
}
