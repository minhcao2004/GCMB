import MoneyInput from '../finance/components/MoneyInput';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import BranchFilterSelect from './BranchFilterSelect';
import './dashboard.css';
import RevenueExpenseOverview from './RevenueExpenseOverview';
import ImportedSourceReport from './ImportedSourceReport';
import './accountantDashboard.css';

const currentVnMonth = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
const currentVnDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const monthNow = currentVnMonth();
const monthDateRange = (value) => {
  const [year, monthNumber] = value.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).toISOString().slice(0, 10);
  const today = currentVnDate();
  return { from: `${value}-01`, to: value === monthNow && lastDay > today ? today : lastDay };
};
const monthNames = Array.from({ length: 12 }, (_, index) => ({ value: String(index + 1).padStart(2, '0'), label: `Tháng ${index + 1}` }));
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
const formatMonth = (value) => `${value.slice(5, 7)}/${value.slice(0, 4)}`;
const formatDate = (value) => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : '—';
const label = { report: 'Tổng quan', periods: 'Chốt kỳ', transactions: 'Giao dịch', export: 'Xuất dữ liệu' };
const transactionKindLabels = { PAYMENT: 'Thu thanh toán', REFUND: 'Hoàn cọc', EXPENSE: 'Chi phí', SALARY: 'Trả lương', ADVANCE: 'Ứng lương', TRANSFER: 'Chuyển quỹ', OPENING: 'Số dư đầu kỳ', ADJUSTMENT: 'Điều chỉnh' };
const exceptionReasonLabels = {
  REFERENCE_NOT_FOUND: 'Không tìm thấy mã tham chiếu thanh toán',
  PERIOD_CLOSED: 'Kỳ tài chính đã khóa',
};
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.';
const parseAdjustments = (value) => { try { return JSON.parse(value || '[]'); } catch { return []; } };
const EARLY_CLOSE_WARNING = 'Chế độ demo: đang cho phép chốt kỳ trước khi tháng kết thúc. Khóa kỳ không thể hoàn tác.';
const TRANSACTIONS_PER_PAGE = 10;

