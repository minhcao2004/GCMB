/** HuyDV: QLCS đặt đơn giá và bàn giao bảng lương cho kế toán. */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi, today, vnd } from '../../finance/services/financeApi';
import '../../finance/styles/finance.css';

const base = '/branch-management/payroll';

export default function BranchPayrollPage({ branchScope, readOnly }) {
  const [employees, setEmployees] = useState([]);
  const [employeeId, setEmployeeId] = useState('');
  const [tab, setTab] = useState('rates');
  const [month, setMonth] = useState(today().slice(0, 7));
  const [effectiveFrom, setEffectiveFrom] = useState(today());
  const [amount, setAmount] = useState('');
  const [bonus, setBonus] = useState('0');
  const [penalty, setPenalty] = useState('0');
  const [reason, setReason] = useState('');
  const [preview, setPreview] = useState(null);
  const [attendanceNotes, setAttendanceNotes] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const settlementFeedback = useRef(null);
  useEffect(() => {
    if (tab === 'settlement' && (notice || error)) {
      settlementFeedback.current?.scrollIntoView({ block: 'nearest' });
    }
  }, [tab, notice, error]);
  useEffect(() => {
    let active = true;
    financeApi.get(base + '/employees', {}, branchScope)
      .then((rows) => { if (active) setEmployees(rows); })
      .catch((e) => { if (active) setError(Object.values(apiErrors(e)).join(' ')); });
    return () => { active = false; };
  }, [branchScope]);

  async function save(path, body, message) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await financeApi.post(base + path, body, branchScope);
      if (message) {
        setNotice(message);
        setPreview(null);
      } else {
        setPreview(result);
        setAttendanceNotes(result.attendanceNotes ?? []);
      }
    } catch (e) {
      setPreview(null);
      setError(Object.values(apiErrors(e)).join(' '));
    } finally {
      setBusy(false);
    }
  }

  const settlement = { employeeId, bonus: Number(bonus), penalty: Number(penalty), reason };
  const change = (setter, clearNotes = false) => (event) => {
    setter(event.target.value);
    setPreview(null);
    if (clearNotes) setAttendanceNotes(null);
  };
  return (
    <section className="finance-page branch-payroll-page">
      <h2>Đơn giá và chốt lương cơ sở</h2>
      <nav className="workforce-tabs" aria-label="Quản lý lương cơ sở">
        {[['rates', 'Đơn giá giờ'], ['settlement', 'Chốt lương tháng']].map(([key, label]) => (
          <button key={key} type="button" disabled={busy}
            aria-current={tab === key ? 'page' : undefined}
            onClick={() => {
              setTab(key);
              setPreview(null);
              setError('');
              setNotice('');
            }}>
            {label}
          </button>
        ))}
      </nav>
      {tab === 'rates' && error && <p className="finance-error" role="alert">{error}</p>}
      {tab === 'rates' && notice && <p className="finance-info" role="status">{notice}</p>}
      <label>Nhân viên
        <select value={employeeId} onChange={change(setEmployeeId, true)} disabled={busy}>
          <option value="">Chọn nhân viên</option>
          {employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.code} · {employee.name}</option>)}
        </select>
      </label>
      {tab === 'rates' ? <form onSubmit={(event) => {
        event.preventDefault();
        save('/hourly-rates', { employeeId, effectiveFrom, amount: Number(amount) }, 'Đã lưu đơn giá giờ.');
      }}>
        <fieldset disabled={busy || readOnly || !employeeId}>
          <legend className="branch-payroll-legend">Đơn giá giờ</legend>
          <label>Ngày hiệu lực <input type="date" required value={effectiveFrom} onChange={change(setEffectiveFrom)} /></label>
          <label>Đơn giá (đ/giờ)
            <input type="text" inputMode="numeric" required
              value={amount.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
              onChange={(event) => {
                const digits = event.target.value.replace(/\./g, '');
                if (/^\d{0,15}$/.test(digits)) setAmount(digits.replace(/^0+/, ''));
              }}
            />
          </label>
          <button type="submit">Lưu đơn giá</button>
        </fieldset>
      </form> : <form onSubmit={(event) => {
        event.preventDefault();
        save('/' + month + '/preview', settlement);
      }}>
        <fieldset disabled={busy || readOnly || !employeeId}>
          <legend className="branch-payroll-legend">Chốt lương tháng</legend>
          <label>Tháng <input type="month" required value={month} onChange={change(setMonth, true)} /></label>
          {attendanceNotes && (
            <section aria-label="Ghi chú chấm công">
              <h3>Ghi chú chấm công trong tháng</h3>
              {attendanceNotes.length ? (
                <details key={employeeId + '/' + month}>
                  <summary>Chi tiết ({attendanceNotes.length} ghi chú)</summary>
                  <ul>
                  {attendanceNotes.map((item, index) => (
                    <li key={index}>
                      <strong>{item.date.split('-').reverse().join('/')} · {item.status === 'ABSENT' ? 'Vắng mặt' : 'Đi làm'}</strong>
                      <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.note}</div>
                    </li>
                  ))}
                  </ul>
                </details>
              ) : <p>Tháng này không có ghi chú chấm công.</p>}
            </section>
          )}
          <label>Thưởng thêm <input type="number" min="0" step="1" required value={bonus} onChange={change(setBonus)} /></label>
          <label>Tiền phạt <input type="number" min="0" step="1" required value={penalty} onChange={change(setPenalty)} /></label>
          <label>Lý do <textarea value={reason} maxLength={2000} onChange={change(setReason)} /></label>
          <button type="submit">Xem trước lương</button>
          {preview && (
            <div role="status">
              <p>Tiền công {vnd(preview.wage)} · Thưởng trên 100 giờ {vnd(preview.reward)} · Tạm ứng {vnd(preview.advance)}</p>
              <p><strong>Thực nhận {vnd(preview.net)}</strong></p>
              <button type="button" onClick={() => save('/' + month + '/submit',
                { ...settlement, reviewedTotal: preview.net }, 'Đã chốt lương và gửi kế toán.')}>
                {busy ? 'Đang xử lý…' : 'Chốt và gửi kế toán'}
              </button>
            </div>
          )}
        </fieldset>
        <div ref={settlementFeedback}>
          {error && <p className="finance-error" role="alert">{error}</p>}
          {notice && <p className="finance-info" role="status">{notice}</p>}
        </div>
      </form>}
    </section>
  );
}
