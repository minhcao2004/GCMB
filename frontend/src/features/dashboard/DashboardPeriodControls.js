const periodNames = { DAY: 'Ngày', MONTH: 'Tháng', YEAR: 'Năm' };
const dateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
});

export function vietnamToday() {
  const parts = Object.fromEntries(dateFormatter.formatToParts(new Date()).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function initialPeriodValue(type) {
  const today = vietnamToday();
  return type === 'DAY' ? today : type === 'YEAR' ? today.slice(0, 4) : today.slice(0, 7);
}

export default function DashboardPeriodControls({
  id, type, value, onTypeChange, onValueChange,
  className = 'executive-period-controls', fieldClassName = 'executive-compact-filter',
}) {
  const today = vietnamToday();
  const setType = (nextType) => { onTypeChange(nextType); onValueChange(initialPeriodValue(nextType)); };
  return <div className={className}>
    <label className={fieldClassName}><span>Xem theo</span>
      <select aria-label={`Loại kỳ ${id}`} value={type} onChange={(event) => setType(event.target.value)}>
        {Object.entries(periodNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select>
    </label>
    <label className={fieldClassName}><span>{periodNames[type]}</span>
      {type === 'YEAR' ? <select aria-label={`Năm ${id}`} value={value} onChange={(event) => onValueChange(event.target.value)}>
        {Array.from({ length: 7 }, (_, index) => Number(today.slice(0, 4)) - index).map((year) => <option key={year} value={String(year)}>{year}</option>)}
      </select> : <input aria-label={`${periodNames[type]} ${id}`} type={type === 'DAY' ? 'date' : 'month'}
        max={type === 'DAY' ? today : today.slice(0, 7)} value={value} onChange={(event) => onValueChange(event.target.value)} />}
    </label>
  </div>;
}
