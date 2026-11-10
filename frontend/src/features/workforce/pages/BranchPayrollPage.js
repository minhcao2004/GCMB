/** HuyDV: QLCS đặt đơn giá và bàn giao bảng lương cho kế toán. */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi, today, vnd } from '../../finance/services/financeApi';
import MoneyInput from '../../finance/components/MoneyInput';
import '../../finance/styles/finance.css';

const base = '/branch-management/payroll';
const monthEnd = (value) => {
  const [year, month] = value.split('-').map(Number);
  return `${value}-${new Date(year, month, 0).getDate()}`;
};

export default function BranchPayrollPage({ branchScope, readOnly }) {
  const [employees, setEmployees] = useState([]);
  const [monthlyEmployees, setMonthlyEmployees] = useState([]);
  const [history, setHistory] = useState([]);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [bank, setBank] = useState({ bankName: '', bankAccount: '', bankHolder: '' });
  const [employeeId, setEmployeeId] = useState('');
  const [tab, setTab] = useState('rates');
  const [historicalRates, setHistoricalRates] = useState(false);
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
    setLoading(true);
    setEmployees([]);
    setMonthlyEmployees([]);
    setHistory([]);
    setEmployeeId('');
    setPreview(null);
    setAttendanceNotes(null);
    Promise.all([financeApi.get(base + '/employees', {}, branchScope),
      financeApi.get(`${base}/${month}/employees`, {}, branchScope),
      financeApi.get(`${base}/${month}/history`, {}, branchScope)])
      .then(([rows, monthly, sent]) => {
        if (active) { setEmployees(rows); setMonthlyEmployees(monthly); setHistory(sent); }
      })
      .catch((e) => { if (active) setError(Object.values(apiErrors(e)).join(' ')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [branchScope, month, revision]);
  useEffect(() => {
    const employee = [...monthlyEmployees, ...employees].find((e) => e.id === employeeId);
    setBank({ bankName: employee?.bank_name || '', bankAccount: employee?.bank_account || '',
      bankHolder: employee?.bank_holder || '' });
  }, [employeeId, employees, monthlyEmployees]);
  const choices = tab === 'rates' ? (historicalRates ? monthlyEmployees : employees) : tab === 'bank'
    ? [...new Map([...employees, ...monthlyEmployees].map((e) => [e.id, e])).values()] : monthlyEmployees;
  const existing = history.find((row) => row.employee_id === employeeId);


  async function save(path, body, message) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await financeApi.post(base + path, body, branchScope);
      if (message) {
        setNotice(result?.message || message);
        setRevision((value) => value + 1);
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
        {[['rates', 'Đơn giá giờ'], ['settlement', 'Chốt lương tháng'], ['history', 'Lương đã gửi'], ['bank', 'Tài khoản nhận lương']].map(([key, label]) => (
          <button key={key} type="button" disabled={busy}
            aria-current={tab === key ? 'page' : undefined}
            onClick={() => {
              setTab(key);
              setEmployeeId('');
              setAttendanceNotes(null);
              setPreview(null);
              setError('');
              setNotice('');
            }}>
            {label}
          </button>
        ))}
      </nav>
      {tab !== 'settlement' && error && <p className="finance-error" role="alert">{error}</p>}
      {tab !== 'settlement' && notice && <p className="finance-info" role="status">{notice}</p>}
      {tab === 'rates' && <label className="finance-check">
        <input type="checkbox" checked={historicalRates} disabled={busy || loading}
          onChange={(event) => {
            setHistoricalRates(event.target.checked);
            if (event.target.checked) setEffectiveFrom(month + '-01');
            setEmployeeId('');
            setAmount('');
            setError('');
            setNotice('');
          }} />
        Bổ sung đơn giá công cũ (nhân viên đã nghỉ, khóa tài khoản hoặc chuyển cơ sở)
      </label>}
      {tab === 'rates' && historicalRates && <p className="finance-muted">
        Chọn tháng đã kết thúc. Chỉ bổ sung đơn giá còn thiếu cho công đi làm tại cơ sở, không thay đổi tháng lương đã gửi.
      </p>}
      {(tab !== 'rates' || historicalRates) && <label>Tháng
        <input type="month" value={month} required disabled={busy}
          onChange={(event) => {
            if (event.target.value) {
              setMonth(event.target.value);
              if (historicalRates) setEffectiveFrom(event.target.value + '-01');
              setError('');
              setNotice('');
            }
          }} />
      </label>}
      {loading && <p role="status">Đang tải thông tin lương…</p>}
      {tab !== 'history' && <label>Nhân viên
        <select value={employeeId} onChange={(event) => {
          const id = event.target.value;
          const saved = history.find((row) => row.employee_id === id);
          setEmployeeId(id);
          setBonus(String(saved?.bonus ?? 0));
          setPenalty(String(saved?.penalty ?? 0));
          setReason(saved?.reason || '');
          setPreview(null);
          setAttendanceNotes(null);
          setError('');
          setNotice('');
        }} disabled={busy || loading}>
          <option value="">Chọn nhân viên</option>
          {choices.map((employee) => <option key={employee.id} value={employee.id}>{employee.code} · {employee.name}</option>)}
        </select>
      </label>}
      {tab === 'rates' ? <form onSubmit={(event) => {
        event.preventDefault();
        if (historicalRates && (!effectiveFrom.startsWith(month) || month >= today().slice(0, 7))) {
          setError('Vui lòng chọn tháng đã kết thúc và ngày hiệu lực trong tháng công cũ đã chọn.');
          return;
        }
        save('/hourly-rates', { employeeId, effectiveFrom, amount: Number(amount) }, 'Đã lưu đơn giá giờ.');
      }}>
        <fieldset disabled={busy || readOnly || !employeeId}>
          <legend className="branch-payroll-legend">Đơn giá giờ</legend>
          <label>Ngày hiệu lực <input type="date" required value={effectiveFrom} onChange={change(setEffectiveFrom)}
            min={historicalRates ? month + '-01' : undefined}
            max={historicalRates ? monthEnd(month) : undefined} /></label>
          <label>Đơn giá (đ/giờ)
            <MoneyInput required value={amount} onValueChange={setAmount} />
          </label>
          <button type="submit">Lưu đơn giá</button>
        </fieldset>
      </form> : tab === 'settlement' ? <form onSubmit={(event) => {
        event.preventDefault();
        save('/' + month + '/preview', settlement);
      }}>
        <fieldset disabled={busy || loading || readOnly || !employeeId || existing?.ready}>
          <legend className="branch-payroll-legend">Chốt lương tháng</legend>
          {existing && <p className="finance-info">{existing.ready
            ? 'Lương tháng đã gửi kế toán. Xem lại tại tab Lương đã gửi.'
            : 'Cơ sở đã gửi phần lương và đang chờ cơ sở khác. Chốt lại sẽ cập nhật phần lương của cơ sở này.'}</p>}
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
          <label>Thưởng thêm
            <MoneyInput required value={bonus} onValueChange={(value) => {
              setBonus(value);
              setPreview(null);
            }} />
          </label>
          <label>Tiền phạt
            <MoneyInput required value={penalty} onValueChange={(value) => {
              setPenalty(value);
              setPreview(null);
            }} />
          </label>
          <label>Lý do <textarea value={reason} maxLength={2000} onChange={change(setReason)} /></label>
          <button type="submit">Xem trước lương</button>
          {preview && (
            <div role="status">
              <p>Tiền công {vnd(preview.wage)} · Thưởng trên 100 giờ {vnd(preview.reward)} · Tạm ứng {vnd(preview.advance)}</p>
              {preview.multipleBranches && <p className="finance-info">Nhân viên có công ở nhiều cơ sở. Thưởng trên 100 giờ và tạm ứng sẽ được tính một lần khi tất cả cơ sở đã chốt.</p>}
              <p><strong>{preview.multipleBranches ? 'Phần lương cơ sở' : 'Thực nhận'} {vnd(preview.net)}</strong></p>
              <button type="button" onClick={() => save('/' + month + '/submit',
                { ...settlement, reviewedTotal: preview.net, reviewedRevision: preview.revision }, 'Đã chốt lương và gửi kế toán.')}>
                {busy ? 'Đang xử lý…' : 'Chốt và gửi kế toán'}
              </button>
            </div>
          )}
        </fieldset>
        <div ref={settlementFeedback}>
          {error && <p className="finance-error" role="alert">{error}</p>}
          {notice && <p className="finance-info" role="status">{notice}</p>}
        </div>
      </form> : tab === 'bank' ? <form onSubmit={(event) => {
        event.preventDefault();
        save('/bank-details', { employeeId, month, ...bank }, 'Đã lưu tài khoản nhận lương.');
      }}>
        <fieldset disabled={busy || loading || readOnly || !employeeId}>
          <legend className="branch-payroll-legend">Tài khoản nhận lương</legend>
          <div className="finance-grid">
            {[['bankName', 'Ngân hàng', 200], ['bankAccount', 'Số tài khoản', 60], ['bankHolder', 'Chủ tài khoản', 200]].map(([key, label, max]) => (
              <label key={key}>{label} *
                <input required maxLength={max} value={bank[key]}
                  onChange={(event) => setBank({ ...bank, [key]: event.target.value })} />
              </label>
            ))}
          </div>
          <button type="submit" className="finance-primary">Lưu tài khoản nhận lương</button>
        </fieldset>
      </form> : <div className="finance-table-wrap">
        <table>
          <thead><tr><th>Nhân viên</th><th>Phần lương cơ sở</th><th>Thực nhận cả tháng</th><th>Trạng thái</th><th>Chi tiết</th></tr></thead>
          <tbody>{history.map((row) => <tr key={row.employee_id}>
            <td>{row.code}<small>{row.name}</small></td>
            <td>{vnd(row.branch_amount)}</td>
            <td>{row.ready ? vnd(row.final_net) : 'Chờ tổng hợp'}</td>
            <td>{row.paid ? 'Đã thanh toán' : row.ready ? 'Đã gửi kế toán' : 'Chờ cơ sở khác chốt'}</td>
            <td><details><summary>Chi tiết</summary>
              <p>Tiền công: {vnd(row.wage)}</p>
              <p>Thưởng thêm: {vnd(row.bonus)} · Khấu trừ: {vnd(row.penalty)}</p>
              <p>{row.reason || 'Không có điều chỉnh.'}</p>
              {!row.ready && <button type="button" disabled={busy || readOnly} onClick={() => {
                setTab('settlement'); setEmployeeId(row.employee_id);
                setBonus(String(row.bonus)); setPenalty(String(row.penalty)); setReason(row.reason);
                setPreview(null); setAttendanceNotes(null);
              }}>Điều chỉnh phần lương</button>}
            </details></td>
          </tr>)}</tbody>
        </table>
        {!loading && !history.length && <p>Chưa có phần lương đã gửi trong tháng này.</p>}
      </div>}
    </section>
  );
}