export default function AccountantFinancePage({ reportsOnly = false }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const availableTabs = reportsOnly ? ['report'] : Object.keys(label);
  const [tab, setTab] = useState(() => availableTabs.includes(searchParams.get('tab')) ? searchParams.get('tab') : 'report');
  const queryMonth = searchParams.get('month') || '';
  const queryFrom = searchParams.get('from') || '';
  const queryTo = searchParams.get('to') || '';
  const validQueryMonth = /^\d{4}-(0[1-9]|1[0-2])$/.test(queryMonth) && queryMonth <= monthNow;
  const rangeMonth = /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(queryFrom) && queryFrom <= currentVnDate() ? queryFrom.slice(0, 7) : '';
  const initialMonth = validQueryMonth ? queryMonth : rangeMonth || monthNow;
  const [month, setMonth] = useState(initialMonth);
  const [reportRange, setReportRange] = useState(() => {
    const validDates = /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(queryFrom)
      && /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(queryTo)
      && queryFrom <= queryTo && queryTo <= currentVnDate()
      && queryFrom.slice(0, 7) === queryTo.slice(0, 7) && queryFrom.slice(0, 7) === initialMonth;
    return validDates ? { from: queryFrom, to: queryTo } : monthDateRange(initialMonth);
  });
  const [data, setData] = useState(null);
  const [revenueSummary, setRevenueSummary] = useState(null);
  const [dataTab, setDataTab] = useState(null);
  const [branchOptions, setBranchOptions] = useState([]);
  const [branchId, setBranchId] = useState(() => searchParams.get('branchId') || '');
  const [rows, setRows] = useState([]);
  const [sourceTransactions, setSourceTransactions] = useState([]);
  const [operatingReport, setOperatingReport] = useState(null);
  const [sepayExceptions, setSepayExceptions] = useState([]);
  const [exceptionFilter, setExceptionFilter] = useState('UNRESOLVED');
  const [resolutionTarget, setResolutionTarget] = useState(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [transactionPage, setTransactionPage] = useState(1);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [user, setUser] = useState(null);
  const [scope, setScope] = useState('ALL');
  const [adjusting, setAdjusting] = useState(null);
  const [adjustmentConfirmed, setAdjustmentConfirmed] = useState(false);
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState(null);
  const [transactionKind, setTransactionKind] = useState('');
  const [transactionQuery, setTransactionQuery] = useState('');
  const [periodInfo, setPeriodInfo] = useState(null);
  const [periodLoading, setPeriodLoading] = useState(false);
  const [periodError, setPeriodError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [periodReadiness, setPeriodReadiness] = useState(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [closingMonth, setClosingMonth] = useState(null);
  const [closeNote, setCloseNote] = useState('');
  const adjustmentFormRef = useRef(null);

  function selectTab(next) {
    setTab(next);
    const params = reportsOnly ? new URLSearchParams(searchParams) : new URLSearchParams();
    if (next === 'report') params.delete('tab');
    else params.set('tab', next);
    setSearchParams(params, { replace: true });
    setError('');
  }

  useEffect(() => {
    const requestedTab = searchParams.get('tab');
    const validTab = reportsOnly ? requestedTab === 'report' : Object.prototype.hasOwnProperty.call(label, requestedTab);
    if (requestedTab === 'expenses') {
      const next = new URLSearchParams(searchParams);
      next.delete('tab');
      setSearchParams(next, { replace: true });
    }
    setTab(validTab ? requestedTab : 'report');
  }, [searchParams, reportsOnly, setSearchParams]);

  useEffect(() => {
    if (!adjusting) return undefined;
    const frame = window.requestAnimationFrame(() => {
      adjustmentFormRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      adjustmentFormRef.current?.querySelector('input[name="adjustmentAmount"]')?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [adjusting]);

  useEffect(() => {
    authApi.me().then((identity) => { if (identity.role !== 'ACCOUNTANT' && !(reportsOnly && identity.role === 'HEAD_OFFICE_MANAGER')) navigate(identity.destination, { replace: true }); else setUser(identity); })
      .catch(() => navigate('/dang-nhap', { replace: true }));
  }, [navigate, reportsOnly]);

  useEffect(() => {
    let live = true;
    setError(''); setData(null); setDataTab(null); setRows([]); setLoading(true);
    const path = tab === 'report' ? '/accounting/reports/monthly'
      : tab === 'periods' ? '/accounting/financial-periods'
        : tab === 'transactions' ? '/accounting/transactions' : null;
    if (!path) { setLoading(false); return undefined; }
    const config = tab === 'periods' ? undefined : { params: {
      month,
      ...(['report', 'transactions'].includes(tab) && branchId ? { branchId } : {}),
      ...(tab === 'transactions' ? { kind: transactionKind, q: transactionQuery } : {}),
    } };
    api.get(path, config).then(({ data: result }) => {
      if (!live) return;
      const report = tab === 'report'
        ? { ...result, results: Array.isArray(result?.results) ? result.results : [], totals: result?.totals || {} }
        : result;
      if (tab === 'periods' || tab === 'transactions') setRows(result);
      else { setData(report); setDataTab(tab); }
      if (tab === 'report' && !reportsOnly && !branchId) setBranchOptions(report.results);
    })
      .catch((e) => { if (live) setError(errorText(e)); }).finally(() => { if (live) setLoading(false); });
    if (tab === 'report' && reportsOnly) api.get('/head-office/dashboard', { params: { month } })
      .then(({ data: dashboard }) => { if (live) setBranchOptions(dashboard.branches || []); })
      .catch(() => { if (live) setBranchOptions([]); });
    if (tab === 'report' && !reportsOnly && branchId) api.get('/accounting/reports/monthly', { params: { month } })
      .then(({ data: report }) => { if (live) setBranchOptions(report.results || []); })
      .catch(() => { if (live) setBranchOptions([]); });
    return () => { live = false; };
  }, [tab, month, branchId, revision, transactionKind, transactionQuery, reportsOnly]);

  useEffect(() => {
    if (tab !== 'report') { setSourceTransactions([]); return undefined; }
    let live = true;
    api.get('/accounting/transactions', { params: { month, ...(branchId ? { branchId } : {}) } })
      .then(({ data: result }) => { if (live) setSourceTransactions(result || []); })
      .catch(() => { if (live) setSourceTransactions([]); });
    return () => { live = false; };
  }, [tab, month, branchId, revision]);

  useEffect(() => {
    if (tab !== 'transactions' || reportsOnly) { setSepayExceptions([]); return undefined; }
    let live = true;
    api.get('/accounting/dashboard-details', { params: { month, ...(branchId ? { branchId } : {}) } })
      .then(({ data: result }) => { if (live) setSepayExceptions(result.sepayExceptions || []); })
      .catch(() => { if (live) setSepayExceptions([]); });
    return () => { live = false; };
  }, [tab, month, branchId, reportsOnly]);

  useEffect(() => {
    setTransactionPage(1);
  }, [tab, month, branchId, transactionKind, transactionQuery]);

  useEffect(() => {
    if (tab !== 'export') { setRevenueSummary(null); return undefined; }
    let live = true;
    api.get('/accounting/reports/monthly', { params: { month, ...(branchId ? { branchId } : {}) } })
      .then(({ data: result }) => { if (live) setRevenueSummary(result.totals); })
      .catch(() => { if (live) setRevenueSummary(null); });
    return () => { live = false; };
  }, [tab, month, branchId, revision]);

  useEffect(() => {
    if (tab !== 'transactions' || branchOptions.length) return undefined;
    let live = true;
    api.get('/accounting/reports/monthly', { params: { month } })
      .then(({ data: result }) => { if (live) setBranchOptions(result.results || []); })
      .catch(() => {});
    return () => { live = false; };
  }, [tab, month, branchOptions.length]);

  useEffect(() => {
    if (reportsOnly || user?.role !== 'ACCOUNTANT' || tab !== 'report') return undefined;
    let live = true;
    setPeriodInfo(null); setPeriodError(false); setPeriodLoading(true);
    api.get('/accounting/financial-periods')
      .then(({ data: periods }) => { if (live) setPeriodInfo(periods.find((period) => String(period.month).slice(0, 7) === month) || null); })
      .catch(() => { if (live) setPeriodError(true); })
      .finally(() => { if (live) setPeriodLoading(false); });
    return () => { live = false; };
  }, [reportsOnly, user?.role, tab, month, revision]);

  useEffect(() => {
    if (reportsOnly || user?.role !== 'ACCOUNTANT' || tab !== 'periods') return undefined;
    let live = true;
    setPeriodReadiness(null); setReadinessLoading(true);
    api.get(`/accounting/financial-periods/${month}/readiness`)
      .then(({ data: result }) => { if (live) setPeriodReadiness(result); })
      .catch((e) => { if (live) setError(errorText(e)); })
      .finally(() => { if (live) setReadinessLoading(false); });
    return () => { live = false; };
  }, [reportsOnly, user?.role, tab, month, revision]);

  async function closePeriod(periodMonth) {
    setBusy(true); setError('');
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      await api.post(`/accounting/financial-periods/${periodMonth}/close`, { confirmed: true, note: closeNote }, { headers: { [csrf.headerName]: csrf.token } });
      setClosingMonth(null); setCloseNote(''); setRevision((value) => value + 1);
    }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  // BR-100/101: xác nhận điều chỉnh được ghi riêng, liên kết giao dịch gốc và không sửa giao dịch gốc.
  async function submitAdjustment(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      const form = new FormData();
      form.append('command', new Blob([JSON.stringify({ amountSigned: amount, reason })], { type: 'application/json' }));
      if (evidence) form.append('evidence', evidence);
      await api.post(`/accounting/transactions/${adjusting.id}/adjustments`, form, { headers: { [csrf.headerName]: csrf.token, 'Content-Type': undefined } });
      setAdjusting(null); setAmount(''); setReason(''); setEvidence(null); setAdjustmentConfirmed(false);
      setRevision((value) => value + 1);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function resolveSePayException() {
    if (!resolutionTarget || !resolutionNote.trim()) return;
    setBusy(true); setError('');
    try {
      const { data: csrf } = await api.get('/auth/csrf');
      const { data: resolved } = await api.post(`/accounting/sepay-exceptions/${resolutionTarget.id}/resolve`,
        { resolutionNote }, { headers: { [csrf.headerName]: csrf.token } });
      setSepayExceptions((current) => current.map((item) => item.id === resolved.id ? { ...item, ...resolved } : item));
      setResolutionTarget(null); setResolutionNote('');
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function downloadEvidence(file) {
    setBusy(true); setError('');
    try {
      const { data: blob } = await api.get(`/finance/attachments/${file.id}`, { responseType: 'blob' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.name; anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function download() {
    setBusy(true); setError('');
    try {
      const response = await api.get('/accounting/tax-data.csv', { params: { month, scope }, responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `gcmb-accounting-${month}.csv`; anchor.click(); URL.revokeObjectURL(url);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  async function logout() {
    setLogoutBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (e) { setError(errorText(e)); }
    finally { setLogoutBusy(false); }
  }

  function updateReportMonth(year, monthNumber) {
    const nextMonth = `${year}-${monthNumber}`;
    if (nextMonth > currentVnMonth()) return;
    setMonth(nextMonth);
    setReportRange(monthDateRange(nextMonth));
  }

  const reportYears = Array.from({ length: Number(monthNow.slice(0, 4)) - 2020 + 1 }, (_, index) => String(Number(monthNow.slice(0, 4)) - index));
  const selectedBranchName = branchId
    ? branchOptions.find((branch) => String(branch.id) === String(branchId))?.name || 'Cơ sở đã chọn'
    : 'Toàn chuỗi';
  const transactionPageCount = Math.max(1, Math.ceil(rows.length / TRANSACTIONS_PER_PAGE));
  const currentTransactionPage = Math.min(transactionPage, transactionPageCount);
  const transactionRows = rows.slice((currentTransactionPage - 1) * TRANSACTIONS_PER_PAGE, currentTransactionPage * TRANSACTIONS_PER_PAGE);

  const content = <section className="performance-dashboard accountant-finance" aria-labelledby="finance-title">
    <header className={`finance-heading${tab === 'report' ? ' finance-heading--report' : ''}`}>
      <div className="finance-heading-copy">{tab === 'report' && <span className="finance-heading-eyebrow">KẾ TOÁN · BÁO CÁO</span>}<h1 id="finance-title">Tài chính &amp; báo cáo</h1></div>
      {tab !== 'report' && <div className="finance-filters" aria-label="Kỳ báo cáo"><label className="finance-month">Tháng<input aria-label="Chọn tháng báo cáo" type="month" value={month} max={currentVnMonth()} onChange={(event) => { if (event.target.value) setMonth(event.target.value); }} /></label></div>}
    </header>
    {tab === 'report' && <div className="finance-report-filters" aria-label="Bộ lọc báo cáo">
      <label className="finance-report-filter-field">Kỳ báo cáo<select value={month.slice(5, 7)} onChange={(event) => updateReportMonth(month.slice(0, 4), event.target.value)} aria-label="Chọn kỳ báo cáo">
        {monthNames.map((item) => <option key={item.value} value={item.value} disabled={`${month.slice(0, 4)}-${item.value}` > currentVnMonth()}>{item.label}</option>)}
      </select></label>
      <label className="finance-report-filter-field">Năm<select value={month.slice(0, 4)} onChange={(event) => updateReportMonth(event.target.value, month.slice(5, 7))} aria-label="Chọn năm báo cáo">
        {reportYears.map((year) => <option key={year} value={year}>{year}</option>)}
      </select></label>
      <label className="finance-report-filter-field">Từ ngày<input type="date" required value={reportRange.from} min={`${month}-01`} max={monthDateRange(month).to} onChange={(event) => { if (event.target.value) setReportRange((current) => ({ from: event.target.value, to: current.to < event.target.value ? event.target.value : current.to })); }} /></label>
      <label className="finance-report-filter-field">Đến ngày<input type="date" required value={reportRange.to} min={reportRange.from} max={monthDateRange(month).to} onChange={(event) => { if (event.target.value) setReportRange((current) => ({ ...current, to: event.target.value })); }} /></label>
      <div className="finance-report-filter-field"><span>Cơ sở</span><BranchFilterSelect branches={branchOptions} value={branchId} onChange={setBranchId} /></div>
      <span className="finance-report-scope-summary">{selectedBranchName}</span>
    </div>}
    <nav className="finance-tabs" aria-label="Nghiệp vụ tài chính">{Object.entries(label).filter(([key]) => availableTabs.includes(key)).map(([key, value]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => selectTab(key)}>{value}</button>)}</nav>
    {error && <p className="finance-state finance-error" role="alert">{error}</p>}
    {loading && <p className="finance-state" role="status">Đang tải dữ liệu…</p>}
    {tab === 'report' && dataTab === 'report' && data && <>
      {!reportsOnly && <section className={`finance-period${periodInfo?.status === 'CLOSED' ? ' is-closed' : Number(periodInfo?.unreconciledClosings) > 0 ? ' is-blocked' : ''}`} aria-labelledby="finance-period-title">
        <div>
          <h2 id="finance-period-title">Tình trạng kỳ {formatMonth(month)}</h2>
          {(!user || periodLoading) && <p role="status">Đang kiểm tra tình trạng chốt kỳ…</p>}
          {periodError && <p>Không tải được tình trạng chốt kỳ. Hãy tải lại dữ liệu.</p>}
          {user && !periodLoading && !periodError && !periodInfo && <p>Chưa có thông tin chốt kỳ cho tháng này.</p>}
          {/* BR-99: chỉ cho chốt kỳ khi mọi phiếu chốt quỹ trong kỳ đã đối soát. */}
          {user && !periodLoading && !periodError && periodInfo && <p>{periodInfo.status === 'CLOSED'
            ? 'Kỳ tài chính đã chốt.'
            : Number(periodInfo.unreconciledClosings) > 0
              ? `Chưa thể chốt kỳ tài chính vì còn ${periodInfo.unreconciledClosings} phiếu chốt quỹ chưa đối soát. Vui lòng hoàn tất đối soát tất cả phiếu trong kỳ.`
              : 'Kỳ chưa chốt · Không còn phiếu chốt quỹ chưa đối soát được ghi nhận.'}</p>}
        </div>
        <button type="button" onClick={() => selectTab('periods')}>Xem kỳ tài chính</button>
      </section>}
      <ImportedSourceReport month={month} range={reportRange} branchId={branchId} reportTotals={data.totals} financialReport={operatingReport} operationalTransactions={sourceTransactions} />
      <RevenueExpenseOverview role={user?.role} month={month} branchId={branchId} from={reportRange.from} to={reportRange.to} compact onReportData={setOperatingReport} />
    </>}
    {!reportsOnly && tab === 'report' && <p className="finance-shortcuts"><span>Nghiệp vụ liên quan</span><Link to="/ke-toan/hoan-coc">Yêu cầu hoàn cọc</Link><Link to="/ke-toan/hoa-don-thue">Hóa đơn thuế</Link></p>}
    {!reportsOnly && tab === 'periods' && !loading && !error && <>
      <section className="finance-period-readiness" aria-labelledby="period-readiness-title">
        <div className="finance-close-head"><div className="finance-close-title"><span>KỲ TÀI CHÍNH</span><h2 id="period-readiness-title">Chốt kỳ {formatMonth(month)}</h2><p>Rà soát điều kiện trước khi khóa sổ.</p></div>
          <div className="finance-close-summary" aria-label="Tình trạng điều kiện chốt kỳ">
            <div><span>Điều kiện đạt</span><strong>{readinessLoading ? '…' : periodReadiness ? `${periodReadiness.checks.filter((item) => item.passed).length}/${periodReadiness.checks.length}` : '—'}</strong></div>
            <div><span>Chưa đạt</span><strong>{readinessLoading ? '…' : periodReadiness ? periodReadiness.checks.filter((item) => !item.passed).length : '—'}</strong></div>
            <span className={`finance-close-badge${periodReadiness?.canClose || periodReadiness?.status === 'CLOSED' ? ' is-ready' : ''}`}>{readinessLoading ? 'Đang kiểm tra' : periodReadiness?.status === 'CLOSED' ? 'Đã khóa' : periodReadiness?.canClose ? 'Sẵn sàng' : 'Cần xử lý'}</span>
            <button type="button" disabled={busy || readinessLoading || !periodReadiness?.canClose} onClick={() => { setCloseNote(''); setClosingMonth(month); }}>{periodReadiness?.status === 'CLOSED' ? 'Đã khóa' : 'Khóa kỳ'}</button>
          </div>
        </div>
        {periodReadiness?.earlyCloseEnabled && <p className="finance-period-early-close-warning" role="status">{EARLY_CLOSE_WARNING}</p>}
        {readinessLoading && <p role="status">Đang kiểm tra điều kiện…</p>}
        {periodReadiness && <>
          <ul className="finance-period-checks">{periodReadiness.checks.map((item) => <li key={item.key} className={item.passed ? 'is-passed' : 'is-pending'}><span className="finance-check-status">{item.passed ? 'Đạt' : 'Cần xử lý'}</span><div className="finance-check-copy"><strong>{item.label}</strong><small>{item.detail}</small></div><span className="finance-check-count">Còn {item.pendingCount}</span></li>)}</ul>
          <p className="finance-period-limitation">Lưu ý: {periodReadiness.cashCalendarLimitation}</p>
        </>}
      </section>
    <section className="finance-period-history" aria-label="Lịch sử chốt kỳ"><div><h2>Lịch sử kỳ tài chính</h2><p>Các kỳ đã khóa và trạng thái đối soát.</p></div><DataTable caption="Lịch sử kỳ tài chính" columns={['Kỳ', 'Trạng thái', 'Phiếu chốt quỹ chưa đối soát', 'Người khóa', 'Thời điểm khóa', 'Ghi chú']} numericColumns={[2]} rows={rows} values={(r) => [formatMonth(String(r.month).slice(0, 7)), r.status === 'CLOSED' ? 'Đã khóa' : 'Đang mở', r.unreconciledClosings, r.closedBy || '—', r.closedAt ? new Date(r.closedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—', r.closeNote || '—']} /></section>
    </>}
    {!reportsOnly && closingMonth && <div className="finance-period-dialog-backdrop" role="presentation"><section className="finance-period-dialog" role="dialog" aria-modal="true" aria-labelledby="closing-period-title"><h2 id="closing-period-title">Xác nhận khóa kỳ {formatMonth(closingMonth)}</h2><p>Thao tác này khóa kỳ vĩnh viễn. Các giao dịch ngày thuộc kỳ sẽ không thể tạo, sửa hoặc xóa.</p>{periodReadiness?.earlyCloseEnabled && <p className="finance-period-early-close-warning">{EARLY_CLOSE_WARNING}</p>}<label>Ghi chú khóa kỳ<textarea value={closeNote} onChange={(e) => setCloseNote(e.target.value)} maxLength={1000} /></label><div className="finance-period-dialog-actions"><button type="button" disabled={busy} onClick={() => setClosingMonth(null)}>Hủy</button><button className="finance-period-dialog-primary" type="button" disabled={busy} onClick={() => closePeriod(closingMonth)}>{busy ? 'Đang khóa…' : 'Xác nhận khóa kỳ'}</button></div></section></div>}
    {!reportsOnly && tab === 'transactions' && !loading && !error && <>
      <section className="accountant-dash-panel accountant-table-panel finance-sepay-panel" aria-labelledby="finance-sepay-title">
        <div className="accountant-panel-heading"><div><h2 id="finance-sepay-title">Giao dịch SePay ngoại lệ</h2><p>Kiểm tra và ghi nhận kết quả xử lý giao dịch ngân hàng chưa khớp.</p></div><label>Trạng thái<select value={exceptionFilter} onChange={(event) => setExceptionFilter(event.target.value)}><option value="UNRESOLVED">Chưa xử lý</option><option value="RESOLVED">Đã xử lý</option><option value="ALL">Tất cả</option></select></label></div>
        {sepayExceptions.filter((row) => exceptionFilter === 'ALL' || row.resolutionStatus === exceptionFilter).length ? <div className="accountant-table-scroll"><table><thead><tr>{['Mã giao dịch', 'Cơ sở', 'Ngày', 'Số tiền', 'Trạng thái', 'Lý do ngoại lệ', 'Nội dung chuyển khoản', 'Người xử lý', 'Thời điểm xử lý', 'Ghi chú xử lý', 'Thao tác'].map((item) => <th key={item}>{item}</th>)}</tr></thead><tbody>{sepayExceptions.filter((row) => exceptionFilter === 'ALL' || row.resolutionStatus === exceptionFilter).map((row) => <tr key={row.id}><td>{row.externalId}</td><td>{row.branchName}</td><td>{formatDate(String(row.occurredAt || '').slice(0, 10))}</td><td>{money(row.amount)}</td><td>{row.resolutionStatus === 'UNRESOLVED' ? 'Chưa xử lý' : 'Đã xử lý'}</td><td>{exceptionReasonLabels[row.exceptionReason] || row.exceptionReason || '—'}</td><td>{row.reason || '—'}</td><td>{row.resolvedBy || '—'}</td><td>{row.resolvedAt ? new Date(row.resolvedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'}</td><td>{row.resolutionNote || '—'}</td><td>{row.resolutionStatus === 'UNRESOLVED' ? <button type="button" onClick={() => { setResolutionTarget(row); setResolutionNote(''); }}>Đánh dấu đã xử lý</button> : 'Đã xử lý'}</td></tr>)}</tbody></table></div> : <p className="accountant-dash-empty">Không có giao dịch ngoại lệ theo bộ lọc.</p>}
      </section>
      {resolutionTarget && <div className="accountant-exception-backdrop" role="presentation"><section className="accountant-exception-dialog" role="dialog" aria-modal="true" aria-labelledby="resolve-sepay-title"><h2 id="resolve-sepay-title">Đánh dấu ngoại lệ đã xử lý</h2><p>Giao dịch {resolutionTarget.externalId} · {money(resolutionTarget.amount)}. Thao tác chỉ ghi nhận trạng thái và ghi chú, không tạo bút toán tiền.</p><label>Ghi chú xử lý<textarea value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} maxLength={1000} required /></label><div><button type="button" disabled={busy} onClick={() => setResolutionTarget(null)}>Hủy</button><button type="button" disabled={busy || !resolutionNote.trim()} onClick={resolveSePayException}>{busy ? 'Đang lưu…' : 'Xác nhận đã xử lý'}</button></div></section></div>}
      <div className="finance-filters">
        <label className="finance-month">Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
        <label className="finance-month">Loại giao dịch<select value={transactionKind} onChange={(e) => setTransactionKind(e.target.value)}><option value="">Tất cả</option>{Object.entries(transactionKindLabels).map(([kind, label]) => <option key={kind} value={kind}>{label}</option>)}</select></label>
        <label className="finance-month">Tìm giao dịch<input value={transactionQuery} onChange={(e) => setTransactionQuery(e.target.value)} placeholder="Lý do, mã giao dịch, cơ sở" /></label>
      </div>
      <DataTable className="finance-transactions-panel" caption={`Giao dịch tháng ${formatMonth(month)}`} columns={['Ngày ghi sổ', 'Kỳ gốc', 'Cơ sở', 'Loại', 'Lý do', 'Tiền gốc', 'Tổng điều chỉnh', 'Sau điều chỉnh', 'Chi tiết điều chỉnh', 'Thao tác']} numericColumns={[5, 6, 7]} rows={transactionRows} values={(r) => [formatDate(r.bookingDate), formatMonth(String(r.periodMonth).slice(0, 7)), r.branchName || 'Toàn chuỗi', transactionKindLabels[r.kind] || 'Khác', r.reason, money(r.originalAmount), money(r.adjustmentAmount), money(r.amount), r.adjustmentCount ? <details><summary>{r.adjustmentCount} lần</summary><ul>{parseAdjustments(r.adjustments).map((item, i) => <li key={`${r.id}-${i}`}><strong>{money(item.amount)}</strong> · {item.reason}<br />Người ghi: {item.createdBy || 'Hệ thống'} · {formatDate(item.createdAt?.slice(0, 10))}<br />Ghi vào kỳ {formatMonth(String(item.periodMonth).slice(0, 7))}{r.periodStatus === 'CLOSED' ? ` · Kỳ gốc ${formatMonth(String(r.periodMonth).slice(0, 7))} đã khóa` : ''}{item.attachments?.map((file) => <div key={file.id}><button type="button" disabled={busy} onClick={() => downloadEvidence(file)}>Chứng từ: {file.name}</button></div>)}</li>)}</ul></details> : '—', r.kind !== 'TRANSFER' && r.kind !== 'OPENING' && r.kind !== 'ADJUSTMENT' && Number(r.entryCount) === 1 ? <button type="button" aria-expanded={adjusting?.id === r.id} onClick={() => { setAdjusting(r); setAmount(''); setReason(''); setEvidence(null); setAdjustmentConfirmed(false); }}>Điều chỉnh</button> : '—']} />
      {rows.length > 0 && <nav className="finance-transaction-pagination" aria-label="Phân trang giao dịch">
        <span className="finance-transaction-pagination-status" aria-live="polite">
          Hiển thị {(currentTransactionPage - 1) * TRANSACTIONS_PER_PAGE + 1}–{Math.min(currentTransactionPage * TRANSACTIONS_PER_PAGE, rows.length)} / {rows.length} giao dịch · Trang {currentTransactionPage}/{transactionPageCount}
        </span>
        <div className="finance-transaction-pagination-actions">
          <button type="button" disabled={currentTransactionPage === 1} onClick={() => setTransactionPage(currentTransactionPage - 1)}>Trang trước</button>
          <button type="button" disabled={currentTransactionPage === transactionPageCount} onClick={() => setTransactionPage(currentTransactionPage + 1)}>Trang sau</button>
        </div>
      </nav>}
      {adjusting && <form ref={adjustmentFormRef} className="finance-adjust-form" onSubmit={submitAdjustment}><h2>Điều chỉnh giao dịch gốc</h2><p>{adjusting.reason} · {formatDate(adjusting.bookingDate)} · kỳ gốc {formatMonth(String(adjusting.periodMonth).slice(0, 7))}{adjusting.periodStatus === 'CLOSED' ? ' (đã khóa)' : ''}</p><p>Giá trị hiện tại sau các điều chỉnh: {money(adjusting.amount)}. Số dương ghi tăng quỹ, số âm ghi giảm quỹ. Tổng sau điều chỉnh được về 0 nhưng không đổi chiều thu/chi.</p><label>Số tiền điều chỉnh (VND)<MoneyInput name="adjustmentAmount" allowNegative   value={amount} onValueChange={value => setAmount(value)} required /></label><label>Lý do<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required /></label><label>Chứng từ minh chứng (không bắt buộc)<input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf" onChange={(e) => setEvidence(e.target.files?.[0] || null)} /></label><p>Sau điều chỉnh: {amount ? money(Number(adjusting.amount) + Number(amount)) : money(adjusting.amount)} · {reason || 'Chưa có lý do'}</p><label className="finance-confirm"><input type="checkbox" checked={adjustmentConfirmed} onChange={(e) => setAdjustmentConfirmed(e.target.checked)} /> Tôi xác nhận ghi điều chỉnh riêng cho giao dịch gốc.</label><button type="submit" disabled={busy || !adjustmentConfirmed}>Xác nhận ghi điều chỉnh</button><button type="button" disabled={busy} onClick={() => setAdjusting(null)}>Hủy</button></form>}
    </>}
    {!reportsOnly && tab === 'export' && <><RevenueSummary totals={revenueSummary} /><section className="finance-export"><h2>Xuất dữ liệu kế toán</h2><p>Tệp CSV gồm các dòng sổ tiền và một dòng tổng hợp doanh thu vận hành được đánh dấu riêng. Việc xuất không thay đổi giao dịch.</p><label>Phạm vi dữ liệu<select value={scope} onChange={(e) => setScope(e.target.value)}><option value="ALL">Tất cả dòng tiền</option><option value="INFLOW">Tiền vào</option><option value="OUTFLOW">Tiền ra</option></select></label><button type="button" disabled={busy} onClick={download}>{busy ? 'Đang tạo tệp…' : 'Tải tệp CSV'}</button></section></>}
  </section>;
  return reportsOnly ? content : <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout}
    breadcrumb="Báo cáo chi tiết" activeTab="report" audience="accountant" hideSearch showCurrentMonth={false}>{content}</AppShell>;
}

function DataTable({ caption, columns = [], numericColumns = [], rows = [], values, className = '' }) {
  return <div className={`finance-table-panel${className ? ` ${className}` : ''}`}><div className="finance-table-wrap" role={className ? 'region' : undefined} aria-label={className ? caption : undefined} tabIndex={className ? 0 : undefined}><table className="finance-table"><caption>{caption}</caption><thead><tr>{columns.map((column, cell) => <th scope="col" className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id || `${index}-${row.categoryName || row.month || ''}`}>{values(row).map((value, cell) => <td data-label={columns[cell]} className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={cell}>{value}</td>)}</tr>)}</tbody></table></div>{rows.length === 0 && <p className="finance-empty">Chưa có dữ liệu trong phạm vi đã chọn.</p>}</div>;
}

function RevenueSummary({ totals }) {
  if (!totals) return null;
  return <dl className="finance-metrics">
    <div><dt>Doanh thu hóa đơn hoàn tất</dt><dd>{money(totals.billRevenue)}</dd><small>Giá trị sau giảm trừ</small></div>
    <div><dt>Thu từ cọc bị tịch thu</dt><dd>{money(totals.forfeitedDeposits)}</dd><small>Ghi nhận theo ngày phát sinh</small></div>
    <div><dt>Thu nhập vận hành</dt><dd>{money(totals.revenue)}</dd><small>Hóa đơn hoàn tất + cọc bị tịch thu</small></div>
  </dl>;
}
