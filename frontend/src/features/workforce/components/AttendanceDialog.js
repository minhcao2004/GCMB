/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87/89, xem lại công thực tế trước khi lưu, xác nhận chốt và khóa tháng.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { dateLabel } from '../services/shiftApi';
import {
  attendanceApi,
  hoursLabel,
  localInput,
  initialAttendanceTime,
  dateTimeLabel,
  previewMinutes,
} from '../services/attendanceApi';

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
  const [assignmentId, setAssignmentId] = useState(dialog.row?.assignmentId || '');
  const [status, setStatus] = useState(dialog.row?.status || '');
  const [actualIn, setActualIn] = useState(
    initialAttendanceTime(dialog.row, 'actualIn', data.timezone)
  );
  const [actualOut, setActualOut] = useState(
    initialAttendanceTime(dialog.row, 'actualOut', data.timezone)
  );
  const [note, setNote] = useState(dialog.row?.note || '');
  const [resolved, setResolved] = useState([]);
  const [resolution, setResolution] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState({});
  const [stale, setStale] = useState(false);
  const row = data.rows.find((r) => r.assignmentId === assignmentId);
  const issues = data.issues.filter((i) => i.assignmentId === assignmentId);
  const minutes = previewMinutes(status, actualIn, actualOut);
  const shiftPay = status === 'ABSENT' ? 0 : row?.hourlyRate == null || minutes == null
    ? null : Math.round((Number(row.hourlyRate) + Number(row.nightPremium)) * minutes / 60);
  const markAttendance = (value) => {
    setStatus(value);
    setActualIn(value === 'WORKED' && row ? localInput(row.startsAt, data.timezone) : '');
    setActualOut(value === 'WORKED' && row ? localInput(row.endsAt, data.timezone) : '');
    setReview(false);
    setErrors({});
  };
  const closing = dialog.type === 'close';
  const employee = dialog.type === 'employee';
  const title = closing
    ? 'Xác nhận chốt bảng công'
    : employee
      ? `Công tháng · ${dialog.employee.fullName}`
      : readOnly
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

  const select = (id) => {
    const current = data.rows.find((r) => r.assignmentId === id);
    setAssignmentId(id);
    setStatus(current?.status || '');
    setActualIn(initialAttendanceTime(current, 'actualIn', data.timezone));
    setActualOut(initialAttendanceTime(current, 'actualOut', data.timezone));
    setNote(current?.note || '');
    setResolved([]);
    setResolution('');
    setReview(false);
    setErrors({});
  };

  async function submit(event) {
    event.preventDefault();
    if (sending.current || readOnly || stale) return;
    const found = {};
    if (closing) {
      if (!confirmed)
        found.confirmedCorrect = 'Vui lòng xác nhận không còn bản ghi chấm công chưa đúng.';
    } else {
      if (!row) found.assignmentId = 'Vui lòng bổ sung nhân viên và phân ca.'; // MSG02.
      if (!status) found.form = 'Vui lòng tích Đi làm hoặc Vắng mặt.';
      else if (status === 'WORKED' && (!actualIn || !actualOut))
        found.actualOut = 'Vui lòng bổ sung ngày giờ vào và ra thực tế.'; // BR-204 / MSG02.
      else if (minutes === null) found.actualOut = 'Giờ ra thực tế phải sau Giờ vào thực tế.'; // BR-195 / MSG07.
      if (resolved.length && !resolution.trim())
        found.resolution = 'Vui lòng bổ sung kết quả xử lý phản ánh.';
    }
    if (Object.keys(found).length) {
      setErrors(found);
      return;
    }
    if (!closing && !review) {
      setReview(true);
      setErrors({});
      return;
    }
    sending.current = true;
    setBusy(true);
    setErrors({});
    try {
      const result = closing
        ? await attendanceApi.close(
            data.month,
            { revision: data.revision, confirmedCorrect: confirmed },
            scope
          )
        : await attendanceApi.record(
            assignmentId,
            {
              status,
              actualIn: status === 'WORKED' ? actualIn : null,
              actualOut: status === 'WORKED' ? actualOut : null,
              note,
              version: row.version,
              resolvedIssueIds: resolved,
              resolution,
            },
            scope
          );
      onSaved(result);
    } catch (e) {
      setErrors(apiErrors(e));
      setStale(e.response?.status === 409);
      setReview(false);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={ref}
      className={`shift-dialog attendance-dialog${employee ? ' attendance-dialog-wide' : ''}`}
      aria-labelledby="attendance-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <header>
        <div>
          <h2 id="attendance-dialog-title">{title}</h2>
          <p>
            {data.branchName} · {data.month || dateLabel(data.date)}
          </p>
        </div>
        <button type="button" disabled={busy} onClick={onClose} aria-label="Đóng">
          ×
        </button>
      </header>
      <form onSubmit={submit}>
        <div className="shift-dialog-body">
          {Object.keys(errors).length > 0 && (
            <div className="shift-message error" role="alert">
              {Object.values(errors).map((message, i) => (
                <p key={i}>{message}</p>
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
              <table className="attendance-table">
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
                    .filter((r) => r.employeeId === dialog.employee.employeeId)
                    .map((r) => (
                      <tr key={r.assignmentId}>
                        <td>
                          {dateTimeLabel(r.startsAt, data.timezone)}
                          <small>→ {dateTimeLabel(r.endsAt, data.timezone)}</small>
                        </td>
                        <td>
                          {!r.attendanceId ? (
                            'Chưa ghi nhận'
                          ) : r.status === 'ABSENT' ? (
                            'Vắng mặt'
                          ) : (
                            <>
                              {dateTimeLabel(r.actualIn, data.timezone)}
                              <small>→ {dateTimeLabel(r.actualOut, data.timezone)}</small>
                            </>
                          )}
                        </td>
                        <td>{r.attendanceId ? hoursLabel(r.workedMinutes) : '—'}</td>
                        <td>
                          {r.exceptions.join(' · ') || '—'}
                          <small>{r.note}</small>
                          {r.openIssues > 0 && <small>{r.openIssues} phản ánh chưa xử lý</small>}
                        </td>
                        <td>
                          <button type="button" onClick={() => onDaily(r.workDate)}>
                            Xem ngày
                          </button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
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
                  <dd>{data.employees.length} người</dd>
                </div>
                <div>
                  <dt>Giờ công thực tế</dt>
                  <dd>{hoursLabel(data.workedMinutes)}</dd>
                </div>
                <div>
                  <dt>Vấn đề cần xử lý</dt>
                  <dd>{data.missing + data.invalid + data.openIssues}</dd>
                </div>
              </dl>
              <p className="attendance-alert">
                Sau xác nhận, bảng công bị khóa để tính lương. Quản lý cơ sở không có thao tác mở
                khóa kỳ đã chốt.
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
              <fieldset
                disabled={busy || stale || readOnly || review}
                className="attendance-fields"
              >
                <label>
                  Nhân viên / phân ca *
                  <select
                    aria-label="Nhân viên / phân ca"
                    value={assignmentId}
                    onChange={(e) => select(e.target.value)}
                  >
                    <option value="">Chọn nhân viên và ca đã phân công</option>
                    {data.rows.map((r) => (
                      <option key={r.assignmentId} value={r.assignmentId}>
                        {r.code} · {r.fullName} · {dateTimeLabel(r.startsAt, data.timezone)}
                      </option>
                    ))}
                  </select>
                </label>
                {row && (
                  <p className="attendance-help">
                    Ca dự kiến: {dateTimeLabel(row.startsAt, data.timezone)} →{' '}
                    {dateTimeLabel(row.endsAt, data.timezone)}
                  </p>
                )}
                <div className="attendance-form-grid">
                  <fieldset className="attendance-status-options">
                    <legend>Tình trạng chấm công *</legend>
                    <label>
                      <input type="radio" name="attendance-status" checked={status === 'WORKED'}
                        disabled={!row} onChange={() => markAttendance('WORKED')} /> Đi làm
                    </label>
                    <label>
                      <input type="radio" name="attendance-status" checked={status === 'ABSENT'}
                        disabled={!row} onChange={() => markAttendance('ABSENT')} /> Vắng mặt
                    </label>
                  </fieldset>
                  {status === 'WORKED' && (
                    <div className="attendance-shift-pay" role="status">
                      <strong>Tiền công ca tạm tính</strong>
                      <p>{shiftPay == null ? 'Chưa có đơn giá áp dụng' : shiftPay.toLocaleString('vi-VN') + ' đ'}</p>
                      <small>{hoursLabel(minutes || 0)}{status === 'WORKED' && row?.nightPremium ? ' · Phụ cấp ca đêm 3.000 đ/giờ' : ''}</small>
                    </div>
                  )}
                </div>
                <label>
                  Ghi chú / lý do ngoại lệ
                  <textarea
                    aria-label="Ghi chú chấm công"
                    maxLength={2000}
                    placeholder="Ví dụ: đi muộn do kẹt xe, về sớm có lý do…"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                  />
                </label>
                {issues.length > 0 && (
                  <section className="attendance-issues">
                    <h3>Phản ánh chấm công</h3>
                    {issues.map((issue) => (
                      <div key={issue.id}>
                        {issue.status === 'OPEN' ? (
                          <label className="attendance-check">
                            <input
                              type="checkbox"
                              checked={resolved.includes(issue.id)}
                              onChange={(e) =>
                                setResolved((old) =>
                                  e.target.checked
                                    ? [...old, issue.id]
                                    : old.filter((id) => id !== issue.id)
                                )
                              }
                            />
                            Đã xử lý: {issue.description}
                          </label>
                        ) : (
                          <p>
                            {issue.description}
                            <small>{issue.resolution || issue.status}</small>
                          </p>
                        )}
                      </div>
                    ))}
                    {resolved.length > 0 && (
                      <label>
                        Kết quả xử lý *
                        <textarea
                          aria-label="Kết quả xử lý phản ánh"
                          maxLength={2000}
                          value={resolution}
                          onChange={(e) => setResolution(e.target.value)}
                        />
                      </label>
                    )}
                  </section>
                )}
              </fieldset>
              <p className="attendance-alert">
                Đi làm tính đủ giờ của ca. Vắng mặt tính 0 giờ. Đi muộn hoặc về sớm ghi vào
                ghi chú để QLCS xem xét cuối tháng.
              </p>
              {review && (
                <div className="attendance-summary" role="status">
                  <strong>Xác nhận cập nhật bản ghi chấm công?</strong>
                  <span>
                    Hệ thống sẽ lưu ngày giờ thực tế, giờ công và các phản ánh được chọn là đã xử
                    lý.
                  </span>
                  <span>
                    {row.code} · {row.fullName} · {status === 'ABSENT' ? 'Vắng mặt' : 'Đi làm'} ·{' '}
                    {hoursLabel(minutes)}
                  </span>
                  {row.attendanceId && (
                    <span>
                      Trước sửa: {row.status === 'ABSENT' ? 'Vắng mặt' : 'Đi làm'} ·{' '}
                      {hoursLabel(row.workedMinutes)} · {dateTimeLabel(row.actualIn, data.timezone)}{' '}
                      → {dateTimeLabel(row.actualOut, data.timezone)}
                    </span>
                  )}
                  {status === 'WORKED' && (
                    <span>
                      Sau lưu: {actualIn.replace('T', ' ')} → {actualOut.replace('T', ' ')}
                    </span>
                  )}
                  {note && <span>Ghi chú: {note}</span>}
                  {resolved.length > 0 && (
                    <span>
                      Giải quyết {resolved.length} phản ánh: {resolution}
                    </span>
                  )}
                </div>
              )}
            </>
          )}
        </div>
        <footer className="shift-dialog-footer">
          <button type="button" disabled={busy} onClick={review ? () => setReview(false) : onClose}>
            {review ? 'Quay lại sửa' : 'Đóng'}
          </button>
          {!employee && !readOnly && (
            <button
              type="submit"
              className="shift-primary"
              disabled={busy || stale || (closing && !confirmed)}
            >
              {busy
                ? 'Đang lưu…'
                : closing
                  ? 'Xác nhận chốt'
                  : review
                    ? 'Xác nhận lưu chấm công'
                    : 'Kiểm tra thông tin'}
            </button>
          )}
        </footer>
      </form>
    </dialog>
  );
}
