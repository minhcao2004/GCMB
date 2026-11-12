/** Author: HuyDV. Màn hình nhận bàn giao tiền mặt cho lễ tân. */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi, apiErrors } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import ShiftCashHandoverPanel from '../components/ShiftCashHandoverPanel';

export default function ReceptionCashHandoverPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
      else setUser(identity);
    }).catch((failure) => { if (active) setError(Object.values(apiErrors(failure)).join(' ')); });
    return () => { active = false; };
  }, [navigate]);
  if (!user) return <p role={error ? 'alert' : 'status'}>{error || 'Đang kiểm tra tài khoản…'}</p>;
  return <AppShell user={user} branchName={user.branchName} activeTab="handover" breadcrumb="Bàn giao tiền mặt"
    hideSearch busy={busy} onLogout={async () => {
      setBusy(true);
      try { await authApi.logout(); navigate('/dang-nhap'); }
      catch (failure) { setError(Object.values(apiErrors(failure)).join(' ')); }
      finally { setBusy(false); }
    }}>
    <div className="finance-page cash-closing-page">
      {error && <p role="alert" className="finance-error">{error}</p>}
      <ShiftCashHandoverPanel user={user} readOnly={user.readOnly} />
    </div>
  </AppShell>;
}
