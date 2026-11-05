/** Author: HuyDV. UC-98..101 / BR-95..98/208, MSG01/21: theo mockup chốt quỹ. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, vnd, today, emptyMessage } from '../services/financeApi';
import '../styles/finance.css';

const statusLabel = { SUBMITTED: 'Chờ đối soát', RECONCILED: 'Đã đối soát', DRAFT: 'Chưa gửi' };
const kinds = {
  DEPOSIT: 'Thu cọc tiền mặt',
  CHECKOUT: 'Thu thanh toán tiền mặt',
  REFUND: 'Hoàn tiền mặt',
  EXPENSE: 'Chi tiền mặt',
  OPENING: 'Bổ sung số dư đầu kỳ',
  TRANSFER: 'Chuyển quỹ',
  ADJUSTMENT: 'Điều chỉnh',
  SALARY: 'Trả lương',
  ADVANCE: 'Ứng lương',
};
export default function CashClosingPage({ user, accounting = false, readOnly = false }) {
  const base = accounting ? '/accounting/cash-closing' : '/branch-management/cash-closing';
  const branch = accounting ? undefined : user.branchId;
  const [tab, setTab] = useState(accounting ? 'history' : 'create');
  const [date, setDate] = useState(today()),
    [from, setFrom] = useState(today().slice(0, 7) + '-01'),
    [to, setTo] = useState(today());
  const [status, setStatus] = useState(''),
    [search, setSearch] = useState('');
  const [preview, setPreview] = useState(null),
    [rows, setRows] = useState([]),
    [detail, setDetail] = useState(null);
  const [actual, setActual] = useState(''),
    [explanation, setExplanation] = useState(''),
    [evidence, setEvidence] = useState(null);
  const [conclusion, setConclusion] = useState(''),
    [resolved, setResolved] = useState(false);
  const [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const [sources, setSources] = useState(false),
    [confirm, setConfirm] = useState(false);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setDetail(null);
    setConfirm(false);
    setPreview(null);
    const request =
      tab === 'create'
        ? api.get(`${base}/preview`, { date }, branch)
        : api.get(base, { from, to, status, alerts: tab === 'alerts' }, branch);
    request
      .then((result) => {
        if (active) tab === 'create' ? setPreview(result) : setRows(result);
      })
      .catch((e) => active && setError(Object.values(apiErrors(e)).join(' ')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [base, branch, tab, date, from, to, status, revision]);
  const open = async (id) => {
    setBusy(true);
    setError('');
    setResolved(false);
    setConclusion('');
    try {
      setDetail(await api.get(`${base}/${id}`, {}, branch));
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
    } finally {
      setBusy(false);
    }
  };
  const submit = async (event) => {
    event.preventDefault();
    if (!confirm) {
      setConfirm(true);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const body = new FormData();
      body.append(
        'command',
        new Blob(
          [
            JSON.stringify({
              date,
              actual: Number(actual),
              explanation,
              revision: preview.revision,
            }),
          ],
          { type: 'application/json' },
        ),
      );
      if (evidence) body.append('evidence', evidence);
      await api.post(base, body, branch);
      setActual('');
      setExplanation('');
      setEvidence(null);
      setTab('history');
      setRevision((n) => n + 1);
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
      setConfirm(false);
    } finally {
      setBusy(false);
    }
  };
  const variance = actual === '' || !preview ? null : Number(actual) - Number(preview.expected);
  const shown = rows.filter((r) =>
    `${r.branchName} ${r.business_date} ${r.explanation || ''}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <section className="finance-page">
      <h1>
        {tab === 'create'
          ? 'Chốt quỹ cuối ngày'
          : tab === 'alerts'
            ? 'Cảnh báo chênh lệch quỹ'
            : 'Lịch sử chốt quỹ'}
      </h1>
      <p className="finance-muted">
        {accounting ? 'Toàn chuỗi · Kế toán đối soát và ghi nhận kết luận' : user.branchName}
      </p>
      <nav className="finance-tabs" aria-label="Chốt quỹ">
        {!accounting && (
          <button
            onClick={() => setTab('create')}
            aria-current={tab === 'create' ? 'page' : undefined}
          >
            Chốt cuối ngày
          </button>
        )}
        <button
          onClick={() => setTab('history')}
          aria-current={tab === 'history' ? 'page' : undefined}
        >
          Lịch sử chốt quỹ
        </button>
        <button
          onClick={() => setTab('alerts')}
          aria-current={tab === 'alerts' ? 'page' : undefined}
        >
          Chênh lệch quỹ
        </button>
      </nav>
      {error && (
        <p className="finance-error" role="alert">
          {error}
        </p>
      )}
      <div className="finance-toolbar">
        {tab === 'create' ? (
          <label>
            Ngày chốt
            <input
              type="date"
              value={date}
              max={today()}
              onChange={(e) => {
                setDate(e.target.value);
                setActual('');
                setExplanation('');
                setEvidence(null);
              }}
            />
          </label>
        ) : (
          <>
            <label>
              Tìm kiếm
              <input
                placeholder="Cơ sở, ngày, giải trình…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
            <label>
              Từ ngày
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label>
              Đến ngày
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </label>
            <label>
              Trạng thái
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">Tất cả</option>
                <option value="SUBMITTED">Chờ đối soát</option>
                <option value="RECONCILED">Đã đối soát</option>
              </select>
            </label>
          </>
        )}
        <button disabled={busy || loading} onClick={() => setRevision((n) => n + 1)}>
          Tải lại
        </button>
      </div>
      {loading ? (
        <p role="status">Đang tải…</p>
      ) : tab === 'create' && preview ? (
        <div className="finance-columns">
          <article className="finance-card">
            <h2>Số liệu tiền mặt từ hệ thống</h2>
            <div className="finance-line">
              <span>Số dư đầu ngày</span>
              <strong>{vnd(preview.opening)}</strong>
            </div>
            {Object.entries(kinds).map(([key, label]) => {
              const amount = preview.movements
                .filter((r) => r.kind === key)
                .reduce((s, r) => s + Number(r.amount), 0);
              return (
                amount !== 0 && (
                  <div className="finance-line" key={key}>
                    <span>{label}</span>
                    <strong>
                      {amount > 0 ? '+' : ''}
                      {vnd(amount)}
                    </strong>
                  </div>
                )
              );
            })}
            <div className="finance-info">
              <span>Tiền mặt dự kiến cuối ngày</span>
              <strong className="finance-total">{vnd(preview.expected)}</strong>
            </div>
            <p className="finance-muted">
              Chỉ cộng/trừ giao dịch tiền mặt. Không cộng lại phần cọc đã khấu trừ vào bill.
            </p>
            <button onClick={() => setSources(!sources)}>
              {sources ? 'Ẩn' : 'Xem'} giao dịch nguồn
            </button>
            {sources && <MovementTable rows={preview.movements} />}
          </article>
          <form className="finance-card" onSubmit={submit}>
            <h2>Kiểm đếm thực tế</h2>
            {preview.existing.length ? (
              <>
                <p className="finance-info">Ngày này đã được gửi chốt quỹ.</p>
                <button
                  type="button"
                  onClick={() => {
                    setTab('history');
                    setFrom(date);
                    setTo(date);
                  }}
                >
                  Về lịch sử chốt quỹ
                </button>
              </>
            ) : (
              <fieldset disabled={busy || readOnly}>
                <label>
                  Tiền mặt đếm thực tế *
                  <input
                    required
                    type="number"
                    min="0"
                    step="1"
                    max="999999999999999"
                    value={actual}
                    onChange={(e) => {
                      setActual(e.target.value);
                      setConfirm(false);
                    }}
                  />
                </label>
                {variance !== null && (
                  <div className={variance === 0 ? 'finance-success' : 'finance-warning'}>
                    <span>Chênh lệch = thực tế − dự kiến</span>
                    <strong className="finance-total">
                      {vnd(variance)} ·{' '}
                      {variance === 0 ? 'Khớp quỹ' : variance < 0 ? 'Thiếu quỹ' : 'Thừa quỹ'}
                    </strong>
                  </div>
                )}
                {variance !== null && variance !== 0 && (
                  <p className="finance-muted">
                    Tiền kiểm đếm chênh lệch {vnd(variance)} so với số tiền dự kiến. Vui lòng kiểm
                    tra lại và nhập lý do chênh lệch trước khi xác nhận chốt quỹ.
                  </p>
                )}
                <label>
                  Giải trình {variance !== null && variance !== 0 ? '*' : '(không bắt buộc)'}
                  <textarea
                    required={variance !== null && variance !== 0}
                    maxLength={2000}
                    value={explanation}
                    onChange={(e) => {
                      setExplanation(e.target.value);
                      setConfirm(false);
                    }}
                  />
                </label>
                <label>
                  Minh chứng (không bắt buộc)
                  <input
                    key={date}
                    type="file"
                    accept=".jpg,.jpeg,.png,.webp,.heic,.heif,.pdf"
                    onChange={(e) => {
                      setEvidence(e.target.files[0] || null);
                      setConfirm(false);
                    }}
                  />
                  <small>Tối đa 20 MB. JPEG, PNG, WebP, HEIC, HEIF hoặc PDF.</small>
                </label>
                {confirm && (
                  <p className="finance-info">
                    Xác nhận gửi số kiểm đếm {vnd(actual)}? Sau khi gửi, số liệu gốc được giữ nguyên
                    để đối soát.
                  </p>
                )}
                <div className="finance-actions">
                  {confirm && (
                    <button type="button" onClick={() => setConfirm(false)}>
                      Quay lại
                    </button>
                  )}
                  <button className="finance-primary">
                    {busy ? 'Đang gửi…' : confirm ? 'Xác nhận gửi chốt quỹ' : 'Gửi chốt quỹ'}
                  </button>
                </div>
              </fieldset>
            )}
          </form>
        </div>
      ) : (
        tab !== 'create' && (
          <div className={detail ? 'finance-columns' : ''}>
            <article className="finance-card finance-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Ngày / cơ sở</th>
                    <th>Dự kiến</th>
                    <th>Thực tế</th>
                    <th>Chênh lệch</th>
                    <th>Trạng thái</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {shown.map((r) => (
                    <tr key={r.id}>
                      <td>
                        {r.business_date}
                        <small>{r.branchName}</small>
                      </td>
                      <td>{vnd(r.expected_closing)}</td>
                      <td>{vnd(r.actual_closing)}</td>
                      <td>{vnd(r.variance)}</td>
                      <td>{statusLabel[r.status]}</td>
                      <td>
                        <button disabled={busy} onClick={() => open(r.id)}>
                          Chi tiết
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!shown.length && <p>{emptyMessage}</p>}
            </article>
            {detail && (
              <article className="finance-card">
                <div className="finance-actions">
                  <h2>
                    {detail.business_date} · {detail.branchName}
                  </h2>
                  <button onClick={() => setDetail(null)}>Đóng</button>
                </div>
                <p>
                  Người chốt: <strong>{detail.submittedBy}</strong>
                </p>
                <p>
                  Trạng thái: <strong>{statusLabel[detail.status]}</strong>
                </p>
                <div
                  className={Number(detail.variance) === 0 ? 'finance-success' : 'finance-warning'}
                >
                  Chênh lệch gốc: <strong>{vnd(detail.variance)}</strong>
                </div>
                <p>Giải trình: {detail.explanation || 'Không có chênh lệch cần giải trình.'}</p>
                {detail.evidence.map((f) => (
                  <button
                    key={f.name}
                    onClick={async () => {
                      try {
                        await api.download(`${base}/${detail.id}/evidence`, f.name, branch);
                      } catch (e) {
                        setError('Không thể tải minh chứng.');
                      }
                    }}
                  >
                    Tải minh chứng: {f.name}
                  </button>
                ))}
                <details>
                  <summary>Giao dịch nguồn tại lúc chốt</summary>
                  <MovementTable rows={detail.source.movements || []} />
                </details>
                {detail.reviews.map((r) => (
                  <div className="finance-info" key={r.id}>
                    {r.username} · {r.note}
                  </div>
                ))}
                {accounting && detail.status === 'SUBMITTED' && (
                  <form
                    onSubmit={async (e) => {
                      e.preventDefault();
                      setBusy(true);
                      setError('');
                      try {
                        await api.post(`${base}/${detail.id}/reconcile`, { resolved, conclusion });
                        setRevision((n) => n + 1);
                      } catch (e) {
                        setError(Object.values(apiErrors(e)).join(' '));
                      } finally {
                        setBusy(false);
                      }
                    }}
                  >
                    <label>
                      Kết luận đối soát *
                      <textarea
                        required
                        maxLength={2000}
                        value={conclusion}
                        onChange={(e) => setConclusion(e.target.value)}
                      />
                    </label>
                    <label className="finance-check">
                      <input
                        required
                        type="checkbox"
                        checked={resolved}
                        onChange={(e) => setResolved(e.target.checked)}
                      />
                      Tôi đã kiểm tra chứng từ và xử lý xong chênh lệch.
                    </label>
                    <button className="finance-primary" disabled={busy}>
                      Xác nhận đối soát
                    </button>
                  </form>
                )}
              </article>
            )}
          </div>
        )
      )}
    </section>
  );
}
function MovementTable({ rows }) {
  return (
    <div className="finance-table-wrap">
      <table>
        <thead>
          <tr>
            <th>Giao dịch</th>
            <th>Số tiền</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>
                {r.description}
                <small>{new Date(r.time).toLocaleString('vi-VN')}</small>
              </td>
              <td>{vnd(r.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && <p>Không có giao dịch tiền mặt trong ngày.</p>}
    </div>
  );
}
