import { PaginatedTable } from '../../../shared/components/Pagination';
import { useEffect, useMemo, useRef, useState } from 'react';

const GROUPS = [
  ['RENT', 'Thuê mặt bằng'], ['UTILITIES', 'Điện nước'], ['INGREDIENTS', 'Nguyên liệu F&B'],
  ['MAINTENANCE', 'Sửa chữa bảo trì'], ['MARKETING', 'Marketing'], ['EQUIPMENT', 'Thiết bị'], ['OTHER', 'Khác'],
];
const GROUP_NAME = Object.fromEntries(GROUPS);
const COST_TYPE_NAME = { OPEX: 'Vận hành', CAPEX: 'Đầu tư' };
const PRESETS = [
  { name: 'Bao bì & vật tư phục vụ', categoryGroup: 'INGREDIENTS', invoiceRequired: false, reimbursementAllowed: true },
  { name: 'Sửa chữa nhỏ', categoryGroup: 'MAINTENANCE', invoiceRequired: true, reimbursementAllowed: true },
  { name: 'Chiến dịch marketing', categoryGroup: 'MARKETING', invoiceRequired: true, reimbursementAllowed: false },
];
const emptyDraft = () => ({
  id: null, name: '', description: '', categoryGroup: 'OTHER', costType: 'OPEX', costNature: 'VARIABLE',
  recognitionMode: 'IMMEDIATE', defaultAllocationMonths: '', invoiceRequired: false, reimbursementAllowed: true,
});
export default function ExpenseCategoriesTab({ categories, busy, onSave, onSetActive, onDelete }) {
  const [draft, setDraft] = useState(emptyDraft);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const editorRef = useRef(null);
  const nameRef = useRef(null);
  const [groupFilter, setGroupFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ACTIVE');
  const [query, setQuery] = useState('');
  const visible = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('vi-VN');
    return categories.filter((item) => (!groupFilter || item.category_group === groupFilter)
      && (statusFilter === 'ALL' || (statusFilter === 'ACTIVE') === Boolean(item.active))
      && (!needle || `${item.code} ${item.name} ${GROUP_NAME[item.category_group] || ''}`.toLocaleLowerCase('vi-VN').includes(needle)))
      .sort((left, right) => (GROUP_NAME[left.category_group] || '').localeCompare(GROUP_NAME[right.category_group] || '', 'vi') || left.name.localeCompare(right.name, 'vi'));
  }, [categories, groupFilter, statusFilter, query]);

  useEffect(() => {
    if (draft.id && settingsOpen) {
      editorRef.current?.scrollIntoView({ block: 'center' });
      nameRef.current?.focus({ preventScroll: true });
    }
  }, [draft.id, settingsOpen]);

  function edit(item) {
    setSettingsOpen(true);
    setDraft({
      id: item.id, name: item.name || '', description: item.description || '', categoryGroup: item.category_group || 'OTHER',
      costType: item.cost_type || 'OPEX', costNature: item.cost_nature || 'VARIABLE',
      recognitionMode: item.recognition_mode || 'IMMEDIATE',
      defaultAllocationMonths: item.default_allocation_months == null ? '' : String(item.default_allocation_months),
      invoiceRequired: Boolean(item.invoice_required), reimbursementAllowed: item.reimbursement_allowed !== false,
    });
  }

  async function submit(event) {
    event.preventDefault();
    const saved = await onSave({
      ...draft,
      defaultAllocationMonths: draft.recognitionMode === 'ALLOCATED' ? Number(draft.defaultAllocationMonths) : null,
    });
    if (saved) { setDraft(emptyDraft()); setSettingsOpen(false); }
  }

  async function addPreset(preset) {
    await onSave({ ...emptyDraft(), ...preset, defaultAllocationMonths: null });
  }

  function confirmStatus(item, active) {
    const prompt = active
      ? `Kích hoạt lại “${item.name}” để cho phép dùng cho đề nghị mới?`
      : `Ngừng sử dụng “${item.name}”? Hạng mục sẽ không dùng cho đề nghị mới; lịch sử cũ vẫn được giữ.`;
    if (window.confirm(prompt)) onSetActive(item.id, active);
  }

  async function confirmDelete(item) {
    if (window.confirm(`Xóa hạng mục chưa được sử dụng “${item.name}”?`) && await onDelete(item.id)) {
      setDraft(emptyDraft());
      setSettingsOpen(false);
    }
  }

  const editingCategory = categories.find((item) => item.id === draft.id);

  return <section className="finance-panel finance-category-panel">
    <div className="finance-section-heading"><div><h2>Hạng mục chi phí</h2><p>Danh mục ngắn, nhất quán cho người tạo đề nghị. Quy tắc chi tiết chỉ kế toán chỉnh trong phần thiết lập.</p></div>
      <div className="finance-mini-total"><span>Hạng mục đang dùng</span><strong>{categories.filter((item) => item.active).length}</strong></div>
    </div>
    <details className="finance-category-settings" open={settingsOpen} onToggle={(event) => setSettingsOpen(event.currentTarget.open)}>
      <summary><span>Quản lý hạng mục<small>Tạo, chỉnh sửa hoặc ngừng dùng hạng mục chi phí</small></span></summary>
      <div className="finance-category-settings-content">
        <div className="finance-category-presets"><h3>Thêm nhanh từ mẫu</h3><p>Mẫu có sẵn để tránh nhập lại các tên gần giống nhau.</p>
          {PRESETS.map((preset) => {
            const exists = categories.some((item) => item.name.trim().toLocaleLowerCase('vi-VN') === preset.name.toLocaleLowerCase('vi-VN'));
            return <div className="finance-category-preset" key={preset.name}>
              <span>{preset.name}<small>{GROUP_NAME[preset.categoryGroup]}</small></span>
              <button type="button" disabled={busy || exists} onClick={() => addPreset(preset)}>{exists ? 'Đã có' : 'Thêm từ mẫu'}</button>
            </div>;
          })}
        </div>
        <div><h3>{draft.id ? 'Sửa hạng mục' : 'Tạo hạng mục mới'}</h3><p>Khoản hằng tháng khai báo ở Chi định kỳ; khoản phát sinh một lần dùng Ghi nhận chi trực tiếp.</p>
    <form ref={editorRef} className="finance-form finance-category-editor" onSubmit={submit}>
      <label>Tên hạng mục *<input ref={nameRef} required maxLength="120" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
      <label>Mô tả<input maxLength="500" value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
      <label>Nhóm chi phí *<select required value={draft.categoryGroup} onChange={(event) => setDraft({ ...draft, categoryGroup: event.target.value })}>
        {GROUPS.map(([value, label]) => <option key={value} value={value} title={value === 'RENT' ? 'Tiền đặt cọc thuê nhà không phải chi phí.' : undefined}>{label}</option>)}
      </select></label>
      <label>Loại chi phí *<select required value={draft.costType} onChange={(event) => setDraft({ ...draft, costType: event.target.value })}><option value="OPEX">Vận hành</option><option value="CAPEX">Đầu tư</option></select></label>
      <label>Tính chất *<select required value={draft.costNature} onChange={(event) => setDraft({ ...draft, costNature: event.target.value })}><option value="FIXED">Cố định</option><option value="VARIABLE">Biến đổi</option></select></label>
      <label>Cách ghi nhận *<select required value={draft.recognitionMode} onChange={(event) => setDraft({ ...draft, recognitionMode: event.target.value, defaultAllocationMonths: event.target.value === 'ALLOCATED' ? draft.defaultAllocationMonths : '' })}><option value="IMMEDIATE">Ghi nhận ngay</option><option value="ALLOCATED">Phân bổ nhiều tháng</option></select></label>
      {draft.recognitionMode === 'ALLOCATED' && <label>Số tháng mặc định *<input required type="number" min="2" max="60" step="1" value={draft.defaultAllocationMonths} onChange={(event) => setDraft({ ...draft, defaultAllocationMonths: event.target.value })} /></label>}
      <label className="finance-category-checkbox"><input type="checkbox" checked={draft.invoiceRequired} onChange={(event) => setDraft({ ...draft, invoiceRequired: event.target.checked })} />Bắt buộc có hóa đơn</label>
      <label className="finance-category-checkbox"><input type="checkbox" checked={draft.reimbursementAllowed} onChange={(event) => setDraft({ ...draft, reimbursementAllowed: event.target.checked })} />Cho phép hoàn ứng</label>
      {draft.categoryGroup === 'RENT' && <p className="finance-category-hint">Tiền đặt cọc thuê nhà không phải chi phí; không dùng hạng mục này để ghi nhận tiền cọc.</p>}
      <div className="finance-category-form-actions"><button type="submit" className="finance-submit-action" disabled={busy}>{draft.id ? 'Cập nhật hạng mục' : 'Tạo hạng mục'}</button>{draft.id && <button type="button" disabled={busy} onClick={() => setDraft(emptyDraft())}>Hủy sửa</button>}{editingCategory && Number(editingCategory.request_count || 0) + Number(editingCategory.recurring_count || 0) === 0 && <button type="button" disabled={busy} onClick={() => confirmDelete(editingCategory)}>Xóa hạng mục</button>}</div>
    </form>
        </div>
      </div>
    </details>
    <div className="finance-category-filters" aria-label="Bộ lọc hạng mục chi phí">
      <label>Tìm hạng mục<input type="search" maxLength="120" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tên hạng mục hoặc nhóm" /></label>
      <label>Nhóm<select value={groupFilter} onChange={(event) => setGroupFilter(event.target.value)}><option value="">Tất cả nhóm</option>{GROUPS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Trạng thái<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ACTIVE">Đang dùng</option><option value="INACTIVE">Đã ngừng</option><option value="ALL">Tất cả</option></select></label>
    </div>
    <div className="finance-table-wrap finance-category-table-wrap"><PaginatedTable pageSize={10} className="finance-category-table">
      <thead><tr><th>Hạng mục</th><th>Nhóm</th><th>Loại</th><th>Ghi nhận</th><th>Hóa đơn</th><th>Hoàn ứng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{visible.map((item) => {
        return <tr key={item.id}>
          <td data-label="Hạng mục"><strong>{item.name}</strong></td>
          <td data-label="Nhóm">{GROUP_NAME[item.category_group] || item.category_group}</td>
          <td data-label="Loại">{COST_TYPE_NAME[item.cost_type] || item.cost_type}</td>
          <td data-label="Ghi nhận">{item.recognition_mode === 'ALLOCATED' ? `Phân bổ · ${item.default_allocation_months} tháng` : 'Ghi nhận ngay'}</td>
          <td data-label="Hóa đơn">{item.invoice_required ? 'Bắt buộc' : 'Không'}</td>
          <td data-label="Hoàn ứng">{item.reimbursement_allowed ? 'Có' : 'Không'}</td>
          <td data-label="Trạng thái"><span className={`finance-status ${item.active ? 'received' : 'rejected'}`}>{item.active ? 'Đang dùng' : 'Ngừng dùng'}</span></td>
          <td data-label="Thao tác"><div className="finance-category-actions"><button type="button" disabled={busy} onClick={() => edit(item)}>Sửa</button>
            <button type="button" disabled={busy} onClick={() => confirmStatus(item, !item.active)}>{item.active ? 'Ngừng dùng' : 'Bật lại'}</button>
          </div></td>
        </tr>;
      })}{!visible.length && <tr><td colSpan="8" className="finance-empty">Không có hạng mục phù hợp. Thử đổi bộ lọc hoặc thêm từ mẫu.</td></tr>}</tbody>
    </PaginatedTable></div>
  </section>;
}
