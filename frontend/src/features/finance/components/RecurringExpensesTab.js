import { useEffect, useState } from 'react';
import { financeApi, today } from '../services/financeApi';

const blankDraft = () => ({
  id: '', branchId: '', categoryId: '', beneficiaryName: '', bankAccount: '', bankName: '', dueDay: '1',
  monthlyAmount: '', expectedAmount: '', expenseBelongsTo: 'SAME_MONTH', startMonth: today().slice(0, 7),
  endMonth: '', reminderDays: '3',
});
const money = (value) => value == null || value === '' ? 'Chưa chốt' : `${Number(value).toLocaleString('vi-VN')} ₫`;
const dateLabel = (value) => value ? new Date(`${String(value).slice(0, 10)}T00:00:00`).toLocaleDateString('vi-VN') : '—';
const monthLabel = (value) => value ? String(value).slice(0, 7) : '—';
const periodStatus = { PENDING: 'Chờ thanh toán', PAID: 'Đã thanh toán', SKIPPED: 'Đã bỏ qua' };

function fromTemplate(item) {
  return {
    id: item.id, branchId: String(item.branch_id), categoryId: String(item.category_id),
    beneficiaryName: item.beneficiary_name || '', bankAccount: item.bank_account || '', bankName: item.bank_name || '',
    dueDay: String(item.due_day), monthlyAmount: item.monthly_amount == null ? '' : String(item.monthly_amount),
    expectedAmount: item.expected_amount == null ? '' : String(item.expected_amount),
    expenseBelongsTo: item.expense_belongs_to, startMonth: monthLabel(item.start_month),
    endMonth: item.end_month ? monthLabel(item.end_month) : '', reminderDays: String(item.reminder_days ?? 3),
  };
}

