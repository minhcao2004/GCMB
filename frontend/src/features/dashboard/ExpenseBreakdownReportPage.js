import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import BranchFilterSelect from './BranchFilterSelect';
import './dashboard.css';
import './expense-breakdown-report.css';

const monthNow = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date());
const noResults = 'Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.';
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const validMonth = (value) => /^\d{4}-(0[1-9]|1[0-2])$/.test(value || '');
const validDate = (value) => /^\d{4}-\d{2}-\d{2}$/.test(value || '') && !Number.isNaN(Date.parse(`${value}T00:00:00Z`));
const todayVn = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const monthEnd = (month) => new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).toISOString().slice(0, 10);
const dateTime = (value) => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)} ${value.slice(11, 16)}` : '—';
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || (error.request
  ? 'Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại.'
  : 'Không thể tải dữ liệu. Vui lòng thử lại.');
const expenseReportPath = `/quan-ly-he-thong?${new URLSearchParams({ screen: 'Báo cáo chi phí' })}`;

export default function ExpenseBreakdownReportPage({ initialUser = null }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [user, setUser] = useState(initialUser);
  const [data, setData] = useState(null);
  const [branchOptions, setBranchOptions] = useState([]);
  const [dataKey, setDataKey] = useState('');
  const [details, setDetails] = useState([]);
  const [expanded, setExpanded] = useState(null);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState('');
  const [detailsError, setDetailsError] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [evidenceBusy, setEvidenceBusy] = useState(false);
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [revision, setRevision] = useState(0);

  const month = validMonth(searchParams.get('month')) ? searchParams.get('month') : monthNow;
  const from = validDate(searchParams.get('from')) ? searchParams.get('from') : '';
  const to = validDate(searchParams.get('to')) ? searchParams.get('to') : '';
  const hasDateRange = Boolean(from && to);
  const rangeError = hasDateRange && from > to ? 'Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.' // BR-163 / MSG106.
    : hasDateRange && (Date.parse(to) - Date.parse(from)) / 86400000 >= 366 ? 'Khoảng ngày báo cáo tối đa là 366 ngày.' : '';
  const branchIds = useMemo(() => searchParams.getAll('branchIds').filter(Boolean), [searchParams]);
  const branchId = searchParams.get('branchId') || '';
  const costType = searchParams.get('costType') || '';
  const categoryId = searchParams.get('categoryId') || '';
  const basis = searchParams.get('basis') || 'ALLOCATION';
  const query = searchParams.get('q') || '';
  const requestKey = JSON.stringify([month, from, to, branchIds, branchId, costType, categoryId, basis, query]);
  const currentData = dataKey === requestKey ? data : null;
  const scopeFrom = hasDateRange ? from : `${month}-01`;
  const scopeTo = hasDateRange ? to : monthEnd(month) > todayVn() ? todayVn() : monthEnd(month);
  const backQuery = new URLSearchParams();
  backQuery.set('from', scopeFrom); backQuery.set('to', scopeTo);
  branchIds.forEach((id) => backQuery.append('branchIds', id));
  if (branchId) backQuery.append('branchIds', branchId);
  if (searchParams.has('granularity')) backQuery.set('granularity', searchParams.get('granularity'));
  if (searchParams.has('compare')) backQuery.set('compare', searchParams.get('compare'));
  backQuery.set('basis', basis);

  function updateFilter(key, value) {
    const next = new URLSearchParams(searchParams);
    if (key === 'month') { next.delete('from'); next.delete('to'); }
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('tab');
    setData(null); setDataKey('');
    setSearchParams(next, { replace: true });
  }

  function updateBranches(selected) {
    const next = new URLSearchParams(searchParams);
    next.delete('branchId'); next.delete('branchIds'); next.delete('tab');
    selected.forEach((id) => next.append('branchIds', id));
    setData(null); setDataKey(''); setSearchParams(next, { replace: true });
  }

  function updateDateRange(key, value) {
    if (!value) return;
    const next = new URLSearchParams(searchParams);
    next.delete('month');
    if (!hasDateRange) {
      next.set('from', `${month}-01`);
      next.set('to', monthEnd(month) > todayVn() ? todayVn() : monthEnd(month));
    }
    next.set(key, value);
    next.delete('tab');
    setData(null); setDataKey(''); setSearchParams(next, { replace: true });
  }

  function choosePreset(period) {
    const current = todayVn();
    const day = new Date(`${current}T00:00:00Z`);
    let start = current;
    if (period === 'week') day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7);
    if (period === 'week') start = day.toISOString().slice(0, 10);
    if (period === 'month') start = `${current.slice(0, 7)}-01`;
    if (period === 'quarter') start = `${current.slice(0, 4)}-${String(Math.floor(day.getUTCMonth() / 3) * 3 + 1).padStart(2, '0')}-01`;
    if (period === 'year') start = `${current.slice(0, 4)}-01-01`;
    const next = new URLSearchParams(searchParams);
    next.delete('month'); next.set('from', start); next.set('to', current);
    setData(null); setDataKey(''); setSearchParams(next, { replace: true });
  }

  useEffect(() => {
    if (initialUser) {
      if (['ACCOUNTANT', 'HEAD_OFFICE_MANAGER'].includes(initialUser.role)) setUser(initialUser);
      else navigate(initialUser.destination || '/dang-nhap', { replace: true });
      return undefined;
    }
    let active = true;
    authApi.me().then((identity) => {
      if (!active) return;
      if (identity.role === 'HEAD_OFFICE_MANAGER') navigate(expenseReportPath, { replace: true });
      else if (identity.role === 'ACCOUNTANT') setUser(identity);
      else navigate(identity.destination || '/dang-nhap', { replace: true });
    }).catch(() => { if (active) navigate('/dang-nhap', { replace: true }); });
    return () => { active = false; };
  }, [initialUser, navigate]);

  useEffect(() => {
    if (!user) return undefined;
    if (rangeError) { setLoading(false); setData(null); setDataKey(''); return undefined; }
    let active = true;
    setLoading(true); setError(''); setData(null); setDataKey('');
    api.get('/accounting/reports/expense-breakdown', { paramsSerializer: { indexes: null }, params: {
      ...(hasDateRange ? { from, to } : { month }),
      ...(branchIds.length ? { branchIds } : branchId ? { branchId } : {}),
      ...(costType ? { costType } : {}),
      ...(categoryId ? { categoryId } : {}),
      basis,
      ...(query ? { q: query } : {}),
    } }).then(({ data: result }) => {
      if (active) {
        setBranchOptions(result.branches || []);
        setData({ ...result, branches: result.branches || [], categories: result.categories || [], rows: result.rows || [] });
        setDataKey(requestKey);
      }
    }).catch((e) => { if (active) setError(errorText(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, month, from, to, hasDateRange, branchIds, branchId, costType, categoryId, basis, query, requestKey, revision, rangeError]);

  useEffect(() => {
    setExpanded(null); setDetails([]); setDetailsError('');
  }, [month, from, to, branchIds, branchId, costType, categoryId, basis, query]);

  useEffect(() => {
    if (!expanded || rangeError) return undefined;
    let active = true;
    setDetailsLoading(true); setDetailsError(''); setDetails([]);
    api.get('/accounting/reports/expense-breakdown/details', { params: {
      ...(hasDateRange ? { from, to } : { month }),
      branchId: expanded.branchId, categoryId: expanded.categoryId, costType: expanded.costType,
      basis,
      ...(query ? { q: query } : {}),
    } }).then(({ data: result }) => { if (active) setDetails(result.rows || []); })
      .catch((e) => { if (active) setDetailsError(errorText(e)); })
      .finally(() => { if (active) setDetailsLoading(false); });
    return () => { active = false; };
  }, [expanded, month, from, to, hasDateRange, basis, query, rangeError]);

  useEffect(() => {
    if (!preview) return undefined;
    const dismiss = (event) => event.key === 'Escape' && setPreview(null);
    window.addEventListener('keydown', dismiss);
    return () => {
      window.removeEventListener('keydown', dismiss);
      URL.revokeObjectURL(preview.url);
    };
  }, [preview]);

  const totals = useMemo(() => (currentData?.rows || []).reduce((sum, row) => {
    const amount = Number(row.amount) || 0;
    const count = Number(row.paymentCount) || 0;
    if (row.costType === 'CAPEX') { sum.capex += amount; sum.capexCount += count; }
    else { sum.opex += amount; sum.opexCount += count; }
    return sum;
  }, { opex: 0, opexCount: 0, capex: 0, capexCount: 0 }), [currentData]);

  async function viewEvidence(file) {
    setEvidenceBusy(true); setError('');
    try {
      const { data: blob } = await api.get(`/finance/attachments/${file.id}`, { responseType: 'blob' });
      setPreview({ name: file.name, url: URL.createObjectURL(blob), type: blob.type });
    } catch (e) { setError(errorText(e)); }
    finally { setEvidenceBusy(false); }
  }

  async function logout() {
    setLogoutBusy(true);
    try { await authApi.logout(); navigate('/dang-nhap', { replace: true }); }
    catch (e) { setError(errorText(e)); }
    finally { setLogoutBusy(false); }
  }

  if (!user) return <main className="booking-auth-state"><p>Đang kiểm tra phiên đăng nhập…</p></main>;

  // BR-103/107: tổng OPEX và CAPEX luôn được tính, hiển thị riêng theo loại đã chốt.
  const rows = currentData?.rows || [];
  const recognitionMonth = (value) => {
    if (Array.isArray(value) && value.length >= 2) return `${String(value[1]).padStart(2, '0')}/${value[0]}`;
    const match = String(value || '').match(/^(\d{4})-(\d{2})/);
    return match ? `${match[2]}/${match[1]}` : '—';
  };
  const allocationBasis = basis !== 'CASH';
  const selectedIds = branchIds.length ? branchIds : branchId ? [branchId] : [];
  const branchScope = selectedIds.length === 0 ? 'toàn chuỗi' : selectedIds.length === 1
    ? branchOptions.find((item) => String(item.id) === String(selectedIds[0]))?.name || '1 cơ sở'
    : `${selectedIds.length} cơ sở`;
  const periodScope = `${scopeFrom.split('-').reverse().join('/')} – ${scopeTo.split('-').reverse().join('/')}`;
  const current = todayVn();
  const currentDay = new Date(`${current}T00:00:00Z`);
  const monday = new Date(currentDay); monday.setUTCDate(monday.getUTCDate() - (monday.getUTCDay() + 6) % 7);
  const selectedPreset = scopeTo === current ? [
    ['day', current], ['week', monday.toISOString().slice(0, 10)], ['month', `${current.slice(0, 7)}-01`],
    ['quarter', `${current.slice(0, 4)}-${String(Math.floor(currentDay.getUTCMonth() / 3) * 3 + 1).padStart(2, '0')}-01`],
    ['year', `${current.slice(0, 4)}-01-01`],
  ].find(([, start]) => start === scopeFrom)?.[0] || '' : '';
  const headOfficeBackQuery = new URLSearchParams(backQuery);
  headOfficeBackQuery.set('screen', 'Báo cáo kinh doanh');
  const returnToExecutive = user.role === 'HEAD_OFFICE_MANAGER' && searchParams.get('returnTo') === 'executive';
  // Executive chỉ hỗ trợ một cơ sở hoặc toàn chuỗi: nhiều cơ sở quay về phạm vi toàn chuỗi.
  const executiveBackQuery = new URLSearchParams({ from: scopeFrom, to: scopeTo });
  if (searchParams.has('period')) executiveBackQuery.set('period', searchParams.get('period'));
  if (selectedIds.length === 1) executiveBackQuery.set('branchId', selectedIds[0]);
  const backHref = user.role === 'HEAD_OFFICE_MANAGER'
    ? returnToExecutive ? `/quan-ly-he-thong?${executiveBackQuery}` : `/quan-ly-he-thong?${headOfficeBackQuery}`
    : `${searchParams.get('returnTo') === 'overview' ? '/ke-toan' : '/ke-toan/bao-cao'}?${backQuery}`;
  const backLabel = returnToExecutive
    ? selectedIds.length > 1 ? 'Quay lại Tổng quan · toàn chuỗi (đã chọn nhiều cơ sở)' : 'Quay lại Tổng quan'
    : searchParams.get('returnTo') === 'overview' ? 'Quay lại tổng quan' : 'Quay lại báo cáo thu chi';
  const content = <section className="performance-dashboard accountant-finance expense-breakdown-report" aria-labelledby="expense-report-title" aria-busy={loading}>
    <header className="finance-heading expense-report-heading">
      <div><h1 id="expense-report-title">Báo cáo chi phí</h1><p>{allocationBasis?'Khoản chi tài chính ghi nhận vào ngày đầu tháng phân bổ; khoảng lọc không chứa ngày đó sẽ không tính phần phân bổ tháng ấy. Khoản chi cũ theo ngày thanh toán.':'Dòng tiền chi được nhóm theo ngày thanh toán thực tế.'}</p></div>
    </header>
      <div className="finance-filters expense-report-filters" role="group" aria-label="Bộ lọc báo cáo chi phí">
        <div className="expense-report-presets" role="group" aria-label="Khoảng thời gian nhanh">{[['day', 'Ngày'], ['week', 'Tuần'], ['month', 'Tháng'], ['quarter', 'Quý'], ['year', 'Năm']].map(([key, text]) => <button type="button" key={key} aria-pressed={selectedPreset === key} onClick={() => choosePreset(key)}>{text}</button>)}</div>
        <label className="finance-month">Xem trọn tháng<input type="month" value={hasDateRange ? '' : month} max={todayVn().slice(0, 7)} onChange={(event) => event.target.value && updateFilter('month', event.target.value)} /></label>
        <label className="finance-month">Từ ngày<input type="date" required value={hasDateRange ? from : `${month}-01`} max={todayVn()} onChange={(event) => updateDateRange('from', event.target.value)} /></label>
        <label className="finance-month">Đến ngày<input type="date" required value={hasDateRange ? to : monthEnd(month) > todayVn() ? todayVn() : monthEnd(month)} max={todayVn()} onChange={(event) => updateDateRange('to', event.target.value)} /></label>
        <div className="finance-month"><span>Cơ sở</span><BranchFilterSelect branches={branchOptions} multiple value={branchIds.length ? branchIds : branchId ? [branchId] : []} onChange={updateBranches} /></div>
        <label className="finance-month">Nhóm chi<select value={costType} onChange={(event) => updateFilter('costType', event.target.value)}><option value="">Tất cả nhóm</option><option value="OPEX">Chi phí vận hành</option><option value="CAPEX">Chi đầu tư (mua sắm, sửa chữa lớn)</option></select></label>
        <label className="finance-month">Cách xem<select value={basis} onChange={(event) => updateFilter('basis', event.target.value)}><option value="ALLOCATION">Theo phân bổ</option><option value="CASH">Theo ngày thanh toán</option></select></label>
        <label className="finance-month">Danh mục<select value={categoryId} onChange={(event) => updateFilter('categoryId', event.target.value)}><option value="">Tất cả danh mục</option>{(currentData?.categories || []).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <label className="finance-month expense-report-search">Tìm kiếm<input type="search" value={query} maxLength={100} placeholder="Mã chi, mô tả, danh mục, cơ sở…" onChange={(event) => updateFilter('q', event.target.value)} /></label>
      </div>

    {(rangeError || error) && <p className="finance-state finance-error" role="alert">{rangeError || error}</p>}
    {loading && <p className="finance-state" role="status">Đang tải báo cáo chi phí…</p>}
    <p className="expense-report-scope">Khoảng đang xem: {periodScope} · Cơ sở: {branchScope} · Gom nhóm bảng: danh mục và cơ sở · Cách ghi nhận: {allocationBasis ? 'theo phân bổ' : 'theo ngày thanh toán'} · Đơn vị tiền: VND</p>
    <dl className="expense-report-totals" aria-label="Tổng chi phí theo nhóm">
      <div className="is-opex"><dt>{allocationBasis?'Chi phí vận hành theo phân bổ':'Chi tiền vận hành theo ngày thanh toán'}</dt><dd>{currentData ? money(totals.opex) : '—'}</dd><small>{periodScope} · {branchScope} · VND</small><small>{currentData ? totals.opexCount : '—'} {allocationBasis?'dòng ghi nhận':'khoản chi'} · không gồm chi đầu tư</small></div>
      <div className="is-capex"><dt>{allocationBasis?'Chi đầu tư theo phân bổ':'Chi đầu tư theo ngày thanh toán'}</dt><dd>{currentData ? money(totals.capex) : '—'}</dd><small>{periodScope} · {branchScope} · VND</small><small>{currentData ? totals.capexCount : '—'} {allocationBasis?'dòng ghi nhận':'khoản chi'} · trình bày riêng</small></div>
    </dl>

    {!loading && !error && currentData && rows.length === 0 && <p className="expense-report-empty" role="status">{noResults}</p>}
    {rows.length > 0 && <div className="expense-report-table-scroll"><table className="expense-report-table">
      <caption>{allocationBasis?'Chi phí theo tháng ghi nhận':'Dòng tiền chi'} · {periodScope} · {branchScope} · VND</caption>
      <thead><tr><th scope="col">Nhóm chi</th><th scope="col">Danh mục</th><th scope="col">Cơ sở</th><th scope="col">{allocationBasis?'Số dòng ghi nhận':'Số khoản thanh toán'}</th><th scope="col">{allocationBasis?'Tổng ghi nhận':'Tổng đã chi'}</th><th scope="col">Chi tiết</th></tr></thead>
      <tbody>{rows.map((row) => {
        const key = `${row.costType}:${row.categoryId}:${row.branchId}`;
        const isExpanded = expanded && `${expanded.costType}:${expanded.categoryId}:${expanded.branchId}` === key;
        return <Fragment key={key}>
          <tr key={key}>
            <td><span className={`expense-report-type ${row.costType === 'CAPEX' ? 'is-capex' : 'is-opex'}`}>{row.costType === 'CAPEX' ? 'Chi đầu tư' : 'Chi phí vận hành'} ({allocationBasis ? 'theo phân bổ' : 'theo ngày thanh toán'})</span></td>
            <td>{row.categoryName}</td><td>{row.branchName}</td>
            <td className="expense-report-number">{Number(row.paymentCount) || 0}</td><td className="expense-report-number">{money(row.amount)}</td>
            <td><button className="expense-report-detail-button" type="button" aria-expanded={Boolean(isExpanded)} onClick={() => { const next = isExpanded ? null : row; setDetails([]); setDetailsError(''); setDetailsLoading(Boolean(next)); setExpanded(next); }}>{isExpanded ? 'Thu gọn' : `Xem ${allocationBasis ? 'phân bổ' : 'ngày thanh toán'}`}</button></td>
          </tr>
          {isExpanded && <tr className="expense-report-detail-row" key={`${key}:details`}><td colSpan="6">
            <div className="expense-report-detail-content"><h3>{row.categoryName} · {row.branchName}</h3>
              {detailsLoading && <p role="status">Đang tải chi tiết…</p>}
              {detailsError && <p role="alert">{detailsError}</p>}
              {!detailsLoading && !detailsError && details.length === 0 && <p>{noResults}</p>}
              {details.length > 0 && <div className="expense-report-detail-scroll"><table><thead><tr><th>{allocationBasis?'Tháng ghi nhận':'Ngày thanh toán'}</th><th>Mã khoản chi</th><th>Loại</th><th>Mô tả</th><th>Số tiền</th><th>Chứng từ</th></tr></thead>
                <tbody>{details.map((item) => <tr key={`${item.sourceType}:${item.expenseId}:${item.recognitionDate}`}>
                  <td>{allocationBasis?recognitionMonth(item.recognitionDate):dateTime(item.paidAt)}</td><td>{item.expenseCode}</td><td>{item.kind === 'REIMBURSEMENT' ? 'Hoàn ứng' : item.origin==='ACCOUNTANT_DIRECT'?'Chi trực tiếp':'Thanh toán'}</td>
                  <td>{item.description || '—'}{item.paymentReference && <small className="expense-report-reference">Mã tham chiếu: {item.paymentReference}</small>}</td><td className="expense-report-number">{money(item.amount)}</td>
                  <td>{item.attachments?.length ? <div className="expense-report-evidence">{item.attachments.map((file) => <button key={file.id} type="button" disabled={evidenceBusy} onClick={() => viewEvidence(file)}>{evidenceBusy ? 'Đang mở…' : `Xem ${file.kind === 'INVOICE' ? 'hóa đơn' : 'chứng từ thanh toán'} · ${file.name}`}</button>)}</div> : '—'}</td>
                </tr>)}</tbody></table></div>}
            </div>
          </td></tr>}
        </Fragment>;
      })}</tbody>
    </table></div>}
    <p className="expense-report-footer"><Link to={backHref}>{backLabel}</Link><button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Tải lại</button></p>
    {preview && <div className="expense-report-preview-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setPreview(null); }}><section className="expense-report-preview" role="dialog" aria-modal="true" aria-labelledby="expense-preview-title"><header><h2 id="expense-preview-title">{preview.name}</h2><button type="button" autoFocus onClick={() => setPreview(null)}>Đóng</button></header>{preview.type?.startsWith('image/') ? <img src={preview.url} alt={preview.name} /> : <iframe src={preview.url} title={preview.name} /> }<a href={preview.url} download={preview.name}>Tải chứng từ</a></section></div>}
  </section>;

  if (user.role === 'HEAD_OFFICE_MANAGER') return content;
  return <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout} breadcrumb="Báo cáo chi phí" activeTab="orders" audience="accountant" hideSearch showCurrentMonth={false}>{content}</AppShell>;
}
