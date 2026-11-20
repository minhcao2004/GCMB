/** Author: HuyDV. UC-91..97 / BR-64..81/213/225/227: bảng lương kế toán. */
import { useEffect, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { financeApi as api, vnd, today, emptyMessage } from '../../finance/services/financeApi';
import MoneyInput from '../../finance/components/MoneyInput';
import '../../finance/styles/finance.css';

const base = '/accounting/payroll';
const componentNames = {
  BASE: 'Tiền công theo ca',
  OVERTIME: 'Ngoài giờ ngày thường',
  REST: 'Làm ngày nghỉ',
  HOLIDAY: 'Làm ngày lễ',
  NIGHT: 'Phụ cấp đêm',
  ADDITION: 'Thưởng đã duyệt',
  DEDUCTION: 'Khấu trừ đã duyệt',
};
const paymentStatusNames = { PAID: 'Đã thanh toán', FAILED: 'Không thành công', PENDING: 'Chờ xác nhận' };
const requestId = () => window.crypto.randomUUID();
export default function PayrollPage() {
  const [month, setMonth] = useState(today().slice(0, 7));
  const [data, setData] = useState(null),
    [options, setOptions] = useState(null),
    [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState([]),
    [search, setSearch] = useState(''),
    [filter, setFilter] = useState('');
  const [branchFilter, setBranchFilter] = useState('');
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState(''),
    [detail, setDetail] = useState(null),
    [reviewed, setReviewed] = useState(false);
  const [payment, setPayment] = useState({
    employeeId: '',
    accountId: '',
    amount: '',
    result: 'PENDING',
    reference: '',
    requestId: requestId(),
  });
  const [available, setAvailable] = useState(null);
  const [results, setResults] = useState({});
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setData(null);
    setDetail(null);
    setSelected([]);
    setReviewed(false);
    setDialog('');
    Promise.all([api.get(`${base}/${month}`), api.get(`${base}/options`)])
      .then(([result, choices]) => {
        if (active) {
          setData(result);
          setOptions(choices);
        }
      })
      .catch((e) => active && setError(Object.values(apiErrors(e)).join(' ')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [month, revision]);
  useEffect(() => {
    let active = true;
    setAvailable(null);
    if (dialog === 'advance' && payment.employeeId)
      api
        .get(`${base}/${month}/available/${payment.employeeId}`)
        .then((r) => active && setAvailable(r))
        .catch((e) => active && setError(Object.values(apiErrors(e)).join(' ')));
    return () => {
      active = false;
    };
  }, [dialog, payment.employeeId, month]);
  const mutate = async (path, body, message) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await api.post(path, body);
      const outcomes = result?.outcomes
        ?.map(
          (r) =>
            `${options.employees.find((e) => e.id === r.employeeId)?.name || ''}: ${r.message || (r.status === 'PAID' ? 'Đã thanh toán' : 'Chưa thành công, chưa ghi giảm quỹ')}`,
        )
        .join('; ');
      setNotice(outcomes || result?.message || message || 'Đã lưu.');
      setRevision((n) => n + 1);
      return true;
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
      return false;
    } finally {
      setBusy(false);
    }
  };
  const toggle = (id) => {
    setReviewed(false);
    setSelected((old) => (old.includes(id) ? old.filter((item) => item !== id) : [...old, id]));
  };
  const moneyInput = (key, label, state, setter) => (
    <label>
      {label}
      <MoneyInput
        required
        value={state[key]}
        onValueChange={(value) => setter({ ...state, [key]: value })}
      />
    </label>
  );
  const employeeInput = (state, setter) => (
    <label>
      Nhân viên *
      <select
        required
        value={state.employeeId}
        onChange={(e) => setter({ ...state, employeeId: e.target.value })}
      >
        <option value="">Chọn nhân viên</option>
        {options?.employees.map((e) => (
          <option key={e.id} value={e.id}>
            {e.code} · {e.name}
          </option>
        ))}
      </select>
    </label>
  );
  const readyRows = (data?.rows || []).filter((row) => data.status === 'CONFIRMED' || row.submitted_branch_id);
  const branchRows = readyRows.filter((row) => !branchFilter ||
    row.submitted_branch_id === branchFilter ||
    row.components?.some((component) => component.branch_id === branchFilter));
  const rows = branchRows.filter(
    (r) =>
      `${r.code} ${r.name}`.toLowerCase().includes(search.toLowerCase()) &&
      (!filter || Boolean(r.paid) === (filter === 'paid')),
  );
  const totalDue = rows.filter((row) => !row.paid).reduce((sum, row) => sum + Number(row.net_amount), 0);
  const pendingBranches = (data?.pendingBranches || []).filter((row) => !branchFilter || row.branch_id === branchFilter);
  const advances = (data?.advances || []).filter((row) => !branchFilter || row.branch_id === branchFilter);
  return (
    <section className="finance-page">
      <h1>Lương nhân viên</h1>
      {error && (
        <p role="alert" className="finance-error">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="finance-info">
          {notice}
        </p>
      )}
      <div className="finance-toolbar">
        <label>
          Tháng lương
          <input
            type="month"
            required
            value={month}
            onChange={(e) => e.target.value && setMonth(e.target.value)}
          />
        </label>
        <label>
          Cơ sở
          <select value={branchFilter} disabled={busy} onChange={(event) => {
            setBranchFilter(event.target.value);
            setSelected([]);
            setDialog('');
            setDetail(null);
            setReviewed(false);
          }}>
            <option value="">Tất cả cơ sở</option>
            {(options?.branches || []).map((branch) => (
              <option key={branch.id} value={branch.id}>{branch.name}</option>
            ))}
          </select>
        </label>
        <label>
          Tìm nhân viên
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Mã hoặc họ tên"
          />
        </label>
        <label>
          Thanh toán
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="">Tất cả</option>
            <option value="paid">Đã trả</option>
            <option value="unpaid">Chưa trả</option>
          </select>
        </label>
        <button disabled={loading || busy} onClick={() => setRevision((n) => n + 1)}>
          Tải lại
        </button>
      </div>
      {loading ? (
        <p role="status">Đang tải…</p>
      ) : (
        data &&
        options &&
        (
          <>
            <div className="finance-info">
              <span>
                {rows.length}{' '}
                nhân viên
              </span>
              <strong className="finance-total">Còn phải trả: {vnd(totalDue)}</strong>
            </div>
            <div className="finance-actions">
              <button
                disabled={busy}
                onClick={() => {
                  setDialog('advance');
                  setPayment({
                    employeeId: '',
                    accountId: '',
                    amount: '',
                    result: 'PENDING',
                    reference: '',
                    requestId: requestId(),
                  });
                }}
              >
                Ghi nhận ứng lương
              </button>
              <button
                disabled={busy || !branchRows.length}
                onClick={() => setDialog('export')}
              >
                Xuất Excel
              </button>
              <button
                className="finance-primary"
                disabled={busy || !selected.length || selected.some((id) =>
                  !data.rows.some((r) => r.employee_id === id &&
                    (data.status === 'CONFIRMED' || r.submitted_branch_id)))}
                onClick={() => {
                  setDialog('payments');
                  setPayment((p) => ({ ...p, accountId: '' }));
                  setReviewed(false);
                  setResults(
                    Object.fromEntries(
                      selected.map((id) => [
                        id,
                        { result: 'PENDING', reference: '', requestId: requestId() },
                      ]),
                    ),
                  );
                }}
              >
                Ghi nhận trả lương ({selected.length})
              </button>
            </div>
            {dialog && (
              <article className="finance-card">
                <div className="finance-actions">
                  <h2>
                    {
                      {
                        advance: 'Ghi nhận ứng đã trả',
                        payments: 'Kết quả trả lương thực tế',
                        export: 'Xuất bảng lương đã chốt',
                      }[dialog]
                    }
                  </h2>
                  <button disabled={busy} onClick={() => setDialog('')}>
                    Đóng
                  </button>
                </div>
                {dialog === 'export' && (
                  <>
                    <p>
                      Xuất dữ liệu bảng lương tháng {month}
                      {branchFilter ? ` · ${options.branches.find((branch) => branch.id === branchFilter)?.name || ''}` : ' · Tất cả cơ sở'}.
                      Việc xuất không đánh dấu đã trả lương.
                    </p>
                    <button
                      className="finance-primary"
                      disabled={busy}
                      onClick={async () => {
                        setBusy(true);
                        try {
                          const query = branchFilter ? `?branchId=${encodeURIComponent(branchFilter)}` : '';
                          await api.download(`${base}/${month}/export${query}`, `Bang-luong-${month}.xlsx`);
                          setDialog('');
                        } catch {
                          setError(
                            'Không thể xuất bảng lương. Vui lòng kiểm tra lại các dòng đã chốt.',
                          );
                        } finally {
                          setBusy(false);
                        }
                      }}
                    >
                      Xác nhận xuất Excel
                    </button>
                  </>
                )}
                {dialog === 'advance' && (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      mutate(
                        `${base}/${month}/advance`,
                        { ...payment, amount: Number(payment.amount) },
                        'Đã ghi nhận ứng lương.',
                      );
                    }}
                  >
                    <div className="finance-grid">
                      {employeeInput(payment, setPayment)}
                      {moneyInput('amount', 'Số tiền ứng *', payment, setPayment)}
                      <FundInput
                        options={options}
                        value={payment.accountId}
                        onChange={(accountId) => setPayment({ ...payment, accountId })}
                      />
                    </div>
                    {available && (
                      <p className="finance-info">
                        Lương tích lũy: {vnd(available.earned)} · Đã ứng:{' '}
                        {vnd(available.paidAdvances)} · Còn có thể ứng:{' '}
                        <strong>{vnd(available.available)}</strong>
                      </p>
                    )}
                    <ResultInputs value={payment} onChange={setPayment} />
                    <p className="finance-muted">
                      Ghi nhận kết quả thanh toán thực tế bên ngoài hệ thống. Chỉ thành công mới trừ
                      quỹ và khấu trừ vào lương.
                    </p>
                    <button disabled={busy || !available} className="finance-primary">
                      Xác nhận kết quả ứng
                    </button>
                  </form>
                )}
                {dialog === 'payments' && (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      mutate(
                        `${base}/${month}/payments`,
                        {
                          version: data.version,
                          payments: selected.map((employeeId) => ({
                            ...results[employeeId],
                            employeeId,
                            accountId: payment.accountId,
                          })),
                        },
                        'Đã ghi nhận kết quả thanh toán.',
                      );
                    }}
                  >
                    <FundInput
                      options={options}
                      value={payment.accountId}
                      onChange={(accountId) => setPayment({ ...payment, accountId })}
                    />
                    {selected.map((id) => {
                      const r = data.rows.find((r) => r.employee_id === id);
                      return (
                        r && (
                          <div className="finance-card" key={id}>
                            <h3>
                              {r.code} · {r.name} · {vnd(r.net_amount)}
                            </h3>
                            <SalaryBankDetails entry={r} />
                            <ResultInputs
                              value={results[id]}
                              onChange={(v) => setResults({ ...results, [id]: v })}
                            />
                          </div>
                        )
                      );
                    })}
                    <label className="finance-check">
                      <input
                        required
                        type="checkbox"
                        checked={reviewed}
                        onChange={(e) => setReviewed(e.target.checked)}
                      />
                      Tôi xác nhận các kết quả trên đúng với thanh toán thực tế.
                    </label>
                    <button disabled={busy} className="finance-primary">
                      Xác nhận ghi nhận kết quả
                    </button>
                  </form>
                )}
              </article>
            )}
            <article className="finance-card finance-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Chọn</th>
                    <th>Nhân viên</th>
                    <th>Tài khoản nhận lương</th>
                    <th>Tổng lương</th>
                    <th>Khấu trừ (gồm ứng)</th>
                    <th>Ứng đã trả</th>
                    <th>Thực nhận</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.id}>
                      <td>
                        <input
                          aria-label={`Chọn ${r.name}`}
                          type="checkbox"
                          disabled={r.paid}
                          checked={selected.includes(r.employee_id)}
                          onChange={() => toggle(r.employee_id)}
                        />
                      </td>
                      <td>
                        {r.code}
                        <small>{r.name}</small>
                        {r.submitted_branch_name && <small>{r.submitted_branch_name}</small>}
                      </td>
                      <td><SalaryBankDetails entry={r} /></td>
                      <td>{vnd(r.gross_amount)}</td>
                      <td>{vnd(r.deduction_amount)}</td>
                      <td>{vnd(r.advances)}</td>
                      <td>{vnd(r.net_amount)}</td>
                      <td>{r.paid ? 'Đã trả' : r.submitted_branch_id ? 'Cơ sở đã gửi' : 'Chưa trả'}</td>
                      <td>
                        <button onClick={() => setDetail(r)}>Chi tiết</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!rows.length && (
                <p>{readyRows.length ? emptyMessage : 'Chưa có bảng lương đã chốt trong tháng này.'}</p>
              )}
            </article>
            {detail && (
              <article className="finance-card">
                <div className="finance-actions">
                  <h2>
                    {detail.code} · {detail.name}
                  </h2>
                  <button onClick={() => setDetail(null)}>Đóng chi tiết</button>
                </div>
                {detail.components.map((c, i) => (
                  <div className="finance-line" key={i}>
                    <span>
                      {componentNames[c.reason] || componentNames[c.kind]}
                      {c.branch_name && <small>{c.branch_name}</small>}
                      {['ADDITION', 'DEDUCTION'].includes(c.kind) && <small>{c.reason}</small>}
                    </span>
                    <strong>{vnd(c.amount)}</strong>
                  </div>
                ))}
                <div className="finance-line">
                  <span>Ứng đã trả</span>
                  <strong>−{vnd(detail.advances)}</strong>
                </div>
                <div className="finance-info">Thực nhận: {vnd(detail.net_amount)}</div>
                <SalaryBankDetails entry={detail} />
                {detail.payments.map((p, i) => (
                  <p key={i}>
                    {p.status === 'PAID' ? 'Đã trả' : 'Chưa thành công'} · {vnd(p.amount)} ·{' '}
                    {p.external_reference}
                  </p>
                ))}
              </article>
            )}
            {!!pendingBranches.length && <details className="finance-card" open>
              <summary>Chờ các cơ sở chốt đủ ({pendingBranches.length} phần lương đã gửi)</summary>
              {pendingBranches.map((row, index) => <p key={index}>{row.code} · {row.name} — {row.branch_name} đã gửi</p>)}
            </details>}
            <details className="finance-card">
              <summary>Lịch sử ứng lương ({advances.length})</summary>
              <div className="finance-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Nhân viên</th>
                      <th>Số tiền</th>
                      <th>Trạng thái</th>
                      <th>Tham chiếu</th>
                    </tr>
                  </thead>
                  <tbody>
                    {advances.map((a) => (
                      <tr key={a.id}>
                        <td>
                          {a.code} · {a.name}
                        </td>
                        <td>{vnd(a.amount)}</td>
                        <td>{paymentStatusNames[a.status] || 'Chưa xác định'}</td>
                        <td>{a.external_reference}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </>
        )
      )}
    </section>
  );
}

