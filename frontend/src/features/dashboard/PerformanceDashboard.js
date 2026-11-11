import { useEffect, useState } from 'react';
import api from '../../api/client';
import './dashboard.css';

const currentMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
};
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;
const amount = (value) => Number(value) || 0;

function Results({ rows, onSelect }) {
  const maxRevenue = Math.max(1, ...rows.map((row) => amount(row.revenue)));
  return <div className="performance-table-wrap"><table className="performance-table">
    <thead><tr><th scope="col">Cơ sở</th><th scope="col">Doanh thu</th><th scope="col">Chi đã trả</th><th scope="col">Lương</th><th scope="col">Lợi nhuận</th><th scope="col">Biên LN</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.id}>
      <td data-label="Cơ sở" className="performance-branch-cell">{onSelect && row.status === 'ACTIVE'
        ? <button className="performance-link" onClick={() => onSelect(row.id)}>{row.name}<span aria-hidden="true"> ↗</span></button>
        : <strong>{row.name}</strong>}<small>{row.code}{row.status !== 'ACTIVE' ? ' · Không hoạt động' : ''}</small></td>
      <td data-label="Doanh thu"><span>{money(row.revenue)}</span><div className="performance-bar" aria-hidden="true"><i style={{ width: `${Math.max(0, amount(row.revenue) / maxRevenue * 100)}%` }} /></div></td>
      <td data-label="Chi đã trả">{money(row.expenses)}</td><td data-label="Lương">{money(row.salary)}</td>
      <td data-label="Lợi nhuận" className={amount(row.profit) < 0 ? 'negative' : 'positive'}>{money(row.profit)}</td>
      <td data-label="Biên lợi nhuận">{percent(row.margin)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function LoadingState() {
  return <div className="performance-loading" role="status" aria-label="Đang tải báo cáo">
    <span className="performance-skeleton performance-skeleton-title" />
    <div className="performance-skeleton-strip">{[0, 1, 2, 3].map((item) => <span className="performance-skeleton" key={item} />)}</div>
    <div className="performance-skeleton-body"><span className="performance-skeleton" /><span className="performance-skeleton" /></div>
  </div>;
}

function BranchContext({ branch }) {
  return <div className="performance-context"><div><strong>{branch.name}</strong><span>{branch.code}{branch.address ? ` · ${branch.address}` : ''}</span></div>
    <span className={`performance-status${branch.status === 'ACTIVE' ? '' : ' is-inactive'}`}>{branch.status === 'ACTIVE' ? 'Đang hoạt động' : 'Không hoạt động'}</span>
  </div>;
}

function FinanceStrip({ totals, isChain, branchSelected }) {
  const metrics = isChain ? [
    ['Doanh thu dịch vụ', money(totals.revenue), `${amount(totals.billCount)} bill hoàn tất`],
    ['Chi vận hành đã trả', money(totals.expenses), 'Đã gồm lương và ứng lương'],
    ['Lợi nhuận vận hành', money(totals.profit), 'Chưa gồm chi đầu tư'],
    ['Dòng tiền thuần', money(totals.netCashFlow), 'Theo sổ giao dịch'],
    ['Cọc tịch thu', money(totals.forfeitedDeposits), 'Trình bày riêng, không cộng doanh thu'],
  ] : [
    ['Doanh thu dịch vụ', money(totals.revenue), `${amount(totals.billCount)} bill hoàn tất`],
    ['Chi vận hành đã trả', money(totals.expenses), 'Chưa gồm chi đầu tư'],
    ['Lợi nhuận vận hành', money(totals.profit), 'Doanh thu trừ chi vận hành'],
    ['Biên lợi nhuận', percent(totals.margin), 'Tính khi doanh thu lớn hơn 0'],
    ['Cọc tịch thu', money(totals.forfeitedDeposits), 'Trình bày riêng, không cộng doanh thu'],
  ];
  return <section className="performance-summary" aria-label="Kết quả tài chính trong kỳ">
    <div className="performance-summary-heading"><h2>Kết quả tài chính</h2><span>{isChain ? branchSelected ? 'Cơ sở đang chọn' : 'Toàn hệ thống' : 'Cơ sở được phân quyền'}</span></div>
    <dl className="performance-metrics">{metrics.map(([label, value, note]) => <div className="performance-metric" key={label}>
      <dt>{label}</dt><dd className={label === 'Lợi nhuận vận hành' && amount(totals.profit) < 0 ? 'negative' : ''}>{value}</dd><small>{note}</small>
    </div>)}</dl>
  </section>;
}

function ChainDetails({ totals }) {
  return <aside className="performance-panel performance-aside">
    <h2>Chi phí cần theo dõi</h2><p className="performance-panel-intro">Khoản đã chi trong kỳ, trình bày riêng theo tính chất.</p>
    <dl className="performance-detail-list">
      <div className="performance-detail"><dt>Đầu tư</dt><dd>{money(totals.investment)}</dd></div>
      <div className="performance-detail"><dt>Lương và ứng lương</dt><dd>{money(totals.salary)}</dd></div>
      <div className="performance-detail"><dt>Số cơ sở báo cáo</dt><dd>{amount(totals.branchCount)}</dd></div>
      <div className="performance-detail"><dt>Cơ sở lỗ trong kỳ</dt><dd className={amount(totals.lossBranches) ? 'negative' : ''}>{amount(totals.lossBranches)}</dd></div>
    </dl>
    <p className="performance-aside-note">Chi đầu tư được trình bày riêng, không tính vào lợi nhuận vận hành.</p>
  </aside>;
}

function BranchFinance({ totals }) {
  const [selectedCost, setSelectedCost] = useState(null);
  const otherOperatingCost = Math.max(0, amount(totals.expenses) - amount(totals.salary));
  const items = [
    ['Chi phí khác', otherOperatingCost],
    ['Lương và ứng lương', amount(totals.salary)],
    ['Đầu tư', amount(totals.investment)],
  ];
  const max = Math.max(1, ...items.map(([, value]) => value));
  return <article className="performance-panel performance-breakdown">
    <div className="performance-panel-heading"><div><h2>Cơ cấu khoản chi</h2><p>Đã thanh toán trong kỳ báo cáo</p></div></div>
    <dl>{items.map(([label, value]) => <div className="performance-breakdown-row" key={label}>
      <dt><button type="button" className="performance-chart-choice" onClick={() => setSelectedCost([label, value])}>{label}</button><span className="performance-bar" aria-hidden="true"><i style={{ width: `${Math.min(100, value / max * 100)}%` }} /></span></dt>
      <dd>{money(value)}</dd>
    </div>)}</dl>
    <p className="performance-chart-detail" aria-live="polite">{selectedCost ? `${selectedCost[0]}: ${money(selectedCost[1])} trong tháng hiện tại.` : 'Bấm vào một khoản chi để xem số liệu hiện tại.'}</p>
  </article>;
}

function BranchOperations({ operations, onNavigate }) {
  const rooms = amount(operations.roomCount);
  const occupied = amount(operations.occupiedRooms);
  const occupiedPercent = rooms ? Math.min(100, Math.round(occupied / rooms * 100)) : 0;
  return <article className="performance-panel performance-operations">
    <div className="performance-panel-heading"><div><h2>Vận hành hiện tại</h2><p>Tình trạng phòng và sự cố tại cơ sở</p></div></div>
    <div className="performance-occupancy"><div><span>Phòng đang sử dụng</span><strong>{occupied} <small>/ {rooms}</small></strong></div>
      <div className="performance-occupancy-track" role="progressbar" aria-label="Tỷ lệ phòng đang sử dụng" aria-valuemin="0" aria-valuemax="100" aria-valuenow={occupiedPercent}><i style={{ width: `${occupiedPercent}%` }} /></div>
    </div>
    <dl className="performance-detail-list">
      <div className="performance-detail"><dt>Phòng bảo trì</dt><dd>{amount(operations.maintenanceRooms)}</dd></div>
      <div className="performance-detail"><dt>Sự cố chưa xử lý xong</dt><dd className={amount(operations.openIncidents) ? 'warning' : ''}>{amount(operations.openIncidents)}</dd></div>
    </dl>
    <button className="performance-action" onClick={() => onNavigate?.('Phòng')}>Xem danh sách phòng <span aria-hidden="true">→</span></button>
  </article>;
}

export default function PerformanceDashboard({ user, isChain, onNavigate }) {
  const month = currentMonth();
  const [branchId, setBranchId] = useState('');
  const [selectedChartBranch, setSelectedChartBranch] = useState(null);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let live = true;
    setLoading(true); setError(''); setData(null);
    const path = isChain ? '/head-office/dashboard' : '/branch-management/dashboard';
    const config = { params: { month, ...(isChain && branchId ? { branchId } : {}) }, ...(!isChain ? { headers: { 'X-GCMB-Branch-Id': user.branchId } } : {}) };
    api.get(path, config).then(({ data: result }) => { if (live) setData(result); })
      .catch((e) => { if (live) setError(e.response?.data?.detail || e.response?.data?.message || 'Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [month, branchId, isChain, user.branchId, revision]);

  useEffect(() => {
    const refresh = () => setRevision((value) => value + 1);
    const timer = window.setInterval(refresh, 90_000);
    window.addEventListener('focus', refresh);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, []);

  const rows = isChain ? data?.results || [] : data?.result ? [data.result] : [];
  const branch = !isChain ? data?.result : branchId ? rows[0] : null;
  const totals = isChain ? data?.totals : data?.result;

  return <section className={`manager-content performance-dashboard${isChain ? ' is-chain' : ' is-branch'}`} aria-labelledby="performance-title">
    <header className="performance-heading"><div><h1 id="performance-title">{isChain ? 'Hiệu quả kinh doanh chuỗi' : 'Tổng quan cơ sở'}</h1>
      <p>{isChain ? 'Kết quả tài chính và mức đóng góp theo từng cơ sở.' : `${user.branchName || 'Cơ sở được phân quyền'} · Kết quả kinh doanh và tình hình vận hành.`}</p></div>
      <div className="performance-filters">
        <span className="performance-current-period">Số liệu tháng hiện tại · tự cập nhật mỗi 90 giây</span>
        {isChain && <label><span>Phạm vi cơ sở</span><select value={branchId} onChange={(event) => { setBranchId(event.target.value); setSelectedChartBranch(null); }}>
          <option value="">Tất cả cơ sở</option>{(data?.branches || []).map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}
        </select></label>}
      </div>
    </header>

    {loading && <LoadingState />}
    {error && <div className="performance-state performance-error" role="alert"><strong>Không tải được báo cáo</strong><span>{error}</span></div>}
    {data && totals && <>
      {branch && <BranchContext branch={branch} />}
      {Number(totals.billCount) === 0 && Number(totals.expenseCount) === 0 && <div className="performance-note" role="status">Chưa có bill hoàn tất hoặc khoản chi đã thanh toán trong kỳ này.</div>}
      <FinanceStrip totals={totals} isChain={isChain} branchSelected={Boolean(branchId)} />
      {isChain ? <div className="performance-grid performance-grid-chain">
        <article className="performance-panel performance-branches"><div className="performance-panel-heading"><div><h2>Kết quả theo cơ sở</h2><p>{branchId ? 'Kết quả của cơ sở đang chọn' : 'Chọn cơ sở đang hoạt động để xem riêng'}</p></div><span>{rows.length} cơ sở</span></div>
          <div className="performance-chart" aria-label="Biểu đồ doanh thu theo cơ sở">
            {rows.map((row) => <button type="button" key={`chart-${row.id}`} className={`performance-chart-row${branchId === row.id ? ' is-selected' : ''}`} onClick={() => { setSelectedChartBranch(row); if (!branchId) setBranchId(row.id); }} aria-pressed={Boolean(branchId && (selectedChartBranch?.id === row.id || branchId === row.id))}>
              <span>{row.name}</span><span className="performance-bar" aria-hidden="true"><i style={{ width: `${Math.max(0, amount(row.revenue) / Math.max(1, ...rows.map((item) => amount(item.revenue))) * 100)}%` }} /></span><strong>{money(row.revenue)}</strong>
            </button>)}
          </div>
          {selectedChartBranch && <p className="performance-chart-detail" aria-live="polite">{selectedChartBranch.name}: lợi nhuận {money(selectedChartBranch.profit)}, chi đã trả {money(selectedChartBranch.expenses)}.</p>}
          {rows.length ? <Results rows={rows} onSelect={branchId ? null : (id) => { setSelectedChartBranch(null); setBranchId(id); }} /> : <p className="performance-empty">Chưa có cơ sở trong phạm vi báo cáo đã chọn.</p>}
        </article>
        <ChainDetails totals={totals} />
      </div> : <div className="performance-grid performance-grid-branch">
        <BranchFinance totals={totals} />
        <BranchOperations operations={data.operations} onNavigate={onNavigate} />
      </div>}
      <p className="performance-explanation">Doanh thu tính theo bill hoàn tất; chi phí và tiền lương tính theo khoản đã trả trong kỳ, theo múi giờ từng cơ sở. Chi đầu tư được trình bày riêng.</p>
    </>}
  </section>;
}