export default function RecurringExpensesTab({ branches, categories, busy, revision, onSave, onTerminate, onSkip, onPay }) {
  const [branchId, setBranchId] = useState('ALL');
  const [dueMonth, setDueMonth] = useState(today().slice(0, 7));
  const [status, setStatus] = useState('ALL');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState(blankDraft);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    financeApi.listRecurringExpenses({
      branchId: branchId === 'ALL' ? undefined : branchId,
      dueMonth,
      status,
    }).then((result) => { if (active) setData(result); })
      .catch((requestError) => { if (active) setError(requestError.response?.data?.message || 'Không tải được danh sách chi định kỳ.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [branchId, dueMonth, status, revision]);

  async function save(event) {
    event.preventDefault();
    const saved = await onSave(draft.id || null, {
      branchId: draft.branchId,
      categoryId: draft.categoryId,
      beneficiaryName: draft.beneficiaryName.trim(),
      bankAccount: draft.bankAccount.trim(),
      bankName: draft.bankName.trim(),
      dueDay: Number(draft.dueDay),
      monthlyAmount: draft.monthlyAmount === '' ? null : Number(draft.monthlyAmount),
      expectedAmount: draft.expectedAmount === '' ? null : Number(draft.expectedAmount),
      expenseBelongsTo: draft.expenseBelongsTo,
      startMonth: `${draft.startMonth}-01`,
      endMonth: draft.endMonth ? `${draft.endMonth}-01` : null,
      reminderDays: Number(draft.reminderDays),
    });
    if (saved) setDraft(blankDraft());
  }

  function skip(period) {
    const reason = window.prompt(`Lý do bỏ qua kỳ ${monthLabel(period.period_month)}:`);
    if (reason?.trim()) onSkip(period.id, reason.trim());
  }

  const templates = data?.recurringExpenses || [];
  const periods = data?.periods || [];
  const notifications = data?.notifications || [];
  const activeBranches = branches.filter((branch) => !branch.status || String(branch.status).toUpperCase() === 'ACTIVE');
  const activeCategories = categories.filter((item) => item.active);
  const todayText = today();

  return <section className="finance-panel finance-recurring-panel">
    <div className="finance-section-heading">
      <div><h2>Chi định kỳ</h2><p>Quản lý khoản chi theo tháng, xử lý từng kỳ và xem nhắc hạn trong ứng dụng.</p></div>
      <div className="finance-mini-total pending"><span>Kỳ cần xử lý</span><strong>{data?.reminderCount ?? '—'}</strong></div>
    </div>

    {notifications.length > 0 && <section className="finance-recurring-notices" aria-label="Nhắc hạn chi định kỳ">
      <h3>Nhắc hạn gần đây</h3>
      <ul>{notifications.map((item) => <li key={item.id}><span className={`finance-recurring-notice-type ${String(item.reminder_type).toLowerCase()}`}>{item.reminder_type === 'BEFORE_DUE' ? 'Sắp đến hạn' : item.reminder_type === 'OVERDUE' ? 'Quá hạn' : 'Đến hạn'}</span><span>{item.message}</span><small>{dateLabel(item.reminder_date)}</small></li>)}</ul>
    </section>}

    <form className="finance-form finance-recurring-editor" onSubmit={save}>
      <div className="finance-section-heading wide"><div><h3>{draft.id ? 'Sửa khoản chi định kỳ' : 'Tạo khoản chi định kỳ'}</h3><p>Nếu chưa biết số tiền cố định, để trống và nhập số thực tế khi thanh toán.</p></div></div>
      <label>Cơ sở *<select required value={draft.branchId} onChange={(event) => setDraft({ ...draft, branchId: event.target.value })}><option value="">Chọn cơ sở</option>{activeBranches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
      <label>Hạng mục đang dùng *<select required value={draft.categoryId} onChange={(event) => setDraft({ ...draft, categoryId: event.target.value })}><option value="">Chọn hạng mục</option>{activeCategories.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></label>
      <label>Người nhận *<input required maxLength="160" value={draft.beneficiaryName} onChange={(event) => setDraft({ ...draft, beneficiaryName: event.target.value })} /></label>
      <label>Số tài khoản *<input required maxLength="80" value={draft.bankAccount} onChange={(event) => setDraft({ ...draft, bankAccount: event.target.value })} /></label>
      <label>Ngân hàng *<input required maxLength="120" value={draft.bankName} onChange={(event) => setDraft({ ...draft, bankName: event.target.value })} /></label>
      <label>Ngày đến hạn trong tháng *<input required type="number" min="1" max="31" step="1" value={draft.dueDay} onChange={(event) => setDraft({ ...draft, dueDay: event.target.value })} /><small>Ngày 29–31 tự chuyển về ngày cuối tháng nếu tháng ngắn hơn.</small></label>
      <label>Số tiền cố định (VND)<input type="number" min="1" step="1" value={draft.monthlyAmount} onChange={(event) => setDraft({ ...draft, monthlyAmount: event.target.value })} placeholder="Để trống nếu thay đổi" /></label>
      <label>Số tiền dự kiến (tùy chọn)<input type="number" min="1" step="1" value={draft.expectedAmount} onChange={(event) => setDraft({ ...draft, expectedAmount: event.target.value })} placeholder="Gợi ý khi thanh toán" /></label>
      <label>Ghi nhận chi phí vào *<select required value={draft.expenseBelongsTo} onChange={(event) => setDraft({ ...draft, expenseBelongsTo: event.target.value })}><option value="SAME_MONTH">Tháng đến hạn</option><option value="PREVIOUS_MONTH">Tháng trước tháng đến hạn</option></select></label>
      <label>Tháng bắt đầu *<input required type="month" value={draft.startMonth} onChange={(event) => setDraft({ ...draft, startMonth: event.target.value })} /></label>
      <label>Tháng kết thúc<input type="month" value={draft.endMonth} onChange={(event) => setDraft({ ...draft, endMonth: event.target.value })} /></label>
      <label>Nhắc trước (ngày)<input required type="number" min="0" max="31" step="1" value={draft.reminderDays} onChange={(event) => setDraft({ ...draft, reminderDays: event.target.value })} /></label>
      <div className="finance-category-form-actions wide"><button disabled={busy}>{draft.id ? 'Lưu thay đổi' : 'Tạo khoản định kỳ'}</button>{draft.id && <button type="button" disabled={busy} onClick={() => setDraft(blankDraft())}>Hủy sửa</button>}</div>
    </form>

    <div className="finance-section-heading finance-list-heading"><div><h3>Mẫu chi định kỳ</h3><p>{templates.length} khoản</p></div></div>
    <div className="finance-table-wrap"><table className="finance-recurring-table">
      <thead><tr><th>Cơ sở · hạng mục</th><th>Người nhận</th><th>Đến hạn</th><th>Số tiền</th><th>Ghi nhận</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{templates.map((item) => <tr key={item.id}>
        <td><strong>{item.branch_name}</strong><small>{item.category_code} · {item.category_name}</small></td>
        <td>{item.beneficiary_name}<small>{item.bank_name} · {item.bank_account}</small></td>
        <td>Ngày {item.due_day}<small>Tiếp theo: {dateLabel(item.next_due_date)}</small></td>
        <td>{money(item.monthly_amount)}<small>{item.monthly_amount == null ? `Dự kiến ${money(item.expected_amount)}` : ''}</small></td>
        <td>{item.expense_belongs_to === 'PREVIOUS_MONTH' ? 'Tháng trước' : 'Cùng tháng'}<small>Nhắc trước {item.reminder_days} ngày</small></td>
        <td><span className={`finance-status ${item.status === 'ACTIVE' ? 'received' : 'rejected'}`}>{item.status === 'ACTIVE' ? 'Đang dùng' : 'Đã chấm dứt'}</span></td>
        <td><div className="finance-category-actions">{item.status === 'ACTIVE' && <><button type="button" disabled={busy} onClick={() => setDraft(fromTemplate(item))}>Sửa</button><button type="button" disabled={busy} onClick={() => window.confirm(`Chấm dứt khoản chi định kỳ cho ${item.beneficiary_name}?`) && onTerminate(item.id)}>Chấm dứt</button></>}</div></td>
      </tr>)}{!templates.length && <tr><td colSpan="7" className="finance-empty">Chưa có khoản chi định kỳ.</td></tr>}</tbody>
    </table></div>

    <div className="finance-section-heading finance-list-heading"><div><h3>Các kỳ đến hạn</h3><p>{periods.length} kỳ trong tháng đã chọn</p></div></div>
    <div className="finance-recurring-filters" aria-label="Lọc kỳ chi định kỳ">
      <label>Cơ sở<select value={branchId} onChange={(event) => setBranchId(event.target.value)}><option value="ALL">Tất cả cơ sở</option>{branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
      <label>Tháng đến hạn<input type="month" value={dueMonth} onChange={(event) => setDueMonth(event.target.value)} /></label>
      <label>Trạng thái<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="ALL">Tất cả</option><option value="PENDING">Chờ thanh toán</option><option value="PAID">Đã thanh toán</option><option value="SKIPPED">Đã bỏ qua</option></select></label>
    </div>
    {error && <p className="finance-error" role="alert">{error}</p>}
    {loading ? <p className="finance-empty">Đang tải kỳ chi…</p> : <div className="finance-table-wrap"><table className="finance-recurring-table">
      <thead><tr><th>Ngày · kỳ ghi nhận</th><th>Cơ sở · hạng mục</th><th>Người nhận</th><th>Số tiền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{periods.map((period) => {
        const overdue = period.status === 'PENDING' && String(period.due_date).slice(0, 10) < todayText;
        return <tr key={period.id} className={overdue ? 'finance-recurring-overdue' : ''}>
          <td><strong>{dateLabel(period.due_date)}</strong><small>Kỳ {monthLabel(period.period_month)}{overdue ? ' · Quá hạn' : ''}</small></td>
          <td>{period.branch_name}<small>{period.category_code} · {period.category_name}</small></td>
          <td>{period.beneficiary_name}<small>{period.bank_name} · {period.bank_account}</small></td>
          <td>{money(period.amount ?? period.suggested_amount)}<small>{period.amount == null ? 'Số thực tế được nhập khi thanh toán' : ''}</small></td>
          <td><span className={`finance-status ${period.status === 'PAID' ? 'received' : period.status === 'SKIPPED' ? 'rejected' : 'pending'}`}>{periodStatus[period.status] || period.status}</span>{period.request_code && <small>Yêu cầu: {period.request_code}</small>}{period.skip_reason && <small>Lý do: {period.skip_reason}</small>}</td>
          <td><div className="finance-category-actions">{period.status === 'PENDING' && <><button type="button" disabled={busy} onClick={() => onPay(period)}>Chi kỳ này</button><button type="button" disabled={busy} onClick={() => skip(period)}>Bỏ qua</button></>}</div></td>
        </tr>;
      })}{!periods.length && <tr><td colSpan="6" className="finance-empty">Không có kỳ chi trong bộ lọc này.</td></tr>}</tbody>
    </table></div>}
  </section>;
}
