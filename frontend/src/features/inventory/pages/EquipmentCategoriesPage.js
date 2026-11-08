/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Màn hình tìm kiếm và quản lý danh mục thiết bị cho UC60–UC64.
 */
import { useCallback, useEffect, useState } from 'react';
import { equipmentCategoryApi } from '../services/equipmentCategoryApi';
import './equipment-categories.css';

const blank = { code: '', name: '', description: '' };
// BR-111: Hiển thị thông báo nghiệp vụ do backend trả về để người dùng sửa dữ liệu.
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể hoàn tất thao tác.';

export default function EquipmentCategoriesPage({ headOffice = false }) {
  const [rows, setRows] = useState([]);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('true');
  const [dialog, setDialog] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(blank);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // Danh mục thiết bị là dữ liệu dùng chung toàn chuỗi, không phụ thuộc cơ sở đang chọn.
  const load = useCallback(async () => {
    try {
      setError('');
      const items = await equipmentCategoryApi.list(headOffice, null, { search, active: active === 'all' ? undefined : active === 'true' });
      setRows(items);
    } catch (e) { setError(errorText(e)); }
  }, [headOffice, search, active]);
  useEffect(() => { load(); }, [load]);

  // UC-60/62: Mở form tạo mới hoặc chỉnh sửa theo đúng dữ liệu danh mục đã chọn.
  const openForm = (item = null) => { setEditing(item); setForm(item ? { code: item.code, name: item.name, description: item.description || '' } : blank); setError(''); setDialog(true); };

  // BR-111: Kiểm tra thông tin bắt buộc trước khi gửi; backend vẫn là nơi xác thực cuối cùng.
  const save = async (event) => {
    event.preventDefault();
    setError('');
    if (!form.code.trim() || !form.name.trim()) { setError('Mã và tên nhóm thiết bị là bắt buộc.'); return; }
    setBusy(true);
    try {
      if (editing) await equipmentCategoryApi.update(headOffice, null, editing.id, form);
      else await equipmentCategoryApi.create(headOffice, null, form);
      setDialog(false); await load();
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // UC-63: Xác nhận trước khi đổi trạng thái; các asset liên kết được giữ nguyên.
  const toggle = async (item) => {
    const next = !item.active;
    if (!window.confirm(`${next ? 'Kích hoạt' : 'Ngừng hoạt động'} nhóm ${item.code}?`)) return;
    setBusy(true);
    setError('');
    try { await equipmentCategoryApi.setActive(headOffice, null, item.id, next); await load(); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // BR-118/BR-191: Yêu cầu xóa có xác nhận; API từ chối nếu tồn tại asset tham chiếu.
  const remove = async (item) => {
    if (!window.confirm(`Xóa nhóm ${item.code}? Chỉ nhóm chưa được thiết bị nào sử dụng mới xóa được.`)) return;
    setBusy(true);
    setError('');
    try { await equipmentCategoryApi.remove(headOffice, null, item.id); await load(); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  return <main className="equipment-categories">
    <div className="ec-heading"><div><p className="ec-breadcrumb">Quản lý cấp cao / Danh mục toàn chuỗi</p><h1>Nhóm thiết bị</h1><p>Danh mục nhóm dùng chung cho tất cả cơ sở</p></div>
      {headOffice && <button className="ec-primary" onClick={() => openForm()} disabled={busy}>＋ Thêm nhóm</button>}</div>
    <section className="ec-filters" aria-label="Tìm kiếm và lọc nhóm thiết bị">
      <label>Tìm theo mã, tên hoặc nội dung<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm theo mã, tên hoặc nội dung..." /></label>
      <label>Trạng thái<select value={active} onChange={(e) => setActive(e.target.value)}><option value="true">Đang sử dụng</option><option value="false">Ngừng hoạt động</option><option value="all">Tất cả trạng thái</option></select></label>
      <small>Toàn chuỗi · {rows.length} nhóm</small>
    </section>
    {error && <p className="ec-error" role="alert">{error}</p>}
    <div className="ec-table-wrap"><table><thead><tr><th>Mã</th><th>Tên nhóm</th><th>Mô tả</th><th>Thiết bị</th><th>Trạng thái</th>{headOffice&&<th>Thao tác</th>}</tr></thead>
      <tbody>{rows.map((item) => <tr key={item.id}><td><strong>{item.code}</strong></td><td>{item.name}</td><td>{item.description || '—'}</td><td>{item.assetCount}</td><td><span className={`ec-status ${item.active ? 'is-active' : ''}`}>{item.active ? 'Đang sử dụng' : 'Ngừng hoạt động'}</span></td>
        {headOffice&&<td className="ec-actions"><button onClick={() => openForm(item)} disabled={busy}>Sửa</button><button onClick={() => toggle(item)} disabled={busy}>{item.active ? 'Ngừng' : 'Kích hoạt'}</button><button onClick={() => remove(item)} disabled={busy}>Xóa</button></td>}</tr>)}
        {!rows.length && <tr><td colSpan={headOffice?6:5} className="ec-empty">Chưa có nhóm thiết bị phù hợp.</td></tr>}</tbody></table></div>
    <p className="ec-footnote">Nhóm thiết bị được dùng chung tại tất cả cơ sở</p>
    {dialog && <div className="ec-overlay" role="presentation"><form className="ec-dialog" onSubmit={save} aria-labelledby="ec-dialog-title">
      <h2 id="ec-dialog-title">{editing ? 'Cập nhật nhóm thiết bị' : 'Thêm nhóm thiết bị'}</h2><p>Danh mục này dùng chung cho toàn chuỗi; không cần chọn cơ sở.</p><label>Mã nhóm<input maxLength="60" placeholder="Ví dụ: EQ-AUDIO" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} required /></label>
      <label>Tên nhóm<input maxLength="200" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
      <label>Mô tả<textarea maxLength="500" rows="3" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      {error && <p className="ec-error" role="alert">{error}</p>}<div className="ec-dialog-actions"><button type="button" onClick={() => setDialog(false)} disabled={busy}>Hủy</button><button className="ec-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận'}</button></div>
    </form></div>}
  </main>;
}
