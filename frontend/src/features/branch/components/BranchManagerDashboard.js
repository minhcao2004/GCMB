/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Dashboard, điều hướng, đồng hồ và hồ sơ quản lý cơ sở.
 */

import DashboardTopbar from '../../../shared/components/DashboardTopbar';
import { useEffect, useLayoutEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { authApi, apiErrors } from '../../auth/services/authApi';
import BrandWordmark from '../../../shared/components/BrandWordmark';
import AccountsPage from '../../backoffice/accounts/pages/AccountsPage';
import BranchRoomsPage from '../pages/BranchRoomsPage';
import MenuItemsPage from '../../inventory/pages/MenuItemsPage';
import InventoryPage from '../../inventory/pages/InventoryPage';
import WorkforcePage from '../../workforce/pages/WorkforcePage';
import StaffPage from '../../workforce/pages/StaffPage';
import '../pages/branch-rooms.css';

const nav = ['Tổng quan', 'Phòng', 'Thực đơn F&B', 'Kho hàng', 'Thiết bị & sự cố', 'Nhân sự', 'Ca làm & chấm công', 'Chốt quỹ', 'Chi phí & đề nghị'];
const branchNav = [...nav, 'Tài khoản'];
const screenIds = ['overview', 'rooms', 'menu', 'inventory', 'equipment', 'staff', 'attendance', 'cash-closing', 'expenses', 'accounts'];
const icons = ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z', 'M4 21V4h16v17 M8 8h8 M8 12h8', 'M4 4h16v16H4z M8 8l8 8 M16 8l-8 8', 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10', 'M14 7l3 3m-8 8-3 3m8-14 3-3 4 4-3 3m-8 8 4-4', 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M8 2v4m8-4v4M3 9h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2', 'M3 7h18v14H3z M7 11h10', 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h5'];

export default function BranchManagerDashboard({ user, busy, onLogout, onProfile, onBranchChange }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeIndex = screenIds.indexOf(searchParams.get('screen'));
  const active = branchNav[activeIndex] || 'Phòng';
  useLayoutEffect(() => {
    if (active === 'Thực đơn F&B' || active === 'Kho hàng') window.scrollTo(0, 0);
  }, [active]);
  const [search, setSearch] = useState('');
  const [accountSearch, setAccountSearch] = useState('');
  const [branches, setBranches] = useState([]);
  const [loadingBranches, setLoadingBranches] = useState(true);
  const [switching, setSwitching] = useState(false);
  const [branchError, setBranchError] = useState('');
  const readOnly = user.readOnly || branches.some((branch) => branch.id === user.branchId && branch.status === 'SUSPENDED');
  useEffect(() => {
    let active = true;
    const load = () => {
      setLoadingBranches(true);
      authApi.managedBranches().then((items) => { if (active) { setBranches(items); setBranchError(''); } })
        .catch((error) => { if (active) setBranchError(Object.values(apiErrors(error))[0]); })
        .finally(() => { if (active) setLoadingBranches(false); });
    };
    load();
    window.addEventListener('focus', load);
    return () => { active = false; window.removeEventListener('focus', load); };
  }, [user.id, user.branchId]);

  const selectBranch = async (event) => {
    const branchId = event.target.value;
    if (branchId === user.branchId || switching) return;
    setSwitching(true);
    setBranchError('');
    try {
      const identity = await authApi.switchBranch(branchId);
      setSearch('');
      onBranchChange(identity);
    } catch (error) { setBranchError(Object.values(apiErrors(error))[0]); }
    finally { setSwitching(false); }
  };
  const selectScreen = (label) => {
    const screen = screenIds[branchNav.indexOf(label)];
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set('screen', screen);
      return next;
    });
  };

  return <div className={`head-office-dashboard branch-manager-dashboard${active === 'Thực đơn F&B' ? ' has-menu-items' : ''}${active === 'Kho hàng' ? ' has-inventory' : ''}`}>
    <aside className="manager-sidebar">
      <div className="manager-brand"><BrandWordmark /></div>
      <p className="manager-role">QUẢN LÝ CƠ SỞ</p>
      <nav className="manager-navigation" aria-label="Chức năng quản lý cơ sở">
        {branchNav.map((label, i) => <button key={label} aria-current={active === label ? 'page' : undefined} onClick={() => selectScreen(label)}>
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
      <DashboardTopbar role="QUẢN LÝ CƠ SỞ" page={active} user={user} busy={busy || switching} onProfile={onProfile} onLogout={onLogout}
        accountHeader={<label className="manager-branch-selector">Cơ sở đang quản lý
          <select aria-label="Cơ sở đang quản lý" title={user.branchName || 'Cơ sở được phân quyền'}
            value={user.branchId || ''} onChange={selectBranch} disabled={busy || switching || loadingBranches}>
            {!branches.some((branch) => branch.id === user.branchId) && <option value={user.branchId || ''}>{user.branchName || 'Chọn cơ sở'}</option>}
            {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
          </select>
        </label>} />
      <section className="manager-content branch-manager-content">
        {readOnly && <p className="br-warning" role="status">Cơ sở {user.branchName} đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.</p>}
        {branchError && <p className="br-error" role="alert">{branchError}</p>}
        {switching ? <p role="status">Đang chuyển cơ sở…</p> : active === 'Tài khoản' ? <AccountsPage key={user.branchId} actorId={user.id} initialSearch={accountSearch} branchScope={user.branchId} readOnly={readOnly} /> : active === 'Nhân sự' ? <StaffPage key={user.branchId} user={user} branchScope={user.branchId} readOnly={readOnly} onAccounts={(username) => { setAccountSearch(username); selectScreen('Tài khoản'); }} /> : active === 'Ca làm & chấm công' ? <WorkforcePage key={user.branchId} user={user} branchScope={user.branchId} readOnly={readOnly} /> : active === 'Phòng' ? <BranchRoomsPage key={user.branchId} user={{ ...user, readOnly }} search={search} onSearchChange={setSearch} /> : active === 'Thực đơn F&B' ? <MenuItemsPage key={user.branchId} user={{ ...user, readOnly }} /> : active === 'Kho hàng' ? <InventoryPage key={user.branchId} user={{ ...user, readOnly }} /> : <div className="br-placeholder"><h1>{active}</h1><p>Chức năng này sẽ hiển thị trong phạm vi cơ sở {user.branchName || 'được phân quyền'}.</p></div>}
      </section>
    </div>
  </div>;
}
