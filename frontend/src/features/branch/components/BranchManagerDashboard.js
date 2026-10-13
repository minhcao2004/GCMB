/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Dashboard, điều hướng, đồng hồ và hồ sơ quản lý cơ sở.
 */

import { useEffect, useState } from 'react';
import BrandWordmark from '../../../shared/components/BrandWordmark';
import BranchRoomsPage from '../pages/BranchRoomsPage';
import '../pages/branch-rooms.css';

const nav = ['Tổng quan', 'Phòng', 'Thực đơn F&B', 'Kho hàng', 'Thiết bị & sự cố', 'Nhân sự', 'Ca làm & chấm công', 'Chốt quỹ', 'Chi phí & đề nghị'];
const icons = ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z', 'M4 21V4h16v17 M8 8h8 M8 12h8', 'M4 4h16v16H4z M8 8l8 8 M16 8l-8 8', 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10', 'M14 7l3 3m-8 8-3 3m8-14 3-3 4 4-3 3m-8 8 4-4', 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M8 2v4m8-4v4M3 9h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2', 'M3 7h18v14H3z M7 11h10', 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h5'];

function initials(name = '') {
  const parts = name.trim().split(/[\s._-]+/).filter(Boolean);
  return parts.length > 1 ? `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase() : name.slice(0, 2).toUpperCase();
}

export default function BranchManagerDashboard({ user, busy, onLogout }) {
  const [active, setActive] = useState('Phòng');
  const [search, setSearch] = useState('');
  const [now, setNow] = useState(() => new Date());
  const [profileOpen, setProfileOpen] = useState(false);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const clock = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(now);

  return <div className="head-office-dashboard branch-manager-dashboard">
    <aside className="manager-sidebar">
      <div className="manager-brand"><BrandWordmark /></div>
      <p className="manager-role">QUẢN LÝ CƠ SỞ</p>
      <nav className="manager-navigation" aria-label="Chức năng quản lý cơ sở">
        {nav.map((label, i) => <button key={label} aria-current={active === label ? 'page' : undefined} onClick={() => setActive(label)}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[i]} /></svg>{label}
        </button>)}
      </nav>
      <div className="manager-sidebar-footer">
        <strong>GenZ Cinema &amp; Music Box</strong>
        <p>Cơ sở {user.branchName || 'được phân quyền'}</p>
        <p>Quản lý cơ sở · {user.username}</p>
        <button disabled={busy} onClick={onLogout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      </div>
    </aside>
    <div className="manager-workspace">
      <div className="manager-topbar">
        <div className="branch-topbar-tools">
          <time className="branch-top-tools-date" dateTime={now.toISOString()}>{clock}</time>
          <button className="branch-icon-button" aria-label="Thông báo">♧</button>
          <div className="branch-profile-wrap">
            <button className="branch-avatar" aria-label="Hồ sơ tài khoản" aria-expanded={profileOpen} onClick={() => setProfileOpen(v => !v)}>{initials(user.username)}</button>
            {profileOpen && <div className="branch-profile-menu" role="dialog" aria-label="Thông tin hồ sơ">
              <strong>{user.username}</strong><span>Quản lý cơ sở</span>
              <div><small>Cơ sở</small><b>{user.branchName || 'Chưa gán cơ sở'}</b></div>
              <button disabled={busy} onClick={onLogout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
            </div>}
          </div>
        </div>
      </div>
      <section className="manager-content branch-manager-content">
        {active === 'Phòng' ? <BranchRoomsPage user={user} search={search} onSearchChange={setSearch} /> : <div className="br-placeholder"><h1>{active}</h1><p>Chức năng này sẽ hiển thị trong phạm vi cơ sở {user.branchName || 'được phân quyền'}.</p></div>}
      </section>
    </div>
  </div>;
}
