import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import RevenueExpenseOverview from './RevenueExpenseOverview';
import './accountantDashboard.css';

const monthNow = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không tải được dữ liệu kế toán. Vui lòng thử lại.';
const dateVi = (value) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).format(date); };
const validCashDate = (value, today) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '2000-01-01' || value > today) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
};
const statusVi = (value) => ({
  UNRESOLVED: 'Chưa xử lý', RESOLVED: 'Đã xử lý', REQUESTED: 'Đang chờ xử lý', APPROVED: 'Đã duyệt', PROCESSING: 'Đang xử lý',
  WAITING_ACCOUNTANT: 'Chờ kế toán duyệt', APPROVED_TO_PAY: 'Chờ thanh toán', WAITING_CONFIRMATION: 'Chờ xác nhận', PAID: 'Đã thanh toán', REJECTED: 'Đã từ chối',
  DRAFT: 'Bản nháp', CALCULATED: 'Đã tính', CONFIRMED: 'Đã xác nhận',
  OPEN: 'Đang mở', CLOSED: 'Đã khóa', SUBMITTED: 'Chờ đối soát', RECONCILED: 'Đã đối soát', NEEDS_EXPLANATION: 'Cần giải trình', FAILED: 'Không thành công', EXCEPTION: 'Ngoại lệ', PENDING: 'Đang chờ xử lý',
})[value] || (value ? 'Chưa xác định' : '—');

