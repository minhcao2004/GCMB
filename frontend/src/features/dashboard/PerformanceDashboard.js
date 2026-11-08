import { useEffect, useState } from 'react';
import api from '../../api/client';
import './dashboard.css';

const currentMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
};
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;

function Metric({ label, value, note, tone }) {
  return <div className={`performance-metric ${tone || ''}`}><span>{label}</span><strong>{value}</strong><small>{note}</small></div>;
}
function Results({ rows, onSelect }) {
  const max = Math.max(1, ...rows.map((row) => Number(row.revenue)));
  return <div className="performance-table-wrap"><table className="performance-table"><thead><tr><th>Cơ sở</th><th>Doanh thu</th><th>Chi vận hành</th><th>Trong đó · lương</th><th>Lợi nhuận</th><th>Biên LN</th></tr></thead><tbody>
    {rows.map((row) => <tr key={row.id}><td>{onSelect && row.status === 'ACTIVE' ? <button className="performance-link" onClick={() => onSelect(row.id)}>{row.name}</button> : <strong>{row.name}</strong>}<small>{row.code}{row.status !== 'ACTIVE' ? ' · Không hoạt động' : ''}</small></td><td><span>{money(row.revenue)}</span><div className="performance-bar" aria-hidden="true"><i style={{ width: `${Math.max(0, Number(row.revenue) / max * 100)}%` }} /></div></td><td>{money(row.expenses)}</td><td>{money(row.salary)}</td><td className={Number(row.profit) < 0 ? 'negative' : 'positive'}>{money(row.profit)}</td><td>{percent(row.margin)}</td></tr>)}
  </tbody></table></div>;
}

export default function PerformanceDashboard({ user, isChain, onNavigate }) {
  const [month, setMonth] = useState(currentMonth);
  const [branchId, setBranchId] = useState('');
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let live = true;
    setLoading(true); setError(''); setData(null);
    const path = isChain ? '/head-office/dashboard' : '/branch-management/dashboard';
    const config = { params: { month, ...(isChain && branchId ? { branchId } : {}) }, ...(!isChain ? { headers: { 'X-GCMB-Branch-Id': user.branchId } } : {}) };
    api.get(path, config).then(({ data: result }) => { if (live) setData(result); }).catch((e) => { if (live) setError(e.response?.data?.detail || e.response?.data?.message || 'Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại.'); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [month, branchId, isChain, user.branchId]);
  const [options, setOptions] = useState([]);
  useEffect(() => { if (isChain && data?.branches) setOptions(data.branches); }, [data, isChain]);
  const totals = isChain ? data?.totals : data?.result;
  const rows = isChain ? data?.results || [] : data?.result ? [data.result] : [];
  const branch = !isChain ? data?.result : branchId ? rows[0] : null;
  return <section className="manager-content performance-dashboard" aria-labelledby="performance-title">
    <div className="performance-heading"><div><h1 id="performance-title">{isChain ? 'Hiệu quả kinh doanh chuỗi' : 'Tổng quan cơ sở'}</h1><p>{isChain ? 'Theo dõi kết quả kinh doanh theo kỳ và cơ sở' : `${user.branchName || 'Cơ sở được phân quyền'} · Theo dõi kết quả và vận hành`}</p></div>
      <div className="performance-filters"><label>Tháng báo cáo<input type="month" value={month} onChange={(e) => { if (e.target.value) setMonth(e.target.value); }} /></label>{isChain && <label>Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Tất cả cơ sở</option>{options.map((b) => <option value={b.id} key={b.id}>{b.name}</option>)}</select></label>}</div>
    </div>
    {loading && <div className="performance-state" role="status">Đang tải dữ liệu báo cáo…</div>}
    {error && <div className="performance-state performance-error" role="alert">{error}</div>}
    {data && <>
      {branch && <div className="performance-context"><div><strong>{branch.name}</strong><span>{branch.code} · {branch.address}</span></div><span className="performance-status">{branch.status === 'ACTIVE' ? 'Đang hoạt động' : 'Không hoạt động'}</span></div>}
      {Number(totals.billCount) === 0 && Number(totals.expenseCount) === 0 && <div className="performance-note" role="status">Chưa có bill hoàn tất hoặc khoản chi đã thanh toán trong tháng này.</div>}
      <div className="performance-metrics"><Metric label="Doanh thu dịch vụ" value={money(totals.revenue)} note={`${totals.billCount} bill hoàn tất trong kỳ`} /><Metric label="Chi vận hành đã trả" value={money(totals.expenses)} note="Gồm chi phí, lương và ứng lương; chưa gồm đầu tư" /><Metric label="Lợi nhuận vận hành · tạm tính" value={money(totals.profit)} note="Doanh thu trừ chi phí và lương" tone={Number(totals.profit) < 0 ? 'negative' : 'positive'} /><Metric label={isChain ? 'Dòng tiền thuần' : 'Biên lợi nhuận'} value={isChain ? money(totals.netCashFlow) : percent(totals.margin)} note={isChain ? 'Thu vào trừ chi ra theo sổ tiền' : 'Tính khi doanh thu lớn hơn 0'} /></div>
      <p className="performance-explanation">Doanh thu tính theo bill hoàn tất; chi phí và tiền lương tính theo khoản đã trả trong kỳ, theo múi giờ từng cơ sở. Chi phí đầu tư được trình bày riêng.</p>
      <div className="performance-grid"><article className="performance-panel"><div className="performance-panel-heading"><div><h2>{isChain ? 'Kết quả theo cơ sở' : 'Kết quả trong tháng'}</h2><p>{isChain ? 'Chọn tên cơ sở đang hoạt động để xem riêng' : 'Doanh thu và chi phí đã ghi nhận'}</p></div><span>Tháng {month.slice(5)}/{month.slice(0, 4)}</span></div>{rows.length ? <Results rows={rows} onSelect={isChain ? setBranchId : null} /> : <p className="performance-empty">Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</p>}</article>
      <article className="performance-panel performance-aside"><h2>{isChain ? 'Thông tin báo cáo' : 'Vận hành hiện tại'}</h2>{isChain ? <><div className="performance-detail"><span>Chi đầu tư đã trả</span><strong>{money(totals.investment)}</strong></div><div className="performance-detail"><span>Lương và ứng lương đã trả</span><strong>{money(totals.salary)}</strong></div><p>Chi đầu tư được trình bày riêng, không tính vào lợi nhuận vận hành.</p></> : <><div className="performance-detail"><span>Phòng đang sử dụng</span><strong>{data.operations.occupiedRooms} / {data.operations.roomCount}</strong></div><div className="performance-detail"><span>Phòng bảo trì</span><strong>{data.operations.maintenanceRooms}</strong></div><div className="performance-detail"><span>Sự cố chưa xử lý xong</span><strong>{data.operations.openIncidents}</strong></div><button className="performance-link performance-action" onClick={() => onNavigate('Phòng')}>Xem danh sách phòng →</button></>}</article></div>
    </>}
  </section>;
}
