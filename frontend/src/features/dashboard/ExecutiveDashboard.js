import { useEffect, useState } from 'react';
import api from '../../api/client';
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import './executive-dashboard.css';

const money = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const percent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(Number(value) * 100)}%`;
const changeText = (value) => value == null ? 'Chưa có doanh thu kỳ trước để so sánh' : `${value >= 0 ? '+' : ''}${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}% so kỳ trước`;
const tooltipMoney = (value) => money(value);
const axisMoney = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value / 1_000_000)} tr`;

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return <div className="executive-tooltip"><strong>{label}</strong>{payload.map((item) => <div key={item.dataKey}>
    <span><i style={{ background: item.color || item.fill }} />{item.name}</span><b>{tooltipMoney(item.value)}</b>
  </div>)}</div>;
}

function PanelHeading({ title, description, trailing }) {
  return <div className="executive-panel-heading"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{trailing}</div>;
}

function Metric({ label, value, footnote, tone = '' }) {
  return <article className={`executive-metric${tone ? ` is-${tone}` : ''}`}>
    <h2>{label}</h2><strong>{value}</strong><p>{footnote}</p>
  </article>;
}

function RankingTable({ rows }) {
  return <div className="executive-table-scroll"><table className="executive-ranking-table">
    <thead><tr><th>Cơ sở</th><th>Doanh thu</th><th>Bill hoàn tất</th><th>Lấp đầy</th><th>Chi phí</th><th>Trạng thái</th></tr></thead>
    <tbody>{rows.map((row, index) => <tr key={row.id}>
      <td><span className="executive-branch-name"><span className="executive-rank">{String(index + 1).padStart(2, '0')}</span><span>{row.name}<small>{row.code}</small></span></span></td>
      <td className="executive-number">{money(row.revenue)}</td><td className="executive-number">{new Intl.NumberFormat('vi-VN').format(row.bookings)}</td>
      <td><span className="executive-occupancy-value">{percent(row.occupancy)}</span>{row.occupancy != null && <span className="executive-mini-track"><i style={{ width: `${row.occupancy * 100}%` }} /></span>}</td>
      <td className="executive-number">{money(row.cost)}</td>
      <td><span className={`executive-status${row.status === 'ACTIVE' ? '' : ' is-paused'}`}><i />{row.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm ngừng'}</span></td>
    </tr>)}</tbody>
  </table></div>;
}

export default function ExecutiveDashboard() {
  const [period, setPeriod] = useState(() => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`; });
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    Promise.allSettled([
      api.get('/head-office/dashboard', { params: { month: period } }),
      api.get('/head-office/finance'),
    ]).then(([reportResult, financeResult]) => {
      if (!active) return;
      if (reportResult.status === 'rejected') { setData(null); setError(reportResult.reason.response?.data?.detail || 'Không tải được báo cáo chuỗi.'); return; }
      const report = reportResult.value.data;
      const branches = report.results || [];
      const occupancyById = new Map((report.occupancyByBranch || []).map((row) => [row.id, row]));
      const totals = report.totals || {};
      const priorRevenue = Number(report.previousTotals?.revenue) || 0;
      const occupancyRows = report.occupancyByBranch || [];
      const roomCount = occupancyRows.reduce((sum, row) => sum + Number(row.total || 0), 0);
      const occupiedCount = occupancyRows.reduce((sum, row) => sum + Number(row.occupied || 0), 0);
      const requests = financeResult.status === 'fulfilled' ? financeResult.value.data.requests || [] : [];
      setData({
        kpis: { revenue: totals.revenue, revenueChange: priorRevenue ? ((Number(totals.revenue) - priorRevenue) / priorRevenue) * 100 : null, bookings: totals.billCount, occupancy: roomCount ? occupiedCount / roomCount : null, cost: totals.expenses, grossProfit: totals.profit, activeBranches: branches.filter((row) => row.status === 'ACTIVE').length, suspendedBranches: branches.filter((row) => row.status !== 'ACTIVE').length, branchTotal: branches.length },
        revenueByBranch: branches.map((row) => {
          const rooms = occupancyById.get(row.id);
          return { ...row, shortName: row.code, revenue: Number(row.revenue) || 0, occupancy: Number(rooms?.total) > 0 ? Number(rooms.occupied) / Number(rooms.total) : null, bookings: row.billCount, cost: row.expenses };
        }),
        occupancyByBranch: occupancyRows.map((row) => ({ ...row, occupancy: Number(row.total) > 0 ? Number(row.occupied) / Number(row.total) : 0 })),
        revenueByDay: (report.dailyRevenue || []).map((row) => {
          const date = String(row.date).slice(0, 10);
          return { ...row, label: `${date.slice(8, 10)}/${date.slice(5, 7)}`, roomRevenue: Number(row.roomRevenue) || 0, fnbRevenue: Number(row.fnbRevenue) || 0 };
        }),
        revenueMix: (report.revenueMix || []).map((row) => ({ name: row.label, value: Number(row.value) || 0, color: row.label === 'Tiền mặt' ? '#168b87' : row.label === 'SePay' ? '#6a3ee8' : '#df9b28' })),
        ranking: [...branches].map((row) => { const rooms = occupancyById.get(row.id); return { ...row, occupancy: Number(rooms?.total) > 0 ? Number(rooms.occupied) / Number(rooms.total) : null, bookings: row.billCount, cost: row.expenses }; }).sort((a, b) => Number(b.revenue) - Number(a.revenue)),
        approvals: requests.filter((row) => ['WAITING_HEAD_OFFICE', 'WAITING_APPROVAL', 'WAITING_MANAGER'].includes(row.status)).map((row) => ({ id: row.id, category: row.description || row.categoryName || 'Đề nghị chi', branch: row.branchName || '—', amount: row.amount || 0 })),
        warnings: branches.filter((row) => row.status !== 'ACTIVE').map((row) => ({ id: row.id, branch: row.name, kind: 'Cơ sở tạm ngừng', detail: 'Trạng thái hoạt động theo hồ sơ cơ sở.', level: 'critical' })),
      });
      if (financeResult.status === 'rejected') setError('Không tải được danh sách đề nghị chi; các biểu đồ báo cáo vẫn đã được tải.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [period]);
  const safeData = data || { kpis: {}, revenueByBranch: [], occupancyByBranch: [], revenueByDay: [], revenueMix: [], ranking: [], approvals: [], warnings: [] };
  const selectedBranch = safeData.revenueByBranch.find((branch) => branch.id === selectedBranchId);
  const { kpis } = safeData;
  const selectedRevenueMix = safeData.revenueMix.reduce((sum, item) => sum + item.value, 0);

  const selectChartBranch = (event) => {
    const row = event?.activePayload?.[0]?.payload;
    if (row?.id) setSelectedBranchId(row.id);
  };

  return <section className="manager-content executive-dashboard" aria-labelledby="executive-title">
    <header className="executive-heading">
      <div><h1 id="executive-title">Tổng quan toàn chuỗi</h1><p>Hiệu quả kinh doanh, sức chứa và các việc cần theo dõi tại tất cả cơ sở.</p></div>
      <label className="executive-period">Kỳ báo cáo<input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label>
    </header>
    {loading && <p role="status">Đang tải báo cáo…</p>}{error && <p role="alert">{error}</p>}

    <div className="executive-kpis" aria-label="Chỉ số chính">
      <Metric label="Doanh thu" value={money(kpis.revenue)} footnote={changeText(kpis.revenueChange)} tone={kpis.revenueChange == null ? '' : kpis.revenueChange >= 0 ? 'positive' : 'negative'} />
      <Metric label="Bill hoàn tất" value={new Intl.NumberFormat('vi-VN').format(kpis.bookings || 0)} footnote="Trong kỳ đang chọn" />
      <Metric label="Tỷ lệ lấp đầy hiện tại" value={percent(kpis.occupancy)} footnote="Theo trạng thái phòng đang sử dụng tại các cơ sở" />
      <Metric label="Tổng chi phí" value={money(kpis.cost)} footnote="Chi phí vận hành trong kỳ" />
      <Metric label="Lợi nhuận gộp" value={money(kpis.grossProfit)} footnote="Doanh thu trừ tổng chi phí" tone={kpis.grossProfit >= 0 ? 'positive' : 'negative'} />
      <Metric label="Cơ sở hoạt động" value={`${kpis.activeBranches} / ${kpis.branchTotal}`} footnote={`${kpis.suspendedBranches} cơ sở tạm ngừng`} />
    </div>

    <div className="executive-chart-grid">
      <article className="executive-panel executive-panel-branch-revenue">
        <PanelHeading title="Doanh thu theo cơ sở" description="Chọn cột để xem số liệu tổng hợp của cơ sở." />
        <label className="executive-chart-select">Chọn cơ sở xem số liệu
          <select value={selectedBranchId} onChange={(event) => setSelectedBranchId(event.target.value)}>
            <option value="">Chọn cơ sở…</option>{safeData.revenueByBranch.map((branch) => <option value={branch.id} key={branch.id}>{branch.name}</option>)}
          </select>
        </label>
        <div className="executive-chart-area executive-chart-area-branches" style={{ height: Math.max(266, safeData.revenueByBranch.length * 32) }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={safeData.revenueByBranch} layout="vertical" margin={{ top: 8, right: 34, left: 8, bottom: 8 }} onClick={selectChartBranch}>
              <CartesianGrid horizontal={false} stroke="#e8eaf1" strokeDasharray="3 5" />
              <XAxis type="number" tickFormatter={axisMoney} tickLine={false} axisLine={false} tick={{ fill: '#59657a', fontSize: 12 }} />
              <YAxis type="category" dataKey="name" width={180} tickLine={false} axisLine={false} tick={{ fill: '#39445a', fontSize: 12 }} />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: '#f5f4fa' }} />
              <Bar dataKey="revenue" name="Doanh thu dịch vụ" fill="#6a3ee8" radius={[0, 4, 4, 0]} barSize={18} cursor="pointer" onClick={(item) => item?.payload?.id && setSelectedBranchId(item.payload.id)} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        {selectedBranch && <div className="executive-branch-insight" aria-live="polite">
          <div><strong>{selectedBranch.name}</strong><small>{selectedBranch.code} · {selectedBranch.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}</small></div>
          <span>Doanh thu <b>{money(selectedBranch.revenue)}</b></span><span>Bill hoàn tất <b>{new Intl.NumberFormat('vi-VN').format(selectedBranch.bookings)}</b></span><span>Lấp đầy <b>{percent(selectedBranch.occupancy)}</b></span><span>Chi phí <b>{money(selectedBranch.cost)}</b></span>
          <button type="button" aria-label="Đóng chi tiết cơ sở" onClick={() => setSelectedBranchId('')}>Đóng</button>
        </div>}
      </article>

      <article className="executive-panel executive-panel-trend">
        <PanelHeading title="Xu hướng doanh thu" description="Doanh thu theo ngày, tách tiền phòng và F&B." />
        <div className="executive-chart-area executive-chart-area-tall">
          {safeData.revenueByDay.length ? <ResponsiveContainer width="100%" height="100%">
            <LineChart data={safeData.revenueByDay} margin={{ top: 10, right: 14, left: 3, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="#e8eaf1" strokeDasharray="3 5" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={18} tick={{ fill: '#758096', fontSize: 10 }} />
              <YAxis tickFormatter={axisMoney} tickLine={false} axisLine={false} width={48} tick={{ fill: '#758096', fontSize: 10 }} />
              <Tooltip content={<ChartTooltip />} />
              <Legend verticalAlign="top" align="right" height={32} iconType="circle" wrapperStyle={{ fontSize: 11 }} />
              <Line type="monotone" dataKey="roomRevenue" name="Tiền phòng" stroke="#6a3ee8" strokeWidth={2.5} dot={{ r: 3, strokeWidth: 0 }} activeDot={{ r: 5 }} />
              <Line type="monotone" dataKey="fnbRevenue" name="F&B" stroke="#168b87" strokeWidth={2.5} dot={{ r: 3, strokeWidth: 0 }} activeDot={{ r: 5 }} />
            </LineChart>
          </ResponsiveContainer> : <p className="executive-empty">API báo cáo hiện chưa có chuỗi doanh thu theo ngày.</p>}
        </div>
      </article>

      <article className="executive-panel executive-panel-mix">
        <PanelHeading title="Cơ cấu doanh thu" description="Tỷ trọng theo nhóm khoản thu trong kỳ." />
        <div className="executive-chart-area executive-chart-area-donut">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={safeData.revenueMix} dataKey="value" nameKey="name" innerRadius="62%" outerRadius="88%" paddingAngle={3} stroke="none">
                {safeData.revenueMix.map((entry) => <Cell key={entry.name} fill={entry.color} />)}
              </Pie>
              <Tooltip formatter={tooltipMoney} />
            </PieChart>
          </ResponsiveContainer>
          <div className="executive-donut-center"><strong>{money(selectedRevenueMix)}</strong><span>Tổng doanh thu</span></div>
        </div>
        <div className="executive-mix-legend">{safeData.revenueMix.length ? safeData.revenueMix.map((item) => <div key={item.name}><span><i style={{ background: item.color }} />{item.name}</span><strong>{money(item.value)}</strong></div>) : <p className="executive-empty">Không có giao dịch thu bill trong kỳ.</p>}</div>
      </article>

      <article className="executive-panel executive-panel-occupancy">
        <PanelHeading title="Tỷ lệ lấp đầy theo cơ sở" description="Trạng thái phòng đang sử dụng tại thời điểm xem." />
        <div className="executive-chart-area executive-chart-area-occupancy" style={{ height: Math.max(278, safeData.occupancyByBranch.length * 44) }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={safeData.occupancyByBranch} layout="vertical" margin={{ top: 8, right: 46, left: 8, bottom: 8 }}>
              <CartesianGrid horizontal={false} stroke="#e8eaf1" strokeDasharray="3 5" />
              <XAxis type="number" domain={[0, 1]} tickFormatter={(value) => `${Math.round(value * 100)}%`} tickLine={false} axisLine={false} tick={{ fill: '#758096', fontSize: 10 }} />
              <YAxis type="category" dataKey="name" width={250} tickLine={false} axisLine={false} tick={{ fill: '#39445a', fontSize: 11 }} />
              <Tooltip formatter={(value) => percent(value)} cursor={{ fill: '#f5f4fa' }} />
              <Bar dataKey="occupancy" name="Tỷ lệ lấp đầy" fill="#168b87" radius={[0, 5, 5, 0]} barSize={16} label={{ position: 'right', formatter: (value) => percent(value), fill: '#39445a', fontSize: 10 }} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </article>
    </div>

    <article className="executive-panel executive-ranking">
      <PanelHeading title="Xếp hạng cơ sở" description="So sánh kết quả kinh doanh và trạng thái vận hành." trailing={<span className="executive-count">{safeData.ranking.length} cơ sở</span>} />
      <RankingTable rows={safeData.ranking} />
    </article>

    <div className="executive-bottom-grid">
      <article className="executive-panel executive-approvals">
        <PanelHeading title="Việc cần duyệt" description="Đề nghị chi đang chờ xử lý từ API tài chính." trailing={<span className="executive-pending-count">{safeData.approvals.length} chờ duyệt</span>} />
        {safeData.approvals.length ? <div className="executive-approval-list">{safeData.approvals.map((item) => <div className="executive-approval-row" key={item.id}>
          <div><strong>{item.category}</strong><span>{item.branch} · {item.id}</span></div><b>{money(item.amount)}</b>
        </div>)}</div> : <p className="executive-empty">Không có đề nghị vượt ngưỡng cần duyệt trong kỳ.</p>}
      </article>

      <article className="executive-panel executive-warnings">
        <PanelHeading title="Cảnh báo vận hành" description="Trạng thái cơ sở trong hồ sơ hệ thống." trailing={<span className="executive-warning-count">{safeData.warnings.length} cảnh báo</span>} />
        {safeData.warnings.length ? <ul className="executive-warning-list">{safeData.warnings.map((item) => <li key={item.id} className={`is-${item.level}`}>
          <span className="executive-warning-mark" aria-hidden="true" /><div><strong>{item.kind}</strong><p>{item.branch} · {item.detail}</p></div>
        </li>)}</ul> : <p className="executive-empty">Không có cảnh báo cần theo dõi.</p>}
      </article>
    </div>
  </section>;
}
