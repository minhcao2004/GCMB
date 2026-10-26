import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
const quantity = (value) => value == null ? 'Chưa ghi nhận' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
const displayError = (error) => error.response?.data?.message || error.response?.data?.detail || error.message || 'Không thể hoàn tất thao tác.';

export default function MenuItemsPage({ user }) {
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [status, setStatus] = useState('all');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [pendingStatus, setPendingStatus] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const detailPanelRef = useRef(null);
  const confirmationRef = useRef(null);
  const listFocusRef = useRef(null);
  const returnFocusRef = useRef(null);

  const reload = useCallback(async () => {
    setError('');
    setLoading(true);
    try {
      const [menuItems, menuCategories] = await Promise.all([menuItemApi.list(), menuItemApi.categories()]);
      setItems(menuItems);
      setCategories(menuCategories);
      setSelectedId((current) => current && menuItems.some((item) => item.id === current) ? current : menuItems[0]?.id || null);
    } catch (loadError) {
      setError(displayError(loadError));
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
    if (!deleteId && !pendingStatus) return;
    const confirmation = confirmationRef.current;
    if (!confirmation) return;
    confirmation.focus({ preventScroll: true });
    if (window.matchMedia('(max-width: 900px)').matches) {
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      confirmation.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
    }
  }, [deleteId, pendingStatus]);

  const restoreFocus = (fallbackToList = false) => {
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
  };

  const cancelConfirmation = () => {
    setDeleteId(null);
    setPendingStatus(null);
    restoreFocus();
  };

  const visibleItems = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return items.filter((item) => {
      const matchesSearch = !term || [item.code, item.name, item.category, item.inventoryName, item.inventorySku]
        .filter(Boolean).join(' ').toLocaleLowerCase('vi-VN').includes(term);
      const matchesCategory = category === 'all' || item.category === category;
      const matchesStatus = status === 'all' || (status === 'active' ? item.active : !item.active);
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [items, search, category, status]);

  const selected = items.find((item) => item.id === selectedId) || null;

  const requestStatusChange = (item, trigger) => {
    returnFocusRef.current = trigger?.dataset.menuFocusKey || null;
    setSelectedId(item.id);
    setDeleteId(null);
    setPendingStatus({ id: item.id, active: !item.active });
  };

  const requestDelete = (item, trigger) => {
    returnFocusRef.current = trigger?.dataset.menuFocusKey || null;
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
    setBusyId(selected.id);
    setError('');
    try {
      await menuItemApi.remove(selected.id);
      setItems((current) => current.filter((item) => item.id !== selected.id));
      setSelectedId(null);
      setDeleteId(null);
      setPendingStatus(null);
      setToast('Đã xóa món khỏi thực đơn. Lịch sử bán hàng được giữ nguyên.');
      restoreFocus(true);
    } catch (actionError) {
      setError(displayError(actionError));
    } finally {
      setBusyId(null);
    }
  };

  return <div className="menu-items-page">
    <div className="menu-breadcrumb">Quản lý cơ sở <span>/</span> Thực đơn F&amp;B</div>
    <header className="menu-heading">
      <div>
        <h1>Thực đơn F&amp;B</h1>
        <p>Cơ sở {user.branchName || 'được phân quyền'} · Quản lý trạng thái món và lịch sử bán hàng</p>
      </div>
    </header>

    {error && <div className="menu-error" role="alert">
      <span>{error}</span><button type="button" onClick={reload} disabled={loading}>Thử lại</button>
    </div>}

    <section className="menu-filters" aria-label="Tìm kiếm và lọc thực đơn">
      <label className="menu-search">
        <span>Tìm món</span>
        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã, tên hoặc danh mục…" />
      </label>
      <label>
        <span>Danh mục</span>
        <select value={category} onChange={(event) => setCategory(event.target.value)}>
          <option value="all">Tất cả danh mục</option>
          {categories.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <label>
        <span>Trạng thái bán</span>
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">Tất cả trạng thái</option>
          <option value="active">Đang bán</option>
          <option value="inactive">Ngừng bán</option>
        </select>
      </label>
      <p className="menu-scope">Phạm vi: {user.branchName || 'cơ sở được phân quyền'} · {loading ? 'Đang tải món…' : `${visibleItems.length} món`}</p>
    </section>

    <div className="menu-content-grid">
      <section ref={listFocusRef} className="menu-table-card" aria-label="Danh sách món và dịch vụ" tabIndex="-1">
        {loading ? <div className="menu-loading" role="status" aria-label="Đang tải thực đơn">
          {[0, 1, 2, 3].map((row) => <div className="menu-skeleton-row" key={row}><i /><i /><i /><i /></div>)}
        </div> : visibleItems.length ? <div className="menu-table-scroll">
          <table className="menu-table">
            <thead><tr>
              <th scope="col">Món / dịch vụ</th>
              <th scope="col">Danh mục</th>
              <th scope="col">Giá bán</th>
              <th scope="col">Tồn liên kết</th>
              <th scope="col">Tồn kho</th>
              <th scope="col">Trạng thái món</th>
              <th scope="col"><span className="visually-hidden">Thao tác</span></th>
            </tr></thead>
            <tbody>{visibleItems.map((item) => <tr key={item.id} className={selectedId === item.id ? 'is-selected' : ''}>
              <td>
                <button className="menu-item-link" type="button" onClick={() => { setSelectedId(item.id); setDeleteId(null); setPendingStatus(null); }}>
                  <strong>{item.name}</strong><span>{item.code}</span>
                </button>
              </td>
              <td>{item.category}</td>
              <td className="menu-price">{money(item.salePrice)}</td>
              <td>{item.inventoryName ? <><strong className="menu-inventory-name">{item.inventoryName}</strong><span className="menu-secondary">{item.inventorySku || 'Liên kết kho'}</span></> : <span className="menu-secondary">Không theo dõi</span>}</td>
              <td><span className={`menu-stock-status ${item.stockStatus.toLowerCase().replaceAll('_', '-')}`}>{stockLabels[item.stockStatus] || item.stockStatus}</span>
                {item.inventoryItemId && <span className="menu-secondary">{quantity(item.stockQuantity)} {item.unitName || ''}</span>}
              </td>
              <td><span className={`menu-sale-status ${item.active ? 'active' : 'inactive'}`}>{item.active ? 'Đang bán' : 'Ngừng bán'}</span></td>
              <td className="menu-row-actions">
                <button type="button" className="menu-row-action" data-menu-focus-key={`status-row-${item.id}`} onClick={(event) => requestStatusChange(item, event.currentTarget)} disabled={Boolean(busyId)} aria-label={`${item.active ? 'Ngừng bán' : 'Đưa vào bán'} ${item.name}`}>
                  {busyId === item.id ? 'Đang lưu…' : item.active ? 'Ngừng bán' : 'Đưa vào bán'}
                </button>
                <button type="button" className="menu-row-delete" data-menu-focus-key={`delete-row-${item.id}`} disabled={Boolean(busyId)} onClick={(event) => requestDelete(item, event.currentTarget)} aria-label={`Xóa ${item.name}`}>
                  Xóa
                </button>
              </td>
            </tr>)}</tbody>
          </table>
        </div> : <div className="menu-empty">
          <h2>{items.length ? 'Không tìm thấy món phù hợp' : 'Chưa có món trong thực đơn'}</h2>
          <p>{items.length ? 'Thử đổi từ khóa hoặc điều kiện lọc.' : 'Món sẽ xuất hiện ở đây sau khi được thêm vào thực đơn của cơ sở.'}</p>
          {(search || category !== 'all' || status !== 'all') && <button type="button" onClick={() => { setSearch(''); setCategory('all'); setStatus('all'); }}>Xóa bộ lọc</button>}
        </div>}
        <footer className="menu-table-footer">Hiển thị {visibleItems.length} / {items.length} món · Dữ liệu thuộc cơ sở được chọn</footer>
      </section>

      <aside ref={detailPanelRef} className="menu-detail-panel" aria-label="Thông tin món được chọn">
        {selected ? <>
          <header className="menu-detail-heading"><div><h2>Thông tin món</h2><p>{user.branchName || 'Cơ sở được phân quyền'}</p></div></header>
          <div className="menu-detail-summary">
            <h3>{selected.name}</h3><span>{selected.code}</span>
            <div className="menu-status-pair">
              <span className={`menu-sale-status ${selected.active ? 'active' : 'inactive'}`}>{selected.active ? 'Đang bán' : 'Ngừng bán'}</span>
              <span className={`menu-stock-status ${selected.stockStatus.toLowerCase().replaceAll('_', '-')}`}>{stockLabels[selected.stockStatus] || selected.stockStatus}</span>
            </div>
          </div>
          <dl className="menu-detail-list">
            <dt>Danh mục</dt><dd>{selected.category}</dd>
            <dt>Loại</dt><dd>{kindLabels[selected.kind] || selected.kind}</dd>
            <dt>Giá bán</dt><dd>{money(selected.salePrice)}</dd>
            <dt>Liên kết kho</dt><dd>{selected.inventoryName ? `${selected.inventorySku || ''} · ${selected.inventoryName}` : 'Không theo dõi tồn kho'}</dd>
            <dt>Tồn khả dụng</dt><dd>{selected.inventoryItemId ? `${quantity(selected.stockQuantity)} ${selected.unitName || ''}` : 'Không theo dõi'}</dd>
          </dl>
          <p className="menu-status-note">Trạng thái bán quyết định món có thể được chọn trong đơn mới. Tình trạng tồn kho được hiển thị riêng; đổi trạng thái bán không thay đổi số lượng tồn.</p>
          {deleteId === selected.id ? <div ref={confirmationRef} tabIndex="-1" className="menu-delete-confirm" role="group" aria-labelledby="menu-delete-title">
            <h3 id="menu-delete-title">Xóa món khỏi thực đơn?</h3>
            <p>Món sẽ không còn xuất hiện trong danh sách bán. Các dòng đơn và lịch sử bán hàng liên quan vẫn được giữ.</p>
            <div><button type="button" onClick={cancelConfirmation} disabled={busyId === selected.id}>Quay lại</button>
              <button type="button" className="confirm-delete" onClick={deleteItem} disabled={busyId === selected.id}>{busyId === selected.id ? 'Đang xóa…' : 'Xác nhận xóa'}</button></div>
          </div> : pendingStatus?.id === selected.id ? <div ref={confirmationRef} tabIndex="-1" className="menu-status-confirm" role="group" aria-labelledby="menu-status-title">
            <h3 id="menu-status-title">{pendingStatus.active ? 'Đưa món vào bán?' : 'Ngừng bán món?'}</h3>
            <p>{pendingStatus.active
              ? 'Món sẽ xuất hiện trong danh sách có thể chọn cho đơn mới. Tồn kho không thay đổi.'
              : 'Món sẽ không thể được chọn cho đơn mới. Đơn đã ghi nhận và lịch sử bán hàng vẫn được giữ.'}</p>
            <div><button type="button" onClick={cancelConfirmation} disabled={Boolean(busyId)}>Quay lại</button>
              <button type="button" className="confirm-status" onClick={applyStatusChange} disabled={Boolean(busyId)}>{busyId === selected.id ? 'Đang lưu…' : 'Xác nhận trạng thái'}</button></div>
          </div> : <div className="menu-detail-actions">
            <button type="button" className="menu-toggle-action" data-menu-focus-key={`status-detail-${selected.id}`} onClick={(event) => requestStatusChange(selected, event.currentTarget)} disabled={Boolean(busyId)}>
              {busyId === selected.id ? 'Đang lưu…' : selected.active ? 'Ngừng bán món' : 'Đưa món vào bán'}
            </button>
            <button type="button" className="menu-delete-action" data-menu-focus-key={`delete-detail-${selected.id}`} onClick={(event) => requestDelete(selected, event.currentTarget)} disabled={Boolean(busyId)}>Xóa khỏi thực đơn</button>
          </div>}
        </> : <div className="menu-detail-empty"><h2>Chọn một món</h2><p>Thông tin và thao tác trạng thái sẽ hiển thị tại đây.</p></div>}
      </aside>
    </div>
    {toast && <div className="menu-toast" role="status" aria-live="polite">{toast}</div>}
  </div>;
}
