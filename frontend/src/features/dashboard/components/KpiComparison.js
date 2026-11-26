import { periodNames } from './DashboardPeriodControls';
import '../styles/kpi-comparison.css';

const percentage = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const formatDate = (date) => date.split('-').reverse().join('/');
const icons = {
  up: 'M12 19V5m-5 5 5-5 5 5',
  down: 'M12 5v14m-5-5 5 5 5-5',
  flat: 'M6 12h12',
};

export function percentChange(current, previous) {
  if (current == null || previous == null || Number(previous) === 0) return null;
  return ((Number(current) - Number(previous)) / Math.abs(Number(previous))) * 100;
}

export default function KpiComparison({ change, periodType, from, to, lowerIsBetter = false }) {
  const period = `${periodNames[periodType]?.toLowerCase() || 'kỳ'} trước`;
  const hasChange = Number.isFinite(change);
  let direction = 'flat';
  let tone = 'neutral';
  let value = '—';
  let detail = 'Chưa có giá trị gốc để tính tỷ lệ';
  if (hasChange) {
    direction = change > 0 ? 'up' : change < 0 ? 'down' : 'flat';
    if (direction !== 'flat') tone = (change > 0) !== lowerIsBetter ? 'positive' : 'negative';
    const magnitude = Math.abs(change);
    value = `${change > 0 ? '+' : change < 0 ? '−' : ''}${magnitude > 0 && magnitude < 0.05 ? '<0,1' : percentage.format(magnitude)}%`;
    detail = direction === 'flat' ? 'Không đổi' : `${direction === 'up' ? 'Tăng' : 'Giảm'} ${value.slice(1)}`;
  }
  const dates = from && to ? ` (${formatDate(from)}${to === from ? '' : ` – ${formatDate(to)}`})` : '';
  const description = `${detail} so với ${period}${dates}`;
  return <div className="kpi-comparison" title={description}>
    <span className="kpi-comparison__accessible">{description}</span>
    <span className={`kpi-comparison__badge kpi-comparison__badge--${tone}`} aria-hidden="true">
      {hasChange && <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d={icons[direction]} /></svg>}
      {value}
    </span>
    <span className="kpi-comparison__period" aria-hidden="true">{period}</span>
  </div>;
}
