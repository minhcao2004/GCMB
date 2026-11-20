import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from './BookingManagementPage';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { depositApi } from '../services/depositApi';
import { formatVnd } from '../data/bookingData';
import '../styles/booking.css';
import '../styles/deposit.css';
import '../styles/accounting-refund.css';

const openStatuses = new Set(['REQUESTED', 'APPROVED', 'PROCESSING']);
const statusLabels = {
  REQUESTED: 'Chờ xử lý',
  APPROVED: 'Đã duyệt',
  PROCESSING: 'Đang xử lý',
  PAID: 'Đã hoàn',
  REJECTED: 'Bị từ chối',
  FAILED: 'Thất bại',
};
const filterLabels = { open: 'Chờ xử lý', paid: 'Đã hoàn', all: 'Tất cả' };
// BR-115, BR-116: giao diện chỉ lọc định dạng/dung lượng; backend kiểm tra chữ ký nội dung.
const MAX_EVIDENCE_BYTES = 20_000_000;
const evidenceMimeTypes = new Set([
  'image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'application/pdf',
]);
const evidenceExtensions = new Set(['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif', 'pdf']);

function errorText(error, fallback = 'Không tải được yêu cầu hoàn cọc.') {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || fallback;
}

function dateTimeText(value) {
  if (!value) return 'Chưa ghi nhận';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value));
}

function statusClass(status) {
  return `deposit-status deposit-status--${String(status || '').toLowerCase().replaceAll('_', '-')}`;
}

function RefundCompletionForm({ item, busy, error, onSubmit }) {
  const [externalReference, setExternalReference] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [image, setImage] = useState(null);
  const [imageError, setImageError] = useState('');
  const [previewUrl, setPreviewUrl] = useState('');
  const imageInput = useRef(null);

  useEffect(() => {
    if (!image) {
      setPreviewUrl('');
      return undefined;
    }
    const url = URL.createObjectURL(image);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [image]);

  function selectImage(file) {
    setImageError('');
    if (!file) { setImage(null); return; }
    const mimeType = (file.type || '').split(';', 2)[0].trim().toLowerCase();
    const extension = file.name?.split('.').pop()?.toLowerCase() || '';
    if (!evidenceMimeTypes.has(mimeType) && !evidenceExtensions.has(extension)) {
      setImage(null);
      setImageError('Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.');
      if (imageInput.current) imageInput.current.value = '';
      return;
    }
    if (file.size === 0 || file.size > MAX_EVIDENCE_BYTES) {
      setImage(null);
      setImageError('Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.');
      if (imageInput.current) imageInput.current.value = '';
      return;
    }
    setImage(file);
  }

  return <form className="accounting-refund-action" onSubmit={(event) => {
    event.preventDefault();
    onSubmit(externalReference.trim(), image);
  }}>
    <div className="deposit-accounting-note">
      <strong>Chỉ ghi nhận sau khi đã chuyển khoản thành công.</strong>
      <p>Việc ghi nhận lưu bút toán đúng số tiền yêu cầu vào sổ cọc và khóa yêu cầu khỏi xử lý lại.</p>
    </div>
    <label className="deposit-form-field">
      <span>Mã giao dịch chuyển khoản <small>(không bắt buộc)</small></span>
      <input maxLength={200} value={externalReference} onChange={(event) => setExternalReference(event.target.value)} autoComplete="off" />
    </label>
    <div className="deposit-form-field accounting-refund-evidence-field">
      <span>Tệp minh chứng chuyển khoản <small>(không bắt buộc)</small></span>
      <label className="accounting-refund-upload">
        <input ref={imageInput} type="file" accept="image/jpeg,.jpg,.jpeg,image/png,.png,image/webp,.webp,image/heic,.heic,image/heif,.heif,application/pdf,.pdf"
          onChange={(event) => selectImage(event.target.files?.[0] || null)}
          aria-describedby="accounting-refund-image-hint" />
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M5 14v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/></svg>
        <span className="accounting-refund-upload-copy">
          <strong>{image ? image.name : 'Chọn tệp minh chứng chuyển khoản'}</strong>
          <small id="accounting-refund-image-hint">JPEG, PNG, WebP, HEIC, HEIF hoặc PDF · tối đa 20 MB</small>
        </span>
        <span className="accounting-refund-upload-action">Chọn tệp</span>
      </label>
      {previewUrl && image && <div className="accounting-refund-image-preview">
        {['image/jpeg', 'image/png', 'image/webp'].includes(image.type)
          ? <img src={previewUrl} alt="Ảnh chứng minh chuyển khoản đã chọn" />
          : <a href={previewUrl} target="_blank" rel="noreferrer">Mở tệp minh chứng đã chọn</a>}
        <button type="button" onClick={() => { setImage(null); setImageError(''); if (imageInput.current) imageInput.current.value = ''; }}>Xóa tệp</button>
      </div>}
      {imageError && <p className="booking-inline-error" role="alert">{imageError}</p>}
    </div>
    <section className="accounting-refund-final-check" aria-label="Đối chiếu lần cuối trước khi ghi nhận">
      <p><span>Số tiền hoàn</span><strong>{formatVnd(item.amount)}</strong></p>
      <p><span>Chủ tài khoản</span><strong>{item.beneficiaryName || '—'}</strong></p>
      <p><span>Ngân hàng · số tài khoản</span><strong>{item.bankName || '—'} · {item.bankAccount || '—'}</strong></p>
    </section>
    <label className="accounting-refund-confirm">
      <input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} required
        aria-describedby="accounting-refund-confirm-copy" />
      <span id="accounting-refund-confirm-copy">Tôi xác nhận đã chuyển khoản thành công {formatVnd(item.amount)} đến {item.beneficiaryName || 'chủ tài khoản'} tại {item.bankName || 'ngân hàng'}, số tài khoản {item.bankAccount || '—'}.</span>
    </label>
    {error && <p className="booking-inline-error" role="alert">{error}</p>}
    <button className="booking-button booking-button-primary" type="submit" disabled={busy || !confirmed}>
      {busy ? 'Đang ghi nhận…' : 'Ghi nhận đã hoàn cọc'}
    </button>
  </form>;
}

