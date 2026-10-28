/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Lịch tuần 7 ngày × 4 ca; UC-83–86, BR-53, BR-60, BR-177.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import {
  shiftApi,
  addDays,
  monday,
  today,
  dateLabel,
  shiftLabel,
  slotKey,
} from '../services/shiftApi';
import ShiftDialog from '../components/ShiftDialog';
import '../styles/shifts.css';
export default function ShiftSchedulePage({ user, branchScope, readOnly = false }) {
  const manager = !!branchScope;
  const [week, setWeek] = useState(() => addDays(monday(today()), manager ? 0 : 7));
  const [data, setData] = useState(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState('');
  const [search, setSearch] = useState(''),
    [filter, setFilter] = useState('ALL'),
    [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0),
    [dialog, setDialog] = useState(null),
    [preparing, setPreparing] = useState(false);
  const modal = useRef(null);
  modal.current = dialog;
  useEffect(() => {
    let active = true,
      pending = false;
    const load = async (quiet = false) => {
      if (pending) return;
      pending = true;
      if (!quiet) {
        setLoading(true);
        setError('');
      }
      try {
        const result = await shiftApi.weekly(week, branchScope);
        if (active) {
          setData(result);
          setError('');
        }
      } catch (e) {
        if (active) setError(Object.values(apiErrors(e)).join(' '));
      } finally {
        pending = false;
        if (active) setLoading(false);
      }
    };
    load();
    const refresh = () => {
      if (!modal.current && !document.hidden) load(true);
    };
    const timer = setInterval(refresh, 15000);
    window.addEventListener('focus', refresh);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
    };
  }, [week, branchScope, revision]);
  async function publish(targetWeek, focusSlot = null) {
    setPreparing(true);
    setError('');
    try {
      const result = await shiftApi.weekly(targetWeek, branchScope);
      setDialog({ type: 'publish', data: result, focusSlot });
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
    } finally {
      setPreparing(false);
    }
  }
  const readonly = readOnly || data?.readOnly;
  const days = Array.from({ length: 7 }, (_, i) => addDays(week, i));
  const slots = new Map(
    (data?.shifts || [])
      .filter((s) => s.status !== 'CANCELLED')
      .map((s) => [slotKey(s.date, s.templateId), s])
  );
  const open = (type, shift, assignment) =>
    setDialog({ type, data: { ...data, readOnly: readonly }, shift, assignment });
  const published = Array.from(slots.values());
  const matchesSlot = (s, people) => {
    if (
      search &&
      !people.some((a) => `${a.fullName} ${a.code}`.toLowerCase().includes(search.toLowerCase()))
    )
      return false;
    if (filter === 'UNPUBLISHED') return !s;
    if (filter === 'SHORT') return !!s && s.registered < s.capacity;
    if (filter === 'FULL') return !!s && s.registered >= s.capacity;
    if (filter === 'MINE') return people.length > 0;
    return true;
  };
  const hasMatches = data?.templates.some((t) =>
    days.some((date) => {
      const s = slots.get(slotKey(date, t.id));
      return matchesSlot(s, s ? data.assignments.filter((a) => a.shiftId === s.id) : []);
    })
  );
  return (
    <section className="shift-page" aria-labelledby="shift-title">
      <header className="shift-heading">
        <div>
          <p className="shift-eyebrow">CA LÀM VIỆC · {data?.branchName || user.branchName}</p>
          <h1 id="shift-title">{manager ? 'Lịch ca tuần' : 'Đăng ký ca làm'}</h1>
          {!manager && (
            <p>
              Đăng ký ca chưa bắt đầu trong tuần này hoặc tuần tới tại cơ sở của bạn. Đăng ký hợp lệ được xác nhận tự động.
            </p>
          )}
        </div>
        {manager && (
          <button
            className="shift-primary"
            disabled={readonly || !data || preparing || loading}
            onClick={() => publish(data.nextWeek)}
          >
            {preparing ? 'Đang tải…' : 'Mở / chỉnh ca tuần tới'}
          </button>
        )}
      </header>
      {notice && (
        <div className="shift-message success" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="shift-message error" role="alert">
          {error}
          <button onClick={() => setRevision((v) => v + 1)}>Tải lại lịch</button>
        </div>
      )}
      <div className="shift-toolbar">
        <div className="shift-week-nav">
          <button
            aria-label="Tuần trước"
            disabled={!!dialog || loading}
            onClick={() => setWeek(addDays(week, -7))}
          >
            ‹
          </button>
          <div>
            <strong>
              {dateLabel(week)} – {dateLabel(addDays(week, 6))}
            </strong>
            <small>Thứ Hai đến Chủ nhật</small>
          </div>
          <button
            aria-label="Tuần sau"
            disabled={!!dialog || loading}
            onClick={() => setWeek(addDays(week, 7))}
          >
            ›
          </button>
          <button onClick={() => setWeek(data?.currentWeek || monday(today()))}>
            Tuần hiện tại
          </button>
          {manager && (
            <button
              className="shift-primary"
              disabled={readonly || loading || preparing || !data?.editableSlots?.length}
              onClick={() => publish(week)}
            >
              Mở / chỉnh ca tuần đang xem
            </button>
          )}
          {!manager && (
            <button onClick={() => setWeek(data?.nextWeek || addDays(monday(today()), 7))}>
              Tuần đăng ký
            </button>
          )}
        </div>
        <div className="shift-filters">
          {manager && (
            <input
              aria-label="Tìm nhân viên trong lịch"
              placeholder="Tìm tên / mã nhân viên…"
              maxLength={200}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          )}
          <select aria-label="Lọc ca" value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="ALL">Tất cả ca</option>
            <option value="SHORT">Còn thiếu người</option>
            <option value="FULL">Đã đủ người</option>
            <option value="UNPUBLISHED">Chưa mở</option>
            {!manager && <option value="MINE">Ca của tôi</option>}
          </select>
        </div>
      </div>
      {loading ? (
        <p className="shift-loading" role="status">
          Đang tải lịch ca…
        </p>
      ) : (
        data && (
          <>
            <div className="shift-stats">
              <span>
                <strong>{published.length}/28</strong> ca đã công bố
              </span>
              <span>
                <strong>{published.filter((s) => s.registered < s.capacity).length}</strong> ca
                thiếu người
              </span>
              <span>
                <strong>{published.reduce((n, s) => n + s.registered, 0)}</strong> lượt phân công
              </span>
              <span className="shift-help">Bốn khung giờ cố định · Giờ Việt Nam</span>
            </div>
            {!manager && ![data.currentWeek, data.nextWeek].includes(data.week) && (
              <p className="shift-message">
                Chỉ có thể đăng ký ca chưa bắt đầu trong tuần hiện tại hoặc tuần tới.
              </p>
            )}
            {!published.length && (
              <p className="shift-message">Chưa có ca được công bố trong tuần này.</p>
            )}
            {!hasMatches && (
              <p className="shift-message" role="status">
                Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.
              </p>
            )}
            <div className="shift-grid-wrap">
              <table className="shift-week-grid">
                <caption className="shift-sr-only">
                  Lịch tuần {dateLabel(week)} tại {data.branchName}
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Ca / giờ</th>
                    {days.map((date, day) => (
                      <th scope="col" key={date} className={date === today() ? 'shift-today' : ''}>
                        <span>
                          {['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'][day]}
                        </span>
                        <strong>{dateLabel(date).slice(0, 5)}</strong>
                        {date === today() && <small>Hôm nay</small>}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.templates.map((t, index) => (
                    <tr key={t.id}>
                      <th scope="row">
                        <strong>Ca {index + 1}</strong>
                        <span>{shiftLabel(t)}</span>
                      </th>
                      {days.map((date) => {
                        const s = slots.get(slotKey(date, t.id)),
                          people = s ? data.assignments.filter((a) => a.shiftId === s.id) : [],
                          full = s && s.registered >= s.capacity,
                          startTime = s ? new Date(s.startsAt).getTime() : NaN,
                          started = !Number.isFinite(startTime) || startTime <= Date.now();
                        const matches = matchesSlot(s, people);
                        return (
                          <td
                            key={date}
                            className={`${matches ? '' : 'shift-dim'} ${date === today() ? 'shift-today-column' : ''}`}
                          >
                            <article
                              className={`shift-slot ${!s ? 'unpublished' : full ? 'full' : 'short'}`}
                              aria-label={`${dateLabel(date)} ca ${index + 1}`}
                            >
                              {s ? (
                                <>
                                  <div className="shift-slot-count">
                                    <strong>
                                      {s.registered}
                                      <span>/{s.capacity}</span>
                                    </strong>
                                    <span className={`shift-badge ${full ? 'full' : 'short'}`}>
                                      {full ? 'Đủ người' : `Thiếu ${s.capacity - s.registered}`}
                                    </span>
                                  </div>
                                  <small className="shift-slot-status">
                                    {s.status === 'OPEN' ? 'Mở đăng ký' : 'Đóng đăng ký'}
                                  </small>
                                  {manager ? (
                                    <ul>
                                      {people.map((a) => (
                                        <li key={a.id}>
                                          <span title={`${a.code} · ${a.fullName}`}>
                                            {a.fullName}
                                          </span>
                                          {a.source === 'MANAGER' && <small>Làm thay</small>}
                                        </li>
                                      ))}
                                    </ul>
                                  ) : people.length ? (
                                    <p className="shift-mine">✓ Bạn đã đăng ký</p>
                                  ) : (
                                    <p className="shift-help">
                                      Còn {Math.max(0, s.capacity - s.registered)} chỗ
                                    </p>
                                  )}
                                  {manager ? (
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      onClick={() => open('detail', s)}
                                    >
                                      Chi tiết / làm thay
                                    </button>
                                  ) : (
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      disabled={
                                        readonly ||
                                        people.length > 0 ||
                                        full ||
                                        s.status !== 'OPEN' ||
                                        ![data.currentWeek, data.nextWeek].includes(data.week) ||
                                        started
                                      }
                                      onClick={() => open('register', s)}
                                    >
                                      {people.length
                                        ? 'Đã xác nhận'
                                        : started
                                          ? 'Ca đã bắt đầu'
                                          : 'Đăng ký ca'}
                                    </button>
                                  )}
                                </>
                              ) : (
                                <>
                                  <span className="shift-unpublished-mark">—</span>
                                  <p>Chưa mở ca</p>
                                  {manager && data.editableSlots?.includes(slotKey(date, t.id)) && (
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      disabled={readonly || preparing}
                                      onClick={() => publish(week, slotKey(date, t.id))}
                                    >
                                      Mở ca
                                    </button>
                                  )}
                                </>
                              )}
                            </article>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="shift-legend">
              <span>
                <i className="full" /> Đã đủ người
              </span>
              <span>
                <i className="short" /> Còn thiếu người
              </span>
              <span>
                <i className="unpublished" /> Chưa công bố
              </span>
              <span>Ca 4 kết thúc lúc 00:00 ngày kế tiếp.</span>
            </div>
          </>
        )
      )}
      {dialog && (
        <ShiftDialog
          key={`${dialog.type}/${dialog.assignment?.id || dialog.shift?.id || dialog.data.week}`}
          dialog={{
            ...dialog,
            onReplace: (assignment) => open('replace', dialog.shift, assignment),
          }}
          manager={manager}
          scope={branchScope}
          onClose={() => {
            setDialog(null);
            setRevision((v) => v + 1);
          }}
          onReload={() => {
            setDialog(null);
            setRevision((v) => v + 1);
          }}
          onSaved={(next, message) => {
            setData(next);
            setWeek(next.week);
            setDialog(null);
            setNotice(message);
          }}
        />
      )}
    </section>
  );
}
