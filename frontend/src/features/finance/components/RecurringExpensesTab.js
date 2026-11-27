import { useEffect, useState } from 'react';
import { PaginatedTable } from '../../../shared/components/Pagination';
import { Drawer, Modal } from '../../../shared/components/Overlays';
import BankNameInput from '../../../shared/components/BankNameInput';
import MoneyInput from '../../../shared/components/MoneyInput';
import { financeApi, today, vnd } from '../services/financeApi';
import { dateKey, dateLabel } from '../utils/financeDates';
import '../styles/accountant-finance-workspace.css';

const blankDraft = (branchId) => ({
  id: '', branchId, categoryId: '', beneficiaryName: '', bankAccount: '', bankName: '', dueDay: '1',
  monthlyAmount: '', expectedAmount: '', expenseBelongsTo: 'SAME_MONTH', startMonth: today().slice(0, 7),
  endMonth: '', reminderDays: '3',
});
const money = (value) => value == null || value === '' ? 'Chưa chốt' : vnd(value);
const monthValue = (value) => dateKey(value).slice(0, 7);
const monthLabel = (value) => monthValue(value).split('-').reverse().join('/') || '—';
const periodStatus = { PENDING: 'Chờ thanh toán', PAID: 'Đã thanh toán', SKIPPED: 'Đã bỏ qua' };
const periodTone = { PENDING: 'pending', PAID: 'received', SKIPPED: 'neutral' };

function fromTemplate(item) {
  return {
    id: item.id, branchId: item.branch_id, categoryId: item.category_id,
    beneficiaryName: item.beneficiary_name || '', bankAccount: item.bank_account || '', bankName: item.bank_name || '',
    dueDay: String(item.due_day), monthlyAmount: item.monthly_amount ?? '', expectedAmount: item.expected_amount ?? '',
    expenseBelongsTo: item.expense_belongs_to, startMonth: monthValue(item.start_month),
    endMonth: monthValue(item.end_month), reminderDays: String(item.reminder_days ?? 3),
  };
}

