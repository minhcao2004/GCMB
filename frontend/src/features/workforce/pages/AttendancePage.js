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
import MonthlyPayrollDialog from '../components/MonthlyPayrollDialog';
import { financeApi, vnd } from '../../finance/services/financeApi';
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
  const [selected, setSelected] = useState([]);
  const [payroll, setPayroll] = useState([]);
  const [payrollError, setPayrollError] = useState('');
  const [drafts, setDrafts] = useState({});
  useEffect(() => {
    setPayroll([]);
    setDrafts({});
    setDialog(null);
  }, [branchScope, month]);
  useEffect(() => setSelected([]), [branchScope, month]);
  useEffect(() => {
    if (data) {
      setSelected((old) => old.filter((id) =>
        data.employees?.some((e) => e.employeeId === id && e.canClose)
      ));
    }
  }, [data]);
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
        const [result, salaries] = await Promise.all([
          monthly ? attendanceApi.monthly(month, branchScope) : attendanceApi.daily(date, branchScope),
          monthly ? financeApi.get(`/branch-management/payroll/${month}/summary`, {}, branchScope)
            .then((rows) => ({ rows }))
            .catch((failure) => ({ error: Object.values(apiErrors(failure)).join(' ') })) : null,
        ]);
        if (active) {
          setData(result);
          if (monthly) {
            setPayroll(salaries.rows || []);
            setPayrollError(salaries.error || '');
          }
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
  const selectedEmployees = (data?.employees || []).filter((e) => selected.includes(e.employeeId) && e.canClose);
  const openRecord = (row = null) => setDialog({ type: 'record', row });

  return (
    <section className="shift-page attendance-page" aria-labelledby="attendance-title">
      <header className="shift-heading">
        <div>
          <h1 id="attendance-title">
            {monthly
              ? 'Bảng công và lương tháng'
              : 'Chấm công theo ngày / ca'}
          </h1>
        </div>
        {!monthly && (
          <button
            className="shift-primary"
            disabled={loading || readonly || !data?.rows.some((r) => r.periodStatus !== 'CLOSED')}
            onClick={() => openRecord()}
          >
            Ghi nhận chấm công
          </button>
        )}
        {monthly && (
          <span className={`attendance-badge ${closed ? 'closed' : ''}`}>
            {closed ? 'Đã chốt công cả cơ sở' : 'Đã chốt công ' + (data?.closedCount || 0) + '/' + (data?.employees.length || 0) + ' nhân viên'}
          </span>
        )}
      </header>
      {notice && (
        <p className="shift-message success" role="status">
          {notice}
        </p>
      )}
      {monthly && payrollError && (
        <p className="shift-message error" role="alert">
          Không tải được tiền lương: {payrollError}
          <button type="button" onClick={() => setRevision((value) => value + 1)}>Tải lại</button>
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
            {monthly && !closed && (
              <div className="attendance-actions attendance-selection">
                <button
                  type="button"
                  disabled={readonly || !employees.some((e) => e.canClose)}
                  onClick={() => setSelected((old) => [...new Set([
                    ...old,
                    ...employees.filter((e) => e.canClose).map((e) => e.employeeId),
                  ])])}
                >
                  Chọn tất cả nhân viên đủ điều kiện
                </button>
                <button type="button" disabled={!selected.length} onClick={() => setSelected([])}>
                  Bỏ chọn tất cả
                </button>
                <span>Đã chọn {selectedEmployees.length} nhân viên</span>
              </div>
            )}
            <div className="attendance-table-scroll">
              {monthly ? (
                <table className="attendance-table monthly-payroll-table">
                  <thead>
                    <tr>
                      <th>Chọn</th>
                      <th>Nhân viên</th>
                      <th>Số ca</th>
                      <th>Giờ thực tế</th>
                      <th>Ca vắng</th>
                      <th>Tiền công</th>
                      <th>Thưởng / phạt</th>
                      <th>Thực nhận</th>
                      <th>Cần xử lý</th>
                      <th>Trạng thái</th>
                      <th>Chi tiết</th>
                    </tr>
                  </thead>
                  <tbody>
                    {employees.map((e) => {
                      const pay = payroll.find((item) => item.employeeId === e.employeeId);
                      const adjustment = drafts[e.employeeId] || pay;
                      const net = pay?.net == null ? null : Number(pay.net)
                        + Number(adjustment?.bonus || 0) - Number(pay.bonus || 0)
                        - Number(adjustment?.penalty || 0) + Number(pay.penalty || 0);
                      return (
                      <tr key={e.employeeId}>
                        <td>
                          <input
                            type="checkbox"
                            aria-label={'Chọn chốt công ' + e.code}
                            disabled={readonly || !e.canClose}
                            checked={selected.includes(e.employeeId)}
                            onChange={(event) => setSelected((old) => event.target.checked
                              ? [...old, e.employeeId]
                              : old.filter((id) => id !== e.employeeId)
                            )}
                          />
                        </td>
                        <td className="monthly-employee">
                          <strong>{e.fullName}</strong>
                          <small>{e.code}</small>
                        </td>
                        <td>
                          {Math.max(0, e.shiftCount - e.missing - e.invalid - e.absences)}
                          <small>/ {e.shiftCount} ca đã phân</small>
                        </td>
                        <td>
                          <strong>{hoursLabel(e.workedMinutes)}</strong>
                        </td>
                        <td>{e.absences}</td>
                        <td className="monthly-money">
                          {pay?.wage == null ? '—' : vnd(pay.wage)}
                        </td>
                        <td className="monthly-money">
                          {pay ? <>
                            <span>+{vnd(Number(adjustment?.bonus || 0) + Number(pay.reward || 0))}</span>
                            <small>−{vnd(adjustment?.penalty)}</small>
                          </> : '—'}
                        </td>
                        <td className="monthly-money">
                          <strong>{net == null ? '—' : vnd(net)}</strong>
                          <small>{pay?.multipleBranches ? 'Phần lương cơ sở' : pay?.ready ? 'Đã chốt lương' : 'Tạm tính'}</small>
                        </td>
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
                          {e.closed ? 'Công đã chốt' : 'Công chưa chốt'}
                          {pay?.submitted && <small>{pay.paid ? 'Đã thanh toán' : pay.ready ? 'Đã gửi kế toán' : 'Chờ cơ sở khác chốt'}</small>}
                        </td>
                        <td>
                          <button
                            className="attendance-link"
                            onClick={() => setDialog({ type: 'payroll', employee: e })}
                          >
                            Chi tiết
                          </button>
                        </td>
                      </tr>
                      );
                    })}
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
                            {readonly || row.periodStatus === 'CLOSED' ? 'Chi tiết' : row.attendanceId ? 'Xem / sửa' : 'Ghi nhận'}
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
                {!closed && !data.monthEnded && (
                  <p className="attendance-help">
                    Có thể chốt sớm theo nhân viên. Cần hoàn tất các ca và xử lý phản ánh; nhân viên đã chốt sẽ bị khóa công cả tháng.
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
                      disabled={readonly || selectedEmployees.length === 0}
                      onClick={() => setDialog({ type: 'close', employeeIds: selectedEmployees.map((e) => e.employeeId) })}
                    >
                      Chốt công nhân viên đã chọn
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        )
      )}
      {dialog?.type === 'payroll' && data && (
        <MonthlyPayrollDialog
          key={dialog.employee.employeeId + '/' + month + '/' + branchScope}
          data={data}
          employee={data.employees.find((e) => e.employeeId === dialog.employee.employeeId) || dialog.employee}
          financial={payroll.find((item) => item.employeeId === dialog.employee.employeeId)}
          draft={drafts[dialog.employee.employeeId]}
          scope={branchScope}
          readOnly={readOnly || data.readOnly}
          onDraft={(draft) => setDrafts((old) => ({ ...old, [dialog.employee.employeeId]: draft }))}
          onClose={() => setDialog(null)}
          onDaily={(day) => { setDialog(null); onDaily(day); }}
          onAttendanceClosed={(result) => {
            setData(result);
            setRevision((value) => value + 1);
            setNotice('Đã chốt công cả tháng cho nhân viên. Có thể xem trước và chốt lương.');
          }}
          onSubmitted={(message) => {
            setDrafts((old) => {
              const next = { ...old };
              delete next[dialog.employee.employeeId];
              return next;
            });
            setDialog(null);
            setNotice(message);
            setRevision((value) => value + 1);
          }}
        />
      )}
      {dialog && dialog.type !== 'payroll' && data && (
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
            setSelected([]);
            setDialog(null);
            // MSG11/12: thông báo lưu và đổi trạng thái theo mẫu tài liệu.
            setNotice(
              monthly
                ? `Đã chốt công cả tháng cho các nhân viên được chọn trong tháng ${result.month.split('-').reverse().join('/')}.`
                : 'Đã cập nhật bản ghi chấm công.'
            );
          }}
        />
      )}
    </section>
  );
}
