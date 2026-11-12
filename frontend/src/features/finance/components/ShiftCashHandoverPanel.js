/** Author: HuyDV. Bàn giao ca giữ nguyên tiền két; chênh lệch chuyển QLCS xử lý. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, today, vnd } from '../services/financeApi';
import MoneyInput from './MoneyInput';
import '../styles/finance.css';
import '../styles/cashHandover.css';

const labels = { MATCHED: 'Đã xác nhận đủ', DISCREPANCY: 'Chờ QLCS xử lý', RESOLVED: 'QLCS đã xử lý' };
const displayDate = (value) => String(value).slice(0, 10).split('-').reverse().join('/');
const errorText = (error) => Object.values(apiErrors(error)).join(' ');

export default function ShiftCashHandoverPanel({ user, manager = false, readOnly = false }) {
  const base = `/${manager ? 'branch-management' : 'reception'}/cash-handovers`;
  const branch = manager ? user.branchId : undefined;
  const [date, setDate] = useState(today());
  const [data, setData] = useState(null);
  const [selected, setSelected] = useState(null);
  const [actual, setActual] = useState('');
  const [note, setNote] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [resolution, setResolution] = useState('');
  const [pendingId, setPendingId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setData(null);
    setSelected(null);
    setPendingId('');
    setError('');
    api.get(base, { date }, branch).then((result) => {
      if (active) setData(result);
    }).catch((failure) => {
      if (active) setError(errorText(failure));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [base, branch, date, refresh]);

  const choose = (shift) => {
    if (!shift.canReceive) return;
    setSelected(shift);
    setActual(String(Math.max(0, Number(shift.expected))));
    setNote('');
    setConfirmed(false);
    setError('');
    setNotice('');
  };
  const save = async (event) => {
    event.preventDefault();
    if (!selected?.canReceive || !confirmed || actual === '' || busy || readOnly) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await api.post(base, { date, shift: selected.shift, actual: Number(actual),
        note, confirmed, revision: selected.revision }, branch);
      setNotice(result.status === 'MATCHED' ? 'Đã xác nhận nhận đủ tiền mặt bàn giao.'
        : 'Đã ghi nhận tiền thực đếm và chuyển chênh lệch cho QLCS xử lý.');
      setRefresh((value) => value + 1);
    } catch (failure) { setError(errorText(failure)); }
    finally { setBusy(false); }
  };
  const resolve = async (event) => {
    event.preventDefault();
    if (!resolution.trim() || busy || readOnly) return;
    setBusy(true);
    setError('');
    try {
      await api.post(`${base}/${pendingId}/resolve`, { note: resolution }, branch);
      setNotice('Đã lưu kết quả xử lý chênh lệch bàn giao.');
      setRefresh((value) => value + 1);
    } catch (failure) { setError(errorText(failure)); }
    finally { setBusy(false); }
  };
  const different = selected && actual !== '' && Number(actual) !== Number(selected.expected);

  return <section className="cash-handover" aria-label="Bàn giao tiền mặt chuyển ca">
    <header className="cash-handover-heading">
      <div><h2>Bàn giao tiền mặt chuyển ca</h2><p>Ca sau kiểm đếm và xác nhận tiền mặt nhận từ ca trước.</p></div>
      <div className="cash-handover-date">
        <label>Ngày của ca bàn giao<input type="date" value={date} max={today()} disabled={busy}
          onChange={(event) => { setDate(event.target.value || today()); setNotice(''); }} /></label>
        <button type="button" disabled={loading || busy} onClick={() => setRefresh((value) => value + 1)}>Tải lại</button>
      </div>
    </header>
    {error && <p className="finance-error" role="alert">{error}</p>}
    {notice && <p className="finance-success" role="status">{notice}</p>}
    {loading ? <p role="status">Đang tải bàn giao ca…</p> : data && <>
      {data.pending.length > 0 && <section className="finance-card cash-handover-pending">
        <h3>{data.pending.length} bàn giao chênh lệch cần QLCS xử lý</h3>
        {data.pending.map((record) => <div className="cash-handover-pending-row" key={record.id}>
          <div><strong>{displayDate(record.business_date)} · Ca {record.shift_number}</strong>
            <p>Thực đếm {vnd(record.actual_amount)} · Chênh lệch {vnd(record.variance)}</p>
            <p>{record.receiver}: {record.note}</p></div>
          {manager && <button type="button" disabled={readOnly || busy} onClick={() => {
            setPendingId(record.id); setResolution('');
          }}>Xử lý chênh lệch</button>}
        </div>)}
        {manager && pendingId && <form className="cash-handover-form" onSubmit={resolve}>
          <label>Kết quả xử lý<textarea required maxLength={2000} value={resolution}
            onChange={(event) => setResolution(event.target.value)} disabled={busy || readOnly} /></label>
          <p className="finance-muted">Kết quả xử lý được lưu vào lịch sử, không tự thay đổi số tiền két.</p>
          <div className="finance-actions"><button type="button" disabled={busy} onClick={() => setPendingId('')}>Hủy</button>
            <button className="finance-primary" disabled={busy || readOnly || !resolution.trim()}>Lưu kết quả xử lý</button></div>
        </form>}
      </section>}
      <div className="cash-handover-grid">
        {data.shifts.map((shift) => {
          const record = shift.record;
          return <article className={`finance-card cash-handover-shift${selected?.shift === shift.shift ? ' is-selected' : ''}`} key={shift.shift}>
            <header><h3>Ca {shift.shift} → Ca {shift.receiveShift}</h3><span>{shift.hours}</span></header>
            <p>Ca giao: {shift.shift} · Ca nhận: {shift.receiveShift}{shift.shift === 4 ? ` ngày ${displayDate(shift.receiveDate)}` : ''}</p>
            <p className="cash-handover-status">{record ? record.assignmentVerified === false ? 'Cần kiểm tra phân công' : labels[record.status]
              : shift.ended ? 'Chưa nhận bàn giao' : 'Chưa đến giờ bàn giao'}</p>
            <dl><dt>Tiền mặt cần bàn giao</dt><dd>{record ? vnd(record.expected_amount) : shift.ended ? vnd(shift.expected) : '—'}</dd>
              {record && <><dt>Thực đếm</dt><dd>{vnd(record.actual_amount)}</dd><dt>Chênh lệch</dt><dd>{vnd(record.variance)}</dd></>}
            </dl>
            {record && <><p>{record.receiver} · {new Date(record.received_at).toLocaleString('vi-VN')}</p>
              {record.assignmentVerified === false && <p className="finance-warning">Bản ghi này không khớp lịch phân công ca nhận tại thời điểm xác nhận. QLCS cần kiểm tra lại.</p>}
              {record.note && <p>Lý do: {record.note}</p>}
              {record.resolution && <p>{record.resolver}: {record.resolution}</p>}</>}
            {!manager && !record && <>
              {shift.canReceive ? <button type="button" disabled={busy || readOnly}
                onClick={() => choose(shift)}>Kiểm đếm / nhận bàn giao</button>
                : <p className="finance-muted">{shift.ended ? `Chỉ nhân viên được phân công ca ${shift.receiveShift} ngày ${displayDate(shift.receiveDate)} được nhận bàn giao.`
                  : 'Chưa đến giờ nhận bàn giao.'}</p>}
            </>}
          </article>;
        })}
      </div>
      {!manager && selected && <form className="finance-card cash-handover-form" onSubmit={save}>
        <h3>Ca {selected.receiveShift} nhận tiền từ ca {selected.shift} · {selected.hours}</h3>
        <p>Tiền mặt cần bàn giao: <strong>{vnd(selected.expected)}</strong></p>
        <label>Tiền mặt thực đếm (đ)<MoneyInput required value={actual} onValueChange={(value) => {
          setActual(value); setConfirmed(false);
        }} disabled={busy || readOnly} /></label>
        {different && <p className="finance-warning">Chênh lệch: {vnd(Number(actual) - Number(selected.expected))}. QLCS sẽ nhận thông tin để xử lý.</p>}
        {different && <label>Lý do chênh lệch<textarea required maxLength={2000} value={note}
          onChange={(event) => setNote(event.target.value)} disabled={busy || readOnly} /></label>}
        <label className="cash-handover-check"><input type="checkbox" checked={confirmed}
          onChange={(event) => setConfirmed(event.target.checked)} disabled={busy || readOnly} />
          {different ? 'Tôi xác nhận số tiền thực đếm và lý do chênh lệch.' : 'Tôi đã kiểm đếm và nhận đủ tiền mặt bàn giao.'}</label>
        <div className="finance-actions"><button type="button" disabled={busy} onClick={() => setSelected(null)}>Hủy</button>
          <button className="finance-primary" disabled={busy || readOnly || !confirmed || actual === '' || (different && !note.trim())}>
            {busy ? 'Đang lưu…' : different ? 'Ghi nhận chênh lệch' : 'Xác nhận nhận đủ'}</button></div>
      </form>}
      <p className="finance-muted">Tiền cần bàn giao là tiền còn trong két đến hết ca, gồm tiền từ ca trước.
        Ca 4 được nhận bàn giao sau 00:00; chọn ngày hôm trước để xác nhận. Chuyển khoản không tính vào tiền két.</p>
    </>}
  </section>;
}
