import { useState } from 'react';
import { Drawer } from '../../../shared/components/Overlays';
import { PaginatedTable } from '../../../shared/components/Pagination';

const GROUPS = [
  ['RENT', 'Thuê mặt bằng'], ['UTILITIES', 'Điện nước'], ['INGREDIENTS', 'Nguyên liệu F&B'],
  ['MAINTENANCE', 'Sửa chữa bảo trì'], ['MARKETING', 'Marketing'], ['EQUIPMENT', 'Thiết bị'], ['OTHER', 'Khác'],
];
const groupName = Object.fromEntries(GROUPS);
const newDraft = {
  id: null, name: '', description: '', categoryGroup: 'OTHER', costType: 'OPEX', costNature: 'VARIABLE',
  recognitionMode: 'IMMEDIATE', defaultAllocationMonths: '', invoiceRequired: false, reimbursementAllowed: true,
};

export default function ExpenseCategoriesTab({ categories, busy, error, onSave, onSetActive, onDelete }) {
  const [draft, setDraft] = useState(null);
  const [failed, setFailed] = useState(false);
  const [groupFilter, setGroupFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ACTIVE');
  const [query, setQuery] = useState('');
  const needle = query.trim().toLocaleLowerCase('vi-VN');
  const visible = categories.filter((item) => (!groupFilter || item.category_group === groupFilter)
    && (statusFilter === 'ALL' || (statusFilter === 'ACTIVE') === Boolean(item.active))
    && (!needle || `${item.code} ${item.name} ${groupName[item.category_group] || ''}`.toLocaleLowerCase('vi-VN').includes(needle)))
    .sort((left, right) => left.name.localeCompare(right.name, 'vi'));

  function openEditor(item) {
    setFailed(false);
    setDraft(item ? {
      id: item.id, name: item.name || '', description: item.description || '', categoryGroup: item.category_group || 'OTHER',
      costType: item.cost_type || 'OPEX', costNature: item.cost_nature || 'VARIABLE',
      recognitionMode: item.recognition_mode || 'IMMEDIATE', defaultAllocationMonths: item.default_allocation_months ?? '',
      invoiceRequired: Boolean(item.invoice_required), reimbursementAllowed: item.reimbursement_allowed !== false,
    } : newDraft);
  }

  function update(field, value) {
    setDraft((current) => ({ ...current, [field]: value }));
  }

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setFailed(false);
    const saved = await onSave({
      ...draft,
      name: draft.name.trim(),
      defaultAllocationMonths: draft.recognitionMode === 'ALLOCATED' ? Number(draft.defaultAllocationMonths) : null,
    });
    if (saved) setDraft(null);
    else setFailed(true);
  }

  function changeStatus(item) {
    const confirmed = item.active
      ? window.confirm(`Ngừng sử dụng “${item.name}” cho các đề nghị mới?`)
      : window.confirm(`Bật lại hạng mục “${item.name}”?`);
    if (confirmed) onSetActive(item.id, !item.active);
  }

  async function remove(item) {
    if (!window.confirm(`Xóa hạng mục chưa được sử dụng “${item.name}”?`)) return;
    setFailed(false);
    if (await onDelete(item.id)) setDraft(null);
    else setFailed(true);
  }

  const editingCategory = categories.find((item) => item.id === draft?.id);
  const canDelete = editingCategory && Number(editingCategory.request_count || 0) + Number(editingCategory.recurring_count || 0) === 0;
  const activeCount = categories.filter((item) => item.active).length;

  return <section className="finance-panel finance-category-panel" aria-labelledby="finance-categories-title">
    <header className="finance-category-heading">
      <div><h2 id="finance-categories-title">Hạng mục chi phí</h2><span>{activeCount} hạng mục đang dùng</span></div>
      <button type="button" className="finance-primary" disabled={busy} onClick={() => openEditor(null)}>Thêm hạng mục</button>
    </header>
    <div className="finance-category-filters" role="group" aria-label="Bộ lọc hạng mục chi phí">
      <label>Tìm hạng mục<input type="search" maxLength="120" value={query}
        onChange={(event) => setQuery(event.target.value)} placeholder="Mã, tên hoặc nhóm"/></label>
      <label>Nhóm chi phí<select value={groupFilter} onChange={(event) => setGroupFilter(event.target.value)}>
        <option value="">Tất cả nhóm</option>
        {GROUPS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label>Trạng thái<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
        <option value="ACTIVE">Đang dùng</option><option value="INACTIVE">Đã ngừng</option><option value="ALL">Tất cả</option>
      </select></label>
    </div>
    <div className="finance-table-wrap finance-category-table-wrap">
      <PaginatedTable pageSize={10} className="finance-category-table"
        resetKey={JSON.stringify([groupFilter, statusFilter, query])} paginationLabel="Phân trang hạng mục chi phí">
        <thead><tr><th scope="col">Hạng mục</th><th scope="col">Nhóm</th><th scope="col">Ghi nhận</th>
          <th scope="col">Hóa đơn</th><th scope="col">Hoàn ứng</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{visible.map((item) => <tr key={item.id}>
          <td data-label="Hạng mục"><strong>{item.name}</strong></td>
          <td data-label="Nhóm">{groupName[item.category_group] || item.category_group}</td>
          <td data-label="Ghi nhận">{item.recognition_mode === 'ALLOCATED' ? `Phân bổ ${item.default_allocation_months} tháng` : 'Ghi nhận ngay'}</td>
          <td data-label="Hóa đơn">{item.invoice_required ? 'Bắt buộc' : 'Tùy chọn'}</td>
          <td data-label="Hoàn ứng">{item.reimbursement_allowed ? 'Cho phép' : 'Không'}</td>
          <td data-label="Trạng thái"><span className={`finance-status ${item.active ? 'received' : 'rejected'}`}>{item.active ? 'Đang dùng' : 'Ngừng dùng'}</span></td>
          <td data-label="Thao tác"><div className="finance-category-actions">
            <button type="button" aria-label={`Sửa ${item.name}`} aria-haspopup="dialog" disabled={busy} onClick={() => openEditor(item)}>Sửa</button>
            <button type="button" disabled={busy} onClick={() => changeStatus(item)}>{item.active ? 'Ngừng dùng' : 'Bật lại'}</button>
          </div></td>
        </tr>)}
          {!visible.length && <tr><td colSpan="7" className="finance-empty">Không có hạng mục phù hợp.</td></tr>}
        </tbody>
      </PaginatedTable>
    </div>
    {draft && <Drawer open title={draft.id ? 'Sửa hạng mục' : 'Thêm hạng mục chi phí'} className="finance-category-drawer"
      onClose={() => { if (!busy) setDraft(null); }}
      footer={<>
        {canDelete && <button type="button" className="finance-category-delete" disabled={busy} onClick={() => remove(editingCategory)}>Xóa hạng mục</button>}
        <button type="button" disabled={busy} onClick={() => setDraft(null)}>Hủy</button>
        <button type="submit" form="finance-category-form" className="finance-primary" disabled={busy || !draft.name.trim()}>
          {busy ? 'Đang lưu…' : draft.id ? 'Lưu thay đổi' : 'Tạo hạng mục'}
        </button>
      </>}>
      <form id="finance-category-form" className="finance-category-editor" onSubmit={submit}>
        <label>Tên hạng mục<input required maxLength="120" value={draft.name} onChange={(event) => update('name', event.target.value)}/></label>
        <label>Nhóm chi phí<select required value={draft.categoryGroup} onChange={(event) => update('categoryGroup', event.target.value)}>
          {GROUPS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <label>Cách ghi nhận<select required value={draft.recognitionMode} onChange={(event) => update('recognitionMode', event.target.value)}>
          <option value="IMMEDIATE">Ghi nhận ngay</option><option value="ALLOCATED">Phân bổ nhiều tháng</option>
        </select></label>
        {draft.recognitionMode === 'ALLOCATED' && <label>Số tháng mặc định<input required type="number" min="2" max="60"
          value={draft.defaultAllocationMonths} onChange={(event) => update('defaultAllocationMonths', event.target.value)}/></label>}
        <label>Mô tả (tùy chọn)<textarea rows="3" maxLength="500" value={draft.description} onChange={(event) => update('description', event.target.value)}/></label>
        <fieldset>
          <legend>Quy tắc áp dụng</legend>
          <label className="finance-category-checkbox"><input type="checkbox" checked={draft.invoiceRequired}
            onChange={(event) => update('invoiceRequired', event.target.checked)}/>Bắt buộc có hóa đơn</label>
          <label className="finance-category-checkbox"><input type="checkbox" checked={draft.reimbursementAllowed}
            onChange={(event) => update('reimbursementAllowed', event.target.checked)}/>Cho phép hoàn ứng</label>
        </fieldset>
        {failed && <p className="finance-error" role="alert">{error || 'Không thể lưu hạng mục. Vui lòng thử lại.'}</p>}
      </form>
    </Drawer>}
  </section>;
}