// HuyDV | UC-97: kế toán đối chiếu tài khoản do QLCS lưu trước khi chuyển tiền bên ngoài.
function SalaryBankDetails({ entry }) {
  const complete = entry.bank_name_snapshot && entry.bank_account_snapshot && entry.bank_holder_snapshot;
  return (
    <div className="payroll-bank-details">
      <span>Ngân hàng: <strong>{entry.bank_name_snapshot || 'Chưa có'}</strong></span>
      <span>Số tài khoản: <strong>{entry.bank_account_snapshot || 'Chưa có'}</strong></span>
      <span>Chủ tài khoản: <strong>{entry.bank_holder_snapshot || 'Chưa có'}</strong></span>
      {!complete && !entry.paid && <small>
        Chưa đủ thông tin nhận lương. Liên hệ quản lý cơ sở để bổ sung, rồi bấm Tải lại trước khi chuyển khoản.
      </small>}
    </div>
  );
}

function FundInput({ options, value, onChange }) {
  return (
    <label>
      Quỹ kế toán chi tiền *
      <select required value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Chọn quỹ</option>
        {options.funds.map((f) => (
          <option key={f.id} value={f.id}>
            {f.name} · Còn {vnd(f.balance)}
          </option>
        ))}
      </select>
      {!options.funds.length && (
        <small>
          Chưa có quỹ kế toán được cấu hình. Cần thiết lập nguồn quỹ trước khi ghi nhận chi tiền.
        </small>
      )}
    </label>
  );
}
function ResultInputs({ value, onChange }) {
  return (
    <div className="finance-grid">
      <label>
        Kết quả thực tế
        <select
          value={value.result}
          onChange={(e) => onChange({ ...value, result: e.target.value })}
        >
          <option value="PENDING">Chưa xác nhận</option>
          <option value="PAID">Đã trả thành công</option>
          <option value="FAILED">Không thành công</option>
        </select>
      </label>
      <label>
        Mã chứng từ / tham chiếu {value.result === 'PAID' ? '*' : '(không bắt buộc)'}
        <input
          required={value.result === 'PAID'}
          maxLength={200}
          value={value.reference}
          onChange={(e) => onChange({ ...value, reference: e.target.value })}
        />
      </label>
    </div>
  );
}
