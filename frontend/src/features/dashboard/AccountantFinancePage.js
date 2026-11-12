import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import './dashboard.css';
import MonthlyPerformanceSummary from './MonthlyPerformanceSummary';

const now = new Date();
const monthNow = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;
const formatMonth = (value) => `${value.slice(5, 7)}/${value.slice(0, 4)}`;
const formatDate = (value) => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : '—';
const label = { report: 'Tổng quan', periods: 'Chốt kỳ', transactions: 'Giao dịch', export: 'Xuất dữ liệu' };
const periodGuidance = {
  periodEnded: {
    owner: 'Kế toán; chỉ thực hiện được sau khi tháng kết thúc.',
    action: 'Đợi sang tháng tiếp theo theo giờ Việt Nam, rồi tải lại điều kiện.',
  },
  cashReconciliation: {
    owner: 'Quản lý cơ sở gửi phiếu còn thiếu; Kế toán đối soát phiếu đã gửi.',
    action: 'Kiểm tra các phiếu chốt quỹ trong tháng. Chỉ số này gồm phiếu chưa đối soát và ngày/quỹ có dòng tiền nhưng chưa có phiếu.',
    to: '/ke-toan/chot-quy',
    link: 'Mở Đối soát chốt quỹ',
  },
  attendance: {
    owner: 'Quản lý cơ sở tại từng cơ sở đang hoạt động.',
    action: 'Nhờ quản lý cơ sở chốt công tháng cho các cơ sở còn thiếu, rồi tải lại điều kiện.',
  },
  payroll: {
    owner: 'Kế toán.',
    action: 'Xác nhận bảng lương của tháng, rồi tải lại điều kiện.',
    to: '/ke-toan/luong',
    link: 'Mở Lương nhân viên',
  },
  expenses: {
    owner: 'Kế toán; Quản lý cấp cao duyệt trước các đề nghị trên 3 triệu đồng.',
    action: 'Xử lý đề nghị chi hoặc hoàn ứng còn chờ; khoản đã được duyệt cần được ghi nhận thanh toán.',
    to: '/ke-toan/chi-phi',
    link: 'Mở Chi & hoàn ứng',
  },
  refunds: {
    owner: 'Kế toán.',
    action: 'Xử lý các yêu cầu hoàn cọc còn mới gửi, đã duyệt hoặc đang xử lý.',
    to: '/ke-toan/hoan-coc',
    link: 'Mở Yêu cầu hoàn cọc',
  },
  sepayExceptions: {
    owner: 'Kế toán.',
    action: 'Kiểm tra và xử lý các giao dịch SePay chưa khớp trước khi khóa kỳ.',
    to: '/ke-toan',
    link: 'Mở Tổng quan kế toán',
  },
};
const transactionKindLabels = { PAYMENT: 'Thu thanh toán', REFUND: 'Hoàn cọc', EXPENSE: 'Chi phí', SALARY: 'Trả lương', ADVANCE: 'Ứng lương', TRANSFER: 'Chuyển quỹ', OPENING: 'Số dư đầu kỳ', ADJUSTMENT: 'Điều chỉnh' };
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.';
const parseAdjustments = (value) => { try { return JSON.parse(value || '[]'); } catch { return []; } };