function DataTable({ title, description, columns, rows, renderRow, state, href, action, emptyMessage = 'Không có mục nào.', errorMessage = 'Không tải được dữ liệu.' }) {
  return <section className="accountant-dash-panel accountant-table-panel">
    <div className="accountant-panel-heading"><div><h2>{title}</h2><p>{description}</p>{href && <Link to={href}>{action}</Link>}</div><span>{state === 'ready' ? rows.length : '—'} mục</span></div>
    {state === 'ready' && rows.length ? <div className="accountant-table-scroll" tabIndex={0} aria-label={title}><table><thead><tr>{columns.map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{rows.map(renderRow)}</tbody></table></div>
      : <p className="accountant-dash-empty" role={state === 'error' ? 'alert' : 'status'}>{state === 'loading' ? 'Đang tải…' : state === 'error' ? errorMessage : emptyMessage}</p>}
  </section>;
}

export default function AccountantDashboard() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedFrom = searchParams.get('from');
  const selectedTo = searchParams.get('to');
  const selectedBranches = searchParams.getAll('branchIds');
  const selectedBranch = selectedBranches.length === 1 ? selectedBranches[0] : '';
  const selectedMonth = /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(selectedTo || '') ? selectedTo.slice(0, 7) : monthNow();
  const scopeQuery = new URLSearchParams(searchParams);
  const periodQuery = new URLSearchParams(scopeQuery);
  periodQuery.set('tab', 'periods');
  const transactionsQuery = new URLSearchParams(scopeQuery);
  transactionsQuery.set('tab', 'transactions');
  const monthLastDay = new Date(Date.UTC(Number(selectedMonth.slice(0, 4)), Number(selectedMonth.slice(5, 7)), 0)).toISOString().slice(0, 10);
  const currentDay = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const cashDate = searchParams.get('cashDate') ?? selectedTo ?? currentDay;
  const cashDateValid = validCashDate(cashDate, currentDay);
  const fullMonthRange = (!selectedFrom && !selectedTo) || (selectedFrom === `${selectedMonth}-01`
    && selectedTo === (monthLastDay > currentDay ? currentDay : monthLastDay));
  const singleMonthScope = fullMonthRange && selectedBranches.length <= 1;
  const [user, setUser] = useState(null);
  const month = selectedMonth;
  const branchId = selectedBranch;
  const [branches, setBranches] = useState([]);
  const [refunds, setRefunds] = useState([]);
  const [requests, setRequests] = useState([]);
  const [fundBalance, setFundBalance] = useState(null);
  const [dailyStatuses, setDailyStatuses] = useState([]);
  const [dailyStatusState, setDailyStatusState] = useState('loading');
  const [dailyStatusForDate, setDailyStatusForDate] = useState('');
  const [dailyRevision, setDailyRevision] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [notificationState, setNotificationState] = useState('loading');
  const [notificationRevision, setNotificationRevision] = useState(0);
  const [closings, setClosings] = useState([]);
  const [payroll, setPayroll] = useState(null);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [sepayExceptions, setSepayExceptions] = useState([]);
  const [periods, setPeriods] = useState([]);
  const [apiStatus, setApiStatus] = useState({});
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [exceptionFilter, setExceptionFilter] = useState('UNRESOLVED');
  const [resolutionTarget, setResolutionTarget] = useState(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolutionBusy, setResolutionBusy] = useState(false);
  const resolutionDialogRef = useRef(null);
  const resolutionTriggerRef = useRef(null);
  const exceptionFilterRef = useRef(null);

  useEffect(() => {
    if (!cashDateValid) { setDailyStatuses([]); setDailyStatusState('invalid'); setDailyStatusForDate(''); return undefined; }
    let active = true;
    setDailyStatusForDate(cashDate);
    setDailyStatuses([]); setDailyStatusState('loading');
    api.get('/accounting/cash-closing/statuses', { params: { date: cashDate } }).then(({ data }) => {
      if (!active) return;
      const ready = Array.isArray(data) && data.every((item) => item.branchId && item.branchName
        && ['SUBMITTED', 'NEEDS_EXPLANATION', 'NOT_SUBMITTED'].includes(item.status));
      setDailyStatuses(ready ? data : []);
      setDailyStatusState(ready ? 'ready' : 'error');
    }).catch(() => { if (active) setDailyStatusState('error'); });
    return () => { active = false; };
  }, [cashDate, cashDateValid, dailyRevision]);

  useEffect(() => {
    let active = true;
    setNotifications([]); setNotificationState('loading');
    api.get('/accounting/cash-closing/notifications').then(({ data }) => {
      if (!active) return;
      const ready = Array.isArray(data) && data.every((item) => item.id && item.closingId
        && item.branchName && item.businessDate && item.sentAt && Number(item.reportVersion) > 0
        && !Number.isNaN(new Date(item.sentAt).getTime()));
      setNotifications(ready ? data : []);
      setNotificationState(ready ? 'ready' : 'error');
    }).catch(() => { if (active) setNotificationState('error'); });
    return () => { active = false; };
  }, [notificationRevision]);

  useEffect(() => {
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role !== 'ACCOUNTANT') navigate(identity.destination, { replace: true });
      else setUser(identity);
    }).catch(() => { if (active) navigate('/dang-nhap', { replace: true }); });
    return () => { active = false; };
  }, [navigate]);

  useEffect(() => {
    let active = true;
    setApiStatus((current) => ({ ...current, refund: 'loading', finance: 'loading' }));
    setBranches([]); setRefunds([]); setRequests([]); setFundBalance(null);
    api.get('/accounting/deposits/refund-requests').then(({ data }) => {
      if (!active) return;
      const ready = Array.isArray(data?.items);
      setRefunds(ready ? data.items : []);
      setApiStatus((current) => ({ ...current, refund: ready ? 'fulfilled' : 'rejected' }));
    }).catch(() => { if (active) setApiStatus((current) => ({ ...current, refund: 'rejected' })); });
    api.get('/accounting/finance').then(({ data }) => {
      if (!active) return;
      const ready = Array.isArray(data?.requests) && Array.isArray(data?.branches) && data.balance != null;
      setBranches(ready ? data.branches : []);
      setRequests(ready ? data.requests : []);
      setFundBalance(ready ? data.balance : null);
      setApiStatus((current) => ({ ...current, finance: ready ? 'fulfilled' : 'rejected' }));
    }).catch(() => { if (active) setApiStatus((current) => ({ ...current, finance: 'rejected' })); });
    return () => { active = false; };
  }, [revision]);

  useEffect(() => {
    if (!singleMonthScope) {
      setApiStatus((current) => ({ ...current, cash: 'idle', payroll: 'idle', periods: 'idle', details: 'idle' }));
      return undefined;
    }
    let active = true;
    setApiStatus((current) => ({ ...current, cash: 'loading', payroll: 'loading', periods: 'loading', details: 'loading' }));
    const from = `${month}-01`;
    const end = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).toISOString().slice(0, 10);
    Promise.allSettled([
      api.get('/accounting/cash-closing', { params: { from, to: end, ...(branchId ? { branchId } : {}) } }),
      api.get(`/accounting/payroll/${month}`),
      api.get('/accounting/financial-periods'),
      api.get('/accounting/dashboard-details', { params: { month, ...(branchId ? { branchId } : {}) } }),
    ]).then((results) => {
      if (!active) return;
      const [cash, payrollResult, periodResult, details] = results;
      const available = {
        cash: cash.status === 'fulfilled' && Array.isArray(cash.value.data),
        payroll: payrollResult.status === 'fulfilled' && Array.isArray(payrollResult.value.data?.rows),
        periods: periodResult.status === 'fulfilled' && Array.isArray(periodResult.value.data),
        details: details.status === 'fulfilled' && Array.isArray(details.value.data?.paymentMethods)
          && Array.isArray(details.value.data?.sepayExceptions),
      };
      setApiStatus((current) => ({ ...current, ...Object.fromEntries(Object.entries(available).map(([key, ready]) => [key, ready ? 'fulfilled' : 'rejected'])) }));
      setClosings(available.cash ? cash.value.data : []);
      setPayroll(available.payroll ? payrollResult.value.data : null);
      setPeriods(available.periods ? periodResult.value.data : []);
      setPaymentMethods(available.details ? details.value.data.paymentMethods : []);
      setSepayExceptions(available.details ? details.value.data.sepayExceptions : []);
    });
    return () => { active = false; };
  }, [month, branchId, singleMonthScope, revision]);

  const monthLabel = `Tháng ${month.slice(5, 7)}/${month.slice(0, 4)}`;
  const selectedBranchName = branches.find((item) => item.id === branchId)?.name;
  // shortcut: API hoàn cọc chỉ có branchName; chặn tên trùng, dùng branchId khi API bổ sung.
  const uniqueRefundBranch = !branchId || (selectedBranchName && branches.filter((item) => item.name === selectedBranchName).length === 1);
  const branchLabel = selectedBranches.length > 1 ? `${selectedBranches.length} cơ sở đang chọn` : branchId ? selectedBranchName || 'cơ sở đang chọn' : 'toàn chuỗi';
  const scopedDailyStatuses = selectedBranches.length ? dailyStatuses.filter((item) => selectedBranches.includes(item.branchId)) : dailyStatuses;
  const dailyPanelState = !cashDateValid ? 'invalid' : dailyStatusForDate === cashDate ? dailyStatusState : 'loading';
  const dailyGroups = [
    { status: 'SUBMITTED', label: 'Đã gửi' },
    { status: 'NEEDS_EXPLANATION', label: 'Cần bổ sung' },
    { status: 'NOT_SUBMITTED', label: 'Chưa có bản gửi' },
  ];
  const dailyHistoryQuery = new URLSearchParams({ date: cashDate, from: cashDate, to: cashDate, status: '' });
  const state = (key) => apiStatus[key] === 'fulfilled' ? 'ready' : apiStatus[key] === 'rejected' ? 'error' : 'loading';
  const refundRows = refunds.filter((row) => ['REQUESTED', 'APPROVED', 'PROCESSING'].includes(row.status));
  // BR-17/93: giữ các yêu cầu hoàn cọc và hoàn ứng còn thao tác của kế toán trong hàng chờ.
  const expenseRequests = requests.filter((row) => ['WAITING_ACCOUNTANT', 'APPROVED_TO_PAY'].includes(row.status));
  const selectedRefunds = branchId ? refundRows.filter((row) => row.branchName === selectedBranchName) : refundRows;
  const selectedRequests = selectedBranches.length ? expenseRequests.filter((row) => selectedBranches.includes(row.branch_id)) : expenseRequests;
  const refundTableState = !uniqueRefundBranch && state('refund') === 'ready' ? 'error' : state('refund');
  const payrollRows = payroll?.rows || [];
  const payrollTotal = payrollRows.reduce((sum, row) => sum + Number(row.net_amount || 0), 0);
  const payrollPaid = payrollRows.filter((row) => row.paid).reduce((sum, row) => sum + Number(row.net_amount || 0), 0);
  const currentPeriod = periods.find((row) => String(row.month).slice(0, 7) === month);
  const exceptions = sepayExceptions;
  const visibleExceptions = exceptions.filter((row) => exceptionFilter === 'ALL' || row.resolutionStatus === exceptionFilter);
  const paymentTotal = paymentMethods.reduce((sum, item) => sum + Number(item.value || 0), 0);
  let paymentOffset = 0;
  const methodParts = paymentMethods.map((item, index) => {
    const start = paymentOffset;
    paymentOffset += paymentTotal ? Number(item.value || 0) / paymentTotal * 100 : 0;
    return `${['#168b87', '#6a3ee8', '#df9b28'][index % 3]} ${start}% ${paymentOffset}%`;
  }).join(', ');

  async function logout() {
    setBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch { setError('Không thể đăng xuất. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  async function resolveSePayException() {
    if (!resolutionTarget || !resolutionNote.trim()) return;
    setResolutionBusy(true); setError('');
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      const { data: resolved } = await api.post(`/accounting/sepay-exceptions/${resolutionTarget.id}/resolve`,
        { resolutionNote }, { headers: { [csrf.headerName]: csrf.token } });
      setSepayExceptions((current) => current.map((item) => item.id === resolved.id ? { ...item, ...resolved } : item));
      setResolutionTarget(null); setResolutionNote('');
    } catch (e) { setError(errorText(e)); }
    finally { setResolutionBusy(false); }
  }

  useEffect(() => {
    if (!resolutionTarget) return undefined;
    const trigger = resolutionTriggerRef.current;
    const fallback = exceptionFilterRef.current;
    resolutionDialogRef.current?.querySelector('textarea')?.focus();
    return () => (trigger?.isConnected ? trigger : fallback)?.focus();
  }, [resolutionTarget]);

  function handleResolutionKeys(event) {
    if (event.key === 'Escape' && !resolutionBusy) { setResolutionTarget(null); return; }
    if (event.key !== 'Tab') return;
    const controls = [...event.currentTarget.querySelectorAll('textarea, button:not([disabled])')];
    if (event.shiftKey && document.activeElement === controls[0]) { event.preventDefault(); controls[controls.length - 1]?.focus(); }
    else if (!event.shiftKey && document.activeElement === controls[controls.length - 1]) { event.preventDefault(); controls[0]?.focus(); }
  }

  return <AppShell user={user} branchName="toàn chuỗi" busy={busy} onLogout={logout} breadcrumb="Tổng quan kế toán" activeTab="overview" audience="accountant" hideSearch showCurrentMonth={false}>
    <main className="accountant-dash" aria-labelledby="accountant-dash-title">
      <header className="accountant-dash-header"><h1 id="accountant-dash-title">Tổng quan tài chính</h1></header>
      {user && <RevenueExpenseOverview role="ACCOUNTANT" />}
      <section className="accountant-dash-panel accountant-daily-status" aria-labelledby="accountant-daily-status-title">
        <div className="accountant-daily-heading">
          <div><h2 id="accountant-daily-status-title">Trạng thái báo cáo chốt quỹ ngày</h2>
            <p>Một ngày kinh doanh · {branchLabel}. Trạng thái bản gửi không làm thay đổi số thu chi tính từ giao dịch hệ thống.</p></div>
          <label>Ngày kinh doanh<input type="date" min="2000-01-01" max={currentDay} value={cashDateValid ? cashDate : ''}
            onChange={(event) => { const next = new URLSearchParams(searchParams); next.set('cashDate', event.target.value); setSearchParams(next); }} /></label>
        </div>
        {cashDateValid && <p className="accountant-daily-scope">Ngày đang xem: <strong>{cashDate.split('-').reverse().join('/')}</strong> · {dailyPanelState === 'ready' ? scopedDailyStatuses.length : '—'} cơ sở trong phạm vi.</p>}
        {dailyPanelState === 'invalid' ? <p className="accountant-dash-empty" role="alert">Ngày trên URL không hợp lệ hoặc ở tương lai. Chọn một ngày từ 01/01/2000 đến hôm nay để xem trạng thái.</p>
          : dailyPanelState === 'loading' ? <p className="accountant-dash-empty" role="status">Đang tải trạng thái báo cáo chốt quỹ ngày…</p>
            : dailyPanelState === 'error' ? <p className="accountant-dash-empty" role="alert">Không tải được trạng thái báo cáo. <button type="button" onClick={() => setDailyRevision((value) => value + 1)}>Thử lại</button></p>
              : <div className="accountant-daily-groups">{dailyGroups.map(({ status, label }) => {
                const rows = scopedDailyStatuses.filter((item) => item.status === status);
                return <div className="accountant-daily-group" key={status}><h3>{label} <span>{rows.length}</span></h3>
                  {rows.length ? <ul>{rows.map((item) => <li key={item.branchId}><span>{item.branchName}</span>
                    {item.closingId && <Link to={`/ke-toan/chot-quy?${new URLSearchParams({ reportId: item.closingId, branchId: item.branchId, date: cashDate, from: cashDate, to: cashDate })}`}>Mở bản ghi</Link>}</li>)}</ul>
                    : <p>Không có cơ sở.</p>}</div>;
              })}</div>}
        {cashDateValid && <Link className="accountant-daily-all" to={`/ke-toan/chot-quy?${dailyHistoryQuery}`}>Xem tất cả · mở toàn chuỗi</Link>}
      </section>
      <section className="accountant-dash-panel accountant-notifications" aria-labelledby="accountant-notifications-title">
        <div className="accountant-notifications-heading"><div><h2 id="accountant-notifications-title">Báo cáo cơ sở vừa gửi</h2>
          <p>Các lần gửi gần đây · toàn chuỗi; không theo bộ lọc ngày hoặc cơ sở của tổng quan tài chính.</p></div>
          <Link to="/ke-toan/chot-quy#cash-closing-notifications-title">Xem danh sách thông báo</Link></div>
        {notificationState === 'loading' ? <p className="accountant-notifications-message" role="status">Đang tải thông báo…</p>
          : notificationState === 'error' ? <p className="accountant-notifications-message" role="alert">Không tải được thông báo. <button type="button" onClick={() => setNotificationRevision((value) => value + 1)}>Thử lại</button></p>
            : notifications.length === 0 ? <p className="accountant-notifications-message" role="status">Chưa có lần gửi báo cáo chốt quỹ ngày nào.</p>
              : <ul className="accountant-notifications-list">{notifications.slice(0, 5).map((item) => <li key={item.id}>
                <div><strong>{item.branchName}</strong><span>Ngày kinh doanh: {dateVi(item.businessDate)} · Gửi lúc: {new Date(item.sentAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })} · Lần gửi {item.reportVersion}</span></div>
                <Link to={`/ke-toan/chot-quy?${new URLSearchParams({ reportId: item.closingId, from: item.businessDate, to: item.businessDate, date: item.businessDate })}`}
                  aria-label={`Xem báo cáo hiện tại và lịch sử phiên bản của ${item.branchName}, ngày kinh doanh ${dateVi(item.businessDate)}`}>Xem báo cáo hiện tại và lịch sử phiên bản</Link>
              </li>)}</ul>}
      </section>
      {(error || Object.values(apiStatus).includes('rejected')) && <div className="accountant-dash-error" role="alert"><span>{error || 'Một số bảng nghiệp vụ không tải được; xem trạng thái tại từng bảng.'}</span><button type="button" onClick={() => { setError(''); setRevision((value) => value + 1); }}>Thử lại</button></div>}
      <section className="accountant-work-section" aria-labelledby="accountant-work-title">
        <h2 className="accountant-work-heading" id="accountant-work-title">Việc cần xử lý</h2>
        <p className="accountant-work-intro">Trạng thái hiện tại, không theo khoảng ngày báo cáo. Đề nghị chi chưa thanh toán chưa phải chi phí vận hành đã ghi nhận.</p>
        <section className="accountant-period-status"><div><strong>Số dư quỹ kế toán hiện tại · toàn chuỗi: {state('finance') === 'ready' ? money(fundBalance) : '—'}</strong>
          <span>{state('finance') === 'loading' ? 'Đang tải số dư quỹ…' : state('finance') === 'error' ? 'Không tải được số dư quỹ.' : 'Số dư hiện tại, không theo kỳ hoặc cơ sở đang lọc.'}</span></div>
          <Link to="/ke-toan/chi-phi?section=payments">Xem quỹ</Link></section>
        <div className="accountant-dash-grid">
          <DataTable title="Hoàn cọc hiện đang chờ xử lý" description={selectedBranches.length > 1 ? 'hiện tại · toàn chuỗi · không theo bộ lọc cơ sở' : `Hiện tại · ${branchLabel} · không theo khoảng ngày báo cáo.`} state={refundTableState} href="/ke-toan/hoan-coc" action="Mở hoàn cọc"
            errorMessage={!uniqueRefundBranch && state('refund') === 'ready' ? 'Không xác định duy nhất cơ sở để lọc yêu cầu hoàn cọc.' : 'Không tải được yêu cầu hoàn cọc.'}
            columns={['Mã đặt phòng', 'Cơ sở', 'Ngày yêu cầu', 'Trạng thái', 'Số tiền']} rows={selectedRefunds}
            renderRow={(row) => <tr key={row.id}><td>{row.bookingCode || row.id}</td><td>{row.branchName || '—'}</td><td>{dateVi(row.requestedAt)}</td><td>{statusVi(row.status)}</td><td>{money(row.amount)}</td></tr>} />
          <DataTable title="Đề nghị chi cần kế toán xử lý" description={`Hiện tại · ${branchLabel} · không theo khoảng ngày; chưa phải chi phí đã ghi nhận.`} state={state('finance')} href="/ke-toan/chi-phi?section=plus&tab=requests" action="Mở đề nghị chi"
            columns={['Mã đề nghị', 'Cơ sở', 'Khoản mục', 'Trạng thái', 'Số tiền']} rows={selectedRequests}
            renderRow={(row) => <tr key={row.id}><td>{row.code || row.id}</td><td>{row.branch_name || '—'}</td><td>{row.category_name || '—'}{row.description && <small>{row.description}</small>}</td><td>{statusVi(row.status)}</td><td>{money(row.amount)}</td></tr>} />
        </div>
      </section>
      {singleMonthScope ? <section className="accountant-month-section" aria-labelledby="accountant-month-title">
        <div className="accountant-month-heading">
          <h2 id="accountant-month-title">Thông tin nghiệp vụ tháng</h2>
          <p>{monthLabel} · {branchLabel}. Các bảng dưới đây dùng dữ liệu tháng; kỳ tài chính và quỹ lương áp dụng cho toàn chuỗi.</p>
        </div>
        <section className="accountant-period-status"><div><strong>Kỳ tài chính {monthLabel} · toàn chuỗi: {state('periods') === 'ready' ? statusVi(currentPeriod?.status) : '—'}</strong>
          <span>{state('periods') === 'loading' ? 'Đang tải điều kiện chốt kỳ…' : state('periods') === 'error' ? 'Không tải được điều kiện chốt kỳ.' : currentPeriod ? `${currentPeriod.unreconciledClosings} phiếu chốt quỹ chưa đối soát` : 'Chưa có thông tin đối soát cho kỳ này.'}</span></div>
          <Link to={`/ke-toan/bao-cao?${periodQuery}`}>Xem điều kiện chốt kỳ</Link></section>
        <div className="accountant-dash-grid">
          <DataTable title="Đối soát tiền mặt" description={`${monthLabel} · ${branchLabel} · số trên phiếu chốt quỹ, không phải chi phí.`} state={state('cash')} href="/ke-toan/chot-quy" action="Mở đối soát chốt quỹ"
            columns={['Cơ sở', 'Ngày', 'Trạng thái', 'Tiền hệ thống', 'Tiền thực tế', 'Chênh lệch']} rows={closings}
            renderRow={(row) => <tr key={row.id}><td>{row.branchName || '—'}</td><td>{dateVi(row.business_date)}</td><td>{statusVi(row.status)}</td><td>{money(row.expected_closing)}</td><td>{money(row.actual_closing)}</td><td className={Number(row.variance || 0) ? 'accountant-deviation' : 'accountant-matched'}>{money(row.variance)}</td></tr>} />
          <section className="accountant-dash-panel accountant-table-panel">
            <div className="accountant-panel-heading"><div><h2>Giao dịch SePay ngoại lệ</h2><p>Phát sinh {monthLabel.toLowerCase()} · {branchLabel} · trạng thái xử lý hiện tại.</p></div><span>{state('details') === 'ready' ? visibleExceptions.length : '—'} mục</span></div>
            <label className="accountant-exception-filter">Trạng thái xử lý<select ref={exceptionFilterRef} value={exceptionFilter} onChange={(event) => setExceptionFilter(event.target.value)}><option value="UNRESOLVED">Chưa xử lý</option><option value="RESOLVED">Đã xử lý</option><option value="ALL">Tất cả</option></select></label>
            {state('details') === 'ready' && visibleExceptions.length ? <div className="accountant-table-scroll" tabIndex={0} aria-label="Giao dịch SePay ngoại lệ"><table><thead><tr>{['Mã giao dịch', 'Cơ sở', 'Ngày', 'Số tiền', 'Trạng thái', 'Lý do ngoại lệ', 'Nội dung chuyển khoản', 'Người xử lý', 'Thời điểm xử lý', 'Ghi chú xử lý', 'Thao tác'].map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{visibleExceptions.map((row) => <tr key={row.id}><td>{row.externalId || '—'}</td><td>{row.branchName || '—'}</td><td>{dateVi(row.occurredAt)}</td><td>{money(row.amount)}</td><td>{statusVi(row.resolutionStatus)}</td><td>{row.exceptionReason || '—'}</td><td>{row.reason || '—'}</td><td>{row.resolvedBy || '—'}</td><td>{row.resolvedAt ? new Date(row.resolvedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'}</td><td>{row.resolutionNote || '—'}</td><td>{row.resolutionStatus === 'UNRESOLVED' ? <button type="button" onClick={(event) => { resolutionTriggerRef.current = event.currentTarget; setResolutionTarget(row); setResolutionNote(''); }}>Đánh dấu đã xử lý</button> : 'Đã xử lý'}</td></tr>)}</tbody></table></div>
              : <p className="accountant-dash-empty" role="status">{state('details') === 'loading' ? 'Đang tải…' : state('details') === 'error' ? 'Không tải được giao dịch SePay.' : 'Không có giao dịch ngoại lệ theo bộ lọc.'}</p>}
          </section>
        </div>
        <div className="accountant-dash-grid">
          <section className="accountant-dash-panel accountant-payroll"><div className="accountant-panel-heading"><div><h2>Quỹ lương kỳ</h2><p>{monthLabel} · toàn chuỗi; số đã chi theo thanh toán thực tế, không cộng vào chi phí lần nữa tại đây.</p></div><span>{state('payroll') === 'ready' ? statusVi(payroll?.status) : '—'}</span></div>
            {state('payroll') === 'ready' ? <><strong className="accountant-payroll-total">{money(payrollTotal)}</strong><div className="accountant-payroll-progress"><div><span>Đã chi</span><strong>{money(payrollPaid)}</strong></div><div className="accountant-expense-track"><i style={{ width: `${payrollTotal ? payrollPaid / payrollTotal * 100 : 0}%` }} /></div><div><span>{payrollTotal ? `${Math.round(payrollPaid / payrollTotal * 100)}% đã chi` : 'Chưa có khoản lương phải chi'}</span><strong>{money(payrollTotal - payrollPaid)} chưa chi</strong></div></div></> : <p className="accountant-dash-empty" role="status">{state('payroll') === 'loading' ? 'Đang tải quỹ lương…' : 'Không tải được quỹ lương.'}</p>}
          </section>
          <section className="accountant-dash-panel"><div className="accountant-panel-heading"><div><h2>Giá trị hóa đơn có thanh toán theo phương thức</h2><p>{monthLabel} · {branchLabel} · chỉ gồm hóa đơn có khoản thanh toán bổ sung; có thể khác doanh thu hóa đơn, không phải chi phí.</p></div></div>
            {state('details') === 'ready' && paymentMethods.length ? <div className="accountant-methods"><div className="accountant-method-donut" style={{ '--method-parts': methodParts }}><div><strong>{money(paymentTotal)}</strong><span>Giá trị hóa đơn hiển thị</span></div></div><div className="accountant-method-legend">{paymentMethods.map((item, index) => <div key={item.label}><span><i style={{ background: ['#168b87', '#6a3ee8', '#df9b28'][index % 3] }} />{item.label}</span><strong>{money(item.value)}</strong></div>)}</div></div> : <p className="accountant-dash-empty" role="status">{state('details') === 'loading' ? 'Đang tải…' : state('details') === 'error' ? 'Không tải được cơ cấu thanh toán.' : 'Không có hóa đơn theo phương thức thanh toán trong kỳ.'}</p>}
          </section>
        </div>
        <nav className="accountant-dash-shortcuts" aria-label="Lối tắt nghiệp vụ kế toán"><Link to={`/ke-toan/bao-cao?${scopeQuery}`}>Báo cáo chi tiết</Link><Link to={`/ke-toan/bao-cao?${periodQuery}`}>Chốt kỳ</Link><Link to={`/ke-toan/bao-cao?${transactionsQuery}`}>Giao dịch &amp; điều chỉnh</Link></nav>
        {resolutionTarget && <div className="accountant-exception-backdrop" role="presentation"><section ref={resolutionDialogRef} className="accountant-exception-dialog" role="dialog" aria-modal="true" aria-labelledby="resolve-sepay-title" onKeyDown={handleResolutionKeys}><h2 id="resolve-sepay-title">Đánh dấu ngoại lệ đã xử lý</h2><p>Giao dịch {resolutionTarget.externalId} · {money(resolutionTarget.amount)}. Thao tác chỉ ghi nhận trạng thái và ghi chú, không tạo bút toán tiền.</p><label>Ghi chú xử lý<textarea value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} maxLength={1000} required /></label><div><button type="button" disabled={resolutionBusy} onClick={() => setResolutionTarget(null)}>Hủy</button><button type="button" disabled={resolutionBusy || !resolutionNote.trim()} onClick={resolveSePayException}>{resolutionBusy ? 'Đang lưu…' : 'Xác nhận đã xử lý'}</button></div></section></div>}
      </section> : <section className="accountant-scope-warning" role="status"><strong>Thông tin nghiệp vụ tháng chưa hiển thị cho phạm vi này.</strong>
        <p>{!fullMonthRange && 'Chọn trọn một tháng (tháng hiện tại tính đến hôm nay). '}{selectedBranches.length > 1 && `Bạn đang chọn ${selectedBranches.length} cơ sở; phần này chỉ hỗ trợ toàn chuỗi hoặc một cơ sở. `}Tổng quan tài chính phía trên vẫn dùng đúng khoảng ngày và cơ sở đã chọn.</p>
        <Link to={`/ke-toan/bao-cao?${scopeQuery}`}>Xem báo cáo theo phạm vi hiện tại</Link></section>}
    </main>
  </AppShell>;
}
