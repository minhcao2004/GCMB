/**
 * Author: HuyDV
 * Updated: 27/10/2026
 * Description: Thanh tài khoản và thông báo yêu cầu hóa đơn cho kế toán.
 */
import TaxInvoiceNotice from '../../features/booking/components/TaxInvoiceNotice';
export default function DashboardTopbar({ role, page, user, busy, onProfile, onLogout, onToggleSidebar, sidebarExpanded, children, accountHeader }) {
  return <div className="manager-topbar">
    {onToggleSidebar && <button
      className="manager-sidebar-toggle"
      type="button"
      onClick={onToggleSidebar}
      aria-label={sidebarExpanded ? 'Thu gọn thanh điều hướng' : 'Mở thanh điều hướng'}
      aria-expanded={sidebarExpanded}
      aria-controls="app-sidebar"
      title={sidebarExpanded ? 'Thu gọn thanh điều hướng' : 'Mở thanh điều hướng'}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d={sidebarExpanded ? 'M9 4v16M14 9l-3 3 3 3' : 'M9 4v16M13 9l3 3-3 3'} />
      </svg>
      <span className="manager-sidebar-toggle-label">{sidebarExpanded ? 'Ẩn menu' : 'Mở menu'}</span>
    </button>}
    <p>{role} <span>/ {page}</span></p>
    {children}
    <div className={`manager-account${accountHeader ? ' has-branch-selector' : ''}`}>
      {user?.role === 'ACCOUNTANT' && <TaxInvoiceNotice />}
      {accountHeader}
      <span>{user?.username || 'Đang tải…'}</span>
      <button type="button" disabled={!user} onClick={onProfile}>Thông tin tài khoản</button>
      <button type="button" disabled={!user || busy} onClick={onLogout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    </div>
  </div>;
}
