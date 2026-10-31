/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, chấm công theo ngày/ca, tổng hợp và chốt công tháng theo mockup.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { today } from '../services/shiftApi';
import { attendanceApi, hoursLabel, dateTimeLabel } from '../services/attendanceApi';
import AttendanceDialog from '../components/AttendanceDialog';
import '../styles/shifts.css';
import '../styles/attendance.css';

export default function AttendancePage({
  branchScope,
  readOnly = false,
  view,
  initialDate,
  onDaily,
}) {
  const monthly = view === 'month';
  const [date, setDate] = useState(initialDate || today());
  const [month, setMonth] = useState(today().slice(0, 7));
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [template, setTemplate] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState(null);
  const modal = useRef(null);
  modal.current = dialog;

  useEffect(() => {
    let active = true;
    let pending = false;
    const load = async (quiet = false) => {
      if (pending) return;
      pending = true;
      if (!quiet) {
        setLoading(true);
        setError('');
      }
      try {
        const result = monthly
          ? await attendanceApi.monthly(month, branchScope)
          : await attendanceApi.daily(date, branchScope);
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
  }, [branchScope, date, month, monthly, revision]);

  const closed = data?.period.status === 'CLOSED';
  const readonly = readOnly || data?.readOnly || closed;
  const matches = (row) =>
    `${row.code} ${row.fullName}`.toLowerCase().includes(search.toLowerCase().trim());
  const rows = (data?.rows || []).filter(
    (row) =>
      matches(row) &&
      (!template || row.templateId === template) &&
      (filter === 'ALL' ||
        (filter === 'MISSING'
          ? !row.attendanceId
          : filter === 'ISSUE'
            ? row.openIssues > 0 || (row.attendanceId && !row.valid)
            : row.status === filter))
  );
  const employees = (data?.employees || []).filter(matches);
  const openRecord = (row = null) => setDialog({ type: 'record', row });

  return (
    <section className="shift-page attendance-page" aria-labelledby="attendance-title">
      <header className="shift-heading">
        <div>
          <p className="shift-eyebrow">
            {data?.branchName || 'Cơ sở đang quản lý'} · CA LÀM & CHẤM CÔNG
          </p>
          <h1 id="attendance-title">
            {monthly
              ? `Bảng công tháng · ${closed ? 'Đã chốt' : 'Chưa chốt'}`
              : 'Chấm công theo ngày / ca'}
          </h1>
          <p>
            {monthly
              ? 'Tổng hợp giờ thực tế đã ghi nhận, không quy đổi giờ đăng ký thành công.'
              : 'Ghi nhận ngày giờ vào / ra thực tế và kiểm tra ngoại lệ của từng phân ca.'}
          </p>
        </div>
        {!monthly && (
          <button
            className="shift-primary"
            disabled={loading || readonly || !data?.rows.length}
            onClick={() => openRecord()}
          >
            Ghi nhận chấm công
          </button>
        )}
        {monthly && (
          <span className={`attendance-badge ${closed ? 'closed' : ''}`}>
            {closed ? 'Đã chốt' : 'Chưa chốt'}
          </span>
        )}
      </header>
      {notice && (
        <p className="shift-message success" role="status">
          {notice}
        </p>
      )}
      {error && (
        <p className="shift-message error" role="alert">
          {error} <button onClick={() => setRevision((v) => v + 1)}>Tải lại</button>
        </p>
      )}
      <div className="shift-toolbar attendance-filters">
        <input
          aria-label="Tìm nhân viên chấm công"
          placeholder="Tìm tên / mã nhân viên…"
          maxLength={200}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {monthly ? (
          <input
            type="month"
            aria-label="Tháng công"
            value={month}
            onChange={(e) => {
              if (e.target.value) {
                setData(null);
                setMonth(e.target.value);
                setNotice('');
              }
            }}
          />
        ) : (
          <>
            <input
              type="date"
              aria-label="Ngày chấm công"
              value={date}
              onChange={(e) => {
                if (e.target.value) {
                  setData(null);
                  setDate(e.target.value);
                  setNotice('');
                }
              }}
            />
            <select
              aria-label="Lọc ca chấm công"
              value={template}
              onChange={(e) => setTemplate(e.target.value)}
            >
              <option value="">Tất cả ca</option>
              {data?.templates.map((t, i) => (
                <option key={t.id} value={t.id}>
                  Ca {i + 1}
                </option>
              ))}
            </select>
            <select
              aria-label="Lọc tình trạng chấm công"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            >
              <option value="ALL">Tất cả tình trạng</option>
              <option value="MISSING">Chưa ghi nhận</option>
              <option value="WORKED">Đi làm</option>
              <option value="ABSENT">Vắng mặt</option>
              <option value="ISSUE">Cần xử lý</option>
            </select>
          </>
        )}
      </div>
      {loading ? (
        <p role="status" className="shift-loading">
          Đang tải bảng công…
        </p>
      ) : (
        data && (
          <>
            {!monthly && closed && (
              <p className="shift-message">
                Công tháng {date.slice(5, 7)}/{date.slice(0, 4)} đã được chốt nên không thể sửa trực
                tiếp bản ghi chấm công này.
              </p>
            )}
            <div className="attendance-table-scroll">
              {monthly ? (
                <table className="attendance-table">
                  <thead>
                    <tr>
                      <th>Nhân viên</th>
                      <th>Số ca</th>
                      <th>Giờ thực tế</th>
                      <th>Ca vắng</th>
                      <th>Ca có ngoại lệ</th>
                      <th>Cần xử lý</th>
                      <th>Chi tiết</th>
                    </tr>
                  </thead>
                  <tbody>
                    {employees.map((e) => (
                      <tr key={e.employeeId}>
                        <td>
                          <strong>
                            {e.code} · {e.fullName}
                          </strong>
                        </td>
                        <td>{e.shiftCount}</td>
                        <td>
                          <strong>{hoursLabel(e.workedMinutes)}</strong>
                        </td>
                        <td>{e.absences}</td>
                        <td>{e.exceptions}</td>
                        <td>
                          <span
                            className={
                              e.missing + e.invalid + e.openIssues ? 'attendance-warning' : ''
                            }
                          >
                            {e.missing + e.invalid + e.openIssues}
                          </span>
                          {e.missing > 0 && <small>{e.missing} ca chưa ghi nhận</small>}
                          {e.invalid > 0 && <small>{e.invalid} bản ghi chưa đúng</small>}
                          {e.openIssues > 0 && <small>{e.openIssues} phản ánh đang mở</small>}
                        </td>
                        <td>
                          <button
                            className="attendance-link"
                            onClick={() => setDialog({ type: 'employee', employee: e })}
                          >
                            Xem công
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <table className="attendance-table">
                  <thead>
                    <tr>
                      <th>Nhân viên</th>
                      <th>Ca dự kiến</th>
                      <th>Thực tế</th>
                      <th>Giờ công</th>
                      <th>Ngoại lệ / phản ánh</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr key={row.assignmentId}>
                        <td>
                          <strong>
                            {row.code} · {row.fullName}
                          </strong>
                        </td>
                        <td>
                          {dateTimeLabel(row.startsAt, data.timezone)}
                          <small>→ {dateTimeLabel(row.endsAt, data.timezone)}</small>
                        </td>
                        <td>
                          {!row.attendanceId ? (
                            'Chưa ghi nhận'
                          ) : row.status === 'ABSENT' ? (
                            'Vắng mặt'
                          ) : (
                            <>
                              {dateTimeLabel(row.actualIn, data.timezone)}
                              <small>→ {dateTimeLabel(row.actualOut, data.timezone)}</small>
                            </>
                          )}
                        </td>
                        <td>{row.attendanceId ? hoursLabel(row.workedMinutes) : '—'}</td>
                        <td>
                          {row.exceptions.join(' · ') ||
                            (row.status === 'ABSENT'
                              ? row.note
                                ? 'Có lý do / ghi chú'
                                : 'Vắng mặt'
                              : row.attendanceId
                                ? 'Không'
                                : 'Cần ghi nhận')}
                          {row.attendanceId && !row.valid && (
                            <small className="attendance-warning">Bản ghi chưa đúng</small>
                          )}
                          {row.openIssues > 0 && (
                            <small className="attendance-warning">
                              {row.openIssues} phản ánh chưa xử lý
                            </small>
                          )}
                        </td>
                        <td>
                          <button className="attendance-link" onClick={() => openRecord(row)}>
                            {readonly ? 'Chi tiết' : row.attendanceId ? 'Xem / sửa' : 'Ghi nhận'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {!(monthly ? employees.length : rows.length) && (
                <p className="attendance-empty" role="status">
                  Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.
                </p>
              )}
            </div>
            {monthly && (
              <>
                <div className="attendance-summary" role="status">
                  <strong>Tổng công đã ghi nhận: {hoursLabel(data.workedMinutes)}</strong>
                  <span>
                    {data.assignmentCount} phân ca · {data.employees.length} nhân viên ·{' '}
                    {data.missing} ca chưa ghi nhận · {data.invalid} bản ghi chưa đúng ·{' '}
                    {data.openIssues} phản ánh chưa xử lý
                  </span>
                  <span>Dữ liệu đã chốt được khóa để kế toán sử dụng tính lương.</span>
                </div>
                {!closed && !data.monthEnded && (
                  <p className="attendance-help">
                    Chỉ chốt khi tháng và các ca cuối tháng đã kết thúc.
                  </p>
                )}
                {closed ? (
                  <p className="attendance-closed">
                    Đã chốt bởi {data.period.closedBy} ·{' '}
                    {dateTimeLabel(data.period.closedAt, data.timezone)}
                  </p>
                ) : (
                  <div className="attendance-actions">
                    <button
                      className="shift-primary"
                      disabled={readOnly || !data.canClose}
                      onClick={() => setDialog({ type: 'close' })}
                    >
                      Chốt bảng công
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        )
      )}
      {dialog && data && (
        <AttendanceDialog
          dialog={dialog}
          data={data}
          scope={branchScope}
          readOnly={readonly}
          onClose={() => setDialog(null)}
          onDaily={(day) => {
            setDialog(null);
            onDaily(day);
          }}
          onReload={() => {
            setDialog(null);
            setRevision((v) => v + 1);
          }}
          onSaved={(result) => {
            setData(result);
            setDialog(null);
            // MSG11/12: thông báo lưu và đổi trạng thái theo mẫu tài liệu.
            setNotice(
              monthly
                ? `Đã chuyển trạng thái của bảng công tháng ${result.month.split('-').reverse().join('/')} thành “Đã chốt”.`
                : 'Đã cập nhật bản ghi chấm công.'
            );
          }}
        />
      )}
    </section>
  );
}
