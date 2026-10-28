import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import BrandWordmark from '../../../shared/components/BrandWordmark';
import DashboardTopbar from '../../../shared/components/DashboardTopbar';
import AccountProfileDialog from '../../backoffice/accounts/components/AccountProfileDialog';
import '../../auth/styles/dashboard.css';
import { apiErrors, authApi } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { clearBookingDraft, readBookingDraft } from '../services/bookingDraft';
import BookingCreatePage from './BookingCreatePage';
import BookingDepositQr from '../components/BookingDepositQr';
import BookingEditDialog from './BookingEditDialog';
import { formatVnd, isLocalDateInput, toLocalDateInput } from '../data/bookingData';
import '../styles/booking.css';

const navigation = [
  ['Ca làm việc', 'shifts'],
  ['Hóa đơn thuế', 'tax'],
  ['Tổng quan', 'overview'], ['Phòng', 'rooms'], ['Booking', 'booking'], ['Order', 'orders'],
  ['Thanh toán', 'payments'], ['Tiền cọc', 'deposit'], ['Kho hàng', 'inventory'], ['Báo sự cố', 'issues'],
];
const accountingNavigation = [
  ['Hóa đơn thuế', 'tax'],
  ['Tổng quan', 'overview'],
  ['Thu & cấp quỹ', 'payments', true],
  ['Chi & hoàn ứng', 'plus', true],
  ['Đối soát chốt quỹ', 'calendar', true],
  ['Hoàn tiền cọc', 'deposit'],
  ['Lương nhân viên', 'booking', true],
  ['Báo cáo tài chính', 'orders', true],
  ['Danh mục chi phí', 'inventory', true],
];
const cancellationReasons = [
  'Khách thay đổi kế hoạch',
  'Khách đổi thời gian nhận phòng',
  'Khách không còn nhu cầu',
  'Khách đặt nhầm booking',
  'Khách không đồng ý giá hoặc phụ phí',
  'Khác',
];

const iconPaths = {
  shifts: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18m-13 4h3m3 0h3m-9 4h3"/></>,
  tax: <><path d="M6 3h9l4 4v14l-3-2-3 2-3-2-4 2V3Z"/><path d="M14 3v5h5M9 11h7M9 15h5"/></>,
  overview: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  rooms: <><path d="M3 20V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14M3 13h18M6 13V9a2 2 0 0 1 2-2h3v6m2 0V7h3a2 2 0 0 1 2 2v4M2 20h20"/></>,
  booking: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M8 14h3m-3 3h7"/></>,
  orders: <><path d="M4 5h16M4 12h16M4 19h16M7 3v4m10-4v4M7 10v4m10-4v4M7 17v4m10-4v4"/></>,
  payments: <><circle cx="12" cy="12" r="9"/><path d="M8 12h8m-4-4v8"/></>,
  deposit: <><path d="m12 3 9 9-9 9-9-9 9-9Z"/></>,
  inventory: <><path d="M4 4h16v16H4zM9 4v16m6-16v16"/></>,
  issues: <><path d="M5 21V4m0 0c5-4 9 4 14 0v11c-5 4-9-4-14 0"/></>,
  search: <><circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4.4 4.4"/></>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></>,
  plus: <><path d="M12 5v14M5 12h14"/></>,
  logout: <><path d="M10 17l5-5-5-5m5 5H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/></>,
};

// Vẽ biểu tượng dùng chung theo tên trong giao diện lễ tân.
function Icon({ name, size = 18 }) {
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{iconPaths[name]}</svg>;
}