function RefundEvidence({ item }) {
  const [imageUrl, setImageUrl] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    let objectUrl = '';
    setImageUrl('');
    if (!item.hasEvidence) {
      setError('');
      setLoading(false);
      return undefined;
    }
    setLoading(true);
    setError('');
    depositApi.refundEvidence(item.id).then((blob) => {
      objectUrl = URL.createObjectURL(blob);
      if (active) setImageUrl(objectUrl);
      else URL.revokeObjectURL(objectUrl);
    }).catch((requestError) => {
      if (active) setError(errorText(requestError));
    }).finally(() => { if (active) setLoading(false); });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [item.id, item.hasEvidence]);

  if (!item.hasEvidence) return null;
  return <section className="accounting-refund-evidence">
    <h3>Chứng từ chuyển khoản</h3>
    {loading && <p className="accounting-refund-evidence-message">Đang tải ảnh chứng từ…</p>}
    {error && <p className="accounting-refund-evidence-message accounting-refund-evidence-message--error" role="alert">{error}</p>}
    {imageUrl && <a href={imageUrl} target="_blank" rel="noreferrer" aria-label="Mở ảnh chứng từ trong tab mới">
      <img src={imageUrl} alt="Ảnh chứng minh đã chuyển tiền" />
    </a>}
  </section>;
}

