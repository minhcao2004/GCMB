/** Author: HuyDV. UC-89/91/95, BR-63/64/67/75: chi tiết công và lương, xác nhận riêng trước khi khóa/gửi. */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi, vnd } from '../../finance/services/financeApi';
import MoneyInput from '../../finance/components/MoneyInput';
import { attendanceApi, dateTimeLabel, hoursLabel } from '../services/attendanceApi';

const base = '/branch-management/payroll';

export default function MonthlyPayrollDialog({
  data, employee, financial, draft, scope, readOnly, onDraft, onClose,
  onAttendanceClosed, onSubmitted, onDaily,
}) {
  const ref = useRef(null);
  const sending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(null);
  const [confirmed, setConfirmed] = useState(false);
  const adjustments = draft || {
    bonus: String(financial?.bonus ?? 0),
    penalty: String(financial?.penalty ?? 0),
    reason: financial?.reason || '',
  };
  const rows = data.rows.filter((row) => row.employeeId === employee.employeeId);
  const editable = !readOnly && !financial?.ready;
  const body = {
    employeeId: employee.employeeId,
    bonus: Number(adjustments.bonus),
    penalty: Number(adjustments.penalty),
    reason: adjustments.reason,
  };

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

  const change = (field, value) => {
    onDraft({ ...adjustments, [field]: value });
    setPreview(null);
    setError('');
  };

  async function closeAttendance() {
    if (sending.current || !employee.canClose || !confirmed || readOnly) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await attendanceApi.close(data.month, {
        revision: data.revision, confirmedCorrect: true, employeeIds: [employee.employeeId],
      }, scope);
      setPreview(null);
      onAttendanceClosed(result);
    } catch (failure) {
      setError(Object.values(apiErrors(failure)).join(' '));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  async function calculate(send = false) {
    if (sending.current || !editable || !employee.closed || (send && !preview)) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await financeApi.post(`${base}/${data.month}/${send ? 'submit' : 'preview'}`, {
        ...body,
        ...(send ? { reviewedTotal: preview.net, reviewedRevision: preview.revision } : {}),
      }, scope);
      if (send) onSubmitted(result.message || 'Đã chốt lương và gửi kế toán.');
      else setPreview(result);
    } catch (failure) {
      setPreview(null);
      setError(Object.values(apiErrors(failure)).join(' '));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  const total = preview || financial;
  return (
    <dialog ref={ref} className="shift-dialog monthly-payroll-dialog"
      aria-labelledby="monthly-payroll-title"
      onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}>
      <header>
        <div>
          <h2 id="monthly-payroll-title">Chi tiết công và lương</h2>
          <p>{employee.code} · {employee.fullName} · {data.month.split('-').reverse().join('/')}</p>
        </div>
        <button type="button" aria-label="Đóng" disabled={busy} onClick={onClose}>×</button>
      </header>
      <form onSubmit={(event) => { event.preventDefault(); calculate(); }}>
        <div className="shift-dialog-body">
          {error && <p className="shift-message error" role="alert">{error}</p>}
          <dl className="attendance-facts">
            <div><dt>Số ca đã phân công</dt><dd>{employee.shiftCount}</dd></div>
            <div><dt>Giờ công đã ghi nhận</dt><dd>{hoursLabel(employee.workedMinutes)}</dd></div>
            <div><dt>Ca vắng</dt><dd>{employee.absences}</dd></div>
            <div><dt>Công tháng</dt><dd>{employee.closed ? 'Đã chốt' : 'Chưa chốt'}</dd></div>
          </dl>
          <details className="monthly-attendance-detail" open={rows.some((row) => row.note)}>
            <summary>Ca làm và ghi chú chấm công ({rows.length})</summary>
            <div className="attendance-table-scroll">
              <table className="attendance-table">
                <thead><tr><th>Ngày / ca</th><th>Chấm công</th><th>Giờ công</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
                <tbody>{rows.map((row) => (
                  <tr key={row.assignmentId}>
                    <td>{dateTimeLabel(row.startsAt, data.timezone)}<small>→ {dateTimeLabel(row.endsAt, data.timezone)}</small></td>
                    <td>{!row.attendanceId ? 'Chưa ghi nhận' : row.status === 'ABSENT' ? 'Vắng mặt' : 'Đi làm'}</td>
                    <td>{row.attendanceId ? hoursLabel(row.workedMinutes) : '—'}</td>
                    <td className="monthly-note">{row.note || '—'}{row.openIssues > 0 && <small>{row.openIssues} phản ánh chưa xử lý</small>}</td>
                    <td><button type="button" disabled={busy} onClick={() => onDaily(row.workDate)}>Xem ngày</button></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          </details>
          {!employee.closed && (
            <section className="monthly-close-attendance">
              <h3>Chốt công trước khi gửi lương</h3>
              {employee.canClose ? <>
                <label className="attendance-check">
                  <input type="checkbox" checked={confirmed} disabled={busy || readOnly}
                    onChange={(event) => setConfirmed(event.target.checked)} />
                  Tôi đã kiểm tra và đồng ý khóa công cả tháng của nhân viên này.
                </label>
                <button type="button" disabled={busy || readOnly || !confirmed} onClick={closeAttendance}>
                  Chốt công cả tháng
                </button>
              </> : <p className="attendance-help">{employee.closeBlockReason}</p>}
            </section>
          )}
          <fieldset className="monthly-adjustments" disabled={busy || !editable}>
            <legend>Thưởng / phạt</legend>
            <div className="monthly-adjustment-grid">
              <label>Thưởng thêm<MoneyInput required value={adjustments.bonus} onValueChange={(value) => change('bonus', value)} /></label>
              <label>Tiền phạt<MoneyInput required value={adjustments.penalty} onValueChange={(value) => change('penalty', value)} /></label>
            </div>
            <label>Lý do điều chỉnh
              <textarea maxLength={2000} value={adjustments.reason}
                required={Number(adjustments.bonus) > 0 || Number(adjustments.penalty) > 0}
                onChange={(event) => change('reason', event.target.value)} />
            </label>
            {editable && <p className="attendance-help">Thưởng/phạt chỉ được lưu khi chốt và gửi kế toán.</p>}
          </fieldset>
          {total?.wage != null ? (
            <section className="monthly-pay-breakdown" aria-label="Chi tiết tiền lương">
              <h3>{financial?.ready ? 'Lương đã gửi kế toán' : preview ? 'Xác nhận số tiền gửi kế toán' : 'Lương tạm tính'}</h3>
              <dl>
                <div><dt>Tiền công (gồm phụ cấp ca đêm)</dt><dd>{vnd(total.wage)}</dd></div>
                <div><dt>Thưởng trên 100 giờ</dt><dd>{vnd(total.reward)}</dd></div>
                <div><dt>Thưởng thêm</dt><dd>{vnd(adjustments.bonus)}</dd></div>
                <div><dt>Tiền phạt</dt><dd>−{vnd(adjustments.penalty)}</dd></div>
                <div><dt>Tạm ứng đã trả</dt><dd>−{vnd(total.advance)}</dd></div>
                <div className="monthly-pay-net"><dt>{total.multipleBranches ? 'Phần lương cơ sở' : 'Thực nhận'}</dt>
                  <dd>{vnd(preview ? preview.net : Number(total.net) + Number(adjustments.bonus) - Number(financial?.bonus || 0) - Number(adjustments.penalty) + Number(financial?.penalty || 0))}</dd></div>
                {financial?.ready && financial.multipleBranches && financial.finalNet != null && (
                  <div className="monthly-pay-net"><dt>Thực nhận cả tháng (tất cả cơ sở)</dt><dd>{vnd(financial.finalNet)}</dd></div>
                )}
              </dl>
              {total.multipleBranches && <p className="attendance-help">Nhân viên làm ở nhiều cơ sở. Thưởng trên 100 giờ và tạm ứng được tính một lần khi tổng hợp đủ các cơ sở.</p>}
              {financial?.submitted && !financial.ready && <p className="attendance-help">Đã gửi phần lương cơ sở, đang chờ cơ sở khác chốt. Có thể cập nhật lại phần thưởng/phạt trước khi tổng hợp.</p>}
              {financial?.paid && <p>Đã thanh toán lương.</p>}
              {preview && <p className="attendance-help">Kiểm tra số tiền trên trước khi xác nhận chốt và gửi kế toán.</p>}
            </section>
          ) : <p className="shift-message" role="status">{financial?.calculationError || 'Đang tải tiền công…'}</p>}
        </div>
        <footer className="shift-dialog-footer">
          <button type="button" disabled={busy} onClick={onClose}>Đóng</button>
          {editable && employee.closed && (preview ? (
            <button type="button" className="shift-primary" disabled={busy} onClick={() => calculate(true)}>
              {busy ? 'Đang gửi…' : 'Xác nhận chốt và gửi kế toán'}
            </button>
          ) : (
            <button type="submit" className="shift-primary" disabled={busy}>
              {busy ? 'Đang tính…' : 'Xem trước và chốt lương'}
            </button>
          ))}
        </footer>
      </form>
    </dialog>
  );
}
