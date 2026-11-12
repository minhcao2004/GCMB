/** Author: HuyDV. UC-98..101 / BR-95..98/208: chốt tiền mặt trong ngày, kế toán đối soát. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, vnd, today, emptyMessage } from '../services/financeApi';
import ShiftCashHandoverPanel from '../components/ShiftCashHandoverPanel';
import MoneyInput from '../components/MoneyInput';
import DrawerFundingPanel from '../components/DrawerFundingPanel';
import '../styles/finance.css';

const labels = { SUBMITTED: 'Chờ kế toán đối soát', NEEDS_EXPLANATION: 'Cần bổ sung', RECONCILED: 'Đã đối soát' };
const message = (e) => Object.values(apiErrors(e)).join(' ');
const displayDate = (value) => String(value || '').split('-').reverse().join('/');

export default function CashClosingPage({ user, accounting = false, readOnly = false }) {
  const base = accounting ? '/accounting/cash-closing' : '/branch-management/cash-closing';
  const branch = accounting ? undefined : user.branchId;
  const [tab, setTab] = useState(accounting ? 'history' : 'create');
  const [date, setDate] = useState(today());
  const [from, setFrom] = useState(today().slice(0, 7) + '-01');
  const [to, setTo] = useState(today());
  const [status, setStatus] = useState(accounting ? 'SUBMITTED' : '');
  const [search, setSearch] = useState('');
  const [preview, setPreview] = useState(null);
  const [saved, setSaved] = useState(null);
  const [rows, setRows] = useState([]);
  const [detail, setDetail] = useState(null);
  const [actual, setActual] = useState('');
  const [explanation, setExplanation] = useState('');
  const [evidence, setEvidence] = useState(null);
  const [conclusion, setConclusion] = useState('');
  const [resolved, setResolved] = useState(false);
  const [amount, setAmount] = useState('');
  const [fullSweep, setFullSweep] = useState(false);
  const [reason, setReason] = useState('');
  const [reference, setReference] = useState('');
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setDetail(null);
    setSaved(null);
    setPreview(null);
    setConfirm(false);
    if (tab === 'handover') { setLoading(false); return () => { active = false; }; }
    const load = async () => {
      try {
        if (tab === 'create' || tab === 'remit') {
          const data = await api.get(base + '/preview', { date }, branch);
          if (!active) return;
          setPreview(data);
          if (data.existing.length) {
            const report = await api.get(base + '/' + data.existing[0].id, {}, branch);
            if (!active) return;
            setSaved(report);
            if (report.status === 'NEEDS_EXPLANATION') {
              setActual(String(report.actual_closing));
              setExplanation(report.explanation || '');
            }
          }
        } else {
          const result = await api.get(base, { from, to, status }, branch);
          if (active) setRows(result);
        }
      } catch (e) {
        if (active) setError(message(e));
      } finally {
        if (active) setLoading(false);
      }
    };
    load();
    return () => { active = false; };
  }, [base, branch, tab, date, from, to, status, refresh]);

  const open = async (id) => {
    setBusy(true);
    setError('');
    setConclusion('');
    setResolved(false);
    try { setDetail(await api.get(base + '/' + id, {}, branch)); }
    catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  };
  const edit = (report) => {
    setEvidence(null);
    setConfirm(false);
    setDate(String(report.business_date).slice(0, 10));
    setTab('create');
    setRefresh((n) => n + 1);
  };
  const changeDate = (value) => {
    setDate(value);
    setActual('');
    setExplanation('');
    setEvidence(null);
    setConfirm(false);
    setRequestId(crypto.randomUUID());
    setFullSweep(false);
  };
  const submit = async (event) => {
    event.preventDefault();
    if (!confirm) { setConfirm(true); return; }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      if (tab === 'remit') {
        await api.post(base + '/remittances', { date, amount: Number(amount), reason, reference, requestId, fullSweep }, branch);
        setNotice('Đã ghi khoản nộp tiền khỏi két. Khoản này được trừ riêng trong báo cáo ngày.');
        setAmount('');
        setReason('');
        setReference('');
        setRequestId(crypto.randomUUID());
        setFullSweep(false);
      } else {
        const body = new FormData();
        body.append('command', new Blob([JSON.stringify({
          date, actual: Number(actual), explanation,
          revision: preview.revision, reportVersion: saved?.report_version,
        })], { type: 'application/json' }));
        if (evidence) body.append('evidence', evidence);
        await api.post(base, body, branch);
        setNotice('Đã gửi báo cáo cho kế toán đối soát. Gửi chốt không tự trừ tiền khỏi két.');
        setActual('');
        setExplanation('');
        setEvidence(null);
        setFrom(date);
        setTo(date);
        setStatus('');
        setTab('history');
      }
      setConfirm(false);
      setRefresh((n) => n + 1);
    } catch (e) { setError(message(e)); setConfirm(false); }
    finally { setBusy(false); }
  };
  const review = async (returned) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await api.post(base + '/' + detail.id + (returned ? '/return' : '/reconcile'), { resolved, conclusion });
      setNotice(returned ? 'Đã yêu cầu QLCS bổ sung báo cáo.' : 'Đã hoàn tất đối soát. Không phát sinh thao tác nhận hoặc nộp tiền.');
      setRefresh((n) => n + 1);
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  };
  const cashVariance = preview && actual !== '' ? Number(actual) - Number(preview.expected) : null;
  const needsExplanation = cashVariance !== null && cashVariance !== 0;
  const shown = rows.filter((r) => (r.branchName + ' ' + r.business_date + ' ' + (r.explanation || '')).toLowerCase().includes(search.toLowerCase()));
  const canCorrect = saved?.status === 'NEEDS_EXPLANATION';
  const remittanceBlocked = preview?.closingBlocked || (!!saved && date !== today());

  return <section className="finance-page cash-closing-page">
    <h1>{accounting ? 'Đối soát báo cáo cuối ngày' : 'Chốt quỹ cuối ngày'}</h1>
    <p className="finance-muted">{accounting ? 'Đối chiếu tiền mặt thực đếm với số dư két theo sổ; ghi kết luận hoặc yêu cầu bổ sung.' : user.branchName}</p>
    <nav className="finance-tabs" aria-label="Báo cáo thu ngân">
      {!accounting && <button aria-current={tab === 'create' ? 'page' : undefined} onClick={() => setTab('create')}>Báo cáo cuối ngày</button>}
      <button aria-current={tab === 'history' ? 'page' : undefined} onClick={() => setTab('history')}>Lịch sử báo cáo</button>
      {!accounting && <button aria-current={tab === 'remit' ? 'page' : undefined} onClick={() => setTab('remit')}>Nộp tiền khỏi két</button>}
      {!accounting && <button aria-current={tab === 'handover' ? 'page' : undefined} onClick={() => setTab('handover')}>Bàn giao ca</button>}
    </nav>
    {tab === 'handover' ? <ShiftCashHandoverPanel user={user} manager readOnly={readOnly} /> : <>
    {!accounting && preview?.pendingHandovers > 0 && <p className="finance-warning" role="status">
      Có {preview.pendingHandovers} bàn giao ca chênh lệch cần xử lý.{' '}
      <button type="button" onClick={() => setTab('handover')}>Xem bàn giao ca</button>
    </p>}
    {error && <p className="finance-error" role="alert">{error}</p>}
    {notice && <p className="finance-success" role="status">{notice}</p>}
    <div className="finance-toolbar">
      {tab === 'create' || tab === 'remit' ? <label>Ngày
        <input type="date" value={date} max={today()} onChange={(e) => changeDate(e.target.value)} />
      </label> : <>
        <label>Tìm kiếm<input value={search} placeholder="Cơ sở, ngày, giải trình…" onChange={(e) => setSearch(e.target.value)} /></label>
        <label>Từ ngày<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label>Đến ngày<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <label>Trạng thái<select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">Tất cả</option>
          {Object.entries(labels).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
        </select></label>
      </>}
      <button disabled={loading || busy} onClick={() => setRefresh((n) => n + 1)}>Tải lại</button>
    </div>
    {loading ? <p role="status">Đang tải…</p> : preview && (tab === 'create' || tab === 'remit') ? <>
      {tab === 'remit' ? <section className="cash-drawer-workspace" aria-label="Nộp tiền và cấp lại tiền két">
      {preview.cashCycle?.due ? <p className="finance-warning" role="alert">Đã {preview.cashCycle.elapsedDays} ngày từ đầu chu kỳ giữ tiền. Đến hạn 5 ngày: QLCS cần nộp hết tiền trong két, rồi yêu cầu cấp lại tiền lẻ.</p>
        : null}
      <form className="finance-card" onSubmit={submit}>
        <h2>Ghi nhận khoản tiền đã nộp khỏi két</h2>
        <p>Chỉ ghi khi tiền đã thực sự được nộp ra khỏi két. Giao dịch được ghi theo ngày nộp thực tế; báo cáo đã gửi vẫn giữ nguyên lịch sử.</p>
        {remittanceBlocked ? <p className="finance-warning">Ngày cũ đã chốt hoặc đã có ngày chốt mới hơn. Vui lòng ghi khoản nộp vào đúng ngày thực tế.</p> : <fieldset disabled={busy || readOnly}>
          {date === today() && <button type="button" disabled={Number(preview.currentBalance) <= 0} onClick={() => { setAmount(String(preview.currentBalance)); setFullSweep(true); setConfirm(false); if (!reason.trim()) setReason('Nộp toàn bộ tiền mặt khỏi két cơ sở.'); }}>Nộp hết tiền trong két ({vnd(preview.currentBalance)})</button>}
          <label>Số tiền đã nộp *<MoneyInput required value={amount} onValueChange={(v) => { setAmount(v); setFullSweep(false); setConfirm(false); }} /></label>
          <label>Nội dung / nơi hoặc người nhận *<textarea required maxLength={2000} value={reason} onChange={(e) => { setReason(e.target.value); setConfirm(false); }} /></label>
          <label>Mã chứng từ (không bắt buộc)<input maxLength={200} value={reference} onChange={(e) => { setReference(e.target.value); setConfirm(false); }} /></label>
          {confirm && <p className="finance-warning">Xác nhận đã nộp {vnd(amount)}? Khoản này sẽ giảm số dư két và được giữ trong lịch sử.</p>}
          <ConfirmButtons confirm={confirm} busy={busy} cancel={() => setConfirm(false)} label="Ghi nhận đã nộp tiền" />
        </fieldset>}
      </form>
      <DrawerFundingPanel user={user} refreshKey={refresh} onChange={() => setRefresh((n) => n + 1)} />
      </section> : <div className="finance-columns">
        <article className="finance-card">
          <h2>Số liệu hệ thống · {displayDate(date)}</h2>
          <ReportSummary source={preview} />
          <details><summary>Giao dịch tiền mặt trong ngày</summary><MovementTable rows={preview.movements} /></details>
        </article>
        <form className="finance-card" onSubmit={submit}>
          <h2>{canCorrect ? 'Bổ sung báo cáo' : 'Đối chiếu thực tế'}</h2>
          {saved && !canCorrect ? <>
            <p className="finance-info">{labels[saved.status]} · Tiền mặt thực đếm: {vnd(saved.actual_closing)}</p>
            <button type="button" onClick={() => { setTab('history'); setFrom(date); setTo(date); setStatus(''); }}>Xem báo cáo đã gửi</button>
          </> : preview.closingBlocked && !canCorrect ? <p className="finance-warning">Ngày này chỉ được xem. Không được chốt lùi trước ngày {displayDate(preview.lastClosingDate)}.</p> : <fieldset disabled={busy || readOnly}>
            {canCorrect && <div className="finance-warning">
              {saved.reviews.filter((r) => r.decision === 'NEEDS_EXPLANATION').slice(-1).map((r) => <p key={r.id}>Kế toán yêu cầu: {r.note}</p>)}
              <p>Lưu thành phiên bản mới; giữ nguyên báo cáo và minh chứng đã gửi trước đó.</p>
            </div>}
            <label>Tiền mặt thực đếm cuối ngày *<MoneyInput required value={actual} onValueChange={(v) => { setActual(v); setConfirm(false); }} /></label>
            {cashVariance !== null && <Difference label="Chênh lệch tiền mặt" value={cashVariance} />}
            <label>Giải trình {needsExplanation ? '*' : '(không bắt buộc)'}<textarea required={needsExplanation} maxLength={2000} value={explanation} onChange={(e) => { setExplanation(e.target.value); setConfirm(false); }} /></label>
            <label>Minh chứng (không bắt buộc)<input key={date + '-' + refresh} type="file" accept=".jpg,.jpeg,.png,.webp,.heic,.heif,.pdf" onChange={(e) => { setEvidence(e.target.files[0] || null); setConfirm(false); }} /><small>Tối đa 20 MB. Ảnh hoặc PDF.</small></label>
            {confirm && <p className="finance-info">Xác nhận gửi tiền mặt thực đếm {vnd(actual)} cho kế toán đối soát?</p>}
            <ConfirmButtons confirm={confirm} busy={busy} cancel={() => setConfirm(false)} label="Gửi kế toán đối soát" />
          </fieldset>}
        </form>
      </div>}
    </> : <div className={detail ? 'finance-columns' : ''}>
      <article className="finance-card finance-table-wrap"><table>
        <thead><tr><th>Ngày / cơ sở</th><th>Thực đếm</th><th>Chênh lệch tiền mặt</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>{shown.map((r) => <tr key={r.id}>
          <td>{displayDate(r.business_date)}<small>{r.branchName}</small></td>
          <td>{vnd(r.actual_closing)}</td><td>{vnd(r.variance)}</td>
          <td>{labels[r.status]}</td><td><button disabled={busy} onClick={() => open(r.id)}>Chi tiết</button></td>
        </tr>)}</tbody>
      </table>{!shown.length && <p>{emptyMessage}</p>}</article>
      {detail && <article className="finance-card">
        <div className="finance-actions"><h2>{displayDate(detail.business_date)} · {detail.branchName}</h2><button onClick={() => setDetail(null)}>Đóng</button></div>
        <p>{labels[detail.status]} · Người gửi: {detail.submittedBy} · Phiên bản {detail.report_version || 'cũ'}</p>
        <ReportSummary source={detail.source} />
        <Line label="Tiền mặt thực đếm" value={detail.actual_closing} />
        <Difference label="Chênh lệch tiền mặt" value={detail.variance} />
        <p>Giải trình: {detail.explanation || 'Không có.'}</p>
        {detail.evidence.map((f) => <button key={f.revision} onClick={async () => {
          try { await api.download(base + '/' + detail.id + '/evidence?revision=' + f.revision, f.name, branch); }
          catch (e) { setError(message(e)); }
        }}>Minh chứng phiên bản {f.revision || 'cũ'}: {f.name}</button>)}
        <details><summary>Giao dịch nguồn lúc gửi</summary><MovementTable rows={detail.source.movements || []} /></details>
        {detail.versions?.length > 1 && <details><summary>Lịch sử các phiên bản báo cáo</summary>{detail.versions.map((v) => <div key={v.revision} className="finance-info">
          <strong>Phiên bản {v.revision} · {v.username} · {new Date(v.submitted_at).toLocaleString('vi-VN')}</strong>
          <p>Thực đếm {vnd(v.payload.actual_closing)} · Chênh lệch {vnd(v.payload.variance)}</p>
          <p>{v.payload.explanation || 'Không có giải trình.'}</p>
        </div>)}</details>}
        {detail.reviews.map((r) => <div className="finance-info" key={r.id}><strong>{r.decision === 'RECONCILED' ? 'Kết luận đối soát' : 'Yêu cầu bổ sung'} · {r.username}</strong><p>{r.note}</p></div>)}
        {!accounting && detail.status === 'NEEDS_EXPLANATION' && !readOnly && <button className="finance-primary" onClick={() => edit(detail)}>Bổ sung báo cáo</button>}
        {accounting && detail.status === 'SUBMITTED' && <form onSubmit={(e) => { e.preventDefault(); review(false); }}>
          <label>Kết luận / nội dung cần bổ sung (không bắt buộc)<textarea maxLength={2000} value={conclusion} onChange={(e) => setConclusion(e.target.value)} /></label>
          <label className="finance-check"><input type="checkbox" checked={resolved} onChange={(e) => setResolved(e.target.checked)} />Tôi đã kiểm tra số liệu, minh chứng và giải quyết chênh lệch.</label>
          <div className="finance-actions">
            <button type="button" disabled={busy} onClick={() => review(true)}>Yêu cầu bổ sung</button>
            <button className="finance-primary" disabled={busy || !resolved}>Hoàn tất đối soát</button>
          </div>
        </form>}
      </article>}
    </div>}
    </>}
  </section>;
}

function ConfirmButtons({ confirm, busy, cancel, label }) {
  return <div className="finance-actions">
    {confirm && <button type="button" onClick={cancel}>Quay lại</button>}
    <button className="finance-primary" disabled={busy}>{busy ? 'Đang gửi…' : confirm ? 'Xác nhận · ' + label : label}</button>
  </div>;
}
function Line({ label, value }) {
  return <div className="finance-line"><span>{label}</span><strong>{vnd(value)}</strong></div>;
}
function Difference({ label, value }) {
  return <p className={Number(value) === 0 ? 'finance-success' : 'finance-warning'}>{label}: <strong>{vnd(value)}</strong></p>;
}
function ReportSummary({ source }) {
  return <>
    <h3>Tiền mặt</h3>
    <Line label="Tồn đầu ngày" value={source.opening} />
    <Line label="Thu tiền mặt trong ngày" value={source.cashReceipts ?? source.cashIn} />
    {Number(source.drawerFunding) > 0 && <Line label="Tiền được kế toán cấp vào két" value={source.drawerFunding} />}
    <Line label="Tiền đã nộp khỏi két trong ngày" value={source.remitted ?? 0} />
    {Number(source.countAdjustment) !== 0 && source.countAdjustment != null && <Line label="Điều chỉnh kiểm đếm đã đối soát (không phải khoản thu/chi mới)" value={source.countAdjustment} />}
    <Line label="Tiền mặt dự kiến cuối ngày" value={source.expected} />

  </>;
}
function MovementTable({ rows }) {
  return <div className="finance-table-wrap"><table>
    <thead><tr><th>Giao dịch</th><th>Số tiền</th></tr></thead>
    <tbody>{rows.map((r) => <tr key={r.id}><td>{r.description}<small>{new Date(r.time).toLocaleString('vi-VN')}</small></td><td>{vnd(r.amount)}</td></tr>)}</tbody>
  </table>{!rows.length && <p>Không có giao dịch tiền mặt trong ngày.</p>}</div>;
}
