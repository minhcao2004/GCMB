/** Author: HuyDV. UC-91..101: khung kế toán dùng chung với các chức năng hiện có. */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import { authApi, apiErrors } from '../../auth/services/authApi';
import CashClosingPage from './CashClosingPage';
import PayrollPage from '../../workforce/pages/PayrollPage';
export default function FinancePage({ payroll = false }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(null),
    [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    authApi
      .me()
      .then((identity) => {
        if (!active) return;
        if (identity.role !== 'ACCOUNTANT') navigate(identity.destination, { replace: true });
        else setUser(identity);
      })
      .catch((e) => active && setError(Object.values(apiErrors(e)).join(' ')));
    return () => {
      active = false;
    };
  }, [navigate]);
  if (!user) return <p role={error ? 'alert' : 'status'}>{error || 'Đang tải…'}</p>;
  return (
    <AppShell
      user={user}
      branchName="Toàn chuỗi"
      audience="accountant"
      activeTab={payroll ? 'booking' : 'calendar'}
      hideSearch
      breadcrumb={payroll ? 'Lương nhân viên' : 'Đối soát chốt quỹ'}
      onLogout={async () => {
        try {
          await authApi.logout();
          navigate('/dang-nhap');
        } catch (e) {
          setError(Object.values(apiErrors(e)).join(' '));
        }
      }}
    >
      {error && <p role="alert">{error}</p>}
      {payroll ? <PayrollPage /> : <CashClosingPage user={user} accounting />}
    </AppShell>
  );
}
