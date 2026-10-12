import { useNavigate } from 'react-router-dom';
import Brand from '../../../shared/components/Brand';

const navigation = [
  ['Tổng quan', 'overview'], ['Phòng', 'rooms', '/le-tan/rooms'], ['Booking', 'booking', '/le-tan/bookings'], ['Order', 'orders'],
  ['Thanh toán', 'payments'], ['Tiền cọc', 'deposit'], ['Kho hàng', 'inventory'], ['Báo sự cố', 'issues'],
];

const iconPaths = {
  overview: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  rooms: <><path d="M3 20V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14M3 13h18M6 13V9a2 2 0 0 1 2-2h3v6m2 0V7h3a2 2 0 0 1 2 2v4M2 20h20"/></>,
  booking: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M8 14h3m-3 3h7"/></>,
  orders: <><path d="M4 5h16M4 12h16M4 19h16M7 3v4m10-4v4M7 10v4m10-4v4M7 17v4m10-4v4"/></>,
  payments: <><circle cx="12" cy="12" r="9"/><path d="M8 12h8m-4-4v8"/></>,
  deposit: <><path d="m12 3 9 9-9 9-9-9 9-9Z"/></>,
  inventory: <><path d="M4 4h16v16H4zM9 4v16m6-16v16"/></>,
  issues: <><path d="M5 21V4m0 0c5-4 9 4 14 0v11c-5 4-9-4-14 0"/></>,
  search: <><circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4.4 4.4"/></>,
};

function Icon({ name, size = 18 }) {
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{iconPaths[name]}</svg>;
}

function initials(user) {
  const value = user?.fullName || user?.username || 'MC';
  return value.split(/[\s._-]+/).filter(Boolean).slice(-2).map((part) => part[0]?.toUpperCase()).join('') || 'MC';
}

export default function ReceptionLayout({ user, branchName, busy, onLogout, children, breadcrumb, search, onSearchChange, activeSection }) {
  const navigate = useNavigate();
  const now = new Date();
  const currentDate = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(now);
  const currentTime = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(now);

  return <div className="booking-app">
    <aside className="booking-sidebar">
      <div className="booking-brand"><Brand /></div>
      <span className="booking-role-chip">LỄ TÂN</span>
      <nav className="booking-navigation" aria-label="Điều hướng lễ tân">
        {navigation.map(([label, icon, path]) => {
          const active = icon === activeSection;
          return <button type="button" key={label} className={`booking-nav-item${active ? ' is-active' : ''}`} aria-current={active ? 'page' : undefined} aria-disabled={!path} disabled={!path} onClick={() => path && navigate(path)}>
            <Icon name={icon} /><span>{label}</span>
          </button>;
        })}
      </nav>
      <div className="booking-sidebar-footer">
        <strong>{branchName || 'Cơ sở theo tài khoản'}</strong>
        <small>Đang làm việc · Lễ tân</small>
        <button type="button" className="booking-logout-link" disabled={busy} onClick={onLogout}>Đăng xuất</button>
      </div>
    </aside>
    <div className="booking-workspace">
      <header className="booking-topbar">
        <p><span>Lễ tân</span><b>/</b><strong>{breadcrumb}</strong></p>
        <div className="booking-topbar-tools">
          <label className="booking-global-search"><Icon name="search" /><input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Tìm phòng, booking, khách hàng..." aria-label="Tìm phòng, booking, khách hàng" /></label>
          <span className="booking-topbar-datetime">{currentDate} {currentTime}</span>
          <span className="booking-user-avatar" aria-label={user?.username}>{initials(user)}</span>
        </div>
      </header>
      <main className="booking-main" id="main">{children}</main>
    </div>
  </div>;
}