export default function AccountantFinancePage({ reportsOnly = false }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const availableTabs = reportsOnly ? ['report'] : Object.keys(label);
  const [tab, setTab] = useState(() => availableTabs.includes(searchParams.get('tab')) ? searchParams.get('tab') : 'report');
  const [month, setMonth] = useState(monthNow);
  const [data, setData] = useState(null);
  const [revenueSummary, setRevenueSummary] = useState(null);
  const [dataTab, setDataTab] = useState(null);
  const [branchOptions, setBranchOptions] = useState([]);
  const [branchId, setBranchId] = useState('');
  const [rows, setRows] = useState([]);
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
  const [loadedAt, setLoadedAt] = useState(null);
  const [periodInfo, setPeriodInfo] = useState(null);
  const [periodLoading, setPeriodLoading] = useState(false);
  const [periodError, setPeriodError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [blockedPeriod, setBlockedPeriod] = useState(null);
  const [periodReadiness, setPeriodReadiness] = useState(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [periodGuidanceItem, setPeriodGuidanceItem] = useState(null);
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
      adjustmentFormRef.current?.querySelector('input[type="number"]')?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [adjusting]);

  useEffect(() => {
    authApi.me().then((identity) => { if (identity.role !== 'ACCOUNTANT' && !(reportsOnly && identity.role === 'HEAD_OFFICE_MANAGER')) navigate(identity.destination, { replace: true }); else setUser(identity); })
      .catch(() => navigate('/dang-nhap', { replace: true }));
  }, [navigate, reportsOnly]);

  useEffect(() => {
    let live = true;
    setError(''); setData(null); setDataTab(null); setRows([]); setLoadedAt(null); setLoading(true);
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
      setLoadedAt(new Date());
      if (tab === 'report' && !branchId) setBranchOptions(report.results);
    })
      .catch((e) => { if (live) setError(errorText(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [tab, month, branchId, revision, transactionKind, transactionQuery]);

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

  useEffect(() => {
    if (!blockedPeriod) return undefined;
    const dismiss = (event) => event.key === 'Escape' && setBlockedPeriod(null);
    window.addEventListener('keydown', dismiss);
    return () => window.removeEventListener('keydown', dismiss);
  }, [blockedPeriod]);

  useEffect(() => {
    if (!periodGuidanceItem) return undefined;
    const dismiss = (event) => event.key === 'Escape' && setPeriodGuidanceItem(null);
    window.addEventListener('keydown', dismiss);
    return () => window.removeEventListener('keydown', dismiss);
  }, [periodGuidanceItem]);

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

  const content = <section className="performance-dashboard accountant-finance" aria-labelledby="finance-title">
    <header className="finance-heading">
      <div><h1 id="finance-title">Tài chính &amp; báo cáo</h1></div>
      {<div className="finance-filters">
        <label className="finance-month">Kỳ báo cáo<input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} /></label>
      {tab === 'report' && <label className="finance-month">Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>}
      </div>}
    </header>
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
      <div className="finance-section-heading">
        <div><h2>Kết quả tháng {formatMonth(month)}</h2><p>{branchId ? 'Cơ sở đang chọn' : `Toàn bộ ${data.results.length} cơ sở`} · Đơn vị: đồng (VND)</p></div>
        <div className="finance-freshness"><span>{loadedAt ? `Tải trên màn hình lúc ${loadedAt.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })}` : ''}</span><Link to={reportsOnly ? { pathname: '/quan-ly-he-thong', search: `?${new URLSearchParams({ screen: 'Báo cáo chi phí' })}` } : '/ke-toan/bao-cao-chi-phi'}>Báo cáo chi phí</Link><button type="button" onClick={() => setRevision((value) => value + 1)}>Tải lại</button></div>
      </div>
      {/* BR-103/107, BR-104, BR-106: cọc tịch thu được trình bày riêng trong Tổng doanh thu; dòng tiền thuần không phải số dư quỹ. */}
      <dl className="finance-metrics">
        <div><dt>Doanh thu từ hóa đơn</dt><dd>{money(data.totals.billRevenue)}</dd><small>Hóa đơn đã thanh toán</small></div>
        <div><dt>Thu từ cọc bị tịch thu</dt><dd>{money(data.totals.forfeitedDeposits)}</dd><small>Ghi nhận theo ngày ghi nhận tại múi giờ của cơ sở</small></div>
        <div><dt>Tổng doanh thu</dt><dd>{money(data.totals.revenue)}</dd><small>Doanh thu hóa đơn đã thanh toán và cọc bị tịch thu</small></div>
        <div><dt>Chi vận hành đã trả</dt><dd>{money(data.totals.expenses)}</dd><small>Gồm lương và ứng lương; không gồm đầu tư</small></div>
        <div><dt>Lợi nhuận vận hành</dt><dd className={Number(data.totals.profit) < 0 ? 'finance-negative' : ''}>{money(data.totals.profit)}</dd><small>Tổng doanh thu − chi vận hành đã trả</small></div>
        <div><dt>Biên lợi nhuận vận hành</dt><dd>{percent(data.totals.margin)}</dd><small>Lợi nhuận vận hành ÷ Tổng doanh thu</small></div>
        <div><dt>Dòng tiền thuần</dt><dd className={Number(data.totals.netCashFlow) < 0 ? 'finance-negative' : ''}>{money(data.totals.netCashFlow)}</dd><small>Tiền vào trừ tiền ra theo sổ; không phải số dư quỹ</small></div>
      </dl>
      <MonthlyPerformanceSummary report={data} month={month} branchId={branchId} />
      <DataTable caption={`Kết quả của ${data.results.length} cơ sở trong tháng ${formatMonth(month)}`} columns={['Cơ sở', 'Hóa đơn đã thanh toán', 'Doanh thu từ hóa đơn', 'Thu từ cọc bị tịch thu', 'Tổng doanh thu', 'Chi vận hành', 'Trong đó · lương/ứng', 'Đầu tư', 'Lợi nhuận vận hành', 'Biên lợi nhuận', 'Dòng tiền thuần']} numericColumns={[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]} rows={data.results} values={(r) => [r.name, r.billCount, money(r.billRevenue), money(r.forfeitedDeposits), money(r.revenue), money(r.expenses), money(r.salary), money(r.investment), money(r.profit), percent(r.margin), money(r.netCashFlow)]} />
    </>}
    {!reportsOnly && tab === 'report' && <p className="finance-shortcuts"><span>Nghiệp vụ liên quan</span><Link to="/ke-toan/hoan-coc">Yêu cầu hoàn cọc</Link><Link to="/ke-toan/hoa-don-thue">Hóa đơn thuế</Link></p>}
    {!reportsOnly && tab === 'periods' && !loading && !error && <>
      <section className="finance-period-readiness" aria-labelledby="period-readiness-title">
        <div className="finance-section-heading"><div><h2 id="period-readiness-title">Điều kiện khóa kỳ {formatMonth(month)}</h2><p>Kiểm tra lại trên máy chủ khi xác nhận khóa.</p></div>
          <button type="button" disabled={busy || readinessLoading || !periodReadiness?.canClose} onClick={() => { setCloseNote(''); setClosingMonth(month); }}>Khóa kỳ</button></div>
        {readinessLoading && <p role="status">Đang kiểm tra điều kiện…</p>}
        {periodReadiness && <>
          <ul className="finance-period-checks">{periodReadiness.checks.map((item) => {
            const guidance = periodGuidance[item.key];
            return <li key={item.key} className={item.passed ? 'is-passed' : 'is-pending'}><span>{item.passed ? 'Đạt' : 'Chưa đạt'}</span><strong>{item.label}</strong><span>{item.pendingCount} tồn</span><small>{item.detail}</small>
              {!item.passed && guidance && <button className="finance-period-guidance-trigger" type="button" onClick={() => setPeriodGuidanceItem(item)}>Xem hướng dẫn xử lý</button>}
            </li>;
          })}</ul>
          <p className="finance-period-limitation">Lưu ý: {periodReadiness.cashCalendarLimitation}</p>
        </>}
      </section>
      {!reportsOnly && periodGuidanceItem && periodGuidance[periodGuidanceItem.key] && <div className="finance-period-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setPeriodGuidanceItem(null); }}><section className="finance-period-dialog finance-period-guidance-dialog" role="dialog" aria-modal="true" aria-labelledby="period-guidance-title"><h2 id="period-guidance-title">Hướng dẫn xử lý: {periodGuidanceItem.label}</h2><p><strong>Còn:</strong> {periodGuidanceItem.pendingCount} mục. {periodGuidanceItem.detail}</p><p><strong>Cần làm:</strong> {periodGuidance[periodGuidanceItem.key].action}</p><p><strong>Vai trò phụ trách:</strong> {periodGuidance[periodGuidanceItem.key].owner}</p><div className="finance-period-dialog-actions">{periodGuidance[periodGuidanceItem.key].to && <Link className="finance-period-dialog-primary" to={periodGuidance[periodGuidanceItem.key].to}>{periodGuidance[periodGuidanceItem.key].link}</Link>}<button type="button" autoFocus onClick={() => setPeriodGuidanceItem(null)}>Đóng</button></div></section></div>}
    <DataTable caption="Lịch sử kỳ tài chính" columns={['Kỳ', 'Trạng thái', 'Phiếu chốt quỹ chưa đối soát', 'Người khóa', 'Thời điểm khóa', 'Ghi chú']} numericColumns={[2]} rows={rows} values={(r) => [formatMonth(String(r.month).slice(0, 7)), r.status === 'CLOSED' ? 'Đã khóa' : 'Đang mở', r.unreconciledClosings, r.closedBy || '—', r.closedAt ? new Date(r.closedAt).toLocaleString('vi-VN') : '—', r.closeNote || '—']} />
    </>}
    {!reportsOnly && closingMonth && <div className="finance-period-dialog-backdrop" role="presentation"><section className="finance-period-dialog" role="dialog" aria-modal="true" aria-labelledby="closing-period-title"><h2 id="closing-period-title">Xác nhận khóa kỳ {formatMonth(closingMonth)}</h2><p>Thao tác này khóa kỳ vĩnh viễn. Các giao dịch ngày thuộc kỳ sẽ không thể tạo, sửa hoặc xóa.</p><label>Ghi chú khóa kỳ<textarea value={closeNote} onChange={(e) => setCloseNote(e.target.value)} maxLength={1000} /></label><div className="finance-period-dialog-actions"><button type="button" disabled={busy} onClick={() => setClosingMonth(null)}>Hủy</button><button className="finance-period-dialog-primary" type="button" disabled={busy} onClick={() => closePeriod(closingMonth)}>{busy ? 'Đang khóa…' : 'Xác nhận khóa kỳ'}</button></div></section></div>}
    {!reportsOnly && blockedPeriod && <div className="finance-period-dialog-backdrop" role="presentation"><section className="finance-period-dialog" role="dialog" aria-modal="true" aria-labelledby="blocked-period-title"><h2 id="blocked-period-title">Chưa thể chốt kỳ {formatMonth(String(blockedPeriod.month).slice(0, 7))}</h2><p>Kỳ này còn {blockedPeriod.unreconciledClosings} phiếu chốt quỹ chưa đối soát. Hãy hoàn tất đối soát các phiếu trước, sau đó quay lại chốt kỳ.</p><div className="finance-period-dialog-actions"><button type="button" autoFocus onClick={() => setBlockedPeriod(null)}>Đóng</button><Link className="finance-period-dialog-primary" to="/ke-toan/chot-quy">Đi tới đối soát quỹ</Link></div></section></div>}
    {!reportsOnly && tab === 'transactions' && !loading && !error && <>
      <div className="finance-filters">
        <label className="finance-month">Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
        <label className="finance-month">Loại giao dịch<select value={transactionKind} onChange={(e) => setTransactionKind(e.target.value)}><option value="">Tất cả</option>{Object.entries(transactionKindLabels).map(([kind, label]) => <option key={kind} value={kind}>{label}</option>)}</select></label>
        <label className="finance-month">Tìm giao dịch<input value={transactionQuery} onChange={(e) => setTransactionQuery(e.target.value)} placeholder="Lý do, mã giao dịch, cơ sở" /></label>
      </div>
      <DataTable caption={`Giao dịch tháng ${formatMonth(month)}`} columns={['Ngày ghi sổ', 'Kỳ gốc', 'Cơ sở', 'Loại', 'Lý do', 'Tiền gốc', 'Tổng điều chỉnh', 'Sau điều chỉnh', 'Chi tiết điều chỉnh', 'Thao tác']} numericColumns={[5, 6, 7]} rows={rows} values={(r) => [formatDate(r.bookingDate), formatMonth(String(r.periodMonth).slice(0, 7)), r.branchName || 'Toàn chuỗi', transactionKindLabels[r.kind] || 'Khác', r.reason, money(r.originalAmount), money(r.adjustmentAmount), money(r.amount), r.adjustmentCount ? <details><summary>{r.adjustmentCount} lần</summary><ul>{parseAdjustments(r.adjustments).map((item, i) => <li key={`${r.id}-${i}`}><strong>{money(item.amount)}</strong> · {item.reason}<br />Người ghi: {item.createdBy || 'Hệ thống'} · {formatDate(item.createdAt?.slice(0, 10))}<br />Ghi vào kỳ {formatMonth(String(item.periodMonth).slice(0, 7))}{r.periodStatus === 'CLOSED' ? ` · Kỳ gốc ${formatMonth(String(r.periodMonth).slice(0, 7))} đã khóa` : ''}{item.attachments?.map((file) => <div key={file.id}><button type="button" disabled={busy} onClick={() => downloadEvidence(file)}>Chứng từ: {file.name}</button></div>)}</li>)}</ul></details> : '—', r.kind !== 'TRANSFER' && r.kind !== 'OPENING' && r.kind !== 'ADJUSTMENT' && Number(r.entryCount) === 1 ? <button type="button" aria-expanded={adjusting?.id === r.id} onClick={() => { setAdjusting(r); setAmount(''); setReason(''); setEvidence(null); setAdjustmentConfirmed(false); }}>Điều chỉnh</button> : '—']} />
      {adjusting && <form ref={adjustmentFormRef} className="finance-adjust-form" onSubmit={submitAdjustment}><h2>Điều chỉnh giao dịch gốc</h2><p>{adjusting.reason} · {formatDate(adjusting.bookingDate)} · kỳ gốc {formatMonth(String(adjusting.periodMonth).slice(0, 7))}{adjusting.periodStatus === 'CLOSED' ? ' (đã khóa)' : ''}</p><p>Giá trị hiện tại sau các điều chỉnh: {money(adjusting.amount)}. Số dương ghi tăng quỹ, số âm ghi giảm quỹ. Tổng sau điều chỉnh được về 0 nhưng không đổi chiều thu/chi.</p><label>Số tiền điều chỉnh (VND)<input type="number" step="1" value={amount} onChange={(e) => setAmount(e.target.value)} required /></label><label>Lý do<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required /></label><label>Chứng từ minh chứng (không bắt buộc)<input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf" onChange={(e) => setEvidence(e.target.files?.[0] || null)} /></label><p>Sau điều chỉnh: {amount ? money(Number(adjusting.amount) + Number(amount)) : money(adjusting.amount)} · {reason || 'Chưa có lý do'}</p><label className="finance-confirm"><input type="checkbox" checked={adjustmentConfirmed} onChange={(e) => setAdjustmentConfirmed(e.target.checked)} /> Tôi xác nhận ghi điều chỉnh riêng cho giao dịch gốc.</label><button type="submit" disabled={busy || !adjustmentConfirmed}>Xác nhận ghi điều chỉnh</button><button type="button" disabled={busy} onClick={() => setAdjusting(null)}>Hủy</button></form>}
    </>}
    {!reportsOnly && tab === 'export' && <><RevenueSummary totals={revenueSummary} /><section className="finance-export"><h2>Xuất dữ liệu kế toán</h2><p>Tệp CSV gồm các dòng sổ tiền và một dòng tổng hợp doanh thu vận hành được đánh dấu riêng. Việc xuất không thay đổi giao dịch.</p><label>Phạm vi dữ liệu<select value={scope} onChange={(e) => setScope(e.target.value)}><option value="ALL">Tất cả dòng tiền</option><option value="INFLOW">Tiền vào</option><option value="OUTFLOW">Tiền ra</option></select></label><button type="button" disabled={busy} onClick={download}>{busy ? 'Đang tạo tệp…' : 'Tải tệp CSV'}</button></section></>}
  </section>;
  return reportsOnly ? content : <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout}
    breadcrumb="Báo cáo chi tiết" activeTab="report" audience="accountant" hideSearch showCurrentMonth={false}>{content}</AppShell>;
}

function DataTable({ caption, columns = [], numericColumns = [], rows = [], values }) {
  return <div className="finance-table-panel"><div className="finance-table-wrap"><table className="finance-table"><caption>{caption}</caption><thead><tr>{columns.map((column, cell) => <th scope="col" className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id || `${index}-${row.categoryName || row.month || ''}`}>{values(row).map((value, cell) => <td className={numericColumns.includes(cell) ? 'is-numeric' : undefined} key={cell}>{value}</td>)}</tr>)}</tbody></table></div>{rows.length === 0 && <p className="finance-empty">Chưa có dữ liệu trong phạm vi đã chọn.</p>}</div>;
}

function RevenueSummary({ totals }) {
  if (!totals) return null;
  return <dl className="finance-metrics">
    <div><dt>Doanh thu từ hóa đơn</dt><dd>{money(totals.billRevenue)}</dd><small>Hóa đơn đã thanh toán</small></div>
    <div><dt>Thu từ cọc bị tịch thu</dt><dd>{money(totals.forfeitedDeposits)}</dd><small>Ngày phát sinh theo múi giờ cơ sở</small></div>
    <div><dt>Tổng doanh thu</dt><dd>{money(totals.revenue)}</dd><small>Hóa đơn đã thanh toán và cọc bị tịch thu</small></div>
  </dl>;
}
