import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../api/client';
import { authApi } from '../auth/services/authApi';
import { AppShell } from '../booking/pages/BookingManagementPage';
import './dashboard.css';
import './expense-breakdown-report.css';

const now = new Date();
const monthNow = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
const noResults = 'Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.';
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const validMonth = (value) => /^\d{4}-(0[1-9]|1[0-2])$/.test(value || '');
const dateTime = (value) => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)} ${value.slice(11, 16)}` : '—';
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.';
const expenseReportPath = `/quan-ly-he-thong?${new URLSearchParams({ screen: 'Báo cáo chi phí' })}`;
const headOfficeMonthlyPath = `/quan-ly-he-thong?${new URLSearchParams({ screen: 'Báo cáo kinh doanh' })}`;

export default function ExpenseBreakdownReportPage({ initialUser = null }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [user, setUser] = useState(initialUser);
  const [data, setData] = useState(null);
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
  const branchId = searchParams.get('branchId') || '';
  const costType = searchParams.get('costType') || '';
  const categoryId = searchParams.get('categoryId') || '';
  const query = searchParams.get('q') || '';
  const requestKey = JSON.stringify([month, branchId, costType, categoryId, query]);
  const currentData = dataKey === requestKey ? data : null;

  function updateFilter(key, value) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('tab');
    setData(null); setDataKey('');
    setSearchParams(next, { replace: true });
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
    let active = true;
    setLoading(true); setError(''); setData(null); setDataKey('');
    api.get('/accounting/reports/expense-breakdown', { params: {
      month,
      ...(branchId ? { branchId } : {}),
      ...(costType ? { costType } : {}),
      ...(categoryId ? { categoryId } : {}),
      ...(query ? { q: query } : {}),
    } }).then(({ data: result }) => {
      if (active) {
        setData({ ...result, branches: result.branches || [], categories: result.categories || [], rows: result.rows || [] });
        setDataKey(requestKey);
      }
    }).catch((e) => { if (active) setError(errorText(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, month, branchId, costType, categoryId, query, requestKey, revision]);

  useEffect(() => {
    setExpanded(null); setDetails([]); setDetailsError('');
  }, [month, branchId, costType, categoryId, query]);

  useEffect(() => {
    if (!expanded) return undefined;
    let active = true;
    setDetailsLoading(true); setDetailsError(''); setDetails([]);
    api.get('/accounting/reports/expense-breakdown/details', { params: {
      month, branchId: expanded.branchId, categoryId: expanded.categoryId, costType: expanded.costType,
      ...(query ? { q: query } : {}),
    } }).then(({ data: result }) => { if (active) setDetails(result.rows || []); })
      .catch((e) => { if (active) setDetailsError(errorText(e)); })
      .finally(() => { if (active) setDetailsLoading(false); });
    return () => { active = false; };
  }, [expanded, month, query]);

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
  const content = <section className="performance-dashboard accountant-finance expense-breakdown-report" aria-labelledby="expense-report-title" aria-busy={loading}>
    <header className="finance-heading expense-report-heading">
      <div><h1 id="expense-report-title">Báo cáo chi phí</h1><p>Khoản chi đã thanh toán trong kỳ, phân nhóm theo loại, danh mục và cơ sở.</p></div>
      <div className="finance-filters expense-report-filters">
        <label className="finance-month">Kỳ báo cáo<input type="month" value={month} onChange={(event) => event.target.value && updateFilter('month', event.target.value)} /></label>
        <label className="finance-month">Cơ sở<select value={branchId} onChange={(event) => updateFilter('branchId', event.target.value)}><option value="">Tất cả cơ sở</option>{(currentData?.branches || []).map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
        <label className="finance-month">Nhóm chi<select value={costType} onChange={(event) => updateFilter('costType', event.target.value)}><option value="">Tất cả nhóm</option><option value="OPEX">Chi vận hành</option><option value="CAPEX">Vốn đầu tư</option></select></label>
        <label className="finance-month">Danh mục<select value={categoryId} onChange={(event) => updateFilter('categoryId', event.target.value)}><option value="">Tất cả danh mục</option>{(currentData?.categories || []).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <label className="finance-month expense-report-search">Tìm kiếm<input type="search" value={query} maxLength={100} placeholder="Mã chi, mô tả, danh mục, cơ sở…" onChange={(event) => updateFilter('q', event.target.value)} /></label>
      </div>
    </header>

    {error && <p className="finance-state finance-error" role="alert">{error}</p>}
    {loading && <p className="finance-state" role="status">Đang tải báo cáo chi phí…</p>}
    <dl className="expense-report-totals" aria-label="Tổng chi phí theo nhóm">
      <div className="is-opex"><dt>Chi vận hành đã trả</dt><dd>{money(totals.opex)}</dd><small>{totals.opexCount} khoản thanh toán · không gồm vốn đầu tư</small></div>
      <div className="is-capex"><dt>Vốn đầu tư đã trả</dt><dd>{money(totals.capex)}</dd><small>{totals.capexCount} khoản thanh toán · trình bày riêng</small></div>
    </dl>

    {!loading && !error && currentData && rows.length === 0 && <p className="expense-report-empty" role="status">{noResults}</p>}
    {rows.length > 0 && <div className="expense-report-table-scroll"><table className="expense-report-table">
      <caption>Chi phí đã thanh toán trong tháng {month}</caption>
      <thead><tr><th scope="col">Nhóm chi</th><th scope="col">Danh mục</th><th scope="col">Cơ sở</th><th scope="col">Số khoản thanh toán</th><th scope="col">Tổng đã trả</th><th scope="col">Chi tiết</th></tr></thead>
      <tbody>{rows.map((row) => {
        const key = `${row.costType}:${row.categoryId}:${row.branchId}`;
        const isExpanded = expanded && `${expanded.costType}:${expanded.categoryId}:${expanded.branchId}` === key;
        return <Fragment key={key}>
          <tr key={key}>
            <td><span className={`expense-report-type ${row.costType === 'CAPEX' ? 'is-capex' : 'is-opex'}`}>{row.costType === 'CAPEX' ? 'Vốn đầu tư' : 'Vận hành'}</span></td>
            <td>{row.categoryName}</td><td>{row.branchName}</td>
            <td className="expense-report-number">{Number(row.paymentCount) || 0}</td><td className="expense-report-number">{money(row.amount)}</td>
            <td><button className="expense-report-detail-button" type="button" aria-expanded={Boolean(isExpanded)} onClick={() => { const next = isExpanded ? null : row; setDetails([]); setDetailsError(''); setDetailsLoading(Boolean(next)); setExpanded(next); }}>{isExpanded ? 'Thu gọn' : 'Xem khoản chi'}</button></td>
          </tr>
          {isExpanded && <tr className="expense-report-detail-row" key={`${key}:details`}><td colSpan="6">
            <div className="expense-report-detail-content"><h3>{row.categoryName} · {row.branchName}</h3>
              {detailsLoading && <p role="status">Đang tải chi tiết…</p>}
              {detailsError && <p role="alert">{detailsError}</p>}
              {!detailsLoading && !detailsError && details.length === 0 && <p>{noResults}</p>}
              {details.length > 0 && <div className="expense-report-detail-scroll"><table><thead><tr><th>Ngày thanh toán</th><th>Mã khoản chi</th><th>Loại</th><th>Mô tả</th><th>Số tiền</th><th>Chứng từ</th></tr></thead>
                <tbody>{details.map((item) => <tr key={`${item.sourceType}:${item.expenseId}`}>
                  <td>{dateTime(item.paidAt)}</td><td>{item.expenseCode}</td><td>{item.kind === 'REIMBURSEMENT' ? 'Hoàn ứng' : 'Thanh toán'}</td>
                  <td>{item.description || '—'}{item.paymentReference && <small className="expense-report-reference">Mã tham chiếu: {item.paymentReference}</small>}</td><td className="expense-report-number">{money(item.amount)}</td>
                  <td>{item.attachments?.length ? <div className="expense-report-evidence">{item.attachments.map((file) => <button key={file.id} type="button" disabled={evidenceBusy} onClick={() => viewEvidence(file)}>{evidenceBusy ? 'Đang mở…' : `Xem ${file.kind === 'INVOICE' ? 'hóa đơn' : 'chứng từ thanh toán'} · ${file.name}`}</button>)}</div> : '—'}</td>
                </tr>)}</tbody></table></div>}
            </div>
          </td></tr>}
        </Fragment>;
      })}</tbody>
    </table></div>}
    <p className="expense-report-footer"><Link to={user.role === 'HEAD_OFFICE_MANAGER' ? headOfficeMonthlyPath : '/ke-toan/bao-cao'}>Quay lại báo cáo hiệu quả tháng</Link><button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Tải lại</button></p>
    {preview && <div className="expense-report-preview-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setPreview(null); }}><section className="expense-report-preview" role="dialog" aria-modal="true" aria-labelledby="expense-preview-title"><header><h2 id="expense-preview-title">{preview.name}</h2><button type="button" autoFocus onClick={() => setPreview(null)}>Đóng</button></header>{preview.type?.startsWith('image/') ? <img src={preview.url} alt={preview.name} /> : <iframe src={preview.url} title={preview.name} /> }<a href={preview.url} download={preview.name}>Tải chứng từ</a></section></div>}
  </section>;

  if (user.role === 'HEAD_OFFICE_MANAGER') return content;
  return <AppShell user={user} branchName="toàn chuỗi" busy={logoutBusy} onLogout={logout} breadcrumb="Báo cáo chi phí" activeTab="orders" audience="accountant" hideSearch showCurrentMonth={false}>{content}</AppShell>;
}
