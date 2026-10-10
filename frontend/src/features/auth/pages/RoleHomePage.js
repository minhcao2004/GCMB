import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/dashboard.css';
import { authApi, apiErrors } from '../services/authApi';
import HeadOfficeDashboard from '../components/HeadOfficeDashboard';

const names = { HEAD_OFFICE_MANAGER: 'Quản lý cấp cao', BRANCH_MANAGER: 'Quản lý chi nhánh', RECEPTIONIST: 'Lễ tân', ACCOUNTANT: 'Kế toán' };
export default function RoleHomePage({ role }) {
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const navigate = useNavigate();
  useEffect(() => {
    let active = true;
    setUser(null);
    setError('');
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
  const logout = async () => {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (e) { setError(apiErrors(e).form); }
    finally { setBusy(false); }
  };
  return <main className={`dashboard-page${user && role === 'HEAD_OFFICE_MANAGER' ? ' dashboard-page-manager' : ''}`}>
    {user && role === 'HEAD_OFFICE_MANAGER' ? <HeadOfficeDashboard user={user} busy={busy} onLogout={logout} /> : user ? <>
      <header className="dashboard-header">
        <h1>Dashboard — {names[role]}</h1>
        <button disabled={busy} onClick={logout}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      </header>
      <p>Xin chào, <strong>{user.username}</strong> — {names[role]}.</p>
    </> : !error && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
    {error && <p className="dashboard-error" role="alert">{error} <button onClick={() => setRetry((value) => value + 1)}>Thử lại</button></p>}
  </main>;
}
