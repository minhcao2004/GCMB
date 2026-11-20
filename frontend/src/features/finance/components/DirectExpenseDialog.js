import { useMemo, useState } from 'react';
import { today } from '../services/financeApi';
import ExpenseAllocationFields from './ExpenseAllocationFields';
import { calculateExpenseAllocation } from '../utils/expenseAllocation';

const newUuid = () => {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16);
    return (char === 'x' ? value : ((value & 0x3) | 0x8)).toString(16);
  });
};
const blankDraft = () => ({
  idempotencyKey: newUuid(), branchId: '', categoryId: '', amount: '', expenseOn: today(), beneficiaryName: '',
  bankAccount: '', bankName: '', description: '', reference: '', allocationMonths: '', allocationStart: today().slice(0, 7),
  reason: '', proof: null, invoice: null,
});

export default function DirectExpenseDialog({ branches, categories, busy, onClose, onSubmit, recurringPeriod = null }) {
  const [draft, setDraft] = useState(() => {
    if (!recurringPeriod) return blankDraft();
    return {
      ...blankDraft(), branchId: String(recurringPeriod.branch_id), categoryId: String(recurringPeriod.category_id),
      amount: String(recurringPeriod.suggested_amount ?? recurringPeriod.amount ?? ''),
      beneficiaryName: recurringPeriod.beneficiary_name || '', bankAccount: recurringPeriod.bank_account || '',
      bankName: recurringPeriod.bank_name || '',
    };
  });
  const [allocationStartEdited, setAllocationStartEdited] = useState(false);
  const selectedCategory = categories.find((item) => String(item.id) === String(draft.categoryId));
  const allocated = !recurringPeriod && selectedCategory?.recognition_mode === 'ALLOCATED';
  const fixedAmountChanged = recurringPeriod?.default_amount != null
    && Number(draft.amount) !== Number(recurringPeriod.default_amount);
  const preview = useMemo(() => allocated
    ? calculateExpenseAllocation(draft.amount, draft.allocationMonths || selectedCategory.default_allocation_months, draft.allocationStart)
    : { error: '' }, [allocated, draft.amount, draft.allocationMonths, draft.allocationStart, selectedCategory]);

  async function submit(event) {
    event.preventDefault();
    if (!draft.proof || (selectedCategory?.invoice_required && !draft.invoice)) return;
    if (recurringPeriod) {
      if (fixedAmountChanged && !draft.reason.trim()) return;
      if (await onSubmit({ amount: draft.amount, reason: draft.reason }, draft.proof, draft.invoice)) onClose();
      return;
    }
    if (allocated && preview.error) return;
    const command = {
      idempotencyKey: draft.idempotencyKey,
      branchId: draft.branchId,
      categoryId: draft.categoryId,
      amount: draft.amount,
      expenseOn: draft.expenseOn,
      beneficiaryName: draft.beneficiaryName,
      bankAccount: draft.bankAccount,
      bankName: draft.bankName,
      description: draft.description,
      reference: draft.reference,
      allocationMonths: allocated ? Number(draft.allocationMonths || selectedCategory.default_allocation_months) : null,
      allocationStart: allocated ? `${draft.allocationStart}-01` : null,
    };
    if (await onSubmit(command, draft.proof, draft.invoice)) onClose();
  }

  function chooseCategory(event) {
    const categoryId = event.target.value;
    const category = categories.find((item) => String(item.id) === String(categoryId));
    setAllocationStartEdited(false);
    setDraft((current) => ({
      ...current,
      categoryId,
      allocationMonths: category?.recognition_mode === 'ALLOCATED' ? String(category.default_allocation_months || '') : '',
      allocationStart: current.expenseOn.slice(0, 7),
    }));
  }

  const periodLabel = recurringPeriod?.period_month?.slice(0, 7);
  return <div className="finance-confirm-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}>
    <section className="finance-confirm-dialog finance-direct-expense-dialog" role="dialog" aria-modal="true" aria-labelledby="finance-direct-title">
      <header className="finance-dialog-heading"><div><span className="finance-dialog-eyebrow">KẾ TOÁN · CHI TRỰC TIẾP</span><h2 id="finance-direct-title">{recurringPeriod ? `Chi định kỳ · kỳ ${periodLabel}` : 'Ghi nhận chi trực tiếp'}</h2></div>
        <button type="button" aria-label="Đóng cửa sổ" disabled={busy} onClick={onClose}>×</button></header>
      {recurringPeriod ? <p>Kiểm tra số tiền thực tế và tải chứng từ trước khi thanh toán kỳ này.</p>
        : <p>Một khoản chi được gắn với đúng một cơ sở và tự ghi nhận các bước gửi, duyệt, thanh toán. Khoản lặp lại hằng tháng được khai báo ở tab Chi định kỳ.</p>}
      <form className="finance-form finance-direct-expense-form" onSubmit={submit}>
        <label>Cơ sở *<select required disabled={Boolean(recurringPeriod)} value={draft.branchId} onChange={(event) => setDraft({ ...draft, branchId: event.target.value })}><option value="">Chọn cơ sở</option>{branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label>
        <label>Hạng mục *<select required disabled={Boolean(recurringPeriod)} value={draft.categoryId} onChange={chooseCategory}><option value="">Chọn hạng mục đang dùng</option>{categories.filter((item) => item.active || String(item.id) === String(recurringPeriod?.category_id)).map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></label>
        <label>Số tiền (VND) *<input required min="1" step="1" type="number" inputMode="numeric" value={draft.amount} onChange={(event) => setDraft({ ...draft, amount: event.target.value })} />
          {recurringPeriod && recurringPeriod.default_amount != null && <small>Số tiền cố định: {Number(recurringPeriod.default_amount).toLocaleString('vi-VN')} ₫</small>}
          {recurringPeriod && recurringPeriod.default_amount == null && <small>{recurringPeriod.expected_amount ? `Số tiền dự kiến: ${Number(recurringPeriod.expected_amount).toLocaleString('vi-VN')} ₫ · có thể nhập số thực tế.` : 'Nhập số tiền thực tế của kỳ này.'}</small>}
        </label>
        {!recurringPeriod && <label>Ngày chi *<input required type="date" value={draft.expenseOn} onChange={(event) => setDraft((current) => ({ ...current, expenseOn: event.target.value, ...(!allocationStartEdited ? { allocationStart: event.target.value.slice(0, 7) } : {}) }))} /></label>}
        {!recurringPeriod && <label>Người nhận *<input required maxLength="160" value={draft.beneficiaryName} onChange={(event) => setDraft({ ...draft, beneficiaryName: event.target.value })} /></label>}
        {!recurringPeriod && <label>Số tài khoản *<input required maxLength="80" value={draft.bankAccount} onChange={(event) => setDraft({ ...draft, bankAccount: event.target.value })} /></label>}
        {!recurringPeriod && <label>Ngân hàng *<input required maxLength="120" value={draft.bankName} onChange={(event) => setDraft({ ...draft, bankName: event.target.value })} /></label>}
        {!recurringPeriod && <label>Mã giao dịch (tùy chọn)<input maxLength="200" value={draft.reference} onChange={(event) => setDraft({ ...draft, reference: event.target.value })} /></label>}
        {!recurringPeriod && <label className="wide">Nội dung chi *<textarea required maxLength="1000" rows="3" value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>}
        {recurringPeriod && <label className="wide">Lý do chênh lệch{fixedAmountChanged ? ' *' : ' (tùy chọn)'}<textarea required={fixedAmountChanged} maxLength="1000" rows="2" value={draft.reason} onChange={(event) => setDraft({ ...draft, reason: event.target.value })} placeholder="Bắt buộc khi số tiền khác mức cố định" /></label>}
        <label>Chứng từ chuyển khoản *<input required type="file" accept="image/jpeg,.jpg,.jpeg,image/png,.png,image/webp,.webp,image/heic,.heic,image/heif,.heif,application/pdf,.pdf" onChange={(event) => setDraft({ ...draft, proof: event.target.files?.[0] || null })} /><small>{draft.proof?.name || 'Ảnh hoặc PDF · tối đa 20 MB'}</small></label>
        <label>Hóa đơn{selectedCategory?.invoice_required ? ' *' : ''}<input required={Boolean(selectedCategory?.invoice_required)} type="file" accept="image/jpeg,.jpg,.jpeg,image/png,.png,image/webp,.webp,image/heic,.heic,image/heif,.heif,application/pdf,.pdf" onChange={(event) => setDraft({ ...draft, invoice: event.target.files?.[0] || null })} /><small>{selectedCategory?.invoice_required ? 'Hạng mục này bắt buộc hóa đơn.' : 'Có thể đính kèm nếu có.'}</small></label>
        {allocated && <div className="wide"><p className="finance-category-hint">Hạng mục này phân bổ chi phí. Kế toán có thể sửa số tháng và tháng bắt đầu ngay khi thanh toán.</p>
          <ExpenseAllocationFields amount={draft.amount} months={draft.allocationMonths || selectedCategory.default_allocation_months} startMonth={draft.allocationStart} onMonthsChange={(value) => setDraft({ ...draft, allocationMonths: value })} onStartMonthChange={(value) => { setAllocationStartEdited(true); setDraft({ ...draft, allocationStart: value }); }} disabled={busy} />
        </div>}
        <div className="finance-confirm-actions wide"><button type="button" disabled={busy} onClick={onClose}>Hủy</button><button type="submit" className="confirm-approve" disabled={busy || !draft.proof || (selectedCategory?.invoice_required && !draft.invoice) || (allocated && Boolean(preview.error)) || (fixedAmountChanged && !draft.reason.trim())}>{busy ? 'Đang ghi nhận…' : recurringPeriod ? 'Thanh toán kỳ này' : 'Ghi nhận chi'}</button></div>
      </form>
    </section>
  </div>;
}
