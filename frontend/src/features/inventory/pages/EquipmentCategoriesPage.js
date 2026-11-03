/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Màn hình tìm kiếm và quản lý danh mục thiết bị cho UC60–UC64.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import apiClient from '../../../api/client';
import { equipmentCategoryApi } from '../services/equipmentCategoryApi';
import './equipment-categories.css';

const blank = { code: '', name: '', description: '' };
// BR-111: Hiển thị thông báo nghiệp vụ do backend trả về để người dùng sửa dữ liệu.
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể hoàn tất thao tác.';

export default function EquipmentCategoriesPage({ user, headOffice = false }) {
  const [branches, setBranches] = useState([]);
  const [branchId, setBranchId] = useState(user?.branchId || '');
  const [rows, setRows] = useState([]);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('true');
  const [dialog, setDialog] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(blank);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const scope = useMemo(() => headOffice, [headOffice]);

  // UC-61: Danh sách HQ cần chọn cơ sở; quản lý cơ sở luôn bị khóa vào branchId trong session.
  useEffect(() => {
    if (!headOffice) return;
    apiClient.get('/api/v1/head-office/branches').then((response) => {
      const items = response.data || [];
      setBranches(items);
      setBranchId((current) => current || items[0]?.id || items[0]?.branchId || '');
    }).catch((e) => setError(errorText(e)));
  }, [headOffice]);

  // UC-61: Tải lại theo bộ lọc hiện hành mà không sửa bản ghi danh mục.
  const load = useCallback(async () => {
    if (headOffice && !branchId) { setRows([]); return; }
    try {
      setError('');
      const items = await equipmentCategoryApi.list(scope, branchId, { search, active: active === 'all' ? undefined : active === 'true' });
      setRows(items);
    } catch (e) { setError(errorText(e)); }
  }, [scope, branchId, search, active, headOffice]);
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
      if (editing) await equipmentCategoryApi.update(scope, branchId, editing.id, form);
      else await equipmentCategoryApi.create(scope, branchId, form);
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
    try { await equipmentCategoryApi.setActive(scope, branchId, item.id, next); await load(); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // BR-118/BR-191: Yêu cầu xóa có xác nhận; API từ chối nếu tồn tại asset tham chiếu.
  const remove = async (item) => {
    if (!window.confirm(`Xóa nhóm ${item.code}? Chỉ nhóm chưa được thiết bị nào sử dụng mới xóa được.`)) return;
    setBusy(true);
    setError('');
    try { await equipmentCategoryApi.remove(scope, branchId, item.id); await load(); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // Scope: tên cơ sở hiển thị luôn khớp với phạm vi được chọn hoặc gán trong phiên.
  const branchName = branches.find((item) => (item.id || item.branchId) === branchId)?.branchName || branches.find((item) => (item.id || item.branchId) === branchId)?.name || user?.branchName || 'Cơ sở được phân quyền';
  return <main className="equipment-categories">
    <div className="ec-heading"><div><p className="ec-breadcrumb">Quản lý cơ sở / Thiết bị &amp; sự cố</p><h1>Nhóm thiết bị</h1><p>{branchName} · Thiết bị &amp; sự cố</p></div>
      <button className="ec-primary" onClick={() => openForm()} disabled={busy || (headOffice && !branchId)}>＋ Thêm nhóm</button></div>
    <section className="ec-filters" aria-label="Tìm kiếm và lọc nhóm thiết bị">
      <label>Tìm theo mã, tên hoặc nội dung<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm theo mã, tên hoặc nội dung..." /></label>
      {headOffice && <label>Cơ sở<select value={branchId} onChange={(e) => setBranchId(e.target.value)}><option value="">Chọn cơ sở</option>{branches.map((b) => <option key={b.id || b.branchId} value={b.id || b.branchId}>{b.branchName || b.name}</option>)}</select></label>}
      <label>Trạng thái<select value={active} onChange={(e) => setActive(e.target.value)}><option value="true">Đang sử dụng</option><option value="false">Ngừng hoạt động</option><option value="all">Tất cả trạng thái</option></select></label>
      <small>Phạm vi: {branchName} · {rows.length} bản ghi</small>
    </section>
    {error && <p className="ec-error" role="alert">{error}</p>}
    <div className="ec-table-wrap"><table><thead><tr><th>Mã</th><th>Tên nhóm</th><th>Mô tả</th><th>Thiết bị</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((item) => <tr key={item.id}><td><strong>{item.code}</strong></td><td>{item.name}</td><td>{item.description || '—'}</td><td>{item.assetCount}</td><td><span className={`ec-status ${item.active ? 'is-active' : ''}`}>{item.active ? 'Đang sử dụng' : 'Ngừng hoạt động'}</span></td>
        <td className="ec-actions"><button onClick={() => openForm(item)} disabled={busy}>Sửa</button><button onClick={() => toggle(item)} disabled={busy}>{item.active ? 'Ngừng' : 'Kích hoạt'}</button><button onClick={() => remove(item)} disabled={busy}>Xóa</button></td></tr>)}
        {!rows.length && <tr><td colSpan="6" className="ec-empty">Chưa có nhóm thiết bị phù hợp.</td></tr>}</tbody></table></div>
    <p className="ec-footnote">Dữ liệu danh mục thiết bị · Chỉ thao tác trong cơ sở được phân quyền</p>
    {dialog && <div className="ec-overlay" role="presentation"><form className="ec-dialog" onSubmit={save} aria-labelledby="ec-dialog-title">
      <h2 id="ec-dialog-title">{editing ? 'Cập nhật nhóm thiết bị' : 'Thêm nhóm thiết bị'}</h2><label>Mã nhóm<input maxLength="60" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></label>
      <label>Tên nhóm<input maxLength="200" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
      <label>Mô tả<textarea maxLength="500" rows="3" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      {error && <p className="ec-error" role="alert">{error}</p>}<div className="ec-dialog-actions"><button type="button" onClick={() => setDialog(false)} disabled={busy}>Hủy</button><button className="ec-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận'}</button></div>
    </form></div>}
  </main>;
}