// Lấy ngày hôm nay theo giờ địa phương cho bộ lọc lịch.
function localToday() { return toLocalDateInput(new Date()); }
// Chỉ khôi phục ngày điều hướng nếu đúng định dạng và là ngày lịch hợp lệ.
function bookingDateFromNavigation(value) {
  return isLocalDateInput(value) ? value : localToday();
}
// Lấy ngày kế tiếp theo giờ địa phương cho bộ lọc lịch.
function localTomorrow() { const date = new Date(); date.setDate(date.getDate() + 1); return toLocalDateInput(date); }
// Định dạng một phần thời gian theo múi giờ chi nhánh.
function displayPart(instant, options, timezone = 'Asia/Ho_Chi_Minh') {
  if (!instant) return '—';
  try { return new Intl.DateTimeFormat('vi-VN', { ...options, timeZone: timezone }).format(new Date(instant)); }
  catch { return new Intl.DateTimeFormat('vi-VN', options).format(new Date(instant)); }
}
// Đổi trạng thái API thành mã dùng cho CSS.
function statusId(value) {
  return ({ CONFIRMED: 'pending', IN_HOUSE: 'checkedIn', CANCELLED: 'cancelled', NO_SHOW: 'noShow', COMPLETED: 'completed', DRAFT: 'draft' })[value] || String(value || '').toLowerCase();
}
// Đổi trạng thái booking thành nhãn tiếng Việt.
function statusLabel(value) {
  return ({ CONFIRMED: 'Chờ check-in', IN_HOUSE: 'Đã check-in', CANCELLED: 'Đã hủy', NO_SHOW: 'No-show', COMPLETED: 'Hoàn tất', DRAFT: 'Nháp' })[value] || 'Chờ check-in';
}
// Định dạng giờ hẹn theo múi giờ chi nhánh.
function timeText(value, timezone) { return displayPart(value, { hour: '2-digit', minute: '2-digit', hour12: false }, timezone); }
// Định dạng ngày hẹn theo múi giờ chi nhánh.
function dateText(value, timezone) { return displayPart(value, { day: '2-digit', month: '2-digit', year: 'numeric' }, timezone); }
// Hiển thị phương thức đã thu cọc cho lễ tân.
function paymentMethodLabel(value) {
  return ({ BANK_TRANSFER: 'Chuyển khoản', MANUAL_TRANSFER: 'Chuyển khoản', CASH: 'Tiền mặt', CARD: 'Thẻ', QR: 'QR', MULTIPLE: 'Nhiều phương thức' })[value] || 'Chưa rõ phương thức';
}
// Mô tả kết quả cọc sau hủy và trường hợp chưa thu cọc.
function depositOutcomeLabel(outcome, receivedAmount, bookingStatus) {
  if (outcome === 'REFUND_ELIGIBLE') return 'Có cọc · Đủ điều kiện hoàn';
  if (outcome === 'NON_REFUNDABLE') return 'Có cọc · Không đủ điều kiện hoàn';
  if (outcome === 'NO_DEPOSIT') return 'Không có cọc';
  if (bookingStatus === 'CANCELLED' && receivedAmount > 0) return 'Chưa xác định kết quả hoàn cọc';
  return receivedAmount > 0 ? 'Đang giữ cọc' : 'Chưa thu cọc';
}
// Hiển thị số cọc còn giữ, chờ hoàn hoặc đã bị giữ lại.
function depositDispositionLabel(outcome, receivedAmount, balance, forfeitedAmount, bookingStatus) {
  if (outcome === 'NON_REFUNDABLE') return forfeitedAmount > 0 ? `Đã tịch thu ${formatVnd(forfeitedAmount)}` : 'Cọc đã bị tịch thu';
  if (outcome === 'REFUND_ELIGIBLE') return balance > 0 ? `Đang chờ hoàn ${formatVnd(balance)}` : `Số dư cọc hiện tại ${formatVnd(balance)}`;
  if (bookingStatus === 'CANCELLED' && receivedAmount > 0) return 'Chưa xác định cách xử lý cọc';
  return receivedAmount > 0 ? `Còn giữ ${formatVnd(balance)}` : null;
}
// Tạo tiêu đề tình trạng phòng trên bảng xác nhận check-in.
function checkInRoomTitle(booking, roomReady) {
  if (booking.status === 'IN_HOUSE') return `${booking.roomCode || 'Phòng'} đang được sử dụng cho booking này`;
  if (!booking.roomCode) return 'Booking chưa được gán phòng';
  return roomReady ? `${booking.roomCode} trống · sẵn sàng nhận khách` : `${booking.roomCode} chưa sẵn sàng nhận khách`;
}
// Giải thích vì sao phòng có hoặc chưa có thể check-in.
function checkInRoomHint(booking, roomReady) {
  if (booking.status === 'IN_HOUSE') return 'Check-in đã được ghi nhận.';
  return roomReady ? 'Phòng đã sẵn sàng cho booking này.' : 'Nếu phòng không sẵn sàng, hãy đổi phòng trước khi check-in.';
}
export function AppShell({
  user,
  branchName,
  busy,
  onLogout,
  children,
  breadcrumb,
  search = '',
  onSearchChange,
  activeTab = 'booking',
  hideSearch = false,
  fitViewport = false,
  audience = 'receptionist',
}) {
  const navigate = useNavigate();
  const [profileOpen, setProfileOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try {
      return window.localStorage.getItem(`gcmb:${audience}:sidebar-collapsed`) === 'true';
    } catch {
      return false;
    }
  });
  useEffect(() => {
    try {
      window.localStorage.setItem(`gcmb:${audience}:sidebar-collapsed`, String(sidebarCollapsed));
    } catch {
      // Tùy chọn vẫn dùng được trong phiên hiện tại nếu trình duyệt chặn lưu trữ.
    }
  }, [audience, sidebarCollapsed]);
  const isAccountant = audience === 'accountant';
  const toggleSidebar = () => setSidebarCollapsed((collapsed) => !collapsed);

  if (isAccountant) {
    const navItems = accountingNavigation;
    const roleLabel = 'KẾ TOÁN THU CHI';
    const now = new Date();
    const currentMonth = new Intl.DateTimeFormat('vi-VN', {
      month: '2-digit',
      year: 'numeric',
    }).format(now);

    return (
      <>
        <div className={`booking-app reception-dashboard${fitViewport ? ' booking-app--fit' : ''} booking-app--accountant`}>
          <div className={`head-office-dashboard${sidebarCollapsed ? ' head-office-dashboard--sidebar-collapsed' : ''}`}>
            <aside className="manager-sidebar" id="app-sidebar" aria-label="Kế toán thu chi">
              <div className="manager-brand"><BrandWordmark /></div>
              <p className="manager-role">KẾ TOÁN THU CHI</p>
              <nav id="booking-navigation" className="manager-navigation" aria-label="Điều hướng kế toán thu chi">
                {navItems.map(([label, icon, unavailable]) => (
                  <button
                    type="button"
                    key={label}
                    aria-label={label}
                    aria-current={icon === activeTab ? 'page' : undefined}
                    aria-disabled={Boolean(unavailable)}
                    disabled={Boolean(unavailable)}
                    title={unavailable ? 'Đang phát triển' : label}
                    onClick={() => {
                      if (unavailable) return;
                      if (icon === 'tax') navigate('/ke-toan/hoa-don-thue');
                      if (icon === 'overview') navigate('/ke-toan');
                      if (icon === 'deposit') navigate('/ke-toan/hoan-coc');
                    }}
                  >
                    <Icon name={icon} /><span>{label}</span>
                  </button>
                ))}
              </nav>
              <div className="manager-sidebar-footer">
                <strong>GenZ Cinema &amp; Music Box</strong>
                <p>Phạm vi: {branchName || 'cơ sở được phân công'}</p>
              </div>
            </aside>

            <div className="manager-workspace">
              <DashboardTopbar
                role={roleLabel}
                page={breadcrumb}
                user={user}
                busy={busy}
                onLogout={onLogout}
                onProfile={() => setProfileOpen(true)}
                onToggleSidebar={toggleSidebar}
                sidebarExpanded={!sidebarCollapsed}
              >
                <span className="booking-topbar-datetime">Tháng {currentMonth}</span>
              </DashboardTopbar>
              <main className="booking-main" id="main">{user?.readOnly && <p className="booking-inline-error" role="status">Cơ sở {branchName} đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.</p>}{children}</main>
            </div>
          </div>
        </div>

        {profileOpen && (
          <div className="dashboard-page dashboard-page-manager profile-dashboard-context">
            <AccountProfileDialog onClose={() => setProfileOpen(false)} />
          </div>
        )}
      </>
    );
  }

  function selectTab(icon) {
    if (icon === 'shifts') navigate('/le-tan/ca-lam');
    if (icon === 'tax') navigate('/le-tan/hoa-don-thue');
    if (icon === 'booking') {
      navigate(readBookingDraft(user) ? '/le-tan/bookings/new' : '/le-tan/bookings');
    } else if (icon === 'rooms') {
      navigate('/le-tan/phong');
    } else if (icon === 'orders') {
      navigate('/le-tan/don-mon');
    } else if (icon === 'payments') {
      navigate('/le-tan/thanh-toan');
    } else if (icon === 'deposit') {
      navigate('/le-tan/deposits');
    }
  }

  return (
    <>
      <div className={`booking-app reception-dashboard${fitViewport ? ' booking-app--fit' : ''}`}>
        <div className={`head-office-dashboard${sidebarCollapsed ? ' head-office-dashboard--sidebar-collapsed' : ''}`}>
          <aside className="manager-sidebar" id="app-sidebar" aria-label="Lễ tân">
            <div className="manager-brand"><BrandWordmark /></div>
            <p className="manager-role">LỄ TÂN</p>
            <nav id="booking-navigation" className="manager-navigation" aria-label="Điều hướng lễ tân">
              {navigation.map(([label, icon]) => (
                <button
                  type="button"
                  key={label}
                  aria-label={label}
                  title={label}
                  aria-current={icon === activeTab ? 'page' : undefined}
                  aria-disabled={!['booking', 'rooms', 'orders', 'payments', 'deposit', 'tax', 'shifts'].includes(icon)}
                  onClick={() => selectTab(icon)}
                >
                  <Icon name={icon} /><span>{label}</span>
                </button>
              ))}
            </nav>
            <div className="manager-sidebar-footer">
              <strong>{branchName || 'Cơ sở theo tài khoản'}</strong>
              <p>Đang làm việc · Lễ tân</p>
            </div>
          </aside>

          <div className="manager-workspace">
            <DashboardTopbar
              role="LỄ TÂN"
              page={breadcrumb}
              user={user}
              busy={busy}
              onLogout={onLogout}
              onProfile={() => setProfileOpen(true)}
              onToggleSidebar={toggleSidebar}
              sidebarExpanded={!sidebarCollapsed}
            >
              {!hideSearch && (
                <label className="booking-global-search">
                  <Icon name="search" />
                  <input
                    value={search}
                    onChange={(event) => onSearchChange?.(event.target.value)}
                    placeholder="Tìm phòng, booking, khách hàng..."
                    aria-label={
                      activeTab === 'rooms'
                        ? 'Tìm kiếm phòng'
                        : activeTab === 'deposit'
                          ? 'Tìm giao dịch tiền cọc'
                          : 'Tìm kiếm booking'
                    }
                  />
                </label>
              )}
            </DashboardTopbar>
            <main className="booking-main" id="main">{user?.readOnly && <p className="booking-inline-error" role="status">Cơ sở {branchName} đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.</p>}{children}</main>
          </div>
        </div>
      </div>

      {profileOpen && (
        <div className="dashboard-page dashboard-page-manager profile-dashboard-context">
          <AccountProfileDialog onClose={() => setProfileOpen(false)} />
        </div>
      )}
    </>
  );
}
// Chọn lớp CSS theo trạng thái booking.
function statusClass(id) { return `booking-status booking-status-${id}`; }

