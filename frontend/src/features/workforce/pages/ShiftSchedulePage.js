/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Lịch tuần 7 ngày × 4 ca; UC-83–86, BR-53, BR-60, BR-177.
 */
import { useEffect, useRef, useState } from 'react';
import { ToastStack } from '../../../shared/components/Feedback';
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
import AttendanceDialog from '../components/AttendanceDialog';
import { attendanceApi, futureAttendanceMessage } from '../services/attendanceApi';
import '../styles/shifts.css';
import '../styles/attendance.css';
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
  const attendanceRequest = useRef(0);
  const [attendanceWarning, setAttendanceWarning] = useState(null);
  useEffect(() => {
    if (!attendanceWarning) return;
    const timer = setTimeout(() => setAttendanceWarning(null), 5000);
    return () => clearTimeout(timer);
  }, [attendanceWarning]);
  useEffect(() => {
    attendanceRequest.current += 1;
    setDialog(null);
    setNotice('');
    setAttendanceWarning(null);
    return () => { attendanceRequest.current += 1; };
  }, [branchScope, week]);
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
    setNotice('');
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
      .filter((slot) => slot.status !== 'CANCELLED')
      .map((slot) => [slotKey(slot.date, slot.templateId), slot])
  );
  const open = (type, shift, assignment) => {
    setNotice('');
    setDialog({ type, data: { ...data, readOnly: readonly }, shift, assignment });
  };
  async function openAttendance(shift) {
    if (!manager || !shift || modal.current) return;
    if (new Date(shift.startsAt).getTime() > Date.now()) {
      setAttendanceWarning({ message: futureAttendanceMessage });
      return;
    }
    setAttendanceWarning(null);
    setNotice('');
    const request = ++attendanceRequest.current;
    setDialog({ type: 'attendanceLoading', shift });
    setError('');
    try {
      const daily = await attendanceApi.daily(shift.date, branchScope);
      if (request !== attendanceRequest.current) return;
      const rows = daily.rows.filter((row) => row.shiftId === shift.id);
      if (!rows.length) {
        setDialog({ type: 'detail', data: { ...data, readOnly: readonly }, shift });
        return;
      }
      setDialog({
        type: 'attendance',
        shift,
        data: { ...daily, rows },
        row: rows[0],
      });
    } catch (error) {
      if (request === attendanceRequest.current) {
        setDialog(null);
        setError(Object.values(apiErrors(error)).join(' '));
      }
    }
  }
  const published = Array.from(slots.values());
  const matchesSlot = (slot, people) => {
    if (
      search &&
      !people.some((person) => `${person.fullName} ${person.code}`.toLowerCase().includes(search.toLowerCase()))
    )
      return false;
    if (filter === 'UNPUBLISHED') return !slot;
    if (filter === 'SHORT') return !!slot && slot.registered < slot.capacity;
    if (filter === 'FULL') return !!slot && slot.registered >= slot.capacity;
    if (filter === 'MINE') return people.length > 0;
    return true;
  };
  const hasMatches = data?.templates.some((template) =>
    days.some((date) => {
      const slot = slots.get(slotKey(date, template.id));
      return matchesSlot(slot, slot ? data.assignments.filter((assignment) => assignment.shiftId === slot.id) : []);
    })
  );
  return (
    <section className="shift-page" aria-labelledby="shift-title">
      <header className="shift-heading">
        <div>
          <h1 id="shift-title">{manager ? 'Lịch ca tuần' : 'Đăng ký ca làm'}</h1>
        </div>
      </header>
      <ToastStack notice={notice} onCloseNotice={() => setNotice('')} />
      {error && (
        <div className="shift-message error" role="alert">
          {error}
          <button onClick={() => setRevision((previousRevision) => previousRevision + 1)}>Tải lại lịch</button>
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
              Chỉnh tuần hiện tại
            </button>
          )}
          <button onClick={() => setWeek(data?.nextWeek || addDays(monday(today()), 7))}>
            {manager ? 'Xem tuần tới' : 'Tuần đăng ký'}
          </button>
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
                <strong>{published.filter((slot) => slot.registered < slot.capacity).length}</strong> ca
                thiếu người
              </span>
              <span>
                <strong>{published.reduce((total, item) => total + item.registered, 0)}</strong> lượt phân công
              </span>
              <span className="shift-help">Bốn khung giờ cố định · Giờ Việt Nam</span>
            </div>
            {!manager && !data.registrationOpen && (
              <p className="shift-message">
                Đăng ký tuần tới mở từ thứ Hai và chốt lúc 22:00 Chủ nhật.
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
                      <th
                        scope="col"
                        key={date}
                        className={`shift-day ${day >= 5 ? 'shift-weekend' : ''} ${date === today() ? 'shift-today' : ''}`}
                      >
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
                  {data.templates.map((template, index) => (
                    <tr key={template.id} className={`shift-period-${template.startMinute}`}>
                      <th scope="row">
                        <strong>Ca {index + 1}</strong>
                        <span>{shiftLabel(template)}</span>
                      </th>
                      {days.map((date) => {
                        const slot = slots.get(slotKey(date, template.id)),
                          people = slot ? data.assignments.filter((assignment) => assignment.shiftId === slot.id) : [],
                          full = slot && slot.registered >= slot.capacity,
                          startTime = slot ? new Date(slot.startsAt).getTime() : NaN,
                          started = !Number.isFinite(startTime) || startTime <= Date.now();
                        const matches = matchesSlot(slot, people);
                        return (
                          <td
                            key={date}
                            className={`${matches ? '' : 'shift-dim'} ${date === today() ? 'shift-today-column' : ''}`}
                          >
                            <article
                              className={`shift-slot ${!slot ? 'unpublished' : full ? 'full' : 'short'}`}
                              aria-label={`${dateLabel(date)} ca ${index + 1}`}
                              tabIndex={manager && slot ? 0 : undefined}
                              onKeyDown={manager && slot ? (event) => {
                                if (event.target === event.currentTarget &&
                                    (event.key === 'Enter' || event.key === ' ')) {
                                  event.preventDefault();
                                  open('detail', slot);
                                }
                              } : undefined}
                              style={manager && slot ? { cursor: 'pointer' } : undefined}
                              onClick={manager && slot ? (event) => {
                                if (!event.target.closest('button')) open('detail', slot);
                              } : undefined}
                            >
                              {slot ? (
                                <>
                                  <div className="shift-slot-count">
                                    <strong>
                                      {slot.registered}
                                      <span>/{slot.capacity}</span>
                                    </strong>
                                    <span className={`shift-badge ${full ? 'full' : 'short'}`}>
                                      {full ? 'Đủ người' : `Thiếu ${slot.capacity - slot.registered}`}
                                    </span>
                                  </div>
                                  <small className="shift-slot-status">
                                    {slot.status === 'OPEN' && data.registrationOpen
                                      ? 'Mở đăng ký'
                                      : 'Đóng đăng ký'}
                                  </small>
                                  {manager ? (
                                    <ul>
                                      {people.map((person) => (
                                        <li key={person.id}>
                                          <span title={`${person.code} · ${person.fullName}`}>
                                            {person.fullName}
                                          </span>
                                          {person.source === 'MANAGER' && <small>Làm thay</small>}
                                        </li>
                                      ))}
                                    </ul>
                                  ) : people.length ? (
                                    <p className="shift-mine">✓ Bạn đã đăng ký</p>
                                  ) : (
                                    <p className="shift-help">
                                      Còn {Math.max(0, slot.capacity - slot.registered)} chỗ
                                    </p>
                                  )}
                                  {manager ? (
                                    <>
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        openAttendance(slot);
                                      }}
                                    >
                                      Chấm công
                                    </button>
                                    </>
                                  ) : (
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      disabled={
                                        readonly ||
                                        people.length > 0 ||
                                        full ||
                                        slot.status !== 'OPEN' ||
                                        !data.registrationOpen ||
                                        started
                                      }
                                      onClick={() => open('register', slot)}
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
                                  {manager && data.editableSlots?.includes(slotKey(date, template.id)) && (
                                    <button
                                      type="button"
                                      className="shift-slot-action"
                                      disabled={readonly || preparing}
                                      onClick={() => publish(week, slotKey(date, template.id))}
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
      <ToastStack notice={attendanceWarning?.message} noticeVariant="warning" onCloseNotice={() => setAttendanceWarning(null)} />
      {dialog?.type === 'attendanceLoading' && (
        <p className="shift-message" role="status">Đang tải chấm công của ca…</p>
      )}
      {dialog?.type === 'attendance' && (
        <AttendanceDialog
          key={dialog.shift.id}
          dialog={{ type: 'record', row: dialog.row }}
          data={dialog.data}
          scope={branchScope}
          readOnly={readonly || dialog.data.readOnly || dialog.data.period.status === 'CLOSED' ||
            new Date(dialog.shift.startsAt).getTime() > Date.now()}
          onClose={() => { setDialog(null); setRevision((previousRevision) => previousRevision + 1); }}
          onReload={() => { setDialog(null); setRevision((previousRevision) => previousRevision + 1); }}
          onSaved={() => {
            setDialog(null);
            setRevision((previousRevision) => previousRevision + 1);
            setNotice('Đã cập nhật chấm công của ca.');
          }}
        />
      )}
      {dialog && !['attendance', 'attendanceLoading'].includes(dialog.type) && (
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
            setRevision((previousRevision) => previousRevision + 1);
          }}
          onReload={() => {
            setDialog(null);
            setRevision((previousRevision) => previousRevision + 1);
          }}
          onSaved={(next, message) => {
            setData(next);
            setWeek(next.week);
            if (dialog.type === 'detail') {
              setDialog({
                ...dialog,
                data: { ...next, readOnly: readonly },
                shift: next.shifts.find((shift) => shift.id === dialog.shift.id),
              });
            } else {
              setDialog(null);
            }
            setNotice(message);
          }}
        />
      )}
    </section>
  );
}
