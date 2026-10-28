/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Kiểm tra phiên đăng nhập và vai trò, hiển thị trang phù hợp và xử lý đăng xuất.

 */

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/dashboard.css';
import { authApi, apiErrors } from '../services/authApi';
import HeadOfficeDashboard from '../components/HeadOfficeDashboard';
import BranchManagerDashboard from '../../branch/components/BranchManagerDashboard';
import AccountProfileDialog from '../../backoffice/accounts/components/AccountProfileDialog';
import EquipmentIncidentsPage from '../../inventory/pages/EquipmentIncidentsPage';

const names = { HEAD_OFFICE_MANAGER: 'Quản lý cấp cao', BRANCH_MANAGER: 'Quản lý chi nhánh', RECEPTIONIST: 'Lễ tân' };
// Kiểm tra phiên/đúng vai trò trước khi hiển thị trang sau đăng nhập.
export default function RoleHomePage({ role }) {
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const [profileOpen, setProfileOpen] = useState(false);
  const navigate = useNavigate();
  useEffect(() => {
    let active = true;
    setUser(null);
    setError('');
    // Kiểm tra lại phiên khi trang mở hoặc cửa sổ được chú ý trở lại.
    const check = () => authApi.me().then((data) => {
      if (!active) return;
      if (data.role !== role) navigate(data.destination, { replace: true });
      else { setUser(data); setError(''); }
    }).catch((e) => {
      if (!active) return;
      setUser(null);
      if (e.response?.status === 401) navigate('/dang-nhap', { replace: true, state: { message: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' } });
      else setError(apiErrors(e).form);
    });
    check();
    window.addEventListener('focus', check);
    return () => { active = false; window.removeEventListener('focus', check); };
  }, [role, navigate, retry]);
  // Thu hồi phiên trên server rồi trở về màn đăng nhập.
  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (e) { setError(apiErrors(e).form); }
    finally { setBusy(false); }
  };
  return <main className={`dashboard-page${user && ['HEAD_OFFICE_MANAGER','BRANCH_MANAGER'].includes(role) ? ' dashboard-page-manager' : ''}`}>
    {user && role === 'HEAD_OFFICE_MANAGER' ? <HeadOfficeDashboard user={user} busy={busy} onLogout={logout} onProfile={() => setProfileOpen(true)} /> : user ? <>
      {role === 'BRANCH_MANAGER' ? <BranchManagerDashboard user={user} onBranchChange={setUser} busy={busy} onLogout={logout} onProfile={() => setProfileOpen(true)} /> : <>
      <header className="dashboard-header">
        <h1>Dashboard — {names[role]}</h1>
        <button onClick={() => setProfileOpen(true)}>Thông tin tài khoản</button>
        <button disabled={busy} onClick={logout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      </header>
      {role === 'RECEPTIONIST' ? <EquipmentIncidentsPage user={user} /> : <p>Xin chào, <strong>{user.username}</strong> — {names[role]}.</p>}
      </>}
    </> : !error && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
    {user && profileOpen && <AccountProfileDialog onClose={() => setProfileOpen(false)} />}
    {error && <p className="dashboard-error" role="alert">{error} <button onClick={() => setRetry((value) => value + 1)}>Thử lại</button></p>}
  </main>;
}
