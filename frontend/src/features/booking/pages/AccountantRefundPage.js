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

function errorText(error) {
  const errors = apiErrors(error);
  return errors.form || Object.values(errors)[0] || 'Không tải được yêu cầu hoàn cọc.';
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
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setImage(null);
      setImageError('Chỉ chọn ảnh JPG, PNG hoặc WebP.');
      if (imageInput.current) imageInput.current.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setImage(null);
      setImageError('Ảnh chứng minh không được vượt quá 5 MB.');
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
      <p>GCMB sẽ lưu bút toán hoàn đúng số tiền yêu cầu vào sổ cọc và khóa yêu cầu này khỏi xử lý lại.</p>
    </div>
    <label className="deposit-form-field">
      <span>Mã giao dịch chuyển khoản <b>*</b></span>
      <input maxLength={200} value={externalReference} onChange={(event) => setExternalReference(event.target.value)} required autoComplete="off" />
    </label>
    <div className="deposit-form-field accounting-refund-evidence-field">
      <span>Ảnh chứng minh chuyển khoản <b>*</b></span>
      <label className="accounting-refund-upload">
        <input ref={imageInput} type="file" accept="image/jpeg,image/png,image/webp" required
          onChange={(event) => selectImage(event.target.files?.[0] || null)}
          aria-describedby="accounting-refund-image-hint" />
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M5 14v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/></svg>
        <span className="accounting-refund-upload-copy">
          <strong>{image ? image.name : 'Chọn ảnh biên lai chuyển khoản'}</strong>
          <small id="accounting-refund-image-hint">JPG, PNG hoặc WebP · tối đa 5 MB</small>
        </span>
        <span className="accounting-refund-upload-action">Chọn ảnh</span>
      </label>
      {previewUrl && <div className="accounting-refund-image-preview">
        <img src={previewUrl} alt="Ảnh chứng minh chuyển khoản đã chọn" />
        <button type="button" onClick={() => { setImage(null); setImageError(''); if (imageInput.current) imageInput.current.value = ''; }}>Xóa ảnh</button>
      </div>}
      {imageError && <p className="booking-inline-error" role="alert">{imageError}</p>}
    </div>
    <label className="accounting-refund-confirm">
      <input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} required />
      <span>Tôi xác nhận đã hoàn {formatVnd(item.amount)} đến đúng tài khoản người nhận ở trên.</span>
    </label>
    {error && <p className="booking-inline-error" role="alert">{error}</p>}
    <button className="booking-button booking-button-primary" type="submit" disabled={busy || !confirmed || !image}>
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

