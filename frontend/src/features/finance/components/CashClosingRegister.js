import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { PaginatedList, PaginatedTable } from '../../../shared/components/Pagination';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, today, vnd } from '../services/financeApi';
import { dateKey, dateLabel, dateTimeLabel } from '../utils/financeDates';

const path = '/accounting/cash-closing';
const statuses = { SUBMITTED: 'Chờ đối soát', NEEDS_EXPLANATION: 'Cần bổ sung', RECONCILED: 'Đã đối soát', NOT_SUBMITTED: 'Chưa gửi' };
const unreconciled = new Set(['SUBMITTED', 'NEEDS_EXPLANATION']);
const tone = { SUBMITTED: 'pending', NEEDS_EXPLANATION: 'pending', RECONCILED: 'received', NOT_SUBMITTED: 'neutral' };
const errorText = (failure) => Object.values(apiErrors(failure)).join(' ') || 'Không tải được dữ liệu đối soát.';
const money = (value) => value == null ? '—' : vnd(value);

function filterFromUrl(search) {
  const query = new URLSearchParams(search);
  const from = dateKey(query.get('from') || query.get('date')) || today();
  const to = dateKey(query.get('to')) || from;
  return { mode: from === to ? 'day' : 'range', day: dateKey(query.get('date')) || to,
    from, to, branchId: query.get('branchId') || '', status: query.get('status') || '' };
}