// Hiển thị thông tin booking, cọc và các thao tác được phép tại thời điểm hiện tại.
function BookingDetails({ booking, detail, timezone, modal, onClose, onCheckIn, onEdit, onCancel, onNoShow, onDepositClose }) {
  const navigate = useNavigate();
  const panelRef = useRef(null);
  const [qrBookingId, setQrBookingId] = useState(null);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, [booking?.id]);
  useEffect(() => {
    const panel = panelRef.current;
    panel?.querySelector('.booking-details-close')?.focus();
    if (!modal || !panel) return undefined;
    function trapFocus(event) {
      if (event.key !== 'Tab') return;
      const focusable = [...panel.querySelectorAll('button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')]
        .filter((element) => element.offsetParent !== null);
      if (!focusable.length) { event.preventDefault(); panel.focus(); return; }
      if (event.shiftKey && document.activeElement === focusable[0]) {
        event.preventDefault();
        focusable[focusable.length - 1].focus();
      } else if (!event.shiftKey && document.activeElement === focusable[focusable.length - 1]) {
        event.preventDefault();
        focusable[0].focus();
      }
    }
    panel.addEventListener('keydown', trapFocus);
    return () => panel.removeEventListener('keydown', trapFocus);
  }, [modal]);
  const currentDetail = detail?.booking?.id === booking?.id ? detail : null;
  const panelRole = modal ? 'dialog' : 'complementary';
  if (!booking) return <aside ref={panelRef} className="booking-details" role={panelRole} aria-modal={modal || undefined} aria-label="Chi tiết booking" tabIndex={-1}>
    <div className="booking-details-scroll">
      <header className="booking-details-heading"><h2>Chi tiết booking</h2><button type="button" className="booking-details-close" onClick={onClose} aria-label="Đóng chi tiết booking">×</button></header>
      <p className="booking-details-loading" role="status">Đang tải thông tin booking…</p>
    </div>
  </aside>;
  const depositDetail = currentDetail?.deposit || null;
  const depositReceived = Number(depositDetail?.receivedAmount ?? booking.depositReceivedAmount ?? booking.depositBalance ?? 0);
  const depositBalance = Number(depositDetail?.balance ?? booking.depositBalance ?? 0);
  const depositForfeited = Number(depositDetail?.forfeitedAmount ?? 0);
  const depositOutcome = depositDetail?.cancellationOutcome;
  const depositStatus = depositOutcomeLabel(depositOutcome, depositReceived, booking.status);
  const depositDisposition = depositDispositionLabel(depositOutcome, depositReceived, depositBalance, depositForfeited, booking.status);
  const canCheckIn = booking.status === 'CONFIRMED';
  const canEdit = canCheckIn && new Date(booking.plannedStart) > new Date();
  const hasReservedRoom = currentDetail?.allocations?.some((allocation) => allocation.state === 'RESERVED');
  const canMarkNoShow = canCheckIn && hasReservedRoom && new Date(booking.plannedStart).getTime() <= now.getTime();
  const room = [booking.roomCode, booking.roomName].filter(Boolean).join(' · ') || 'Chưa gán phòng';
  return <aside ref={panelRef} className="booking-details" role={panelRole} aria-modal={modal || undefined} aria-label={`Chi tiết booking ${booking.code}`} tabIndex={-1}>
    <div className="booking-details-scroll">
      <header className="booking-details-heading">
        <div><h2>Chi tiết booking</h2><strong className="booking-detail-code">{booking.code}</strong></div>
        <span className={statusClass(statusId(booking.status))}>{statusLabel(booking.status)}</span>
        <button type="button" className="booking-details-close" onClick={onClose} aria-label="Đóng chi tiết booking">×</button>
      </header>
      <div className="booking-detail-divider" />
      <section className={`booking-detail-deposit${depositOutcome === 'NON_REFUNDABLE' ? ' is-danger' : depositOutcome === 'REFUND_ELIGIBLE' ? ' is-refund' : ''}`} aria-label="Thông tin tiền cọc">
        <div><span>Tiền cọc đã nhận</span><strong>{formatVnd(depositReceived)}</strong></div>
        <div><span className="booking-deposit-status">{depositStatus}</span>{depositDisposition && <small>{depositDisposition}</small>}</div>
      </section>
      {canCheckIn && depositReceived === 0 && <button type="button" className="booking-button booking-button-secondary" onClick={() => setQrBookingId(booking.id)}>QR cọc 30%</button>}
      {qrBookingId === booking.id && <BookingDepositQr bookingId={booking.id} onBack={() => { setQrBookingId(null); onDepositClose(); }} />}
      <div className="booking-detail-divider" />
      <dl className="booking-detail-list">
        <div><dt>Khách hàng</dt><dd>{booking.contactName || '—'}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{booking.contactPhone || '—'}</dd></div>
        <div><dt>Giờ hẹn lúc đặt</dt><dd>{timeText(booking.plannedStart, timezone)} · {dateText(booking.plannedStart, timezone)}</dd></div>
        <div><dt>Số khách</dt><dd>{booking.guestCount || '—'}</dd></div>
        <div><dt>Hạng phòng</dt><dd>{booking.roomTypeName || booking.roomName || '—'}</dd></div>
        <div><dt>Phòng</dt><dd>{room}</dd></div>
        <div><dt>Giá dự kiến</dt><dd>{formatVnd(booking.expectedAmount)}</dd></div>
      </dl>
      {currentDetail?.note && <p className="booking-detail-note">{currentDetail.note}</p>}
      <div className="booking-detail-room-ready"><i />{booking.roomCode ? `Phòng ${booking.roomCode} đã được giữ cho booking này` : 'Booking chưa được gán phòng'}</div>
    </div>
    <div className="booking-details-actions" role="group" aria-label="Thao tác booking">
      <strong className="booking-details-actions-title">Thao tác</strong>
      <button type="button" className="booking-button booking-button-primary booking-detail-checkin" onClick={onCheckIn} disabled={!canCheckIn}>Check-in</button>
      {canMarkNoShow && <button type="button" className="booking-button booking-button-danger booking-detail-no-show" onClick={() => onNoShow(booking)}>Đánh dấu no-show</button>}
      {booking.status === 'IN_HOUSE' && <button type="button" className="booking-detail-room-link" onClick={() => navigate('/le-tan/phong', { state: { roomId: booking.roomId } })}>Xem phòng và thao tác lưu trú</button>}
      <div className="booking-detail-actions"><button type="button" className="booking-detail-action-edit" disabled={!canEdit} onClick={() => onEdit(booking)}>Sửa booking</button><button type="button" className="booking-detail-action-cancel is-danger" disabled={!canCheckIn} onClick={() => onCancel(booking)}>Hủy booking</button></div>
    </div>
  </aside>;
}

