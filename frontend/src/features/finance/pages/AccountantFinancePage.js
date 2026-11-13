import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { AppShell } from '../../booking/pages/BookingManagementPage';
import { authApi, apiErrors } from '../../auth/services/authApi';
import CashClosingPage from './CashClosingPage';
import FinancePage from './FinancePage';
import PayrollPage from '../../workforce/pages/PayrollPage';

export default function AccountantFinancePage({ mode = 'requests' }) {
  const navigate=useNavigate(); const [user,setUser]=useState(null); const [error,setError]=useState(''); const [busy,setBusy]=useState(false);
  const section=new URLSearchParams(useLocation().search).get('section');
  useEffect(()=>{let active=true;authApi.me().then((identity)=>{if(!active)return;if(identity.role!=='ACCOUNTANT'){navigate(identity.destination,{replace:true});return;}setUser(identity);}).catch((e)=>{if(active)setError(apiErrors(e).form||'Không thể kiểm tra phiên đăng nhập.');});return()=>{active=false;};},[navigate]);
  async function logout(){setBusy(true);try{await authApi.logout();navigate('/dang-nhap',{replace:true});}catch(e){setError(apiErrors(e).form||'Không thể đăng xuất.');}finally{setBusy(false);}}
  if(error)return <main className="booking-auth-state"><p>{error}</p></main>;
  if(!user)return <main className="booking-auth-state"><p>Đang kiểm tra phiên đăng nhập…</p></main>;
  const activeTab=mode==='payroll'?'booking':mode==='cash-closing'?'calendar':section==='payments'?'payments':'plus';
  const breadcrumb=mode==='payroll'?'Lương nhân viên':mode==='cash-closing'?'Đối soát chốt quỹ':section==='payments'?'Thu & cấp quỹ':'Chi & hoàn ứng';
  const page=mode==='payroll'?<PayrollPage/>:mode==='cash-closing'?<CashClosingPage user={user} accounting/>:<FinancePage user={user}/>;
  return <AppShell user={user} busy={busy} onLogout={logout} breadcrumb={breadcrumb} activeTab={activeTab} audience="accountant" hideSearch>{page}</AppShell>;
}