export default function CashClosingRegister({ refresh, busy, onOpen }) {
  const { search, hash } = useLocation();
  const [filter, setFilter] = useState(() => filterFromUrl(search));
  const [rows, setRows] = useState([]);
  const [branches, setBranches] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [notificationsOpen, setNotificationsOpen] = useState(hash === '#cash-closing-notifications-title');
  const [query, setQuery] = useState('');
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notificationError, setNotificationError] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingNotices, setLoadingNotices] = useState(true);
  const { mode, day, from, to, branchId, status } = filter;
  const isDay = mode === 'day';
  const invalidRange = !isDay && from > to;

  useEffect(() => { setFilter(filterFromUrl(search)); }, [search]);

  useEffect(() => {
    if (hash === '#cash-closing-notifications-title') {
      setNotificationsOpen(true);
      document.getElementById('cash-closing-notifications-title')?.focus();
    }
  }, [hash]);

  useEffect(() => {
    if (invalidRange) return undefined;
    let active = true;
    setRows([]);
    setError('');
    setLoading(true);
    api.get(isDay ? path + '/statuses' : path, isDay ? { date: day } : { from, to })
      .then((result) => {
        if (!active) return;
        const normalized = result.map((item) => ({
          id: item.closingId || item.id, branchId: item.branchId || item.branch_id,
          branchName: item.branchName, day: dateKey(item.businessDate || item.business_date),
          status: item.status, submittedAt: item.submittedAt || item.submitted_at,
          expected: item.expectedClosing ?? item.expected_closing, actual: item.actualClosing ?? item.actual_closing,
          variance: item.variance, explanation: item.explanation,
        }));
        setRows(normalized);
        setBranches((old) => [...new Map([...old, ...normalized.map((item) => [item.branchId, item.branchName])]).entries()]
          .sort((left, right) => left[1].localeCompare(right[1], 'vi')));
      })
      .catch((failure) => { if (active) setError(errorText(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [isDay, day, from, to, invalidRange, revision, refresh]);

  useEffect(() => {
    let active = true;
    setNotificationError('');
    setLoadingNotices(true);
    api.get(path + '/notifications').then((result) => { if (active) setNotifications(result); })
      .catch((failure) => { if (active) setNotificationError(errorText(failure)); })
      .finally(() => { if (active) setLoadingNotices(false); });
    return () => { active = false; };
  }, [revision, refresh]);

  function update(key, value) {
    setFilter((current) => ({ ...current, [key]: value, ...(key === 'mode' ? { status: '' } : {}) }));
  }

  const scoped = invalidRange ? [] : rows.filter((item) => !branchId || item.branchId === branchId);
  const shown = scoped.filter((item) => (!status || (status === 'UNRECONCILED' ? unreconciled.has(item.status) : item.status === status))
    && `${item.branchName} ${item.day} ${item.explanation || ''}`.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi')));
  const availableStatuses = Object.entries(statuses).filter(([key]) => isDay || key !== 'NOT_SUBMITTED');

  return <>
    <dl className="finance-workspace-metrics" aria-label="Tổng hợp đối soát">
      {availableStatuses.map(([key, label]) => <div key={key}><dt>{label}</dt>
        <dd className={`is-${tone[key]}`}>{loading || invalidRange || error ? '—' : scoped.filter((row) => row.status === key).length}</dd></div>)}
    </dl>
    <section className="finance-panel finance-workspace-view" aria-labelledby="cash-register-title" aria-busy={loading && !invalidRange}>
      <header className="finance-workspace-header"><div><h2 id="cash-register-title">Báo cáo chốt quỹ</h2>
        <span>{isDay ? dateLabel(day) : `${dateLabel(from)} – ${dateLabel(to)}`}</span></div>
        <button type="button" disabled={loading && !invalidRange} onClick={() => setRevision((value) => value + 1)}>Tải lại</button>
      </header>
      <div className="finance-workspace-filters" role="group" aria-label="Lọc báo cáo chốt quỹ">
        <label>Xem theo<select value={mode} onChange={(event) => update('mode', event.target.value)}>
          <option value="day">Ngày</option><option value="range">Khoảng ngày</option></select></label>
        {isDay ? <label>Ngày<input type="date" max={today()} value={day}
          onChange={(event) => event.target.value && update('day', event.target.value)}/></label> : <>
          <label>Từ ngày<input type="date" max={today()} value={from} onChange={(event) => event.target.value && update('from', event.target.value)}/></label>
          <label>Đến ngày<input type="date" max={today()} value={to} onChange={(event) => event.target.value && update('to', event.target.value)}/></label>
        </>}
        <label>Cơ sở<select value={branchId} onChange={(event) => update('branchId', event.target.value)}>
          <option value="">Tất cả cơ sở</option>{branches.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select></label>
        <label>Trạng thái<select value={status} onChange={(event) => update('status', event.target.value)}>
          <option value="">Tất cả trạng thái</option><option value="UNRECONCILED">Chưa hoàn tất đối soát</option>
          {availableStatuses.map(([key, name]) => <option key={key} value={key}>{name}</option>)}
        </select></label>
        <label>Tìm báo cáo<input type="search" value={query} placeholder="Tên cơ sở, ngày…" onChange={(event) => setQuery(event.target.value)}/></label>
      </div>
      {invalidRange && <p className="finance-error" role="alert">Từ ngày phải bằng hoặc trước Đến ngày.</p>}
      {!invalidRange && error && <p className="finance-error" role="alert">{error}</p>}
      {!invalidRange && loading && <p className="finance-empty" role="status">Đang tải báo cáo…</p>}
      {!invalidRange && !loading && !error && <div className="finance-table-wrap finance-workspace-table" role="region" aria-label="Danh sách đối soát quỹ" tabIndex={0}>
        <PaginatedTable pageSize={10} resetKey={JSON.stringify([mode, day, from, to, branchId, status, query])} paginationLabel="Phân trang báo cáo chốt quỹ">
          <thead><tr><th scope="col">Ngày</th><th scope="col">Cơ sở</th><th scope="col" className="finance-workspace-money">Dự kiến</th>
            <th scope="col" className="finance-workspace-money">Thực đếm</th><th scope="col" className="finance-workspace-money">Chênh lệch</th>
            <th scope="col">Trạng thái</th><th scope="col">Thời điểm gửi</th><th scope="col">Thao tác</th></tr></thead>
          <tbody>{shown.map((item) => <tr key={item.id || `${item.branchId}-${item.day}`}>
            <td>{dateLabel(item.day)}</td><td><strong>{item.branchName}</strong></td>
            <td className="finance-workspace-money">{money(item.expected)}</td><td className="finance-workspace-money">{money(item.actual)}</td>
            <td className={`finance-workspace-money ${Number(item.variance) !== 0 && item.variance != null ? 'finance-workspace-variance' : ''}`}>{money(item.variance)}</td>
            <td><span className={`finance-status ${tone[item.status]}`}>{statuses[item.status] || item.status}</span></td>
            <td>{dateTimeLabel(item.submittedAt)}</td><td>{item.id
              ? <button type="button" disabled={busy} aria-haspopup="dialog" onClick={() => onOpen(item.id)}>Mở báo cáo</button> : '—'}</td>
          </tr>)}
            {!shown.length && <tr><td colSpan="8" className="finance-empty">Không có báo cáo phù hợp.</td></tr>}
          </tbody>
        </PaginatedTable>
      </div>}
    </section>
    <details className="finance-panel finance-workspace-notices" open={notificationsOpen}
      onToggle={(event) => setNotificationsOpen(event.currentTarget.open)}>
      <summary id="cash-closing-notifications-title">Thông báo báo cáo đã gửi <span>{notifications.length}</span></summary>
      {loadingNotices ? <p role="status">Đang tải thông báo…</p>
        : notificationError ? <p className="finance-error" role="alert">{notificationError}</p>
          : notifications.length ? <PaginatedList as="ul" pageSize={5} paginationLabel="Phân trang thông báo chốt quỹ">
            {notifications.map((item) => <li key={item.id}>
              <Link to={`/ke-toan/chot-quy?${new URLSearchParams({ reportId: item.closingId, date: dateKey(item.businessDate) })}`}>
                <strong>{item.branchName}</strong><span>Ngày {dateLabel(item.businessDate)} · Lần gửi {item.reportVersion} · {dateTimeLabel(item.sentAt)}</span>
              </Link>
            </li>)}</PaginatedList> : <p>Chưa có thông báo gửi báo cáo.</p>}
    </details>
  </>;
}
