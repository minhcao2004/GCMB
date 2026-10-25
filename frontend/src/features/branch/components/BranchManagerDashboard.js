/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Dashboard, điều hướng, đồng hồ và hồ sơ quản lý cơ sở.
 */

import DashboardTopbar from '../../../shared/components/DashboardTopbar';
import { useEffect, useState } from 'react';
import { authApi, apiErrors } from '../../auth/services/authApi';
import BrandWordmark from '../../../shared/components/BrandWordmark';
import BranchRoomsPage from '../pages/BranchRoomsPage';
import '../pages/branch-rooms.css';

const nav = ['Tổng quan', 'Phòng', 'Thực đơn F&B', 'Kho hàng', 'Thiết bị & sự cố', 'Nhân sự', 'Ca làm & chấm công', 'Chốt quỹ', 'Chi phí & đề nghị'];
const icons = ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z', 'M4 21V4h16v17 M8 8h8 M8 12h8', 'M4 4h16v16H4z M8 8l8 8 M16 8l-8 8', 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10', 'M14 7l3 3m-8 8-3 3m8-14 3-3 4 4-3 3m-8 8 4-4', 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M8 2v4m8-4v4M3 9h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2', 'M3 7h18v14H3z M7 11h10', 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h5'];

export default function BranchManagerDashboard({ user, busy, onLogout, onProfile, onBranchChange }) {
  const [active, setActive] = useState('Phòng');
  const [search, setSearch] = useState('');
  const [branches, setBranches] = useState([]);
  const [loadingBranches, setLoadingBranches] = useState(true);
  const [switching, setSwitching] = useState(false);
  const [branchError, setBranchError] = useState('');
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
      <DashboardTopbar role="QUẢN LÝ CƠ SỞ" page={active} user={user} busy={busy || switching} onProfile={onProfile} onLogout={onLogout}
        accountHeader={<label className="manager-branch-selector">Cơ sở đang quản lý
          <select aria-label="Cơ sở đang quản lý" value={user.branchId || ''} onChange={selectBranch} disabled={busy || switching || loadingBranches}>
            {!branches.some((branch) => branch.id === user.branchId) && <option value={user.branchId || ''}>{user.branchName || 'Chọn cơ sở'}</option>}
            {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
          </select>
        </label>} />
      <section className="manager-content branch-manager-content">
        {branchError && <p className="br-error" role="alert">{branchError}</p>}
        {switching ? <p role="status">Đang chuyển cơ sở…</p> : active === 'Phòng' ? <BranchRoomsPage key={user.branchId} user={user} search={search} onSearchChange={setSearch} /> : <div className="br-placeholder"><h1>{active}</h1><p>Chức năng này sẽ hiển thị trong phạm vi cơ sở {user.branchName || 'được phân quyền'}.</p></div>}
      </section>
    </div>
  </div>;
}
