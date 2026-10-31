import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiAssetUrl } from '../../../api/client';
import menuItemApi from '../services/menuItemApi';
import './menu-items.css';

const stockLabels = {
  AVAILABLE: 'Còn hàng',
  LOW_STOCK: 'Sắp hết',
  OUT_OF_STOCK: 'Hết hàng',
  UNTRACKED: 'Không theo dõi',
};

const kindLabels = {
  PACKAGED: 'Hàng hóa',
  PREPARED: 'Món chế biến',
  SERVICE: 'Dịch vụ',
};

const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)}đ`;
const groupPriceDigits = (value) => String(value ?? '').replace(/\D/g, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
const saleStatusLabel = (item) => {
  if (!item.active) return 'Ngừng bán';
  if (item.stockStatus === 'LOW_STOCK') return 'Sắp hết';
  if (item.stockStatus === 'OUT_OF_STOCK') return 'Hết hàng';
  return 'Sẵn bán';
};
const quantity = (value) => value == null ? 'Chưa ghi nhận' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
const displayError = (error) => error.response?.data?.message || error.response?.data?.detail || error.message || 'Không thể hoàn tất thao tác.';

export default function MenuItemsPage({ user }) {
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [inventoryOptions, setInventoryOptions] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [status, setStatus] = useState('all');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [deleteId, setDeleteId] = useState(null);
  const [pendingStatus, setPendingStatus] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [editorMode, setEditorMode] = useState(null);
  const [editorItemId, setEditorItemId] = useState(null);
  const [draft, setDraft] = useState(null);
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState('');
  const [reviewingSave, setReviewingSave] = useState(false);
  const [saving, setSaving] = useState(false);
  const editorPanelRef = useRef(null);
  const editorReturnFocusRef = useRef(null);
  const createButtonRef = useRef(null);
  const confirmationRef = useRef(null);
  const listFocusRef = useRef(null);
  const returnFocusRef = useRef(null);
  const actionModalOpen = Boolean(detailOpen || deleteId || pendingStatus);

  const reload = useCallback(async () => {
    setError('');
    setLoading(true);
    try {
      const [menuItems, menuCategories, linkedItems] = await Promise.all([
        menuItemApi.list(), menuItemApi.categories(), menuItemApi.inventoryOptions(),
      ]);
      setItems(menuItems);
      setCategories(menuCategories);
      setInventoryOptions(linkedItems);
      setCategory((current) => current === 'all' || menuCategories.includes(current) ? current : 'all');
      setSelectedId((current) => current && menuItems.some((item) => item.id === current) ? current : null);
      return true;
    } catch (loadError) {
      setError(displayError(loadError));
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(''), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    if (!detailOpen && !deleteId && !pendingStatus) return;
    const confirmation = confirmationRef.current;
    if (!confirmation) return;
    confirmation.focus({ preventScroll: true });
    if (window.matchMedia('(max-width: 900px)').matches) {
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      confirmation.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
    }
  }, [detailOpen, deleteId, pendingStatus]);
  useEffect(() => {
    if (!imageFile) { setImagePreview(''); return undefined; }
    const url = URL.createObjectURL(imageFile);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);
  useEffect(() => {
    if (!editorMode) return undefined;
    const frame = window.requestAnimationFrame(() => document.getElementById('menu-item-name')?.focus({ preventScroll: true }));
    return () => window.cancelAnimationFrame(frame);
  }, [editorMode]);
  useEffect(() => {
    if (!editorMode) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [editorMode]);
  useEffect(() => {
    if (!actionModalOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [actionModalOpen]);
  const restoreFocus = useCallback((fallbackToList = false) => {
    const focusKey = returnFocusRef.current;
    returnFocusRef.current = null;
    window.requestAnimationFrame(() => {
      const trigger = !fallbackToList && focusKey
        ? Array.from(document.querySelectorAll('[data-menu-focus-key]')).find((element) => element.dataset.menuFocusKey === focusKey)
        : null;
      const target = trigger || listFocusRef.current;
      if (!target) return;
      target.focus({ preventScroll: true });
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      target.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: fallbackToList ? 'start' : 'nearest', inline: 'nearest' });
    });
  }, []);

  const cancelConfirmation = useCallback(() => {
    setDetailOpen(false);
    setDeleteId(null);
    setPendingStatus(null);
    setSelectedId(null);
    restoreFocus();
  }, [restoreFocus]);

  const visibleItems = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return items.filter((item) => {
      const matchesSearch = !term || [item.code, item.name, item.category, item.description, item.inventoryName, item.inventorySku]
        .filter(Boolean).join(' ').toLocaleLowerCase('vi-VN').includes(term);
      const matchesCategory = category === 'all' || item.category === category;
      const matchesStatus = status === 'all' || (status === 'active' ? item.active : !item.active);
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [items, search, category, status]);

  const selected = items.find((item) => item.id === selectedId) || null;

  const selectItem = (item, event) => {
    returnFocusRef.current = event.currentTarget.dataset.menuFocusKey || null;
    setSelectedId(item.id);
    setDetailOpen(true);
    setEditorMode(null);
    setEditorItemId(null);
    setDraft(null);
    setImageFile(null);
    setReviewingSave(false);
    setDeleteId(null);
    setPendingStatus(null);
  };

  const openCreate = (event) => {
    editorReturnFocusRef.current = event.currentTarget.dataset.menuFocusKey;
    setDetailOpen(false);
    setSelectedId(null);
    setDeleteId(null);
    setPendingStatus(null);
    setEditorItemId(null);
    setDraft({
      name: '', category: categories[0] || '', kind: 'PACKAGED', inventoryItemId: '',
      salePrice: '', active: true, description: '', removeImage: false,
      imageUrl: '', imageOriginalName: '', imageMimeType: '',
    });
    setImageFile(null);
    setReviewingSave(false);
    setEditorMode('create');
  };

  const openUpdate = (item, event) => {
    const focusKey = event.currentTarget.dataset.menuFocusKey;
    editorReturnFocusRef.current = focusKey?.startsWith('edit-detail-') ? `menu-item-${item.id}` : focusKey;
    setDetailOpen(false);
    setSelectedId(null);
    setEditorItemId(item.id);
    setDraft({
      name: item.name, category: item.category, kind: item.kind,
      inventoryItemId: item.inventoryItemId || '', salePrice: String(item.salePrice),
      active: item.active, description: item.description || '', removeImage: false,
      imageUrl: item.imageUrl || '', imageOriginalName: item.imageOriginalName || '',
      imageMimeType: item.imageMimeType || '',
    });
    setImageFile(null);
    setReviewingSave(false);
    setError('');
    setEditorMode('update');
  };

  const closeEditor = useCallback(() => {
    setEditorMode(null);
    setEditorItemId(null);
    setDraft(null);
    setImageFile(null);
    setReviewingSave(false);
    const focusKey = editorReturnFocusRef.current;
    window.requestAnimationFrame(() => {
      const target = Array.from(document.querySelectorAll('[data-menu-focus-key]'))
        .find((element) => element.dataset.menuFocusKey === focusKey);
      target?.focus({ preventScroll: true });
    });
  }, []);

  useEffect(() => {
    if (!editorMode) return undefined;
    const handleKeyDown = (event) => {
      const dialog = editorPanelRef.current;
      if (!dialog) return;
      if (event.key === 'Escape' && !saving) {
        event.preventDefault();
        closeEditor();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(dialog.querySelectorAll(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])',
      ));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [editorMode, saving, closeEditor]);

  useEffect(() => {
    if (!actionModalOpen) return undefined;
    const handleKeyDown = (event) => {
      const dialog = confirmationRef.current;
      if (!dialog) return;
      if (event.key === 'Escape' && !busyId) {
        event.preventDefault();
        cancelConfirmation();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(dialog.querySelectorAll(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])',
      ));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [actionModalOpen, busyId, cancelConfirmation]);

  const changeDraft = (field, value) => setDraft((current) => ({ ...current, [field]: value }));

  const chooseImage = (event) => {
    const file = event.target.files?.[0] || null;
    if (file && file.size > 20_000_000) {
      setError('Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.');
      event.target.value = '';
      return;
    }
    setError('');
    setImageFile(file);
    if (file) changeDraft('removeImage', false);
  };

  const removeDraftImage = () => {
    setImageFile(null);
    setDraft((current) => ({ ...current, imageUrl: '', imageOriginalName: '', imageMimeType: '', removeImage: true }));
  };

  const reviewSave = (event) => {
    event.preventDefault();
    if (!draft) return;
    if (!draft.name.trim() || !draft.category.trim()) {
      setError('Vui lòng nhập tên món và danh mục.');
      return;
    }
    if (!/^\d{1,18}$/.test(draft.salePrice)) {
      setError('Giá bán phải là số nguyên VND không âm và tối đa 18 chữ số.');
      return;
    }
    if (draft.kind === 'PACKAGED' && !draft.inventoryItemId) {
      setError('Hàng hóa cần liên kết với một mặt hàng kho.');
      return;
    }
    if (draft.description.length > 1000) {
      setError('Mô tả không được vượt quá 1.000 ký tự.');
      return;
    }
    setError('');
    setReviewingSave(true);
  };

  const saveItem = async () => {
    if (!draft || saving) return;
    setSaving(true);
    setError('');
    const command = {
      name: draft.name.trim(), category: draft.category.trim(), kind: draft.kind,
      inventoryItemId: draft.kind === 'PACKAGED' ? draft.inventoryItemId || null : null,
      salePrice: draft.salePrice, active: draft.active,
      description: draft.description.trim() || null, removeImage: Boolean(draft.removeImage),
    };
    try {
      const saved = editorMode === 'create'
        ? await menuItemApi.create(command, imageFile)
        : await menuItemApi.update(editorItemId, command, imageFile);
      setItems((current) => [...current.filter((item) => item.id !== saved.id), saved]
        .sort((left, right) => left.name.localeCompare(right.name, 'vi')));
      setCategories((current) => current.includes(saved.category)
        ? current : [...current, saved.category].sort((left, right) => left.localeCompare(right, 'vi')));
      setSelectedId(null);
      setToast(editorMode === 'create' ? `Đã tạo món ${saved.code}.` : `Đã cập nhật món ${saved.code}.`);
      setEditorMode(null);
      setEditorItemId(null);
      setDraft(null);
      setImageFile(null);
      setReviewingSave(false);
      const focusKey = editorReturnFocusRef.current;
      editorReturnFocusRef.current = null;
      window.requestAnimationFrame(() => {
        const trigger = focusKey
          ? Array.from(document.querySelectorAll('[data-menu-focus-key]')).find((element) => element.dataset.menuFocusKey === focusKey)
          : null;
        (trigger || listFocusRef.current)?.focus({ preventScroll: true });
      });
    } catch (saveError) {
      setError(displayError(saveError));
    } finally {
      setSaving(false);
    }
  };

  const requestStatusChange = (item, trigger) => {
    const focusKey = trigger?.dataset.menuFocusKey;
    returnFocusRef.current = focusKey?.startsWith('status-detail-') ? `menu-item-${item.id}` : focusKey || null;
    setDetailOpen(false);
    setError('');
    setEditorMode(null);
    setDraft(null);
    setImageFile(null);
    setSelectedId(item.id);
    setDeleteId(null);
    setPendingStatus({ id: item.id, active: !item.active });
  };

  const requestDelete = (item, trigger) => {
    const focusKey = trigger?.dataset.menuFocusKey;
    returnFocusRef.current = focusKey?.startsWith('delete-detail-') ? `menu-item-${item.id}` : focusKey || null;
    setDetailOpen(false);
    setError('');
    setEditorMode(null);
    setDraft(null);
    setImageFile(null);
    setSelectedId(item.id);
    setDeleteId(item.id);
    setPendingStatus(null);
  };

  const applyStatusChange = async () => {
    if (!selected || !pendingStatus || busyId) return;
    const active = pendingStatus.active;
    setBusyId(selected.id);
    setError('');
    try {
      const updated = await menuItemApi.setActive(selected.id, active);
      setItems((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
      setToast(active ? 'Đã đưa món vào bán.' : 'Đã ngừng bán món.');
      setSelectedId(null);
      setDeleteId(null);
      setPendingStatus(null);
      restoreFocus();
    } catch (actionError) {
      setError(displayError(actionError));
    } finally {
      setBusyId(null);
    }
  };

  const deleteItem = async () => {
    if (!selected || busyId) return;
    const deletedId = selected.id;
    setBusyId(deletedId);
    setError('');
    try {
      await menuItemApi.remove(deletedId);
      setDetailOpen(false);
      setDeleteId(null);
      setPendingStatus(null);
      setSelectedId(null);
      setItems((current) => current.filter((item) => item.id !== deletedId));
      restoreFocus(true);
      const refreshed = await reload();
      setToast(refreshed
        ? 'Đã xóa món khỏi thực đơn. Lịch sử bán hàng được giữ nguyên.'
        : 'Đã xóa món, nhưng chưa tải lại được danh sách. Hãy thử tải lại để đồng bộ dữ liệu.');
    } catch (actionError) {
      setError(displayError(actionError));
    } finally {
      setBusyId(null);
    }
  };

  return <div className="menu-items-page">
    <header className="menu-heading">
      <div>
        <h1>Thực đơn F&amp;B</h1>
        <p>Cơ sở {user.branchName || 'được phân quyền'} · Thực đơn F&amp;B</p>
      </div>
      <button ref={createButtonRef} type="button" className="menu-create-button" data-menu-focus-key="create-menu"
        onClick={openCreate} disabled={loading || Boolean(error)}>+ Thêm món / dịch vụ</button>
    </header>

    {error && <div className="menu-error" role="alert">
      <span>{error}</span><button type="button" onClick={reload} disabled={loading}>Thử lại</button>
    </div>}

    <section className="menu-filters" aria-label="Tìm kiếm và lọc thực đơn">
      <div className="menu-filter-controls">
        <label className="menu-search">
          <span className="visually-hidden">Tìm món</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã, tên hoặc nội dung..." />
        </label>
        <label>
          <span className="visually-hidden">Danh mục</span>
          <select value={category} onChange={(event) => setCategory(event.target.value)}>
            <option value="all">Tất cả danh mục</option>
            {categories.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label>
          <span className="visually-hidden">Trạng thái bán</span>
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="all">Tất cả trạng thái</option>
            <option value="active">Đang bán</option>
            <option value="inactive">Ngừng bán</option>
          </select>
        </label>
      </div>
      <p className="menu-scope">Phạm vi: {user.branchName || 'cơ sở được phân quyền'} · {loading ? 'Đang tải món…' : `${visibleItems.length} bản ghi mẫu`}</p>
    </section>

    <div className="menu-content-grid">
      <section ref={listFocusRef} className="menu-table-card" aria-label="Danh sách món và dịch vụ" tabIndex="-1">
        {loading ? <div className="menu-loading" role="status" aria-label="Đang tải thực đơn">
          {[0, 1, 2, 3].map((row) => <div className="menu-skeleton-row" key={row}><i /><i /><i /><i /><i /></div>)}
        </div> : visibleItems.length ? <div className="menu-table-scroll">
          <table className="menu-table">
            <thead><tr>
              <th scope="col">Món / dịch vụ</th>
              <th scope="col">Danh mục</th>
              <th scope="col">Giá bán</th>
              <th scope="col">Tồn liên kết</th>
              <th scope="col">Trạng thái</th>
              <th scope="col" className="menu-row-actions">Thao tác</th>
            </tr></thead>
            <tbody>{visibleItems.map((item) => <tr key={item.id} className={selectedId === item.id ? 'is-selected' : ''}>
              <td>
                <button className="menu-item-link" type="button" data-menu-focus-key={`menu-item-${item.id}`}
                  onClick={(event) => selectItem(item, event)}>
                  <strong>{item.name}</strong><span>{item.code}</span>
                </button>
              </td>
              <td>{item.category}</td>
              <td className="menu-price">{money(item.salePrice)}</td>
              <td className="menu-inventory-quantity">{item.inventoryItemId ? `${quantity(item.stockQuantity)} ${item.unitName || ''}`.trim() : 'Không theo dõi'}</td>
              <td className="menu-list-status">{saleStatusLabel(item)}</td>
              <td className="menu-row-actions">
                <div className="menu-row-action-group">
                  <button type="button" className="menu-row-action" data-menu-focus-key={`edit-row-${item.id}`}
                    aria-label={`Sửa ${item.name}`} onClick={(event) => openUpdate(item, event)}
                    disabled={Boolean(busyId || deleteId || pendingStatus)}>Sửa</button>
                  <button type="button" className="menu-row-action" data-menu-focus-key={`status-row-${item.id}`}
                    aria-label={`${item.active ? 'Ngừng bán' : 'Đưa vào bán'} ${item.name}`}
                    onClick={(event) => requestStatusChange(item, event.currentTarget)}
                    disabled={Boolean(busyId || deleteId || pendingStatus)}>
                    {item.active ? 'Ngừng bán' : 'Bật bán'}
                  </button>
                  <button type="button" className="menu-row-delete" data-menu-focus-key={`delete-row-${item.id}`}
                    aria-label={`Xóa ${item.name} khỏi thực đơn`}
                    onClick={(event) => requestDelete(item, event.currentTarget)}
                    disabled={Boolean(busyId || deleteId || pendingStatus)}>Xóa</button>
                </div>
              </td>
            </tr>)}</tbody>
          </table>
        </div> : <div className="menu-empty">
          <h2>{items.length ? 'Không tìm thấy món phù hợp' : 'Chưa có món trong thực đơn'}</h2>
          <p>{items.length ? 'Thử đổi từ khóa hoặc điều kiện lọc.' : 'Món sẽ xuất hiện ở đây sau khi được thêm vào thực đơn của cơ sở.'}</p>
          {(search || category !== 'all' || status !== 'all') && <button type="button" onClick={() => { setSearch(''); setCategory('all'); setStatus('all'); }}>Xóa bộ lọc</button>}
        </div>}
      </section>

    </div>
    <footer className="menu-page-footer">
      <p>Hiển thị {visibleItems.length} kết quả · Trang 1</p>
      <p>Dữ liệu minh họa · Chỉ thao tác trong cơ sở được phân quyền</p>
    </footer>
    {actionModalOpen && selected && <div className="menu-action-overlay">
      <div className="menu-action-scrim" aria-hidden="true" onClick={() => { if (!busyId) cancelConfirmation(); }} />
      <section ref={confirmationRef} className="menu-action-dialog" role="dialog" aria-modal="true"
        aria-labelledby="menu-action-dialog-info-title" tabIndex="-1">
        <MenuItemInfo item={selected} branchName={user.branchName || 'Cơ sở được phân quyền'}
          titleId="menu-action-dialog-info-title" onClose={cancelConfirmation} closeDisabled={Boolean(busyId)} />
        {error && <p className="menu-action-error" role="alert">{error}</p>}
        {detailOpen ? <div className="menu-detail-actions">
          <button type="button" className="menu-edit-action" data-menu-focus-key={`edit-detail-${selected.id}`}
            onClick={(event) => openUpdate(selected, event)} disabled={Boolean(busyId)}>Sửa món</button>
          <button type="button" className="menu-toggle-action" data-menu-focus-key={`status-detail-${selected.id}`}
            onClick={(event) => requestStatusChange(selected, event.currentTarget)} disabled={Boolean(busyId)}>
            {busyId === selected.id ? 'Đang lưu…' : selected.active ? 'Ngừng bán món' : 'Đưa món vào bán'}
          </button>
          <button type="button" className="menu-delete-action" data-menu-focus-key={`delete-detail-${selected.id}`}
            onClick={(event) => requestDelete(selected, event.currentTarget)} disabled={Boolean(busyId)}>Xóa khỏi thực đơn</button>
        </div> : deleteId === selected.id ? <div className="menu-action-confirm is-delete">
          <h3 id="menu-action-title">Xóa món khỏi thực đơn?</h3>
          <p>Món sẽ không còn xuất hiện trong danh sách bán. Các dòng đơn và lịch sử bán hàng liên quan vẫn được giữ.</p>
          <div><button type="button" onClick={cancelConfirmation} disabled={Boolean(busyId)}>Quay lại</button>
            <button type="button" className="confirm-delete" onClick={deleteItem} disabled={Boolean(busyId)}>{busyId === selected.id ? 'Đang xóa…' : 'Xác nhận xóa'}</button></div>
        </div> : pendingStatus?.id === selected.id && <div className="menu-action-confirm is-status">
          <h3 id="menu-action-title">{pendingStatus.active ? 'Đưa món vào bán?' : 'Ngừng bán món?'}</h3>
          <p>{pendingStatus.active
            ? 'Món sẽ xuất hiện trong danh sách có thể chọn cho đơn mới. Tồn kho không thay đổi.'
            : 'Món sẽ không thể được chọn cho đơn mới. Đơn đã ghi nhận và lịch sử bán hàng vẫn được giữ.'}</p>
          <div><button type="button" onClick={cancelConfirmation} disabled={Boolean(busyId)}>Quay lại</button>
            <button type="button" className="confirm-status" onClick={applyStatusChange} disabled={Boolean(busyId)}>{busyId === selected.id ? 'Đang lưu…' : 'Xác nhận trạng thái'}</button></div>
        </div>}
      </section>
    </div>}
    {editorMode && draft && <div className={`menu-editor-overlay${editorMode === 'update' ? ' is-compact' : ''}`}>
      <button type="button" className="menu-editor-scrim" aria-label="Đóng biểu mẫu" onClick={closeEditor} disabled={saving} />
      <section ref={editorPanelRef} className="menu-editor-dialog" role="dialog" aria-modal="true" aria-labelledby="menu-editor-title">
        <MenuItemEditor
          mode={editorMode}
          draft={draft}
          branchName={user.branchName || 'được phân quyền'}
          categories={categories}
          inventoryOptions={inventoryOptions}
          imageFile={imageFile}
          imagePreview={imagePreview}
          reviewing={reviewingSave}
          saving={saving}
          onChange={changeDraft}
          onImage={chooseImage}
          onRemoveImage={removeDraftImage}
          onReview={reviewSave}
          onConfirm={saveItem}
          onBackToEdit={() => setReviewingSave(false)}
          onBack={closeEditor}
        />
      </section>
    </div>}
    {toast && <div className="menu-toast" role="status" aria-live="polite">{toast}</div>}
  </div>;
}

function MenuItemInfo({ item, branchName, titleId, onClose, closeDisabled = false }) {
  return <>
    <header className="menu-detail-heading">
      <div><h2 id={titleId}>Thông tin món</h2><p>{branchName}</p></div>
      {onClose && <button type="button" className="menu-detail-close" aria-label="Đóng thông tin món"
        onClick={onClose} disabled={closeDisabled}>
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>}
    </header>
    <div className="menu-detail-summary">
      <h3>{item.name}</h3><span>{item.code}</span>
      <div className="menu-status-pair">
        <span className={`menu-sale-status ${item.active ? 'active' : 'inactive'}`}>{item.active ? 'Đang bán' : 'Ngừng bán'}</span>
        <span className={`menu-stock-status ${item.stockStatus.toLowerCase().replaceAll('_', '-')}`}>{stockLabels[item.stockStatus] || item.stockStatus}</span>
      </div>
    </div>
    <dl className="menu-detail-list">
      <dt>Danh mục</dt><dd>{item.category}</dd>
      <dt>Loại</dt><dd>{kindLabels[item.kind] || item.kind}</dd>
      <dt>Giá bán</dt><dd>{money(item.salePrice)}</dd>
      <dt>Liên kết kho</dt><dd>{item.inventoryName ? `${item.inventorySku || ''} · ${item.inventoryName}` : 'Không theo dõi tồn kho'}</dd>
      <dt>Tồn khả dụng</dt><dd>{item.inventoryItemId ? `${quantity(item.stockQuantity)} ${item.unitName || ''}` : 'Không theo dõi'}</dd>
      {item.description && <><dt>Mô tả</dt><dd>{item.description}</dd></>}
    </dl>
    {item.imageUrl && <div className="menu-detail-image">
      {item.imageMimeType === 'application/pdf' ? <a href={apiAssetUrl(item.imageUrl)} target="_blank" rel="noreferrer">Xem tệp: {item.imageOriginalName}</a>
        : <img src={apiAssetUrl(item.imageUrl)} alt={`Ảnh ${item.name}`} />}
    </div>}
    <p className="menu-status-note">Trạng thái bán quyết định món có thể được chọn trong đơn mới. Tình trạng tồn kho được hiển thị riêng; đổi trạng thái bán không thay đổi số lượng tồn.</p>
  </>;
}

function MenuItemEditor({ mode, draft, branchName, categories, inventoryOptions, imageFile, imagePreview,
  reviewing, saving, onChange, onImage, onRemoveImage, onReview, onConfirm, onBackToEdit, onBack }) {
  const [priceFocused, setPriceFocused] = useState(false);
  const previewUrl = imagePreview || apiAssetUrl(draft.imageUrl);
  const previewName = imageFile?.name || draft.imageOriginalName;
  const previewMime = imageFile?.type || draft.imageMimeType;
  const isPdf = previewMime === 'application/pdf' || /\.pdf$/i.test(previewName || '');
  const inventory = inventoryOptions.find((item) => item.id === draft.inventoryItemId);
  const changePrice = (event) => {
    onChange('salePrice', event.currentTarget.value.replace(/\D/g, '').slice(0, 18));
  };
  let pricePreview = '—';
  if (/^\d{1,18}$/.test(draft.salePrice)) {
    pricePreview = `${groupPriceDigits(draft.salePrice)}đ`;
  }

  return <section className="menu-editor" aria-labelledby="menu-editor-title">
    <header className="menu-editor-heading">
      <div>
        <h2 id="menu-editor-title">{mode === 'create' ? 'Thêm món / dịch vụ' : 'Thông tin món / dịch vụ'}</h2>
        <p>Cơ sở {branchName}</p>
      </div>
      <button type="button" className="menu-editor-close" aria-label="Đóng biểu mẫu" onClick={onBack} disabled={saving}>
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
    </header>
    <form className="menu-editor-form" onSubmit={onReview}>
      {!reviewing ? <fieldset className="menu-editor-fields" disabled={saving}>
        <label className="menu-editor-field" htmlFor="menu-item-name">Tên món *
          <input id="menu-item-name" value={draft.name} onChange={(event) => onChange('name', event.target.value)}
            maxLength={200} autoComplete="off" required />
        </label>

        <label className="menu-editor-field" htmlFor="menu-item-category">Danh mục
          <input id="menu-item-category" list="menu-category-options" value={draft.category}
            onChange={(event) => onChange('category', event.target.value)} maxLength={200} required />
        </label>
        <datalist id="menu-category-options">{categories.map((value) => <option key={value} value={value} />)}</datalist>

        <label className="menu-editor-field" htmlFor="menu-item-kind">Loại
          <select id="menu-item-kind" value={draft.kind} onChange={(event) => {
            onChange('kind', event.target.value);
            if (event.target.value !== 'PACKAGED') onChange('inventoryItemId', '');
          }}>
            <option value="PACKAGED">Hàng hóa</option>
            <option value="PREPARED">Món chế biến</option>
            <option value="SERVICE">Dịch vụ</option>
          </select>
        </label>

        <label className="menu-editor-field" htmlFor="menu-item-price">Giá bán *
          <div className="menu-price-input">
            <input id="menu-item-price" type="text" inputMode="numeric" maxLength={23}
              aria-label="Giá bán bằng đồng Việt Nam" value={priceFocused ? draft.salePrice : groupPriceDigits(draft.salePrice)}
              onFocus={() => setPriceFocused(true)} onBlur={() => setPriceFocused(false)} onChange={changePrice}
              placeholder="Nhập số tiền" required />
            <span aria-hidden="true">đ</span>
          </div>
        </label>

        {draft.kind === 'PACKAGED' && <>
          <label className="menu-editor-field" htmlFor="menu-item-inventory">Liên kết kho
            <select id="menu-item-inventory" value={draft.inventoryItemId}
              onChange={(event) => onChange('inventoryItemId', event.target.value)} required>
              <option value="">Chọn mặt hàng kho</option>
              {inventoryOptions.map((item) => <option key={item.id} value={item.id}
                disabled={!item.active && item.id !== draft.inventoryItemId}>
                {item.sku} · {item.name} · {item.unitName}{item.active ? '' : ' · Ngừng dùng'}
              </option>)}
            </select>
          </label>
        </>}

        <label className="menu-editor-field" htmlFor="menu-item-active">Tình trạng bán
          <select id="menu-item-active" value={String(draft.active)}
            onChange={(event) => onChange('active', event.target.value === 'true')}>
            <option value="true">Sẵn bán</option>
            <option value="false">Ngừng bán</option>
          </select>
        </label>

        <label className="menu-editor-field" htmlFor="menu-item-description">Mô tả
          <textarea id="menu-item-description" value={draft.description}
            onChange={(event) => onChange('description', event.target.value)} maxLength={1000} rows={3} />
        </label>

        <div className="menu-editor-field">
          <label htmlFor="menu-item-image">Ảnh món</label>
          <div className="menu-upload-field">
            <input id="menu-item-image" className="menu-upload-input" type="file" accept=".jpg,.jpeg,.png,.webp,.heic,.heif,.pdf"
              onChange={onImage} />
            {previewUrl ? <div className="menu-upload-preview">
              {isPdf ? <a href={previewUrl} target="_blank" rel="noreferrer">Mở tệp {previewName || 'đã tải lên'}</a>
                : <img src={previewUrl} alt={previewName ? `Xem trước ${previewName}` : 'Ảnh món hiện tại'} />}
              <div className="menu-upload-actions">
                <label className="menu-upload-change" htmlFor="menu-item-image">Thay ảnh / tài liệu</label>
                <button type="button" onClick={onRemoveImage}>Gỡ tệp</button>
              </div>
            </div> : <label className="menu-upload-trigger" htmlFor="menu-item-image">
              <span>+ Tải ảnh</span>
              <small>JPG, PNG, WebP, HEIC, HEIF, PDF</small>
              <small>Tối đa 20 MB · Xem trước sau khi tải</small>
            </label>}
          </div>
        </div>
        {draft.kind === 'PACKAGED' && <p className="menu-inventory-note" role="note">
          Hết hàng phụ thuộc tồn khả dụng. Lưu thực đơn không làm tăng số lượng tồn.
        </p>}
      </fieldset> : <section className="menu-save-review" aria-labelledby="menu-save-review-title" role="group">
        <h3 id="menu-save-review-title">Xem lại thông tin</h3>
        <dl>
          <dt>Tên món</dt><dd>{draft.name.trim()}</dd>
          <dt>Danh mục</dt><dd>{draft.category.trim()}</dd>
          <dt>Loại</dt><dd>{kindLabels[draft.kind]}</dd>
          <dt>Giá bán</dt><dd>{pricePreview}</dd>
          {draft.kind === 'PACKAGED' && <><dt>Liên kết kho</dt><dd>{inventory ? `${inventory.sku} · ${inventory.name}` : 'Chưa chọn'}</dd></>}
          <dt>Tình trạng bán</dt><dd>{draft.active ? 'Sẵn bán' : 'Ngừng bán'}</dd>
          {draft.description.trim() && <><dt>Mô tả</dt><dd>{draft.description.trim()}</dd></>}
          <dt>Tệp</dt><dd>{imageFile ? imageFile.name : draft.removeImage ? 'Đã gỡ tệp hiện tại' : previewName || 'Không có'}</dd>
        </dl>
        <p>Đơn hàng và lịch sử bán đã ghi nhận vẫn giữ nguyên thông tin tại thời điểm bán.</p>
      </section>}
      <footer className="menu-editor-footer">
        <button type="button" onClick={onBack} disabled={saving}>Quay lại</button>
        {reviewing ? <>
          <button type="button" onClick={onBackToEdit} disabled={saving}>Quay lại chỉnh sửa</button>
          <button type="button" className="menu-editor-confirm" onClick={onConfirm} disabled={saving}>
            {saving ? 'Đang lưu…' : mode === 'create' ? 'Xác nhận tạo món' : 'Xác nhận cập nhật'}
          </button>
        </> : <button type="submit" className="menu-editor-confirm" disabled={saving}>Lưu món</button>}
      </footer>
    </form>
  </section>;
}
