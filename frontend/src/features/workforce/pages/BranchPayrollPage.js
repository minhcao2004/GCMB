/** HuyDV: QLCS đặt đơn giá; chốt lương tại bảng công và lương tháng. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi, today } from '../../finance/services/financeApi';
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
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [employeeId, setEmployeeId] = useState('');
  const [historicalRates, setHistoricalRates] = useState(false);
  const [month, setMonth] = useState(today().slice(0, 7));
  const [effectiveFrom, setEffectiveFrom] = useState(today());
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true);
    setEmployees([]);
    setMonthlyEmployees([]);
    setEmployeeId('');
    Promise.all([financeApi.get(base + '/employees', {}, branchScope),
      financeApi.get(`${base}/${month}/employees`, {}, branchScope)])
      .then(([rows, monthly]) => {
        if (active) { setEmployees(rows); setMonthlyEmployees(monthly); }
      })
      .catch((e) => { if (active) setError(Object.values(apiErrors(e)).join(' ')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [branchScope, month, revision]);
  const choices = historicalRates ? monthlyEmployees : employees;

  async function save(path, body, message) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await financeApi.post(base + path, body, branchScope);
      if (message) {
        setNotice(result?.message || message);
        setRevision((value) => value + 1);
      }
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
    } finally {
      setBusy(false);
    }
  }

  const change = (setter) => (event) => setter(event.target.value);
  return (
    <section className="finance-page branch-payroll-page">
      <h2>Đơn giá giờ cơ sở</h2>
      {error && <p className="finance-error" role="alert">{error}</p>}
      {notice && <p className="finance-info" role="status">{notice}</p>}
      <label className="finance-check">
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
      </label>
      {historicalRates && <p className="finance-muted">
        Chọn tháng đã kết thúc. Chỉ bổ sung đơn giá còn thiếu cho công đi làm tại cơ sở, không thay đổi tháng lương đã gửi.
      </p>}
      {historicalRates && <label>Tháng
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
      <label>Nhân viên
        <select value={employeeId} onChange={(event) => {
          const id = event.target.value;
          setEmployeeId(id);
          setError('');
          setNotice('');
        }} disabled={busy || loading}>
          <option value="">Chọn nhân viên</option>
          {choices.map((employee) => <option key={employee.id} value={employee.id}>{employee.code} · {employee.name}</option>)}
        </select>
      </label>
      <form onSubmit={(event) => {
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
      </form>
    </section>
  );
}
