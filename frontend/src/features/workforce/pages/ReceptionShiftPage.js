/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-83, lễ tân đăng ký ca đúng cơ sở, không xem lịch cá nhân của người khác.
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi, apiErrors } from '../../auth/services/authApi';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import ShiftSchedulePage from './ShiftSchedulePage';
export default function ReceptionShiftPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    authApi
      .me()
      .then((identity) => {
        if (active) {
          if (identity.role !== 'RECEPTIONIST') navigate(identity.destination, { replace: true });
          else setUser(identity);
        }
      })
      .catch((e) => {
        if (active) setError(Object.values(apiErrors(e)).join(' '));
      });
    return () => {
      active = false;
    };
  }, [navigate]);
  if (!user) return <p role={error ? 'alert' : 'status'}>{error || 'Đang kiểm tra tài khoản…'}</p>;
  return (
    <AppShell
      user={user}
      branchName={user.branchName}
      activeTab="shifts"
      hideSearch
      breadcrumb="Ca làm việc"
      busy={busy}
      onLogout={async () => {
        setBusy(true);
        try {
          await authApi.logout();
          navigate('/dang-nhap');
        } catch (e) {
          setError(Object.values(apiErrors(e)).join(' '));
        } finally {
          setBusy(false);
        }
      }}
    >
      {error && <p role="alert">{error}</p>}
      <ShiftSchedulePage user={user} readOnly={user.readOnly} />
    </AppShell>
  );
}
