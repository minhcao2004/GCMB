/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Danh sách và hồ sơ tài sản thiết bị theo use case UC65–UC69.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import apiClient from '../../../api/client';
import { equipmentAssetApi } from '../services/equipmentAssetApi';
import './equipment-assets.css';

const blank = { code: '', name: '', categoryId: '', roomId: '', locationNote: '', serialNumber: '', purchaseDate: '', purchaseValue: '', warrantyExpiry: '', status: 'IN_USE' };
const statuses = [ ['IN_USE', 'Đang sử dụng'], ['BROKEN', 'Bị hỏng'], ['REPAIRING', 'Đang sửa'], ['IN_TRANSIT', 'Đang chuyển'], ['RETIRED', 'Đã thanh lý'] ];
// UC-66: Hiển thị trạng thái vận hành theo nhãn tiếng Việt.
const statusName = (value) => statuses.find(([key]) => key === value)?.[1] || value || '—';
// BR-111: Hiển thị lỗi nghiệp vụ mà backend trả về để người quản lý sửa hồ sơ.
const errorText = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể hoàn tất thao tác.';
// UC-67: Chuẩn hóa ngày rỗng để biểu mẫu date không nhận null.
const dateValue = (value) => value || '';
// UC-66: Trình bày ngày bảo hành theo định dạng ngày/tháng/năm.
const formatDate = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('vi-VN') : '—';