function RefundDetails({ item, busy, error, onComplete }) {
  if (!item) return <aside className="deposit-details-card accounting-refund-details">
    <div className="deposit-details-empty">Chọn một yêu cầu để đối chiếu thông tin và xử lý hoàn cọc.</div>
  </aside>;

  const canComplete = openStatuses.has(item.status);
  return <aside className="deposit-details-card accounting-refund-details" aria-label="Chi tiết yêu cầu hoàn cọc">
    <div className="deposit-details-scroll">
      <header className="deposit-details-heading">
        <div><h2>Chi tiết yêu cầu</h2><strong>{item.bookingCode} · {item.customerName}</strong></div>
        <span className={statusClass(item.status)}>{statusLabels[item.status] || item.status}</span>
      </header>
      <section className="deposit-details-amount">
        <span>Số tiền yêu cầu hoàn</span>
        <strong>{formatVnd(item.amount)}</strong>
        <small>Booking đã hủy</small>
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
    const searchText = `${item.bookingCode} ${item.customerName} ${item.phone || ''} ${item.bankName} ${item.bankAccount}`.toLocaleLowerCase('vi');
    return matchesFilter && searchText.includes(search.trim().toLocaleLowerCase('vi'));
  }), [items, filter, search]);
  const selected = items.find((item) => item.id === selectedId) || null;
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
      setNotice(`Đã ghi nhận hoàn ${formatVnd(result.amount)} cho booking ${item.bookingCode} và lưu ảnh chứng minh.`);
      setReload((value) => value + 1);
    } catch (error) { setMutationError(errorText(error)); }
    finally { setBusy(false); }
  }

  if (authError) return <main className="booking-auth-state"><p>{authError}</p><button type="button" onClick={() => setRetryAuth((value) => value + 1)}>Thử lại</button></main>;

  return <AppShell user={user} branchName={queue?.branchName} busy={logoutBusy} onLogout={logout}
    breadcrumb="Hoàn tiền cọc" activeTab="deposit" audience="accountant" hideSearch>
    <div className="deposit-page accounting-refund-page">
      <header className="deposit-page-heading">
        <div><h1>Hoàn tiền cọc</h1><p>Đối chiếu người nhận và ghi nhận giao dịch hoàn cọc cho khách.</p></div>
      </header>
      {notice && <p className="booking-live-notice" role="status"><span aria-hidden="true">✓</span>{notice}</p>}
      {loadError && <p className="booking-inline-error" role="alert">{loadError}<button type="button" onClick={() => setRetryQueue((value) => value + 1)}>Tải lại</button></p>}
      <section className="accounting-refund-summary" aria-label="Tổng hợp yêu cầu hoàn cọc">
        <p className="accounting-refund-metric accounting-refund-metric--pending">
          <span>Yêu cầu chờ xử lý</span><strong>{loading ? '—' : pendingItems.length}</strong>
        </p>
        <p className="accounting-refund-metric">
          <span>Giá trị chờ hoàn</span><strong>{loading ? '—' : formatVnd(pendingAmount)}</strong>
        </p>
        <p className="accounting-refund-metric accounting-refund-metric--paid">
          <span>Đã hoàn cho khách</span><strong>{loading ? '—' : formatVnd(refundedAmount)}</strong>
        </p>
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
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm booking, khách hàng, ngân hàng..." aria-label="Tìm yêu cầu hoàn cọc" />
        </label>
      </div>
      <div className="accounting-refund-content-grid">
        <section className="deposit-table-card" aria-label="Danh sách yêu cầu hoàn cọc">
          <div className="deposit-table-scroll">
            <table className="deposit-table accounting-refund-table">
              <thead><tr><th>Booking</th><th>Khách hàng</th><th>Số tiền</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {loading && <tr><td className="deposit-table-message" colSpan="4">Đang tải yêu cầu hoàn cọc…</td></tr>}
                {!loading && loadError && <tr><td className="deposit-table-message deposit-table-message--error" colSpan="4">Danh sách chưa tải được.</td></tr>}
                {!loading && !loadError && visibleItems.length === 0 && <tr><td className="deposit-table-message" colSpan="4">
                  {items.length === 0 ? 'Chưa có yêu cầu hoàn cọc nào.' : filter === 'open' ? 'Không có yêu cầu đang chờ xử lý.' : 'Không tìm thấy yêu cầu phù hợp.'}
                </td></tr>}
                {!loading && visibleItems.map((item) => <tr key={item.id} className={item.id === selectedId ? 'is-selected' : ''}
                  onClick={() => { setSelectedId(item.id); setMutationError(''); }} tabIndex={0} aria-selected={item.id === selectedId}
                  onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedId(item.id); setMutationError(''); } }}>
                  <td><strong>{item.bookingCode}</strong><small>{dateTimeText(item.requestedAt)}</small></td>
                  <td><strong>{item.customerName}</strong><small>{item.phone || 'Không có số điện thoại'}</small></td>
                  <td><strong>{formatVnd(item.amount)}</strong></td>
                  <td><span className={statusClass(item.status)}>{statusLabels[item.status] || item.status}</span></td>
                </tr>)}
              </tbody>
            </table>
          </div>
          <footer className="deposit-table-footer"><span>{loading ? 'Đang cập nhật danh sách' : `${visibleItems.length} yêu cầu`}</span>
            <button type="button" className="accounting-refund-refresh" onClick={() => setReload((value) => value + 1)} disabled={loading}>Tải lại</button>
          </footer>
        </section>
        <RefundDetails item={selected} busy={busy} error={mutationError} onComplete={(reference, image) => completeRefund(selected, reference, image)} />
      </div>
    </div>
  </AppShell>;
}
