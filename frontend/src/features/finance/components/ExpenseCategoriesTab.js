import { useMemo, useState } from 'react';

const GROUPS = [
  ['RENT', 'Thuê mặt bằng'], ['UTILITIES', 'Điện nước'], ['INGREDIENTS', 'Nguyên liệu F&B'],
  ['MAINTENANCE', 'Sửa chữa bảo trì'], ['MARKETING', 'Marketing'], ['EQUIPMENT', 'Thiết bị'], ['OTHER', 'Khác'],
];
const GROUP_NAME = Object.fromEntries(GROUPS);
const emptyDraft = () => ({
  id: null, name: '', description: '', categoryGroup: 'OTHER', costType: 'OPEX', costNature: 'VARIABLE',
  recognitionMode: 'IMMEDIATE', defaultAllocationMonths: '', invoiceRequired: false, reimbursementAllowed: true,
});
const money = (value) => `${Number(value || 0).toLocaleString('vi-VN')}đ`;

export default function ExpenseCategoriesTab({ categories, busy, onSave, onSetActive, onDelete }) {
  const [draft, setDraft] = useState(emptyDraft);
  const [groupFilter, setGroupFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const visible = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('vi-VN');
    return categories.filter((item) => (!groupFilter || item.category_group === groupFilter)
      && (!typeFilter || item.cost_type === typeFilter)
      && (statusFilter === 'ALL' || (statusFilter === 'ACTIVE') === Boolean(item.active))
      && (!needle || `${item.code} ${item.name}`.toLocaleLowerCase('vi-VN').includes(needle)));
  }, [categories, groupFilter, typeFilter, statusFilter, query]);

  function edit(item) {
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
    if (saved) setDraft(emptyDraft());
  }

  function confirmStatus(item, active) {
    const prompt = active
      ? `Kích hoạt lại “${item.name}” để cho phép dùng cho đề nghị mới?`
      : `Ngừng sử dụng “${item.name}”? Hạng mục sẽ không dùng cho đề nghị mới; lịch sử cũ vẫn được giữ.`;
    if (window.confirm(prompt)) onSetActive(item.id, active);
  }

  function confirmDelete(item) {
    if (window.confirm(`Xóa hạng mục chưa được sử dụng “${item.name}”?`)) onDelete(item.id);
  }

  return <section className="finance-panel finance-category-panel">
    <div className="finance-section-heading"><div><h2>Hạng mục chi phí</h2><p>Quản lý quy tắc ghi nhận và phạm vi sử dụng cho đề nghị mới.</p><p className="finance-category-hint">Tại đây chỉ khai báo quy tắc. Khoản chi hằng tháng được khai báo ở tab Chi định kỳ; khoản phát sinh một lần dùng nút Ghi nhận chi trực tiếp.</p></div>
      <div className="finance-mini-total"><span>Hạng mục đang dùng</span><strong>{categories.filter((item) => item.active).length}</strong></div>
    </div>
    <form className="finance-form finance-category-editor" onSubmit={submit}>
      <label>Tên hạng mục *<input required maxLength="120" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
      <label>Mô tả<input maxLength="500" value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
      <label>Nhóm chi phí *<select required value={draft.categoryGroup} onChange={(event) => setDraft({ ...draft, categoryGroup: event.target.value })}>
        {GROUPS.map(([value, label]) => <option key={value} value={value} title={value === 'RENT' ? 'Tiền đặt cọc thuê nhà không phải chi phí.' : undefined}>{label}</option>)}
      </select></label>
      <label>OPEX/CAPEX *<select required value={draft.costType} onChange={(event) => setDraft({ ...draft, costType: event.target.value })}><option value="OPEX">OPEX · Vận hành</option><option value="CAPEX">CAPEX · Đầu tư</option></select></label>
      <label>Tính chất *<select required value={draft.costNature} onChange={(event) => setDraft({ ...draft, costNature: event.target.value })}><option value="FIXED">Cố định</option><option value="VARIABLE">Biến đổi</option></select></label>
      <label>Cách ghi nhận *<select required value={draft.recognitionMode} onChange={(event) => setDraft({ ...draft, recognitionMode: event.target.value, defaultAllocationMonths: event.target.value === 'ALLOCATED' ? draft.defaultAllocationMonths : '' })}><option value="IMMEDIATE">Ghi nhận ngay</option><option value="ALLOCATED">Phân bổ nhiều tháng</option></select></label>
      {draft.recognitionMode === 'ALLOCATED' && <label>Số tháng mặc định *<input required type="number" min="2" max="60" step="1" value={draft.defaultAllocationMonths} onChange={(event) => setDraft({ ...draft, defaultAllocationMonths: event.target.value })} /></label>}
      <label className="finance-category-checkbox"><input type="checkbox" checked={draft.invoiceRequired} onChange={(event) => setDraft({ ...draft, invoiceRequired: event.target.checked })} />Bắt buộc có hóa đơn</label>
      <label className="finance-category-checkbox"><input type="checkbox" checked={draft.reimbursementAllowed} onChange={(event) => setDraft({ ...draft, reimbursementAllowed: event.target.checked })} />Cho phép hoàn ứng</label>
      {draft.categoryGroup === 'RENT' && <p className="finance-category-hint">Tiền đặt cọc thuê nhà không phải chi phí; không dùng hạng mục này để ghi nhận tiền cọc.</p>}
      <div className="finance-category-form-actions"><button disabled={busy}>{draft.id ? 'Cập nhật hạng mục' : 'Tạo hạng mục'}</button>{draft.id && <button type="button" disabled={busy} onClick={() => setDraft(emptyDraft())}>Hủy sửa</button>}</div>
    </form>

    <div className="finance-category-filters" aria-label="Bộ lọc hạng mục chi phí">
      <label>Nhóm<select value={groupFilter} onChange={(event) => setGroupFilter(event.target.value)}><option value="">Tất cả nhóm</option>{GROUPS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Loại<select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}><option value="">OPEX/CAPEX</option><option value="OPEX">OPEX</option><option value="CAPEX">CAPEX</option></select></label>
      <label>Trạng thái<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ACTIVE">Đang dùng</option><option value="INACTIVE">Đã ngừng</option><option value="ALL">Tất cả</option></select></label>
      <label>Tìm mã hoặc tên<input type="search" maxLength="120" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="CAT… hoặc tên hạng mục" /></label>
    </div>
    <div className="finance-table-wrap finance-category-table-wrap"><table className="finance-category-table">
      <thead><tr><th>Mã CAT</th><th>Tên</th><th>Nhóm</th><th>OPEX/CAPEX</th><th>Tính chất</th><th>Cách ghi nhận</th><th>Lượt sử dụng</th><th>Tổng đã chi 12 tháng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{visible.map((item) => {
        const usageCount = Number(item.request_count || 0) + Number(item.recurring_count || 0);
        const used = usageCount > 0;
        return <tr key={item.id}>
          <td>{item.code}</td><td><strong>{item.name}</strong><small>{item.description || '—'}{item.invoice_required ? ' · Bắt buộc hóa đơn' : ''}{!item.reimbursement_allowed ? ' · Không hoàn ứng' : ''}</small></td>
          <td>{GROUP_NAME[item.category_group] || item.category_group}</td><td>{item.cost_type}</td>
          <td>{item.cost_nature === 'FIXED' ? 'Cố định' : 'Biến đổi'}</td>
          <td>{item.recognition_mode === 'ALLOCATED' ? `Phân bổ · ${item.default_allocation_months} tháng mặc định` : 'Ghi nhận ngay'}</td>
          <td>{usageCount.toLocaleString('vi-VN')}</td><td>{money(item.total_paid_12_months)}</td>
          <td><span className={`finance-status ${item.active ? 'received' : 'rejected'}`}>{item.active ? 'Đang dùng' : 'Đã ngừng'}</span></td>
          <td><div className="finance-category-actions"><button type="button" disabled={busy} onClick={() => edit(item)}>Sửa</button>
            <button type="button" disabled={busy} onClick={() => confirmStatus(item, !item.active)}>{item.active ? 'Ngừng dùng' : 'Kích hoạt lại'}</button>
            {!used && <button type="button" disabled={busy} onClick={() => confirmDelete(item)}>Xóa</button>}
          </div></td>
        </tr>;
      })}{!visible.length && <tr><td colSpan="10" className="finance-empty">Không tìm thấy hạng mục phù hợp.</td></tr>}</tbody>
    </table></div>
  </section>;
}
