export default function DashboardTopbar({ role, page, user, busy, onProfile, onLogout, children }) {
  return <div className="manager-topbar">
    <p>{role} <span>/ {page}</span></p>
    {children}
    <div className="manager-account">
      <span>{user?.username || 'Đang tải…'}</span>
      <button type="button" disabled={!user} onClick={onProfile}>Thông tin tài khoản</button>
      <button type="button" disabled={!user || busy} onClick={onLogout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    </div>
  </div>;
}