function RefundDetails({ item, busy, error, onComplete, onBackToQueue }) {
  if (!item) return <aside className="deposit-details-card accounting-refund-details">
    <div className="deposit-details-empty">Chọn một yêu cầu để đối chiếu thông tin và xử lý hoàn cọc.</div>
  </aside>;

  const canComplete = openStatuses.has(item.status);
  return <aside className="deposit-details-card accounting-refund-details" aria-label="Chi tiết yêu cầu hoàn cọc">
    <div className="deposit-details-scroll">
      <header className="deposit-details-heading">
        <button type="button" className="accounting-refund-back" onClick={onBackToQueue}>
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6"/><path d="M9 12h12"/></svg>
          Danh sách yêu cầu
        </button>
        <div><h2>Chi tiết yêu cầu</h2><strong>{item.bookingCode} · {item.customerName}</strong></div>
        <span className={statusClass(item.status)}>{statusLabels[item.status] || 'Chưa xác định'}</span>
      </header>
      <section className="deposit-details-amount">
        <span>Số tiền yêu cầu hoàn</span>
        <strong>{formatVnd(item.amount)}</strong>
        <small>Đặt phòng đã hủy</small>
      </section>
      <dl className="deposit-detail-list">
        <div><dt>Số điện thoại</dt><dd>{item.phone || '—'}</dd></div>
        <div><dt>Chủ tài khoản</dt><dd>{item.beneficiaryName}</dd></div>
        <div><dt>Ngân hàng</dt><dd>{item.bankName}</dd></div>
        <div><dt>Số tài khoản</dt><dd>{item.bankAccount}</dd></div>
        <div><dt>Gửi yêu cầu lúc</dt><dd>{dateTimeText(item.requestedAt)}</dd></div>
        {item.paidAt && <div><dt>Đã hoàn lúc</dt><dd>{dateTimeText(item.paidAt)}</dd></div>}
        {item.externalReference && <div><dt>Mã giao dịch hoàn</dt><dd>{item.externalReference}</dd></div>}
      </dl>
      <section className="deposit-history accounting-refund-reason">
        <h3>Lý do hoàn cọc</h3>
        <p>{item.reason}</p>
      </section>
      <RefundEvidence item={item} />
      {canComplete
        ? <RefundCompletionForm key={item.id} item={item} busy={busy} error={error} onSubmit={onComplete} />
        : <div className="deposit-accounting-note accounting-refund-completed">
          <strong>{item.status === 'PAID' ? 'Yêu cầu đã được hoàn.' : `Yêu cầu ${statusLabels[item.status]?.toLowerCase() || 'đã đóng'}.`}</strong>
          <p>{item.status === 'PAID' ? 'Bút toán đã được ghi nhận trong lịch sử tiền cọc.' : 'Không thể ghi nhận hoàn tiền cho yêu cầu này.'}</p>
        </div>}
    </div>
  </aside>;
}