export default function EquipmentAssetsPage({ user, headOffice = false }) {
  const [branches, setBranches] = useState([]), [branchId, setBranchId] = useState(user?.branchId || '');
  const [rows, setRows] = useState([]), [options, setOptions] = useState({ categories: [], rooms: [] });
  const [search, setSearch] = useState(''), [roomId, setRoomId] = useState(''), [status, setStatus] = useState(''), [activeFilter, setActiveFilter] = useState('true');
  const [drawer, setDrawer] = useState(false), [editing, setEditing] = useState(null), [form, setForm] = useState(blank);
  const [imageFile, setImageFile] = useState(null), [imagePreview, setImagePreview] = useState(''), [imageMime, setImageMime] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const scopedHeadOffice = useMemo(() => headOffice, [headOffice]);

  // UC-66: Quản lý cấp cao chọn chi nhánh; quản lý cơ sở nhận phạm vi từ phiên đăng nhập.
  useEffect(() => {
    if (!headOffice) return;
    apiClient.get('/head-office/branches').then(({ data }) => {
      const items = data || [];
      setBranches(items);
      setBranchId((current) => current || items[0]?.branchId || items[0]?.id || '');
    }).catch((e) => setError(errorText(e)));
  }, [headOffice]);

  // UC-66: Tải danh sách theo từ khóa, phòng, tình trạng và trạng thái sử dụng.
  const load = useCallback(async () => {
    if (headOffice && !branchId) { setRows([]); return; }
    try {
      setError('');
      setRows(await equipmentAssetApi.list(scopedHeadOffice, branchId, {
        search, roomId: roomId || undefined, status: status || undefined,
        active: activeFilter === 'all' ? undefined : activeFilter === 'true',
      }));
    } catch (e) { setError(errorText(e)); }
  }, [scopedHeadOffice, branchId, search, roomId, status, activeFilter, headOffice]);
  useEffect(() => { load(); }, [load]);

  // UC-65/67: Tải lựa chọn nhóm và phòng đúng cơ sở trước khi mở biểu mẫu.
  const loadOptions = useCallback(async (assetId = null) => {
    try { setOptions(await equipmentAssetApi.options(scopedHeadOffice, branchId, assetId)); }
    catch (e) { setError(errorText(e)); }
  }, [scopedHeadOffice, branchId]);

  // UC-66: Nạp phòng làm bộ lọc vị trí và nhóm hiện hành khi người dùng đổi cơ sở.
  useEffect(() => { if (branchId) loadOptions(null); }, [branchId, loadOptions]);

  // UC-65/67: Mở hồ sơ mới hoặc nạp đầy đủ dữ liệu hiện có để người dùng rà soát.
  const openDrawer = async (item = null) => {
    setEditing(item); setError(''); setImageFile(null); setImagePreview(''); setImageMime('');
    setForm(item ? {
      code: item.code, name: item.name, categoryId: item.categoryId || '', roomId: item.roomId || '',
      locationNote: item.locationNote || '', serialNumber: item.serialNumber || '',
      purchaseDate: dateValue(item.purchaseDate), purchaseValue: item.purchaseValue ?? '',
      warrantyExpiry: dateValue(item.warrantyExpiry), status: item.status || 'IN_USE',
    } : blank);
    setDrawer(true);
    await loadOptions(item?.id || null);
  };

  // BR-115/BR-116: Tạo URL xem trước cục bộ; backend sẽ kiểm tra lại loại nội dung và dung lượng.
  const chooseImage = (event) => {
    const file = event.target.files?.[0] || null;
    setImageFile(file);
    setImagePreview(file ? URL.createObjectURL(file) : '');
    setImageMime(file?.type || '');
  };

  // BR-202/BR-111: Kiểm tra trường bắt buộc ở form trước khi gửi; server vẫn xác thực cuối cùng.
  const save = async (event) => {
    event.preventDefault(); setError('');
    if (!form.code.trim() || !form.name.trim() || !form.categoryId || (!form.roomId && !form.locationNote.trim())) {
      setError('Vui lòng nhập mã, tên, nhóm và ít nhất một vị trí phòng/khu vực.'); return;
    }
    setBusy(true);
    try {
      const payload = { ...form, categoryId: form.categoryId, roomId: form.roomId || null,
        purchaseDate: form.purchaseDate || null, purchaseValue: form.purchaseValue === '' ? null : Number(form.purchaseValue),
        warrantyExpiry: form.warrantyExpiry || null };
      const asset = editing
        ? await equipmentAssetApi.update(scopedHeadOffice, branchId, editing.id, payload)
        : await equipmentAssetApi.create(scopedHeadOffice, branchId, payload);
      if (imageFile) {
        try { await equipmentAssetApi.uploadImage(scopedHeadOffice, branchId, asset.id, imageFile); }
        catch (imageError) { setError(`Hồ sơ đã lưu nhưng ảnh chưa tải lên: ${errorText(imageError)}`); await load(); setEditing(asset); return; }
      }
      setDrawer(false); setImageFile(null); setImagePreview(''); await load();
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // UC-68: Xác nhận trạng thái hoạt động riêng, không thay đổi tình trạng hỏng/sửa/chuyển.
  const toggleActive = async (item) => {
    const next = !item.active;
    if (!window.confirm(`${next ? 'Kích hoạt' : 'Ngừng sử dụng'} thiết bị ${item.code}?`)) return;
    setBusy(true); setError('');
    try {
      const updated = await equipmentAssetApi.setActive(scopedHeadOffice, branchId, item.id, next);
      if (editing?.id === item.id) setEditing(updated);
      await load();
    }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // UC-69/BR-118: Chỉ yêu cầu xóa sau xác nhận; backend giữ asset có lịch sử sự cố/điều chuyển.
  const remove = async (item) => {
    if (!window.confirm(`Xóa thiết bị ${item.code} — ${item.name}? Chỉ thiết bị chưa phát sinh lịch sử mới được xóa.`)) return;
    setBusy(true); setError('');
    try { await equipmentAssetApi.remove(scopedHeadOffice, branchId, item.id); setDrawer(false); await load(); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  };

  // UC-66: Đọc ảnh theo phiên và phạm vi hiện hành; URL cục bộ được thu hồi khi đóng hồ sơ.
  useEffect(() => {
    if (!drawer || !editing?.hasImage || imageFile) return undefined;
    let url;
    let live = true;
    equipmentAssetApi.image(scopedHeadOffice, branchId, editing.id).then((blob) => {
      if (live) { url = URL.createObjectURL(blob); setImagePreview(url); setImageMime(blob.type); }
    }).catch(() => {});
    return () => { live = false; if (url) URL.revokeObjectURL(url); };
  }, [drawer, editing, imageFile, scopedHeadOffice, branchId]);

  // BR-115: Thu hồi URL xem trước của file cục bộ để tránh giữ blob sau khi thay ảnh.
  useEffect(() => () => { if (imageFile && imagePreview) URL.revokeObjectURL(imagePreview); }, [imageFile, imagePreview]);

  const branchName = branches.find((item) => (item.branchId || item.id) === branchId)?.branchName
    || branches.find((item) => (item.branchId || item.id) === branchId)?.name || user?.branchName || 'Cơ sở được phân quyền';
  const lockedInTransit = editing?.status === 'IN_TRANSIT';
  return <main className="equipment-assets">
    <div className="ea-heading"><div><p className="ea-breadcrumb">Quản lý cơ sở / Thiết bị &amp; sự cố</p><h1>Danh sách thiết bị</h1><p>{branchName} · Thiết bị &amp; sự cố</p></div>
      <button className="ea-primary" onClick={() => openDrawer()} disabled={busy || (headOffice && !branchId)}>＋ Thêm thiết bị</button></div>
    <section className="ea-filters" aria-label="Tìm kiếm và lọc thiết bị">
      <label className="ea-search">Tìm theo mã, tên hoặc nội dung<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm theo mã, tên hoặc nội dung..." /></label>
      {headOffice && <label>Cơ sở<select value={branchId} onChange={(e) => { setBranchId(e.target.value); setRoomId(''); }}><option value="">Chọn cơ sở</option>{branches.map((item) => <option key={item.branchId || item.id} value={item.branchId || item.id}>{item.branchName || item.name}</option>)}</select></label>}
      <label>Vị trí<select value={roomId} onChange={(e) => setRoomId(e.target.value)}><option value="">Tất cả vị trí</option>{options.rooms.map((room) => <option key={room.id} value={room.id}>{room.code} · {room.name}</option>)}</select></label>
      <label>Trạng thái<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{statuses.map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label>Sử dụng<select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value)}><option value="true">Đang sử dụng</option><option value="false">Ngừng sử dụng</option><option value="all">Tất cả</option></select></label>
      <small>Phạm vi: {branchName} · {rows.length} thiết bị</small>
    </section>
    {error && !drawer && <p className="ea-error" role="alert">{error}</p>}
    <div className="ea-table-wrap"><table><thead><tr><th>Mã / tên</th><th>Nhóm</th><th>Vị trí</th><th>Bảo hành</th><th>Trạng thái</th><th>Sử dụng</th></tr></thead>
      <tbody>{rows.map((item) => <tr key={item.id} onClick={() => openDrawer(item)} tabIndex="0" onKeyDown={(e) => e.key === 'Enter' && openDrawer(item)}>
        <td><strong>{item.code} · {item.name}</strong></td><td>{item.categoryName}</td><td>{item.roomCode ? `${item.roomCode}${item.roomName ? ` · ${item.roomName}` : ''}` : item.locationNote || '—'}</td><td>{formatDate(item.warrantyExpiry)}</td>
        <td>{statusName(item.status)}</td><td>{item.active ? 'Đang sử dụng' : 'Ngừng sử dụng'}</td>
      </tr>)}{!rows.length && <tr><td colSpan="6" className="ea-empty">Chưa có thiết bị phù hợp.</td></tr>}</tbody></table></div>
    <p className="ea-footnote">Dữ liệu thiết bị · Chỉ thao tác trong cơ sở được phân quyền</p>
    {drawer && <div className="ea-overlay" onMouseDown={(e) => e.target === e.currentTarget && setDrawer(false)}>
      <aside className="ea-drawer" aria-label="Hồ sơ thiết bị">
        <header><div><h2>{editing ? 'Hồ sơ thiết bị' : 'Thêm thiết bị'}</h2><p>{branchName}</p></div><button className="ea-close" onClick={() => setDrawer(false)} aria-label="Đóng">×</button></header>
        <form onSubmit={save}>
          <div className="ea-fields">
            <label>Mã thiết bị *<input maxLength="60" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></label>
            <label>Tên thiết bị *<input maxLength="200" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
            <label>Nhóm *<select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })} required><option value="">Chọn nhóm</option>{options.categories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ' (ngừng hoạt động)'}</option>)}</select></label>
            <label>Phòng / vị trí<select value={form.roomId} onChange={(e) => setForm({ ...form, roomId: e.target.value })}><option value="">Khu vực chung / vị trí khác</option>{options.rooms.map((room) => <option key={room.id} value={room.id}>{room.code} · {room.name}</option>)}</select></label>
            {!form.roomId && <label>Khu vực / vị trí khác *<input maxLength="200" value={form.locationNote} onChange={(e) => setForm({ ...form, locationNote: e.target.value })} required /></label>}
            {form.roomId && <label>Ghi chú vị trí<input maxLength="200" value={form.locationNote} onChange={(e) => setForm({ ...form, locationNote: e.target.value })} /></label>}
            <label>Số serial<input maxLength="200" value={form.serialNumber} onChange={(e) => setForm({ ...form, serialNumber: e.target.value })} /></label>
            <label>Ngày mua<input type="date" value={form.purchaseDate} onChange={(e) => setForm({ ...form, purchaseDate: e.target.value })} /></label>
            <label>Giá trị mua<input type="number" min="0" step="1" value={form.purchaseValue} onChange={(e) => setForm({ ...form, purchaseValue: e.target.value })} /></label>
            <label>Hạn bảo hành<input type="date" min={form.purchaseDate || undefined} value={form.warrantyExpiry} onChange={(e) => setForm({ ...form, warrantyExpiry: e.target.value })} /></label>
            <label>Trạng thái<select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} disabled={lockedInTransit}>
              {statuses.filter(([key]) => key !== 'IN_TRANSIT' || key === form.status).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
            </select></label>
            <label className="ea-image-label">Ảnh thiết bị
              <input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf,.jpg,.jpeg,.png,.webp,.heic,.heif,.pdf" onChange={chooseImage} />
              <small>JPEG, PNG, WebP, HEIC, HEIF hoặc PDF · tối đa 20 MB</small>
              {imagePreview && (imageMime === 'application/pdf' ? <a href={imagePreview} target="_blank" rel="noreferrer">Xem file đã chọn</a> : <img src={imagePreview} alt="Ảnh thiết bị" />)}
            </label>
            <label className="ea-incident-label">Lịch sử gần nhất<input readOnly value={editing?.latestIncident || 'Chưa có sự cố được ghi nhận'} /></label>
          </div>
          <p className="ea-note">Cơ sở của thiết bị chỉ thay đổi qua quy trình điều chuyển đã xác nhận. Lịch sử sự cố và chi phí được lưu riêng.</p>
          {error && <p className="ea-error" role="alert">{error}</p>}
          <footer>{editing && <><button type="button" className="ea-danger" onClick={() => remove(editing)} disabled={busy || lockedInTransit}>Xóa</button><button type="button" onClick={() => toggleActive(editing)} disabled={busy || lockedInTransit}>{editing.active ? 'Ngừng sử dụng' : 'Kích hoạt'}</button></>}
            <span className="ea-spacer" /><button type="button" onClick={() => setDrawer(false)} disabled={busy}>Quay lại</button><button className="ea-primary" disabled={busy}>{busy ? 'Đang lưu…' : editing ? 'Lưu hồ sơ' : 'Thêm thiết bị'}</button></footer>
        </form>
      </aside>
    </div>}
  </main>;
}
