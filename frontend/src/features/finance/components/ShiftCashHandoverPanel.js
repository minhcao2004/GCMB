/** Author: HuyDV. Bàn giao ca giữ nguyên tiền két; chênh lệch chuyển QLCS xử lý. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, today, vnd } from '../services/financeApi';
import MoneyInput from './MoneyInput';
import '../styles/finance.css';
import '../styles/cashHandover.css';

const labels = { MATCHED: 'Khớp tiền', DISCREPANCY: 'Chờ QLCS xử lý', RESOLVED: 'QLCS đã xử lý' };
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

  const choose = (shift, dispatch) => {
    if (dispatch ? !shift.canDispatch : !shift.canReceive) return;
    setSelected({ ...shift, dispatching: dispatch,
      expected: dispatch ? shift.expected : shift.receiveExpected,
      revision: dispatch ? shift.revision : shift.receiveRevision });
    setActual('');
    setNote('');
    setConfirmed(false);
    setError('');
    setNotice('');
  };
  const save = async (event) => {
    event.preventDefault();
    if (!selected || !confirmed || actual === '' || busy || readOnly) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await api.post(selected.dispatching ? `${base}/dispatch` : base, { date, shift: selected.shift, actual: Number(actual),
        note, confirmed, revision: selected.revision }, branch);
      setNotice(result.status === 'MATCHED' ? selected.dispatching
        ? 'Đã gửi bàn giao. Nhân viên ca sau cần kiểm đếm và xác nhận nhận tiền.' : 'Đã xác nhận nhận đủ tiền mặt bàn giao.'
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
      <div><h2>Bàn giao tiền mặt chuyển ca</h2></div>
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
          <div><strong>{displayDate(record.business_date)} · Ca {record.shift_number} · {record.stage === 'DISPATCH' ? 'Giao tiền' : 'Nhận tiền'}</strong>
            <p>Thực đếm {vnd(record.actual_amount)} · Chênh lệch {vnd(record.variance)}</p>
            <p>{record.actor_name}: {record.note}</p></div>
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
          const dispatch = shift.dispatch;
          return <article className={`finance-card cash-handover-shift${selected?.shift === shift.shift ? ' is-selected' : ''}`} key={shift.shift}>
            <header><h3>Ca {shift.shift} → Ca {shift.receiveShift}</h3><span>{shift.hours}</span></header>
            <p>Ca giao: {shift.shift} · Ca nhận: {shift.receiveShift}{shift.shift === 4 ? ` ngày ${displayDate(shift.receiveDate)}` : ''}</p>
            <p className="cash-handover-status">{record ? 'Đã nhận bàn giao' : dispatch ? 'Đã giao · Chờ ca sau nhận'
              : shift.ended ? 'Chờ ca trước giao tiền' : 'Chưa đến giờ bàn giao'}</p>
            {!dispatch && !record && <dl><dt>Tiền két theo hệ thống</dt><dd>{shift.ended ? vnd(shift.expected) : '—'}</dd></dl>}
            {[{ entry: dispatch, title: 'Người giao', time: 'handed_at' }, { entry: record, title: 'Người nhận', time: 'received_at' }].map(({ entry, title, time }) => entry &&
              <section className="cash-handover-step" key={title} aria-label={title}>
                <h4>{title} · {labels[entry.status]}</h4>
                <p>{entry.actor_name} · {new Date(entry[time]).toLocaleString('vi-VN')}</p>
                <dl><dt>{title === 'Người giao' || !dispatch ? 'Tiền két theo hệ thống' : 'Số tiền ca trước giao'}</dt><dd>{vnd(entry.expected_amount)}</dd>
                  <dt>{title === 'Người giao' ? 'Thực đếm khi giao' : 'Thực đếm khi nhận'}</dt><dd>{vnd(entry.actual_amount)}</dd>
                  <dt>Chênh lệch</dt><dd>{vnd(entry.variance)}</dd></dl>
                {entry.note && <p>Lý do: {entry.note}</p>}
                {entry.resolution && <p>{entry.resolver}: {entry.resolution}</p>}
              </section>)}
            {!manager && !record && <>
              {shift.canDispatch && <button type="button" disabled={busy || readOnly}
                onClick={() => choose(shift, true)}>Kiểm đếm / giao tiền</button>}
              {shift.canReceive && <button type="button" disabled={busy || readOnly}
                onClick={() => choose(shift, false)}>Kiểm đếm / nhận tiền</button>}
              {!shift.canDispatch && !shift.canReceive && <p className="finance-muted">{!shift.ended ? 'Chưa đến giờ bàn giao.'
                : !dispatch ? `Nhân viên ca ${shift.shift} cần gửi bàn giao trước.`
                  : `Chỉ nhân viên được phân công ca ${shift.receiveShift} ngày ${displayDate(shift.receiveDate)} được nhận tiền.`}</p>}
            </>}
          </article>;
        })}
      </div>
      {!manager && selected && <form className="finance-card cash-handover-form" onSubmit={save}>
        <h3>{selected.dispatching ? `Ca ${selected.shift} bàn giao tiền cho ca ${selected.receiveShift}`
          : `Ca ${selected.receiveShift} nhận tiền từ ca ${selected.shift}`} · {displayDate(date)}</h3>
        <p>{selected.dispatching ? 'Tiền két theo hệ thống' : 'Số tiền ca trước giao'}: <strong>{vnd(selected.expected)}</strong></p>
        <label>Tiền mặt thực đếm (đ)<MoneyInput required value={actual} onValueChange={(value) => {
          setActual(value); setConfirmed(false);
        }} disabled={busy || readOnly} /></label>
        {different && <p className="finance-warning">Chênh lệch: {vnd(Number(actual) - Number(selected.expected))}. QLCS sẽ nhận thông tin để xử lý.</p>}
        {different && <label>Lý do chênh lệch<textarea required maxLength={2000} value={note}
          onChange={(event) => setNote(event.target.value)} disabled={busy || readOnly} /></label>}
        <label className="cash-handover-check"><input type="checkbox" checked={confirmed}
          onChange={(event) => setConfirmed(event.target.checked)} disabled={busy || readOnly} />
          Tôi đã kiểm đếm và xác nhận số tiền {selected.dispatching ? 'bàn giao cho ca sau' : 'nhận từ ca trước'}{different ? ', kèm lý do chênh lệch' : ''}.</label>
        <div className="finance-actions"><button type="button" disabled={busy} onClick={() => setSelected(null)}>Hủy</button>
          <button className="finance-primary" disabled={busy || readOnly || !confirmed || actual === '' || (different && !note.trim())}>
            {busy ? 'Đang lưu…' : selected.dispatching ? 'Xác nhận giao tiền' : 'Xác nhận nhận tiền'}</button></div>
      </form>}
    </>}
  </section>;
}