export default function AccountantRefundPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState('');
  const [retryAuth, setRetryAuth] = useState(0);
  const [queue, setQueue] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [retryQueue, setRetryQueue] = useState(0);
  const [reload, setReload] = useState(0);
  const [selectedId, setSelectedId] = useState('');
  const [filter, setFilter] = useState('open');
  const [search, setSearch] = useState('');
  const [mobileView, setMobileView] = useState('queue');
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [busy, setBusy] = useState(false);
  const [mutationError, setMutationError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'ACCOUNTANT') navigate(identity.destination, { replace: true });
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
    let active = true;
    setLoading(true);
    setLoadError('');
    depositApi.refundRequests().then((result) => {
      if (!active) return;
      const items = result.items || [];
      setQueue({ branchName: result.branchName, items });
      setSelectedId((current) => current && items.some((item) => item.id === current)
        ? current : items.find((item) => openStatuses.has(item.status))?.id || items[0]?.id || '');
    }).catch((error) => {
      if (!active) return;
      setQueue(null);
      setLoadError(errorText(error));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, reload, retryQueue]);

  const items = useMemo(() => queue?.items || [], [queue]);
  const visibleItems = useMemo(() => items.filter((item) => {
    const matchesFilter = filter === 'all' || (filter === 'open' ? openStatuses.has(item.status) : item.status === 'PAID');
    const searchText = `${item.bookingCode} ${item.branchName || ''} ${item.customerName} ${item.phone || ''} ${item.bankName} ${item.bankAccount}`.toLocaleLowerCase('vi');
    return matchesFilter && searchText.includes(search.trim().toLocaleLowerCase('vi'));
  }), [items, filter, search]);
  const selected = items.find((item) => item.id === selectedId) || null;
  const emptyQueueMessage = items.length === 0 ? 'Chưa có yêu cầu hoàn cọc nào.'
    : filter === 'open' ? 'Không có yêu cầu đang chờ xử lý.' : 'Không tìm thấy yêu cầu phù hợp.';
  const pendingItems = items.filter((item) => openStatuses.has(item.status));
  const pendingAmount = pendingItems.reduce((total, item) => total + Number(item.amount || 0), 0);
  const refundedAmount = items.filter((item) => item.status === 'PAID').reduce((total, item) => total + Number(item.amount || 0), 0);

  useEffect(() => {
    if (visibleItems.length && !visibleItems.some((item) => item.id === selectedId)) setSelectedId(visibleItems[0].id);
    else if (!visibleItems.length && selectedId) setSelectedId('');
  }, [visibleItems, selectedId]);

  async function logout() {
    setLogoutBusy(true);
    try { await authApi.logout(); } catch { /* Phiên có thể đã hết hạn; vẫn chuyển về đăng nhập. */ }
    navigate('/dang-nhap', { replace: true });
  }

  async function completeRefund(item, externalReference, image) {
    setBusy(true);
    setMutationError('');
    setNotice('');
    try {
      const result = await depositApi.completeRefundRequest(item.id, externalReference, image);
      setFilter('all');
      const proofNotice = image ? ' và lưu tệp minh chứng.'
        : externalReference?.trim() ? ' theo mã giao dịch.' : ' không kèm mã giao dịch hoặc tệp minh chứng.';
      setNotice(`Đã ghi nhận hoàn ${formatVnd(result.amount)} cho đặt phòng ${item.bookingCode}${proofNotice}`);
      setReload((value) => value + 1);
    } catch (error) { setMutationError(errorText(error, 'Không thể ghi nhận hoàn cọc. Dữ liệu đã nhập vẫn được giữ để bạn thử lại.')); }
    finally { setBusy(false); }
  }

  function selectRequest(id) {
    setSelectedId(id);
    setMutationError('');
    setMobileView('detail');
  }

  if (authError) return <main className="booking-auth-state"><p>{authError}</p><button type="button" onClick={() => setRetryAuth((value) => value + 1)}>Thử lại</button></main>;

  return <AppShell user={user} branchName={queue?.branchName} busy={logoutBusy} onLogout={logout}
    breadcrumb="Hoàn tiền cọc" activeTab="deposit" audience="accountant" hideSearch>
    <div className="deposit-page accounting-refund-page">
      <header className="deposit-page-heading">
        <div>
          <h1>Hoàn tiền cọc</h1>
          <p>Chọn yêu cầu, đối chiếu người nhận, xác nhận đã chuyển khoản rồi ghi nhận.</p>
          <p className="accounting-refund-scope">Phạm vi cơ sở: {queue?.branchName || (loading ? 'Đang tải…' : 'Chưa tải được')}</p>
        </div>
      </header>
      {notice && <p className="booking-live-notice" role="status"><span aria-hidden="true">✓</span>{notice}</p>}
      {loadError && <p className="booking-inline-error accounting-refund-load-error" role="alert">{loadError}<button type="button" onClick={() => setRetryQueue((value) => value + 1)}>Tải lại</button></p>}
      <section className="accounting-refund-summary" aria-label="Tổng hợp yêu cầu hoàn cọc">
        <div className="accounting-refund-summary-heading"><h2>Tổng quan</h2><p>Số liệu theo toàn bộ yêu cầu được tải về; API hiện không lọc theo thời gian.</p></div>
        <div className="accounting-refund-summary-metrics" aria-live="polite" aria-busy={loading}>
          <p className="accounting-refund-metric accounting-refund-metric--pending">
            <span>Yêu cầu chờ xử lý</span><strong>{loading ? '—' : pendingItems.length}</strong>
          </p>
          <p className="accounting-refund-metric">
            <span>Giá trị chờ hoàn</span><strong>{loading ? '—' : formatVnd(pendingAmount)}</strong>
          </p>
          <p className="accounting-refund-metric accounting-refund-metric--paid">
            <span>Đã hoàn cho khách</span><strong>{loading ? '—' : formatVnd(refundedAmount)}</strong>
          </p>
        </div>
      </section>
      <div className="accounting-refund-controls">
        <div className="accounting-refund-filters" role="group" aria-label="Lọc yêu cầu hoàn cọc">
          {Object.entries(filterLabels).map(([key, label]) => <button key={key} type="button" className={filter === key ? 'is-active' : ''}
            aria-pressed={filter === key} onClick={() => setFilter(key)}>
            {label}{key === 'open' && <span>{pendingItems.length}</span>}
          </button>)}
        </div>
        <label className="accounting-refund-search">
          <svg aria-hidden="true" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4.4 4.4"/></svg>
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Mã đặt phòng, khách hàng, cơ sở, SĐT, ngân hàng, số tài khoản" aria-label="Tìm theo mã đặt phòng, khách hàng, cơ sở, số điện thoại, ngân hàng hoặc số tài khoản" />
        </label>
      </div>
      <div className="accounting-refund-content-grid" data-mobile-view={mobileView}>
        <section className="deposit-table-card" aria-label="Danh sách yêu cầu hoàn cọc">
          <div className="deposit-table-scroll accounting-refund-desktop-list">
            <table className="deposit-table accounting-refund-table">
              <thead><tr><th>Mã đặt phòng · cơ sở · thời điểm gửi</th><th>Khách hàng</th><th>Số tiền</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {loading && <tr className="accounting-refund-table-row--message"><td className="deposit-table-message" colSpan="4" role="status">Đang tải yêu cầu hoàn cọc…</td></tr>}
                {!loading && loadError && <tr className="accounting-refund-table-row--message"><td className="deposit-table-message deposit-table-message--error" colSpan="4">Danh sách chưa tải được.</td></tr>}
                {!loading && !loadError && visibleItems.length === 0 && <tr className="accounting-refund-table-row--message"><td className="deposit-table-message" colSpan="4">
                  {emptyQueueMessage}
                </td></tr>}
                {!loading && visibleItems.map((item) => <tr key={item.id} className={item.id === selectedId ? 'is-selected' : ''}
                  onClick={() => selectRequest(item.id)} tabIndex={0} aria-selected={item.id === selectedId}
                  onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectRequest(item.id); } }}>
                  <td><strong>{item.bookingCode}</strong><small><b>Cơ sở</b> · {item.branchName || '—'}</small><small><b>Gửi lúc</b> · {dateTimeText(item.requestedAt)}</small></td>
                  <td><strong>{item.customerName}</strong><small>{item.phone || 'Không có số điện thoại'}</small></td>
                  <td><small className="accounting-refund-row-label">Số tiền hoàn</small><strong>{formatVnd(item.amount)}</strong></td>
                  <td><span className={statusClass(item.status)}>{statusLabels[item.status] || 'Chưa xác định'}</span></td>
                </tr>)}
              </tbody>
            </table>
          </div>
          <div className="accounting-refund-mobile-list" aria-label="Yêu cầu hoàn cọc">
            {loading && <p className="accounting-refund-mobile-state" role="status">Đang tải yêu cầu hoàn cọc…</p>}
            {!loading && loadError && <p className="accounting-refund-mobile-state accounting-refund-mobile-state--error" role="alert">Danh sách chưa tải được.</p>}
            {!loading && !loadError && visibleItems.length === 0 && <p className="accounting-refund-mobile-state">{emptyQueueMessage}</p>}
            {!loading && visibleItems.map((item) => <button key={item.id} type="button"
              className={`accounting-refund-mobile-card${item.id === selectedId ? ' is-selected' : ''}`}
              aria-pressed={item.id === selectedId} onClick={() => selectRequest(item.id)}>
              <span className="accounting-refund-mobile-card-heading"><strong>{item.bookingCode}</strong>
                <span className={statusClass(item.status)}>{statusLabels[item.status] || 'Chưa xác định'}</span></span>
              <span className="accounting-refund-mobile-customer"><strong>{item.customerName}</strong><small>{item.phone || 'Không có số điện thoại'}</small></span>
              <span className="accounting-refund-mobile-amount"><small>Số tiền hoàn</small><strong>{formatVnd(item.amount)}</strong></span>
              <span className="accounting-refund-mobile-meta accounting-refund-mobile-branch"><small>Cơ sở</small>{item.branchName || '—'}</span>
              <span className="accounting-refund-mobile-meta accounting-refund-mobile-requested"><small>Gửi lúc</small>{dateTimeText(item.requestedAt)}</span>
            </button>)}
          </div>
          <footer className="deposit-table-footer"><span>{loading ? 'Đang cập nhật danh sách' : `${visibleItems.length} yêu cầu`}</span></footer>
        </section>
        <RefundDetails item={selected} busy={busy} error={mutationError} onComplete={(reference, image) => completeRefund(selected, reference, image)}
          onBackToQueue={() => setMobileView('queue')} />
      </div>
    </div>
  </AppShell>;
}
