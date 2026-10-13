/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Updated: 12/10/2026
 * Description: Hiển thị khung quản lý cấp cao với menu chọn mục, thông tin tài khoản, lời chào và nút đăng xuất.
 */

import { useState } from 'react';

import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import BrandWordmark from '../../../shared/components/BrandWordmark';
import RoomCatalogPage from '../../pricing/pages/RoomCatalogPage';
import BranchAssignmentPanel from '../../branch/components/BranchAssignmentPanel';

const tabs = ['Tổng quan', 'Cơ sở', 'Báo cáo kinh doanh', 'Phê duyệt chi', 'Nhân sự', 'Phòng & bảng giá', 'Thiết bị', 'Nhật ký hệ thống'];
const catalogPath = '/quan-ly-he-thong/phong-bang-gia';
const dashboardPath = '/quan-ly-he-thong';
const icons = [
  'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  'M4 21V5l8-3 8 3v16 M9 21v-5h6v5 M8 7h1 M15 7h1 M8 11h1 M15 11h1',
  'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M18 8v6 M15 11h6',
  'M4 3v18h17 M8 16v-4 M13 16V8 M18 16V5',
  'M8 3H5v18h14V3h-3 M8 2h8v4H8z M8 13l3 3 5-6',
  'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M17 4a4 4 0 0 1 0 7 M22 21v-2a4 4 0 0 0-3-4',
  'M3 21V3h12v18 M7 7h4 M7 11h4 M18 8h3v13 M2 21h20',
  'M3 4h18v13H3z M8 21h8 M12 17v4',
  'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h5',
];

export default function HeadOfficeDashboard({ user, busy, onLogout, onProfile }) {
  const [activeTab, setActiveTab] = useState('Tổng quan');
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    setActiveTab(location.pathname === catalogPath ? 'Phòng & bảng giá' : 'Tổng quan');
  }, [location.pathname]);
  const selectTab = (tab) => {
    setActiveTab(tab);
    if (tab === 'Phòng & bảng giá') navigate(catalogPath);
    else if (location.pathname !== dashboardPath) navigate(dashboardPath);
  };
  return <div className="head-office-dashboard">
    <aside className="manager-sidebar" aria-label="Quản lý cấp cao">
      <div className="manager-brand"><BrandWordmark /></div>
      <p className="manager-role">QUẢN LÝ CẤP CAO</p>
      <nav className="manager-navigation" aria-label="Chức năng quản lý cấp cao">
        {tabs.map((tab, index) => tab === 'Phòng & bảng giá' ? <NavLink key={tab} to={catalogPath} aria-current={activeTab === tab ? 'page' : undefined}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[index]} /></svg>
          {tab}
        </NavLink> : <button key={tab} aria-current={activeTab === tab ? 'page' : undefined} onClick={() => selectTab(tab)}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[index]} /></svg>
          {tab}
        </button>)}
      </nav>
      <div className="manager-sidebar-footer"><strong>GenZ Cinema &amp; Music Box</strong><p>Phạm vi: toàn hệ thống</p></div>
    </aside>
    <div className="manager-workspace">
      <div className="manager-topbar">
        <p>QUẢN LÝ CẤP CAO <span>/ {activeTab}</span></p>
        <div className="manager-account">
          <span>{user.username}</span>
          <button onClick={onProfile}>Thông tin tài khoản</button>
          <button disabled={busy} onClick={onLogout}>
            {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}
          </button>
        </div>
      </div>
      {activeTab === 'Tài khoản & phân quyền' ? (
        <AccountsPage actorId={user.id} onAudit={() => setActiveTab('Nhật ký hệ thống')} />
      ) : activeTab === 'Nhật ký hệ thống' ? (
        <AuditLogPage onAccounts={() => setActiveTab('Tài khoản & phân quyền')} />
      ) : <section className="manager-content" aria-labelledby="manager-greeting"><h1 id="manager-greeting">Xin chào quản lý cấp cao.</h1></section>}
    </div>
  </div>;
}