export default function RecurringExpensesTab({ branches, categories, busy, revision, actionError, onSave, onTerminate, onSkip, onPay }) {
  const [view, setView] = useState('periods');
  const [branchId, setBranchId] = useState('');
  const [dueMonth, setDueMonth] = useState(today().slice(0, 7));
  const [status, setStatus] = useState('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [draft, setDraft] = useState(null);
  const [skipPeriod, setSkipPeriod] = useState(null);
  const [reason, setReason] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError('');
    financeApi.listRecurringExpenses({ branchId: branchId || undefined, dueMonth, status })
      .then((result) => { if (active) setData(result); })
      .catch((failure) => { if (active) setLoadError(failure.response?.data?.detail || failure.response?.data?.message || 'Không tải được danh sách chi định kỳ.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [branchId, dueMonth, status, revision, retry]);

  function openEditor(item) {
    setFailed(false);
    setDraft(item ? fromTemplate(item) : blankDraft(branchId));
  }

  function update(field, value) {
    setDraft((before) => ({ ...before, [field]: value }));
  }

  async function save(event) {
    event.preventDefault();
    if (busy) return;
    setFailed(false);
    const { id, ...values } = draft;
    const saved = await onSave(id || null, {
      ...values,
      beneficiaryName: draft.beneficiaryName.trim(),
      bankAccount: draft.bankAccount.trim(),
      bankName: draft.bankName.trim(),
      dueDay: Number(draft.dueDay),
      monthlyAmount: draft.monthlyAmount === '' ? null : Number(draft.monthlyAmount),
      expectedAmount: draft.expectedAmount === '' ? null : Number(draft.expectedAmount),
      startMonth: `${draft.startMonth}-01`,
      endMonth: draft.endMonth ? `${draft.endMonth}-01` : null,
      reminderDays: Number(draft.reminderDays),
    });
    if (saved) {
      setDraft(null);
      setView('templates');
    } else setFailed(true);
  }

  async function skip(event) {
    event.preventDefault();
    if (busy || !reason.trim()) return;
    setFailed(false);
    if (await onSkip(skipPeriod.id, reason.trim())) setSkipPeriod(null);
    else setFailed(true);
  }

  const invalidRange = Boolean(from && to && from > to);
  const templates = (data?.recurringExpenses || []).filter((item) => {
    if (invalidRange) return false;
    if (!from && !to) return true;
    const created = dateKey(item.created_at);
    return created && (!from || created >= from) && (!to || created <= to);
  });
  const periods = data?.periods || [];
  const activeBranches = branches.filter((branch) => !branch.status || String(branch.status).toUpperCase() === 'ACTIVE');
  const activeCategories = categories.filter((item) => item.active);
  const todayText = today();
  const showingPeriods = view === 'periods';

  return <section className="finance-panel finance-workspace-view finance-recurring-workspace" aria-labelledby="recurring-title" aria-busy={loading}>
    <header className="finance-workspace-header">
      <div><h2 id="recurring-title">Chi định kỳ</h2><span>Nhắc hạn toàn chuỗi · {loading || loadError ? '—' : data?.reminderCount ?? 0}</span></div>
      <div className="finance-workspace-actions">
        <button type="button" disabled={loading || busy} onClick={() => setRetry((value) => value + 1)}>Tải lại</button>
        <button type="button" className="finance-primary" disabled={busy} onClick={() => openEditor(null)}>Thêm mẫu chi</button>
      </div>
    </header>
    <nav className="finance-tabs" aria-label="Danh sách chi định kỳ">
      <button type="button" aria-pressed={showingPeriods} className={showingPeriods ? 'active' : ''} onClick={() => setView('periods')}>Kỳ thanh toán</button>
      <button type="button" aria-pressed={!showingPeriods} className={!showingPeriods ? 'active' : ''} onClick={() => setView('templates')}>Mẫu chi định kỳ</button>
    </nav>
    <div className="finance-workspace-filters" role="group" aria-label={showingPeriods ? 'Lọc kỳ chi định kỳ' : 'Lọc mẫu chi định kỳ'}>
      <label>Cơ sở<select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
        <option value="">Tất cả cơ sở</option>{branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select></label>
      {showingPeriods ? <>
        <label>Tháng đến hạn<input type="month" value={dueMonth} onChange={(event) => event.target.value && setDueMonth(event.target.value)}/></label>
        <label>Trạng thái<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="ALL">Tất cả trạng thái</option>
          {Object.entries(periodStatus).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      </> : <>
        <label>Ngày tạo từ<input type="date" value={from} max={todayText} onChange={(event) => setFrom(event.target.value)}/></label>
        <label>Ngày tạo đến<input type="date" value={to} max={todayText} onChange={(event) => setTo(event.target.value)}/></label>
      </>}
    </div>
    {loadError && <p className="finance-error" role="alert">{loadError}</p>}
    {!showingPeriods && invalidRange && <p className="finance-error" role="alert">Ngày tạo từ phải bằng hoặc trước Ngày tạo đến.</p>}
    {loading && <p className="finance-empty" role="status">Đang tải chi định kỳ…</p>}
    {!loading && !loadError && (showingPeriods || !invalidRange) && <div className="finance-table-wrap finance-workspace-table" tabIndex={0}
      role="region" aria-label={showingPeriods ? 'Danh sách kỳ thanh toán' : 'Danh sách mẫu chi định kỳ'}>
      {showingPeriods ? <PaginatedTable pageSize={5} resetKey={JSON.stringify([branchId, dueMonth, status])} paginationLabel="Phân trang kỳ chi định kỳ">
        <thead><tr><th>Đến hạn · kỳ ghi nhận</th><th>Cơ sở · hạng mục</th><th>Người nhận</th><th className="finance-workspace-money">Số tiền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{periods.map((period) => {
          const overdue = period.status === 'PENDING' && dateKey(period.due_date) < todayText;
          return <tr key={period.id}>
            <td><strong>{dateLabel(period.due_date)}</strong><small>Kỳ {monthLabel(period.period_month)}</small></td>
            <td><strong>{period.branch_name}</strong><small>{period.category_code} · {period.category_name}</small></td>
            <td>{period.beneficiary_name}<small>{period.bank_name} · {period.bank_account}</small></td>
            <td className="finance-workspace-money">{money(period.amount ?? period.suggested_amount)}
              {period.amount == null && period.suggested_amount != null && <small>Dự kiến</small>}</td>
            <td><span className={`finance-status ${overdue ? 'rejected' : periodTone[period.status]}`}>{overdue ? 'Quá hạn' : periodStatus[period.status]}</span>
              {period.request_code && <small>{period.request_code}</small>}{period.skip_reason && <small>{period.skip_reason}</small>}</td>
            <td>{period.status === 'PENDING' ? <div className="finance-workspace-actions">
              <button type="button" className="finance-primary" disabled={busy} onClick={() => onPay(period)}>Chi kỳ này</button>
              <button type="button" disabled={busy} onClick={() => { setFailed(false); setReason(''); setSkipPeriod(period); }}>Bỏ qua</button>
            </div> : '—'}</td>
          </tr>;
        })}
          {!periods.length && <tr><td colSpan="6" className="finance-empty">Không có kỳ chi phù hợp.</td></tr>}
        </tbody>
      </PaginatedTable> : <PaginatedTable pageSize={5} resetKey={JSON.stringify([branchId, from, to])} paginationLabel="Phân trang mẫu chi định kỳ">
        <thead><tr><th>Cơ sở · hạng mục</th><th>Người nhận</th><th>Ngày tạo</th><th>Lịch chi</th><th className="finance-workspace-money">Số tiền</th><th>Ghi nhận</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{templates.map((item) => <tr key={item.id}>
          <td><strong>{item.branch_name}</strong><small>{item.category_code} · {item.category_name}</small></td>
          <td>{item.beneficiary_name}<small>{item.bank_name} · {item.bank_account}</small></td>
          <td>{dateLabel(item.created_at)}</td>
          <td>Ngày {item.due_day}<small>Tiếp theo · {dateLabel(item.next_due_date)}</small><small>Từ tháng {monthLabel(item.start_month)}{item.end_month ? ` đến ${monthLabel(item.end_month)}` : ''}</small></td>
          <td className="finance-workspace-money">{money(item.monthly_amount)}
            {item.monthly_amount == null && item.expected_amount != null && <small>Dự kiến {money(item.expected_amount)}</small>}</td>
          <td>{item.expense_belongs_to === 'PREVIOUS_MONTH' ? 'Tháng trước' : 'Cùng tháng'}<small>Nhắc trước {item.reminder_days} ngày</small></td>
          <td><span className={`finance-status ${item.status === 'ACTIVE' ? 'received' : 'neutral'}`}>{item.status === 'ACTIVE' ? 'Đang dùng' : 'Đã chấm dứt'}</span></td>
          <td>{item.status === 'ACTIVE' ? <div className="finance-workspace-actions">
            <button type="button" disabled={busy} aria-haspopup="dialog" onClick={() => openEditor(item)}>Sửa mẫu</button>
            <button type="button" disabled={busy} onClick={() => window.confirm(`Chấm dứt khoản chi định kỳ cho ${item.beneficiary_name}?`) && onTerminate(item.id)}>Chấm dứt</button>
          </div> : '—'}</td>
        </tr>)}
          {!templates.length && <tr><td colSpan="8" className="finance-empty">Không có mẫu chi định kỳ phù hợp.</td></tr>}
        </tbody>
      </PaginatedTable>}
    </div>}
    {draft && <Drawer open title={draft.id ? 'Sửa mẫu chi định kỳ' : 'Thêm mẫu chi định kỳ'} className="finance-workspace-drawer finance-workspace-drawer-wide"
      onClose={() => { if (!busy) setDraft(null); }} footer={<>
        <button type="button" disabled={busy} onClick={() => setDraft(null)}>Hủy</button>
        <button type="submit" form="recurring-template-form" className="finance-primary" disabled={busy}>
          {busy ? 'Đang lưu…' : draft.id ? 'Lưu thay đổi' : 'Tạo mẫu chi'}
        </button>
      </>}>
      <form id="recurring-template-form" className="finance-workspace-form" onSubmit={save}>
        <fieldset disabled={busy}><legend>Khoản chi và người nhận</legend>
          <label>Cơ sở<select required value={draft.branchId} onChange={(event) => update('branchId', event.target.value)}>
            <option value="">Chọn cơ sở</option>{activeBranches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
          <label>Hạng mục<select required value={draft.categoryId} onChange={(event) => update('categoryId', event.target.value)}>
            <option value="">Chọn hạng mục</option>{activeCategories.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></label>
          <label className="wide">Người nhận<input required maxLength="160" value={draft.beneficiaryName} onChange={(event) => update('beneficiaryName', event.target.value)}/></label>
          <label>Số tài khoản<input required maxLength="80" value={draft.bankAccount} onChange={(event) => update('bankAccount', event.target.value)}/></label>
          <label>Ngân hàng<BankNameInput required maxLength="120" value={draft.bankName} onChange={(event) => update('bankName', event.target.value)}/></label>
          <label>Số tiền cố định<MoneyInput min="1" value={draft.monthlyAmount} onValueChange={(value) => update('monthlyAmount', value)} placeholder="Để trống nếu thay đổi"/></label>
          <label>Số tiền dự kiến<MoneyInput min="1" value={draft.expectedAmount} onValueChange={(value) => update('expectedAmount', value)}/></label>
        </fieldset>
        <fieldset disabled={busy}><legend>Lịch thanh toán</legend>
          <label>Tháng bắt đầu<input required type="month" value={draft.startMonth} onChange={(event) => update('startMonth', event.target.value)}/></label>
          <label>Tháng kết thúc (tùy chọn)<input type="month" min={draft.startMonth} value={draft.endMonth} onChange={(event) => update('endMonth', event.target.value)}/></label>
          <label>Ngày đến hạn trong tháng<input required type="number" min="1" max="31" value={draft.dueDay} onChange={(event) => update('dueDay', event.target.value)}/></label>
          <label>Nhắc trước (ngày)<input required type="number" min="0" max="31" value={draft.reminderDays} onChange={(event) => update('reminderDays', event.target.value)}/></label>
          <label className="wide">Tháng ghi nhận<select required value={draft.expenseBelongsTo} onChange={(event) => update('expenseBelongsTo', event.target.value)}>
            <option value="SAME_MONTH">Tháng đến hạn</option><option value="PREVIOUS_MONTH">Tháng trước tháng đến hạn</option></select></label>
        </fieldset>
        {failed && <p className="finance-error" role="alert">{actionError || 'Không thể lưu mẫu chi định kỳ.'}</p>}
      </form>
    </Drawer>}
    {skipPeriod && <Modal open title="Bỏ qua kỳ chi" className="finance-workspace-drawer" onClose={() => { if (!busy) setSkipPeriod(null); }}
      footer={<><button type="button" disabled={busy} onClick={() => setSkipPeriod(null)}>Hủy</button>
        <button type="submit" form="recurring-skip-form" className="finance-primary" disabled={busy || !reason.trim()}>Xác nhận bỏ qua</button></>}>
      <form id="recurring-skip-form" className="finance-workspace-form" onSubmit={skip}>
        <p>{skipPeriod.category_name} · {skipPeriod.branch_name} · {dateLabel(skipPeriod.due_date)}</p>
        <label>Lý do bỏ qua<textarea required maxLength={1000} rows={3} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
        {failed && <p className="finance-error" role="alert">{actionError || 'Không thể bỏ qua kỳ chi.'}</p>}
      </form>
    </Modal>}
  </section>;
}
