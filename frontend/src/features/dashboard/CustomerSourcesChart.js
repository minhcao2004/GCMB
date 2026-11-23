import { useEffect, useId, useState } from 'react';
import api from '../../api/client';
import { branchAdministrationApi } from '../branch/services/branchAdministrationApi';
import DashboardPeriodControls, { initialPeriodValue } from './DashboardPeriodControls';
import './customer-sources-chart.css';

const platforms = [
  ['FACEBOOK', 'Facebook', 'var(--gcmb-color-info)'],
  ['ZALO', 'Zalo', 'var(--gcmb-color-success)'],
  ['TIKTOK', 'TikTok', 'var(--gcmb-color-ink)'],
  ['INSTAGRAM', 'Instagram', 'var(--gcmb-color-primary)'],
];
const formatCount = new Intl.NumberFormat('vi-VN');

export default function CustomerSourcesChart({ branchId, headOffice = false, className = '' }) {
  const titleId = useId();
  const [period, setPeriod] = useState('MONTH');
  const [date, setDate] = useState(() => initialPeriodValue('MONTH'));
  const [selectedBranch, setSelectedBranch] = useState('');
  const [branches, setBranches] = useState([]);
  const [branchError, setBranchError] = useState(false);
  const [result, setResult] = useState(null);
  const scopedBranch = headOffice ? selectedBranch : branchId;
  const queryKey = `${headOffice}-${scopedBranch}-${period}-${date}`;
  const canQuery = Boolean(date && (headOffice || scopedBranch));

  useEffect(() => {
    if (!headOffice) return undefined;
    let active = true;
    branchAdministrationApi.list().then((options) => {
      if (active) setBranches(options.map(({ branchId: id, branchName: name }) => ({ id, name })));
    }).catch(() => { if (active) setBranchError(true); });
    return () => { active = false; };
  }, [headOffice]);

  useEffect(() => {
    if (!canQuery) return undefined;
    const controller = new AbortController();
    const path = headOffice ? 'head-office' : 'branch-management';
    api.get(`/${path}/dashboard/customer-sources`, {
      params: { period, date, ...(headOffice && scopedBranch ? { branchId: scopedBranch } : {}) },
      headers: !headOffice ? { 'X-GCMB-Branch-Id': scopedBranch } : undefined,
      signal: controller.signal,
    }).then(({ data }) => {
      if (!controller.signal.aborted) setResult({ key: queryKey, sources: data.sources });
    }).catch(() => {
      if (!controller.signal.aborted) setResult({ key: queryKey, error: true });
    });
    return () => controller.abort();
  }, [canQuery, date, headOffice, period, queryKey, scopedBranch]);

  const report = result?.key === queryKey ? result : null;
  const counts = new Map(report?.sources?.map(({ channel, count }) => [channel, count]));
  const rows = platforms.map(([channel, label, color]) => ({ label, color, count: counts.get(channel) ?? 0 }))
    .sort((left, right) => right.count - left.count);
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  const largestCount = rows[0].count || 1;
  const loading = canQuery && !report;

  return <section className={`customer-sources-chart ${className}`} aria-labelledby={titleId}>
    <header className="customer-sources-chart__heading">
      <h2 id={titleId}>Nguồn khách</h2>
      <p>Lượt đặt không hủy theo ngày nhận phòng dự kiến.</p>
    </header>
    <div className="customer-sources-chart__filters">
      <DashboardPeriodControls id="nguồn khách" type={period} value={date} onTypeChange={setPeriod} onValueChange={setDate}
        className="customer-sources-chart__period" fieldClassName="customer-sources-chart__field" />
      {headOffice && <label className="customer-sources-chart__field"><span>Cơ sở</span>
        <select aria-label="Cơ sở nguồn khách" value={selectedBranch} onChange={(event) => setSelectedBranch(event.target.value)}>
          <option value="">Toàn chuỗi</option>
          {branches.map(({ id, name }) => <option key={id} value={id}>{name}</option>)}
        </select>
      </label>}
      {branchError && <p className="customer-sources-chart__message" role="alert">Không tải được danh sách cơ sở.</p>}
    </div>
    <div className="customer-sources-chart__result" aria-busy={loading}>
      {!canQuery ? <p className="customer-sources-chart__message">{date ? 'Chưa xác định cơ sở.' : 'Chọn kỳ để xem nguồn khách.'}</p>
        : loading ? <p className="customer-sources-chart__message" role="status">Đang tải nguồn khách…</p>
          : report.error ? <p className="customer-sources-chart__message" role="alert">Không tải được dữ liệu nguồn khách.</p>
            : <>
              <p className="customer-sources-chart__total" role="status">Tổng <strong>{formatCount.format(total)}</strong> lượt đặt từ 4 nền tảng</p>
              <ul className="customer-sources-chart__bars" aria-label="Lượt đặt theo nền tảng">
                {rows.map(({ label, count, color }) => <li key={label}>
                  <span>{label}</span>
                  <span className="customer-sources-chart__track" aria-hidden="true">
                    <span style={{ width: `${count / largestCount * 100}%`, backgroundColor: color }} />
                  </span>
                  <strong>{formatCount.format(count)}</strong>
                </li>)}
              </ul>
            </>}
    </div>
  </section>;
}
