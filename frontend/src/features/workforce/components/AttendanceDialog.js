import { PaginatedTable, PaginatedList } from '../../../shared/components/Pagination';
/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87/89, ghi nhận công thực tế, xác nhận chốt và khóa tháng.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { dateLabel } from '../services/shiftApi';
import {
  attendanceApi,
  futureAttendanceMessage,
  hoursLabel,
  localInput,
  dateTimeLabel,
  previewMinutes,
} from '../services/attendanceApi';

const attendanceOptions = [['WORKED', 'Đi làm'], ['ABSENT', 'Vắng mặt']];
const makeEntry = (row) => ({
  row,
  status: row.status || '',
  note: row.note || '',
  resolved: [],
  resolution: '',
});
const isFuture = (row) => new Date(row.startsAt).getTime() > Date.now();
const shiftLabel = (row, timezone) =>
  `${dateTimeLabel(row.startsAt, timezone)} → ${dateTimeLabel(row.endsAt, timezone)}`;

export default function AttendanceDialog({
  dialog,
  data,
  scope,
  readOnly,
  onClose,
  onSaved,
  onReload,
  onDaily,
}) {
  const ref = useRef(null);
  const sending = useRef(false);
  const closing = dialog.type === 'close';
  const employee = dialog.type === 'employee';
  const recording = !closing && !employee;
  const [entries, setEntries] = useState(() => recording
    ? data.rows.filter((row) => !dialog.row || row.shiftId === dialog.row.shiftId).map(makeEntry)
    : []);
  const [issues, setIssues] = useState(data.issues || []);
  const [savedCount, setSavedCount] = useState(0);
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState({});
  const [stale, setStale] = useState(false);
  const locked = readOnly || (recording && entries.every(({ row }) => row.periodStatus === 'CLOSED'));
  const closingEmployees = (data.employees || []).filter((e) => dialog.periodOnly || !dialog.employeeIds || dialog.employeeIds.includes(e.employeeId));
  const editableEntries = entries.filter(({ row }) => !readOnly && row.periodStatus !== 'CLOSED' && !isFuture(row));
  const pendingEntries = editableEntries
    .filter(({ row, status, note, resolved }) =>
      status !== (row.status || '') || note.trim() !== (row.note || '').trim() || resolved.length > 0)
    .map((entry) => ({
      ...entry,
      actualIn: entry.status === 'WORKED' ? localInput(entry.row.startsAt, data.timezone) : null,
      actualOut: entry.status === 'WORKED' ? localInput(entry.row.endsAt, data.timezone) : null,
    }));
  const sameShift = entries.length > 0 && entries.every(({ row }) => row.shiftId === entries[0].row.shiftId);
  const futureShift = entries.length > 0 && entries.every(({ row }) => isFuture(row));
  const closeDialog = () => onClose(savedCount > 0);
  const updateEntry = (assignmentId, changes) => {
    setEntries((old) => old.map((entry) => entry.row.assignmentId === assignmentId
      ? { ...entry, ...changes }
      : entry));
    setErrors({});
  };
  const title = closing
    ? dialog.periodOnly ? 'Xác nhận chốt công tháng' : 'Xác nhận chốt bảng công'
    : employee
      ? `Công tháng · ${dialog.employee.fullName}`
      : locked
        ? 'Chi tiết chấm công'
        : 'Ghi nhận chấm công';

  useEffect(() => {
    const element = ref.current;
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);

  async function submit(event) {
    event.preventDefault();
    if (sending.current || locked || stale || employee) return;
    if (futureShift) {
      setErrors({ form: futureAttendanceMessage });
      return;
    }
    const found = {};
    if (closing) {
      if (!dialog.periodOnly && !closingEmployees.length) found.employeeIds = 'Vui lòng chọn nhân viên cần chốt.';
      if (!confirmed)
        found.confirmedCorrect = 'Vui lòng xác nhận không còn bản ghi chấm công chưa đúng.';
    } else {
      if (!pendingEntries.length) found.form = 'Chọn Đi làm hoặc Vắng mặt, hoặc sửa một bản ghi để lưu.';
      for (const { row, status, actualIn, actualOut, resolved, resolution } of pendingEntries) {
        let message;
        if (!status) message = 'Vui lòng chọn Đi làm hoặc Vắng mặt.';
        else if (previewMinutes(status, actualIn, actualOut) === null)
          message = 'Giờ ra phải sau giờ vào.';
        else if (resolved.length && !resolution.trim())
          message = 'Vui lòng bổ sung kết quả xử lý phản ánh.';
        if (message) found[row.assignmentId] = `${row.fullName}: ${message}`;
      }
    }
    if (Object.keys(found).length) {
      setErrors(found);
      return;
    }
    sending.current = true;
    setBusy(true);
    setErrors({});
    let savingRow;
    try {
      let result;
      if (closing) {
        result = await attendanceApi.close(
          data.month,
          { revision: data.revision, confirmedCorrect: confirmed,
            employeeIds: dialog.periodOnly ? [] : closingEmployees.map((e) => e.employeeId) },
          scope
        );
      } else {
        // shortcut: mỗi phân ca lưu riêng, thêm API giao dịch chung nếu cần lưu cả ca cùng thành công hoặc cùng thất bại.
        for (const { row, status, actualIn, actualOut, note, resolved, resolution } of pendingEntries) {
          savingRow = row;
          result = await attendanceApi.record(
            row.assignmentId,
            {
              status,
              actualIn,
              actualOut,
              note,
              version: row.version,
              resolvedIssueIds: resolved,
              resolution,
            },
            scope
          );
          const savedRow = result.rows.find((item) => item.assignmentId === row.assignmentId);
          setEntries((old) => old.map((entry) => entry.row.assignmentId === row.assignmentId
            ? makeEntry(savedRow)
            : entry));
          setIssues(result.issues);
          setSavedCount((count) => count + 1);
        }
      }
      onSaved(result);
    } catch (e) {
      const messages = apiErrors(e);
      setErrors(savingRow
        ? { [savingRow.assignmentId]: `${savingRow.fullName}: ${Object.values(messages).join(' ')}` }
        : messages);
      setStale(e.response?.status === 409);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={ref}
      className={`shift-dialog attendance-dialog${employee ? ' attendance-dialog-wide' : recording ? ' attendance-dialog-record' : ''}`}
      aria-labelledby="attendance-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) closeDialog();
      }}
    >
      <header>
        <div>
          <h2 id="attendance-dialog-title">{title}</h2>
          <p>
            {data.branchName} · {data.month || dateLabel(data.date)}
          </p>
        </div>
        <button type="button" disabled={busy} onClick={closeDialog} aria-label="Đóng">
          ×
        </button>
      </header>
      <form onSubmit={submit}>
        <div className="shift-dialog-body">
          {futureShift && !Object.keys(errors).length && (
            <p className="shift-message" role="status">{futureAttendanceMessage}</p>
          )}
          {Object.keys(errors).length > 0 && (
            <div className="shift-message error" role="alert">
              {Object.entries(errors).map(([key, message]) => (
                <p key={key} id={`attendance-error-${key}`}>{message}</p>
              ))}
              {stale && (
                <button type="button" onClick={onReload}>
                  Tải lại bảng công
                </button>
              )}
            </div>
          )}
          {employee ? (
            <div className="attendance-table-scroll">
              <PaginatedTable pageSize={10} className="attendance-table">
                <thead>
                  <tr>
                    <th>Ngày / ca</th>
                    <th>Thực tế</th>
                    <th>Giờ công</th>
                    <th>Ngoại lệ / ghi chú</th>
                    <th>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {data.rows
                    .filter((rowItem) => rowItem.employeeId === dialog.employee.employeeId)
                    .map((rowItem) => (
                      <tr key={rowItem.assignmentId}>
                        <td>
                          {dateTimeLabel(rowItem.startsAt, data.timezone)}
                          <small>→ {dateTimeLabel(rowItem.endsAt, data.timezone)}</small>
                        </td>
                        <td>
                          {!rowItem.attendanceId ? (
                            'Chưa ghi nhận'
                          ) : rowItem.status === 'ABSENT' ? (
                            'Vắng mặt'
                          ) : (
                            <>
                              {dateTimeLabel(rowItem.actualIn, data.timezone)}
                              <small>→ {dateTimeLabel(rowItem.actualOut, data.timezone)}</small>
                            </>
                          )}
                        </td>
                        <td>{rowItem.attendanceId ? hoursLabel(rowItem.workedMinutes) : '—'}</td>
                        <td>
                          {rowItem.exceptions.join(' · ') || '—'}
                          <small>{rowItem.note}</small>
                          {rowItem.openIssues > 0 && <small>{rowItem.openIssues} phản ánh chưa xử lý</small>}
                        </td>
                        <td>
                          <button type="button" onClick={() => onDaily(rowItem.workDate)}>
                            Xem ngày
                          </button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </PaginatedTable>
            </div>
          ) : closing ? (
            <>
              <dl className="attendance-facts">
                <div>
                  <dt>Kỳ công</dt>
                  <dd>{data.month.split('-').reverse().join('/')}</dd>
                </div>
                <div>
                  <dt>Nhân viên</dt>
                  <dd>{closingEmployees.length} người</dd>
                </div>
                <div>
                  <dt>Giờ công thực tế</dt>
                  <dd>{hoursLabel(closingEmployees.reduce((sum, e) => sum + e.workedMinutes, 0))}</dd>
                </div>
                <div>
                  <dt>Vấn đề cần xử lý</dt>
                  <dd>{closingEmployees.reduce((sum, e) => sum + e.missing + e.invalid + e.openIssues, 0)}</dd>
                </div>
              </dl>
              <p className="attendance-alert">
                {dialog.periodOnly
                  ? 'Công của mọi nhân viên trong tháng đã được chốt. Xác nhận để khóa kỳ công chung của cơ sở.'
                  : <>Sau xác nhận, công cả tháng của các nhân viên được chọn bị khóa để tính lương.
                    Các ca đăng ký từ ngày mai trong tháng này sẽ bị hủy.
                    Những nhân viên khác vẫn được chấm công bình thường. Không có thao tác mở khóa.</>}
              </p>
              <label className="attendance-check">
                <input
                  type="checkbox"
                  checked={confirmed}
                  disabled={busy || stale}
                  onChange={(e) => setConfirmed(e.target.checked)}
                />
                Tôi đã đối chiếu và xác nhận không còn bản ghi chấm công chưa đúng hoặc phản ánh
                chưa xử lý.
              </label>
            </>
          ) : (
            <>
              {sameShift && <p className="attendance-help attendance-entry-shift">
                Ca dự kiến: {shiftLabel(entries[0].row, data.timezone)}
              </p>}
              {savedCount > 0 && !busy && (
                <p className="shift-message" role="status">Đã lưu {savedCount} bản ghi trước khi gặp lỗi.</p>
              )}
              <fieldset
                disabled={busy || stale || locked}
                className="attendance-fields"
              >
                <div className="attendance-table-scroll attendance-entry-scroll" role="region"
                  aria-label="Danh sách nhân viên chấm công" tabIndex={0}>
                  <table className="attendance-table attendance-entry-table">
                    <colgroup>
                      <col className="attendance-entry-person" />
                      <col className="attendance-entry-status" span={2} />
                      <col />
                    </colgroup>
                    <thead>
                      <tr>
                        <th scope="col">Nhân viên</th>
                        <th scope="col" className="attendance-entry-choice">Đi làm</th>
                        <th scope="col" className="attendance-entry-choice">Vắng mặt</th>
                        <th scope="col">Ghi chú</th>
                      </tr>
                    </thead>
                    <tbody>
                      {entries.map(({ row, status, note, resolved, resolution }) => {
                        const { assignmentId, code, fullName } = row;
                        const closed = row.periodStatus === 'CLOSED';
                        const future = isFuture(row);
                        const rowIssues = issues.filter((issue) => issue.assignmentId === assignmentId);
                        const rowLabel = `${fullName} · ${code}${sameShift ? '' : ` · ${shiftLabel(row, data.timezone)}`}`;
                        const edit = (changes) => updateEntry(assignmentId, changes);
                        return (
                          <tr key={assignmentId}>
                            <th scope="row">
                              <strong>{fullName}</strong>
                              <small>{code}</small>
                              {!sameShift && <small>{shiftLabel(row, data.timezone)}</small>}
                              {closed ? <small>Công đã chốt</small> : future && <small>Ca chưa bắt đầu</small>}
                            </th>
                            {attendanceOptions.map(([value, label]) => (
                              <td key={value} className="attendance-entry-choice">
                                <label>
                                  <input type="radio" name={`attendance-${assignmentId}`}
                                    aria-label={`${label} · ${rowLabel}`}
                                    aria-describedby={errors[assignmentId] ? `attendance-error-${assignmentId}` : undefined}
                                    checked={status === value} disabled={closed || future}
                                    onChange={() => edit({ status: value })} />
                                </label>
                              </td>
                            ))}
                            <td>
                              <textarea aria-label={`Ghi chú · ${rowLabel}`} maxLength={2000} rows={2}
                                placeholder="Nhập ghi chú…" value={note} disabled={closed || future}
                                onChange={(e) => edit({ note: e.target.value })} />
                              {rowIssues.length > 0 && (
                                <details className="attendance-issues">
                                  <summary>Phản ánh chấm công ({rowIssues.length})</summary>
                                  <PaginatedList as="section" pageSize={5}>
                                    {rowIssues.map((issue) => (
                                      <div key={issue.id}>
                                        {issue.status === 'OPEN' ? (
                                          <label className="attendance-check">
                                            <input type="checkbox" disabled={closed || future}
                                              checked={resolved.includes(issue.id)}
                                              onChange={(e) => edit({
                                                resolved: e.target.checked ? [...resolved, issue.id]
                                                  : resolved.filter((id) => id !== issue.id),
                                              })} />
                                            Đã xử lý: {issue.description}
                                          </label>
                                        ) : (
                                          <p>{issue.description}<small>{issue.resolution || issue.status}</small></p>
                                        )}
                                      </div>
                                    ))}
                                  </PaginatedList>
                                  {resolved.length > 0 && (
                                    <label>
                                      Kết quả xử lý *
                                      <textarea maxLength={2000} value={resolution} disabled={closed || future}
                                        aria-label={`Kết quả xử lý phản ánh · ${rowLabel}`}
                                        onChange={(e) => edit({ resolution: e.target.value })} />
                                    </label>
                                  )}
                                </details>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                  {!entries.length && <p className="attendance-empty">Chưa có nhân viên được phân ca.</p>}
                </div>
              </fieldset>
            </>
          )}
        </div>
        <footer className="shift-dialog-footer">
          <button type="button" disabled={busy} onClick={closeDialog}>
            Đóng
          </button>
          {!employee && !locked && (
            <button
              type="submit"
              className="shift-primary"
              disabled={busy || stale || (closing ? !confirmed : !editableEntries.length)}
            >
              {busy
                ? 'Đang lưu…'
                : closing
                  ? 'Xác nhận chốt'
                  : 'Lưu chấm công'}
            </button>
          )}
        </footer>
      </form>
    </dialog>
  );
}