// Xác nhận phòng và thời lượng trước khi gửi thao tác check-in.
function CheckInPanel({ booking, detail, timezone, onClose, onConfirm, busy, error }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);
  const actualTime = displayPart(now, { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', hour12: false }, timezone);
  const recordedCheckIn = detail?.booking?.id === booking?.id
    ? detail.booking.actualCheckin || booking?.actualCheckin
    : booking?.actualCheckin;
  const savedCheckInTime = recordedCheckIn
    ? displayPart(recordedCheckIn, { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', hour12: false }, timezone)
    : 'Chưa có dữ liệu';
  const charge = Number(booking?.expectedAmount || 0);
  const depositReceived = Number(detail?.booking?.id === booking?.id
    ? detail.deposit?.receivedAmount ?? booking?.depositReceivedAmount ?? booking?.depositBalance ?? 0
    : booking?.depositReceivedAmount ?? booking?.depositBalance ?? 0);
  const depositBalance = Number(detail?.booking?.id === booking?.id ? detail.deposit?.balance ?? booking?.depositBalance ?? 0 : booking?.depositBalance ?? 0);
  const depositDetail = detail?.booking?.id === booking?.id ? detail.deposit : null;
  const depositOutcome = depositDetail?.cancellationOutcome;
  const depositStatus = depositOutcomeLabel(depositOutcome, depositReceived, booking?.status);
  const depositDisposition = depositDispositionLabel(depositOutcome, depositReceived, depositBalance, Number(depositDetail?.forfeitedAmount ?? 0), booking?.status);
  const roomReady = booking?.roomOperatingStatus === 'AVAILABLE' && Boolean(booking?.roomCode);
  const plannedStartMs = booking ? new Date(booking.plannedStart).getTime() : NaN;
  const plannedEndMs = booking ? new Date(booking.plannedEnd).getTime() : NaN;
  const earlyCheckIn = Boolean(booking) && now.getTime() < plannedStartMs;
  const shiftedCheckout = earlyCheckIn ? new Date(now.getTime() + (plannedEndMs - plannedStartMs)) : null;
  const checkInTimeValid = booking && plannedEndMs > now.getTime();
  let checkInButtonLabel = booking?.status === 'IN_HOUSE' ? 'Đã check-in' : 'Xác nhận check-in';
  if (busy) checkInButtonLabel = 'Đang xác nhận…';
  if (!booking) return <div className="booking-checkin-overlay"><button className="booking-overlay-dismiss" type="button" onClick={onClose} aria-label="Đóng" /><aside className="booking-checkin-panel"><button className="booking-panel-close" type="button" onClick={onClose}>×</button><p role="status">Đang tải thông tin booking…</p></aside></div>;
  return <div className="booking-checkin-overlay">
    <button className="booking-overlay-dismiss" type="button" onClick={onClose} aria-label="Đóng check-in" />
    <aside className="booking-checkin-panel" aria-labelledby="booking-checkin-title">
      <div className="booking-checkin-heading"><div><h2 id="booking-checkin-title">Check-in booking</h2><strong>{booking.code}</strong></div><button className="booking-panel-close" type="button" onClick={onClose} aria-label="Đóng">×</button></div>
      <section className="booking-checkin-section"><h3>Khách hàng</h3><strong className="booking-checkin-customer">{booking.contactName}</strong><p>{booking.contactPhone || 'Chưa có số điện thoại'} · {booking.guestCount ? `${booking.guestCount} khách` : 'Chưa cập nhật số khách'}</p></section>
      <section className="booking-checkin-section"><h3>Thông tin đặt phòng</h3><div className="booking-checkin-grid">
        <div><span>Ngày nhận</span><strong>{dateText(booking.plannedStart, timezone)}</strong></div><div><span>Giờ hẹn lúc đặt</span><strong>{timeText(booking.plannedStart, timezone)}</strong></div>
        <div><span>Hạng phòng</span><strong>{booking.roomTypeName || booking.roomName || '—'}</strong></div><div><span>Phòng</span><strong>{booking.roomCode ? `${booking.roomCode} · ${booking.roomName || ''}` : 'Chưa gán'}</strong></div>
      </div></section>
      <div className={`booking-checkin-ready${roomReady ? '' : ' is-unavailable'}`}><i /><div>
        <strong>{checkInRoomTitle(booking, roomReady)}</strong>
        <p>{checkInRoomHint(booking, roomReady)}</p>
      </div></div>
      <section className="booking-checkin-deposit"><div><strong>Tiền cọc đã nhận</strong><b>{formatVnd(depositReceived)}</b></div><div className="booking-checkin-deposit-meta"><span className={`booking-deposit-status${depositOutcome === 'NON_REFUNDABLE' ? ' is-danger' : depositOutcome === 'REFUND_ELIGIBLE' ? ' is-refund' : ''}`}>{depositStatus}</span>{depositReceived > 0 && <small>{paymentMethodLabel(depositDetail?.paymentMethod)} · {depositDisposition}</small>}</div></section>
      <label className="booking-checkin-time"><span>{booking.status === 'IN_HOUSE' ? 'Giờ check-in đã ghi nhận' : 'Giờ check-in thực tế'}</span><div><input value={booking.status === 'IN_HOUSE' ? savedCheckInTime : actualTime} readOnly /><small>{booking.status === 'IN_HOUSE' ? 'Đã lưu theo giờ hệ thống' : 'Sẽ ghi nhận khi xác nhận'}</small></div></label>
      {booking.status === 'CONFIRMED' && earlyCheckIn && roomReady && checkInTimeValid && <p className="booking-checkin-time-note is-info">Nhận phòng sớm sẽ giữ giờ hẹn {timeText(booking.plannedStart, timezone)} và dời giờ trả dự kiến đến {timeText(shiftedCheckout, timezone)} · {dateText(shiftedCheckout, timezone)}, để khách vẫn dùng đủ thời lượng đã đặt. Hệ thống sẽ kiểm tra khung giờ mới có trùng booking khác không.</p>}
      <section className="booking-checkin-receipt"><strong>Tóm tắt</strong><div><span>{detail?.charges?.[0]?.description || 'Dịch vụ phòng'}</span><b>{formatVnd(charge)}</b></div><p>Tiền cọc đang giữ {formatVnd(depositBalance)} sẽ được đối chiếu khi checkout.</p></section>
      {booking.status !== 'CONFIRMED' && booking.status !== 'IN_HOUSE' && <p className="booking-checkin-time-note">Booking này không còn ở trạng thái chờ check-in.</p>}
      {booking.status === 'CONFIRMED' && !checkInTimeValid && <p className="booking-checkin-time-note">Đã quá giờ kết thúc booking, không thể check-in.</p>}
      {error && <p className="booking-inline-error" role="alert">{error}</p>}
      <button type="button" className="booking-button booking-button-primary booking-confirm-checkin" onClick={() => onConfirm(booking)} disabled={booking.status !== 'CONFIRMED' || !roomReady || !checkInTimeValid || busy}>{checkInButtonLabel}</button>
    </aside>
  </div>;
}

// Nạp số dư cọc mới nhất rồi xác định nhánh hoàn hoặc giữ cọc khi hủy.
function CancelDialog({ booking, onClose, onConfirm, busy, error, timezone }) {
  const [reasonOption, setReasonOption] = useState(cancellationReasons[0]);
  const [otherReason, setOtherReason] = useState('');
  const [beneficiaryName, setBeneficiaryName] = useState('');
  const [bankAccount, setBankAccount] = useState('');
  const [bankName, setBankName] = useState('');
  const [bookingDetail, setBookingDetail] = useState(null);
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    setReasonOption(cancellationReasons[0]);
    setOtherReason('');
    setNow(new Date());
    setBeneficiaryName(booking?.contactName || '');
    setBankAccount('');
    setBankName('');
    setBookingDetail(null);
    setDetailError('');
    if (!booking?.id) return undefined;
    let active = true;
    setDetailLoading(true);
    bookingApi.detail(booking.id).then((value) => { if (active) setBookingDetail(value); })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setDetailError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [booking?.id, booking?.contactName]);
  useEffect(() => {
    if (!booking?.id) return undefined;
    const timer = window.setInterval(() => setNow(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, [booking?.id]);
  if (!booking) return null;
  const reason = reasonOption === 'Khác' ? otherReason.trim() : reasonOption;
  const currentDetail = bookingDetail?.booking?.id === booking.id ? bookingDetail : null;
  const currentBooking = currentDetail?.booking || booking;
  const depositAmount = currentDetail ? Number(currentDetail.deposit?.balance ?? currentBooking.depositBalance ?? 0) : null;
  const hasDeposit = depositAmount !== null && depositAmount > 0;
  const cutoffMinutes = Number(currentDetail?.deposit?.refundCutoffMinutes || 40);
  const millisecondsUntilStart = new Date(currentBooking.plannedStart).getTime() - now.getTime();
  const refundEligible = hasDeposit && millisecondsUntilStart >= cutoffMinutes * 60000;
  const cancellationAllowed = millisecondsUntilStart > 0 && ['CONFIRMED', 'DRAFT'].includes(currentBooking.status);
  const remainingMinutes = Math.max(0, Math.floor(millisecondsUntilStart / 60000));
  let remainingLabel;
  if (millisecondsUntilStart <= 0) remainingLabel = 'Đã đến giờ nhận phòng';
  else if (remainingMinutes >= 60) {
    remainingLabel = `Còn ${String(Math.floor(remainingMinutes / 60)).padStart(2, '0')} giờ ${String(remainingMinutes % 60).padStart(2, '0')} phút trước check-in`;
  } else remainingLabel = `Còn ${remainingMinutes} phút trước check-in`;
  const cancelAt = displayPart(now, { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', hour12: false }, timezone);
  const cancelError = error || detailError;
  const missingRefundDetails = refundEligible && (!beneficiaryName.trim() || !bankAccount.trim() || !bankName.trim());
  const depositMethod = paymentMethodLabel(currentDetail?.deposit?.paymentMethod);
  let outcomeClass = 'is-empty';
  if (hasDeposit) outcomeClass = 'is-forfeited';
  if (refundEligible) outcomeClass = 'is-refundable';
  let outcomeTitle;
  let outcomeDescription;
  if (!currentDetail && !detailError) {
    outcomeTitle = 'Đang tải thông tin tiền cọc';
    outcomeDescription = 'Đang đối chiếu khoản cọc đã ghi nhận và điều kiện hoàn.';
  } else if (!currentDetail) {
    outcomeTitle = 'Không tải được thông tin tiền cọc';
    outcomeDescription = 'Vui lòng thử mở lại hộp thoại trước khi hủy booking.';
  } else if (!cancellationAllowed) {
    outcomeTitle = 'Đã đến giờ sử dụng';
    outcomeDescription = 'Booking đã đến giờ sử dụng; hãy xử lý theo luồng check-in hoặc no-show.';
  } else if (refundEligible) {
    outcomeTitle = '✓ Đủ điều kiện hoàn cọc';
    outcomeDescription = `${remainingLabel} · điều kiện hoàn: hủy trước ít nhất ${cutoffMinutes} phút`;
  } else if (hasDeposit) {
    outcomeTitle = '! Không đủ điều kiện hoàn cọc';
    outcomeDescription = `${remainingLabel} · hủy dưới mốc ${cutoffMinutes} phút`;
  } else {
    outcomeTitle = 'Không có tiền cọc';
    outcomeDescription = 'Booking sẽ được hủy và phòng được giải phóng.';
  }
  let cancelButtonLabel = 'Xác nhận hủy';
  if (busy) cancelButtonLabel = 'Đang hủy…';
  else if (!cancellationAllowed) cancelButtonLabel = 'Không thể hủy lúc này';
  else if (refundEligible) cancelButtonLabel = 'Hủy & yêu cầu hoàn';
  return <div className="booking-cancel-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="booking-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="cancel-title">
      <div className="booking-cancel-title"><div><h2 id="cancel-title">Hủy booking</h2><p>{currentBooking.code} · {currentBooking.contactName}</p></div><button type="button" className="booking-panel-close" onClick={onClose} aria-label="Đóng">×</button></div>
      <h3 className="booking-cancel-section-title">Thông tin booking</h3>
      <div className="booking-cancel-booking-info"><div><span>Khách hàng</span><strong>{currentBooking.contactName}</strong></div><div><span>Số điện thoại</span><strong>{currentBooking.contactPhone || '—'}</strong></div><div><span>Check-in</span><strong>{timeText(currentBooking.plannedStart, timezone)} · {dateText(currentBooking.plannedStart, timezone)}</strong></div><div><span>Phòng</span><strong>{currentBooking.roomCode ? `${currentBooking.roomCode} · ${currentBooking.roomName || ''}` : 'Chưa gán'}</strong></div></div>
      <div className="booking-cancel-deposit-summary"><span>Tiền cọc đã nhận</span><strong>{depositAmount === null ? (detailError ? 'Chưa xác định' : 'Đang tải…') : formatVnd(depositAmount)}{hasDeposit ? ` · ${depositMethod}` : ''}</strong></div>
      <div className={`booking-cancel-outcome ${outcomeClass}`} role="status">
        <strong>{outcomeTitle}</strong>
        <p>{outcomeDescription}</p>
        <small>Thời điểm hủy dự kiến: {cancelAt}</small>
      </div>
      <label className="booking-form-field booking-cancel-reason"><span>Lý do hủy *</span><select value={reasonOption} onChange={(event) => { setReasonOption(event.target.value); setOtherReason(''); }}>
        {cancellationReasons.map((item) => <option key={item} value={item}>{item}</option>)}
      </select></label>
      {reasonOption === 'Khác' && <label className="booking-form-field booking-cancel-reason"><span>Lý do cụ thể *</span><textarea rows="2" maxLength="1000" value={otherReason} onChange={(event) => setOtherReason(event.target.value)} placeholder="Nhập lý do hủy booking" /></label>}
      {refundEligible && <>
        <h3 className="booking-cancel-section-title">Thông tin tài khoản hoàn cọc</h3>
        <div className="booking-cancel-account-grid">
          <label className="booking-form-field"><span>Chủ tài khoản *</span><input maxLength="200" value={beneficiaryName} onChange={(event) => setBeneficiaryName(event.target.value)} placeholder="Tên chủ tài khoản" /></label>
          <label className="booking-form-field"><span>Số tài khoản *</span><input maxLength="60" value={bankAccount} onChange={(event) => setBankAccount(event.target.value)} inputMode="numeric" placeholder="Nhập số tài khoản" /></label>
          <label className="booking-form-field"><span>Ngân hàng *</span><input maxLength="200" value={bankName} onChange={(event) => setBankName(event.target.value)} placeholder="Nhập tên ngân hàng" /></label>
        </div>
        <div className="booking-cancel-next-step"><strong>Tạo yêu cầu hoàn {formatVnd(depositAmount)} và chuyển Kế toán xử lý.</strong><p>Lễ tân không đánh dấu giao dịch là đã hoàn.</p></div>
      </>}
      {hasDeposit && !refundEligible && <div className="booking-cancel-forfeit-note"><strong>{formatVnd(depositAmount)} cọc sẽ chuyển thành “Cọc không hoàn”.</strong><p>Booking chuyển sang Đã hủy, phòng được giải phóng.</p><small>Lý do và thời điểm hủy được lưu trong lịch sử.</small></div>}
      {detailLoading && <p role="status">Đang tải số dư cọc và điều kiện hoàn…</p>}
      {cancelError && <p className="booking-inline-error" role="alert">{cancelError}</p>}
      <div className="booking-cancel-buttons"><button className={`booking-button ${refundEligible ? 'booking-button-primary' : 'booking-button-danger'}`} type="button" disabled={!reason.trim() || missingRefundDetails || !cancellationAllowed || busy || !currentDetail || detailLoading || Boolean(detailError)} onClick={() => onConfirm(booking, { reason: reason.trim(), beneficiaryName: beneficiaryName.trim(), bankAccount: bankAccount.trim(), bankName: bankName.trim() })}>{cancelButtonLabel}</button><button className="booking-button booking-button-secondary" type="button" onClick={onClose}>Quay lại</button></div>
    </section>
  </div>;
}

// Nạp booking mới nhất để xác nhận no-show và số cọc sẽ bị giữ.
function NoShowDialog({ booking, onClose, onConfirm, busy, error, timezone }) {
  const [bookingDetail, setBookingDetail] = useState(null);
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [reason, setReason] = useState('Khách không đến nhận phòng');
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    setBookingDetail(null);
    setDetailError('');
    setReason('Khách không đến nhận phòng');
    if (!booking?.id) return undefined;
    let active = true;
    setDetailLoading(true);
    bookingApi.detail(booking.id).then((value) => { if (active) setBookingDetail(value); })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setDetailError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [booking?.id]);
  useEffect(() => {
    setNow(new Date());
    if (!booking?.id) return undefined;
    const timer = window.setInterval(() => setNow(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, [booking?.id]);
  if (!booking) return null;
  const currentDetail = bookingDetail?.booking?.id === booking.id ? bookingDetail : null;
  const currentBooking = currentDetail?.booking || booking;
  const depositBalance = currentDetail ? Number(currentDetail.deposit?.balance ?? 0) : null;
  const hasReservedRoom = currentDetail?.allocations?.some((allocation) => allocation.state === 'RESERVED');
  const hasStarted = new Date(currentBooking.plannedStart).getTime() <= now.getTime();
  const canMarkNoShow = currentBooking.status === 'CONFIRMED' && hasStarted && hasReservedRoom;
  const detailMessage = detailError || (!currentDetail && !detailLoading ? 'Không tải được thông tin booking. Hãy đóng và mở lại hộp thoại.' : '');
  const markedAt = displayPart(now, { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', hour12: false }, timezone);
  let outcomeTitle = 'Đang tải thông tin tiền cọc';
  let outcomeDescription = 'Đang đối chiếu số dư tiền cọc còn lại.';
  let outcomeClass = 'is-empty';
  if (currentDetail && depositBalance > 0) {
    outcomeTitle = `Cọc ${formatVnd(depositBalance)} sẽ bị tịch thu`;
    outcomeDescription = 'Số dư cọc hiện tại được ghi thành khoản FORFEIT theo chính sách no-show.';
    outcomeClass = 'is-forfeited';
  } else if (currentDetail) {
    outcomeTitle = 'Không có cọc cần xử lý';
    outcomeDescription = 'Booking sẽ chuyển sang NO_SHOW và phòng được giải phóng.';
  }
  const buttonLabel = busy ? 'Đang ghi nhận…' : !hasStarted ? 'Chưa tới giờ bắt đầu' : !hasReservedRoom && currentDetail ? 'Không còn phòng đang giữ' : !canMarkNoShow ? 'Chưa thể đánh dấu no-show' : 'Xác nhận no-show';
  return <div className="booking-cancel-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <section className="booking-cancel-dialog booking-no-show-dialog" role="dialog" aria-modal="true" aria-labelledby="no-show-title">
      <div className="booking-cancel-title"><div><h2 id="no-show-title">Đánh dấu khách no-show</h2><p>{currentBooking.code} · {currentBooking.contactName}</p></div><button type="button" className="booking-panel-close" onClick={onClose} aria-label="Đóng" disabled={busy}>×</button></div>
      <h3 className="booking-cancel-section-title">Thông tin booking</h3>
      <div className="booking-cancel-booking-info"><div><span>Khách hàng</span><strong>{currentBooking.contactName}</strong></div><div><span>Số điện thoại</span><strong>{currentBooking.contactPhone || '—'}</strong></div><div><span>Giờ bắt đầu</span><strong>{timeText(currentBooking.plannedStart, timezone)} · {dateText(currentBooking.plannedStart, timezone)}</strong></div><div><span>Phòng</span><strong>{currentBooking.roomCode ? `${currentBooking.roomCode} · ${currentBooking.roomName || ''}` : 'Chưa gán'}</strong></div></div>
      <div className={`booking-cancel-outcome ${outcomeClass}`} role="status"><strong>{outcomeTitle}</strong><p>{outcomeDescription}</p><small>Thời điểm ghi nhận dự kiến: {markedAt}</small></div>
      <label className="booking-form-field booking-cancel-reason"><span>Ghi chú</span><textarea rows="2" maxLength="1000" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Khách không đến nhận phòng" /></label>
      <p className="booking-no-show-warning">Xác nhận sẽ đổi trạng thái booking thành NO_SHOW, giải phóng phòng đang giữ và tịch thu số dư cọc (nếu có). Không thể check-in booking sau thao tác này.</p>
      {detailLoading && <p role="status">Đang tải số dư cọc…</p>}
      {detailMessage && <p className="booking-inline-error" role="alert">{detailMessage}</p>}
      {error && <p className="booking-inline-error" role="alert">{error}</p>}
      <div className="booking-cancel-buttons"><button className="booking-button booking-button-danger" type="button" disabled={!currentDetail || !canMarkNoShow || detailLoading || Boolean(detailError) || busy} onClick={() => onConfirm(booking, { reason: reason.trim() || undefined })}>{buttonLabel}</button><button className="booking-button booking-button-secondary" type="button" onClick={onClose} disabled={busy}>Quay lại</button></div>
    </section>
  </div>;
}

// Điều phối danh sách, chi tiết và các thao tác booking trong phạm vi lễ tân.
export default function BookingManagementPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { bookingId } = useParams();
  const createMode = location.pathname.endsWith('/new');
  const checkinMode = location.pathname.endsWith('/check-in');
  const editMode = location.pathname.endsWith('/edit');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [options, setOptions] = useState(null);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionsError, setOptionsError] = useState('');
  const [selectedDate, setSelectedDate] = useState(() => bookingDateFromNavigation(location.state?.bookingDate));
  const [activeFilter, setActiveFilter] = useState('all');
  const [roomTier, setRoomTier] = useState('all');
  const [search, setSearch] = useState(() => location.state?.search || '');
  const [rows, setRows] = useState([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [hasLoadedRows, setHasLoadedRows] = useState(false);
  const [selectedId, setSelectedId] = useState(() => location.state?.bookingId || '');
  const [detailsOpen, setDetailsOpen] = useState(() => Boolean(location.state?.bookingId));
  const [modalDetails, setModalDetails] = useState(() => typeof window !== 'undefined' && Boolean(window.matchMedia?.('(max-width: 999px)').matches));
  const rowFocusRef = useRef(null);
  const pendingSelectionRef = useRef('');
  const [detail, setDetail] = useState(null);
  const [notice, setNotice] = useState('');
  const [noticeKind, setNoticeKind] = useState('success');
  const [checkInError, setCheckInError] = useState('');
  const [reload, setReload] = useState(0);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelError, setCancelError] = useState('');
  const [noShowTarget, setNoShowTarget] = useState(null);
  const [noShowError, setNoShowError] = useState('');
  const [busy, setBusy] = useState(false);

  const openBookingDetails = (id, row) => {
    rowFocusRef.current = row?.querySelector('.booking-row-action') || row;
    setSelectedId(id);
    setDetailsOpen(true);
  };
  const closeBookingDetails = () => {
    setDetailsOpen(false);
    window.requestAnimationFrame(() => rowFocusRef.current?.focus());
  };

  useEffect(() => { document.title = `${createMode ? 'Tạo booking' : editMode ? 'Sửa booking' : 'Booking'} | GenZ Cinema & Music Box`; }, [createMode, editMode]);

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else { setUser(identity); setError(''); }
    }).catch((requestError) => {
      if (!active) return;
      if (requestError.response?.status === 401) navigate('/dang-nhap', { replace: true, state: { message: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' } });
      else { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    });
    return () => { active = false; };
  }, [navigate, retry]);

  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const query = window.matchMedia('(max-width: 999px)');
    const update = () => setModalDetails(query.matches);
    update();
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    setOptionsLoading(true);
    bookingApi.options().then((value) => { if (active) { setOptions(value); setOptionsError(''); } })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setOptionsError(errors.form || Object.values(errors)[0]); } })
      .finally(() => { if (active) setOptionsLoading(false); });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    if (!user || createMode) return undefined;
    if (selectedDate !== '' && !isLocalDateInput(selectedDate)) {
      setSelectedDate(localToday());
      return undefined;
    }
    let active = true;
    setListLoading(true);
    const params = { search: search.trim() || undefined, page: 0, size: 100 };
    if (isLocalDateInput(selectedDate)) { params.dateFrom = selectedDate; params.dateTo = selectedDate; }
    bookingApi.list(params)
      .then((result) => {
        if (!active) return;
        const items = result.items || [];
        const pendingId = pendingSelectionRef.current;
        const pendingFound = pendingId && items.some((item) => item.id === pendingId);
        if (pendingId) pendingSelectionRef.current = '';
        setRows(items);
        setHasLoadedRows(true);
        setListError('');
        if (pendingFound) {
          setSelectedId(pendingId);
          setDetailsOpen(true);
        } else {
          setSelectedId((currentId) => currentId && items.some((item) => item.id === currentId) ? currentId : '');
          if (pendingId) setDetailsOpen(false);
        }
      }).catch((requestError) => { if (active) {
        const pendingId = pendingSelectionRef.current;
        pendingSelectionRef.current = '';
        if (pendingId) setDetailsOpen(false);
        const errors = apiErrors(requestError);
        setListError(errors.form || Object.values(errors)[0]);
      } })
      .finally(() => { if (active) setListLoading(false); });
    return () => { active = false; };
  }, [user, createMode, selectedDate, search, reload]);

  const activeId = checkinMode || editMode ? bookingId : selectedId;
  useEffect(() => {
    if (!activeId || !user) { setDetail(null); return undefined; }
    let active = true;
    bookingApi.detail(activeId).then((value) => { if (active) setDetail(value); })
      .catch((requestError) => { if (active) { const errors = apiErrors(requestError); setListError(errors.form || Object.values(errors)[0]); } });
    return () => { active = false; };
  }, [activeId, user, reload]);

  const selected = (detail?.booking?.id === activeId ? detail.booking : null) || rows.find((item) => item.id === activeId) || null;
  const timezone = options?.timezone || 'Asia/Ho_Chi_Minh';
  const counts = useMemo(() => ({
    all: rows.length,
    pending: rows.filter((item) => item.status === 'CONFIRMED').length,
    checkedIn: rows.filter((item) => item.status === 'IN_HOUSE').length,
    cancelled: rows.filter((item) => item.status === 'CANCELLED').length,
    noShow: rows.filter((item) => item.status === 'NO_SHOW').length,
    paid: rows.filter((item) => Number(item.depositReceivedAmount ?? item.depositBalance) > 0).length,
    unpaid: rows.filter((item) => Number(item.depositReceivedAmount ?? item.depositBalance) <= 0).length,
  }), [rows]);
  const visibleRows = useMemo(() => rows.filter((item) => {
    const id = statusId(item.status);
    if (['pending', 'checkedIn', 'cancelled', 'noShow'].includes(activeFilter) && id !== activeFilter) return false;
    if (activeFilter === 'paid' && Number(item.depositReceivedAmount ?? item.depositBalance) <= 0) return false;
    if (activeFilter === 'unpaid' && Number(item.depositReceivedAmount ?? item.depositBalance) > 0) return false;
    if (roomTier !== 'all' && !(item.roomTypeName || item.roomName || '').toLocaleLowerCase('vi-VN').includes(roomTier.toLocaleLowerCase('vi-VN'))) return false;
    return true;
  }), [rows, activeFilter, roomTier]);

  useEffect(() => {
    if (!hasLoadedRows || listLoading) return;
    if (!selectedId) {
      if (detailsOpen) setDetailsOpen(false);
      return;
    }
    if (selectedId === pendingSelectionRef.current) return;
    if (!visibleRows.some((item) => item.id === selectedId)) {
      setSelectedId('');
      setDetailsOpen(false);
      setDetail(null);
    }
  }, [hasLoadedRows, listLoading, visibleRows, selectedId, detailsOpen]);

  useEffect(() => {
    if (!detailsOpen) return undefined;
    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setDetailsOpen(false);
        window.requestAnimationFrame(() => rowFocusRef.current?.focus());
      }
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [detailsOpen]);

  useEffect(() => {
    if (!detailsOpen || !modalDetails) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [detailsOpen, modalDetails]);

  // Đăng xuất rồi chuyển về trang đăng nhập.
  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); clearBookingDraft(user); navigate('/dang-nhap', { replace: true }); }
    catch (requestError) { const errors = apiErrors(requestError); setError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  // Gửi booking mới và cập nhật danh sách sau khi lưu.
  const createBooking = async (payload) => {
    setBusy(true);
    try {
      const result = await bookingApi.create(payload);
      const booking = result.booking;
      setSelectedDate(toLocalDateInput(new Date(booking.plannedStart)));
      pendingSelectionRef.current = booking.id;
      setSelectedId(booking.id);
      setDetailsOpen(true);
      setActiveFilter('all');
      setRoomTier('all');
      setSearch('');
      setReload((value) => value + 1);
      const requestedDeposit = Number(payload.depositAmount || 0);
      if (requestedDeposit > 0 && payload.paymentMethod === 'BANK_TRANSFER') {
        setNoticeKind('success');
        setNotice(`Đã tạo booking ${booking.code}; đang chờ chuyển khoản cọc.`);
        return { ok: true, booking };
      }
      clearBookingDraft(user);
      const recordedDeposit = Number(result.deposit?.balance ?? booking.depositBalance ?? 0);
      if (requestedDeposit > 0 && recordedDeposit < requestedDeposit) {
        setNoticeKind('warning');
        setNotice(`Booking ${booking.code} đã được tạo nhưng API chưa ghi nhận đủ tiền cọc (${formatVnd(recordedDeposit)} / ${formatVnd(requestedDeposit)}). Không tạo booking khác; cần đối chiếu khoản thu trước khi xác nhận lại.`);
      } else if (recordedDeposit > 0) {
        setNoticeKind('success');
        setNotice(`Đã tạo booking ${booking.code} và ghi nhận tiền cọc ${formatVnd(recordedDeposit)}.`);
      } else {
        setNoticeKind('success');
        setNotice(`Đã tạo booking ${booking.code}.`);
      }
      navigate('/le-tan/bookings', { replace: true });
      return { ok: true };
    } catch (requestError) { const errors = apiErrors(requestError); return { error: errors.form || Object.values(errors)[0] }; }
    finally { setBusy(false); }
  };

  // Gửi yêu cầu hủy và đồng bộ lại chi tiết cùng bộ lọc.
  const cancelBooking = async (booking, cancellation) => {
    setBusy(true);
    setCancelError('');
    try {
      const result = await bookingApi.cancel(booking.id, cancellation);
      setCancelTarget(null);
      let message = `Đã hủy booking ${booking.code}.`;
      if (result.refundRequestId) {
        message = `Đã hủy booking ${booking.code} và tạo yêu cầu hoàn ${formatVnd(result.depositAmount)} cho Kế toán.`;
      } else if (result.depositOutcome === 'NON_REFUNDABLE') {
        message = `Đã hủy booking ${booking.code}; ${formatVnd(result.depositAmount)} đã chuyển thành cọc không hoàn.`;
      }
      setNoticeKind('success');
      setNotice(message);
      setReload((value) => value + 1);
    } catch (requestError) { const errors = apiErrors(requestError); setCancelError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  // Ghi no-show và làm mới số dư cọc, trạng thái phòng.
  const markNoShow = async (booking, payload) => {
    setBusy(true);
    setNoShowError('');
    try {
      const result = await bookingApi.markNoShow(booking.id, payload);
      setNoShowTarget(null);
      setNoticeKind('success');
      setNotice(result.depositOutcome === 'NON_REFUNDABLE'
        ? `Đã ghi nhận no-show cho ${booking.code}; ${formatVnd(result.depositAmount)} cọc đã bị tịch thu và phòng được giải phóng.`
        : `Đã ghi nhận no-show cho ${booking.code}; phòng được giải phóng.`);
      setReload((value) => value + 1);
    } catch (requestError) { const errors = apiErrors(requestError); setNoShowError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  // Ghi check-in rồi tải lại booking để hiển thị giờ thực tế.
  const confirmCheckIn = async (booking) => {
    setBusy(true);
    setCheckInError('');
    try {
      const result = await bookingApi.checkIn(booking.id);
      setSelectedId(booking.id);
      setSelectedDate(toLocalDateInput(new Date(result.booking.plannedStart)));
      setNoticeKind('success');
      const checkoutWasExtended = new Date(result.booking.plannedEnd).getTime() > new Date(booking.plannedEnd).getTime();
      setNotice(checkoutWasExtended
        ? `Đã check-in booking ${booking.code}; giờ hẹn ${timeText(result.booking.plannedStart, timezone)} được giữ nguyên, giờ trả dự kiến dời đến ${timeText(result.booking.plannedEnd, timezone)} để bảo đảm đủ thời lượng.`
        : `Đã check-in booking ${booking.code}.`);
      setReload((value) => value + 1);
      navigate('/le-tan/bookings', { replace: true });
    } catch (requestError) { const errors = apiErrors(requestError); setCheckInError(errors.form || Object.values(errors)[0]); }
    finally { setBusy(false); }
  };

  // Lưu thay đổi booking và đóng hộp thoại sửa khi thành công.
  const updateBooking = async (id, payload) => {
    setBusy(true);
    try {
      const result = await bookingApi.update(id, payload);
      const updated = result.booking;
      setSelectedDate(toLocalDateInput(new Date(updated.plannedStart)));
      setSelectedId(updated.id);
      setNoticeKind('success');
      setNotice(`Đã cập nhật booking ${updated.code}. Giá mới: ${formatVnd(updated.expectedAmount)}; tiền cọc đã ghi được giữ nguyên.`);
      setReload((value) => value + 1);
      navigate('/le-tan/bookings', { replace: true });
      return { ok: true };
    } catch (requestError) {
      const errors = apiErrors(requestError);
      return { error: errors.form || Object.values(errors)[0] };
    } finally { setBusy(false); }
  };

  if (!user) return <main className="booking-auth-state">{error ? <div className="booking-auth-message" role="alert"><p>{error}</p><button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button></div> : <p role="status">Đang kiểm tra phiên đăng nhập…</p>}</main>;

  const tabs = [
    ['all', 'Tất cả', counts.all], ['pending', 'Chờ check-in', counts.pending], ['checkedIn', 'Đã check-in', counts.checkedIn],
    ['cancelled', 'Đã hủy', counts.cancelled], ['noShow', 'No-show', counts.noShow], ['paid', 'Có cọc', counts.paid], ['unpaid', 'Không cọc', counts.unpaid],
  ];

  // Chuyển đến đường dẫn xác nhận check-in của booking được chọn.
  const openCheckIn = (booking) => {
    setCheckInError('');
    setSelectedId(booking.id);
    setDetailsOpen(false);
    navigate(`/le-tan/bookings/${booking.id}/check-in`);
  };
  // Mở đường dẫn sửa booking tương ứng.
  const openEdit = (booking) => {
    setDetailsOpen(false);
    navigate(`/le-tan/bookings/${booking.id}/edit`);
  };
  // Quay về danh sách khi đóng bảng check-in.
  const closeCheckIn = () => navigate('/le-tan/bookings');
  const mainContent = createMode ? <BookingCreatePage user={user} options={options} optionsLoading={optionsLoading} optionsError={optionsError} onBack={() => navigate('/le-tan/bookings')} onCreate={createBooking} /> : <>
    <div className="booking-page-heading">
      <div><h1>Booking</h1><p>Theo dõi lịch đặt và check-in khách trong ca</p></div>
      <button className="booking-button booking-button-primary booking-create-button" type="button" onClick={() => navigate('/le-tan/bookings/new')}><Icon name="plus" /> Tạo booking</button>
    </div>
    {notice && <div className={`booking-live-notice${noticeKind === 'warning' ? ' is-warning' : ''}`} role={noticeKind === 'warning' ? 'alert' : 'status'}><span>{noticeKind === 'warning' ? '!' : '✓'}</span>{notice}<button type="button" aria-label="Đóng thông báo" onClick={() => setNotice('')}>×</button></div>}
    {listError && <div className="booking-inline-error" role="alert">{listError}<button type="button" onClick={() => setReload((value) => value + 1)}>Thử lại</button></div>}
    <section className="booking-filters" aria-label="Bộ lọc booking">
      <div className="booking-filter-date-group"><span className="booking-filter-label">Ngày nhận phòng</span><div className="booking-date-buttons">
        <button type="button" className={selectedDate === localToday() ? 'is-selected' : ''} onClick={() => setSelectedDate(localToday())}>Hôm nay</button>
        <button type="button" className={selectedDate === localTomorrow() ? 'is-selected' : ''} onClick={() => setSelectedDate(localTomorrow())}>Ngày mai</button>
        <label className="booking-date-picker"><Icon name="calendar" /><input aria-label="Chọn ngày booking" type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} /></label>
      </div></div>
      <label className="booking-search-field"><Icon name="search" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm tên, SĐT, mã booking, phòng..." /></label>
      <label className="booking-room-filter"><select aria-label="Hạng phòng" value={roomTier} onChange={(event) => setRoomTier(event.target.value)}><option value="all">Hạng phòng · Tất cả</option>{(options?.roomTypes || []).map((type) => <option key={type.id} value={type.name}>{type.name}</option>)}</select></label>
    </section>
    <section className="booking-status-tabs" aria-label="Lọc theo trạng thái booking">
      {tabs.map(([id, label, count]) => <button type="button" key={id} className={`booking-filter-chip chip-${id}${activeFilter === id ? ' is-active' : ''}`} aria-pressed={activeFilter === id} onClick={() => setActiveFilter(id)}><span>{label}</span>{id === 'all' && <small>{count}</small>}</button>)}
      <span className="booking-result-count">Hiển thị {visibleRows.length}/{rows.length}</span>
    </section>
      <div className={`booking-content-grid${detailsOpen ? ' has-details' : ''}`}>
      <section className="booking-table-card" aria-label="Danh sách booking">
        <div className="booking-table-scroll"><table className="booking-table">
          <thead><tr><th>Mã booking</th><th>Giờ hẹn</th><th>Khách hàng / SĐT</th><th>Hạng / Phòng</th><th>Cọc đã nhận</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{visibleRows.map((item) => {
            const selectedRow = item.id === selected?.id;
            const lateMinutes = item.status === 'CONFIRMED' ? Math.floor((Date.now() - new Date(item.plannedStart).getTime()) / 60000) : 0;
            return <tr key={item.id} className={selectedRow ? 'is-selected' : ''} onClick={(event) => openBookingDetails(item.id, event.currentTarget)}>
              <td><strong>{item.code}</strong></td><td><b>{timeText(item.plannedStart, timezone)}</b></td>
              <td><strong>{item.contactName}</strong><small>{item.contactPhone || '—'}{Number(item.guestCount) > 0 ? ` · ${item.guestCount} khách` : ''}</small></td>
              <td><span>{item.roomTypeName || item.roomName || '—'}{item.roomCode ? ` · ${item.roomCode}` : ' · Chưa gán'}</span></td>
              <td>{Number(item.depositReceivedAmount ?? item.depositBalance) > 0 ? formatVnd(item.depositReceivedAmount ?? item.depositBalance) : '0đ'}</td>
              <td>{lateMinutes > 0 ? <span className="booking-status booking-status-late">Quá giờ {lateMinutes} phút</span> : <span className={statusClass(statusId(item.status))}>{statusLabel(item.status)}</span>}</td>
              <td><button type="button" className="booking-row-action" aria-label={`Xem chi tiết booking ${item.code}`} onClick={(event) => { event.stopPropagation(); openBookingDetails(item.id, event.currentTarget.closest('tr')); }}>Chi tiết</button></td>
            </tr>;
          })}</tbody>
        </table>
        {listLoading && <div className="booking-table-message" role="status">Đang tải booking…</div>}
        {!listLoading && !visibleRows.length && <div className="booking-table-message">Không có booking trong ngày này.</div>}
        </div>
      </section>
      {detailsOpen && <div className={`booking-details-host${modalDetails ? ' is-modal' : ''}`} role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeBookingDetails(); }}>
        <BookingDetails booking={selected} detail={detail} timezone={timezone} modal={modalDetails} onClose={closeBookingDetails}
          onDepositClose={() => setReload((value) => value + 1)}
          onCheckIn={() => selected && openCheckIn(selected)} onEdit={openEdit}
          onCancel={(booking) => { setDetailsOpen(false); setCancelError(''); setCancelTarget(booking); }}
          onNoShow={(booking) => { setDetailsOpen(false); setNoShowError(''); setNoShowTarget(booking); }} />
      </div>}
    </div>
  </>;

  return <AppShell user={user} branchName={options?.branchName} busy={busy} onLogout={logout} breadcrumb={createMode ? 'Booking / Tạo booking' : editMode ? 'Booking / Sửa booking' : 'Booking'} search={search} onSearchChange={setSearch} fitViewport={!createMode}>
    {mainContent}
    {editMode && <BookingEditDialog bookingId={bookingId} timezone={timezone} onClose={() => navigate('/le-tan/bookings')} onUpdate={updateBooking} />}
    {checkinMode && <CheckInPanel booking={selected} detail={detail} timezone={timezone} onClose={closeCheckIn} onConfirm={confirmCheckIn} busy={busy} error={checkInError} />}
    <CancelDialog booking={cancelTarget} onClose={() => setCancelTarget(null)} onConfirm={cancelBooking} busy={busy} error={cancelError} timezone={timezone} />
    <NoShowDialog booking={noShowTarget} onClose={() => setNoShowTarget(null)} onConfirm={markNoShow} busy={busy} error={noShowError} timezone={timezone} />
  </AppShell>;
}
