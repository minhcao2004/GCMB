import { Fragment, useEffect, useMemo, useState } from 'react';
import inventoryApi from '../services/inventoryApi';
import './inventory.css';

const formatQuantity = (value) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(Number(value || 0));
const formatVnd = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value || 0))}₫`;
const displayError = (error) => error.response?.data?.message || error.response?.data?.detail || error.message || 'Không thể hoàn tất thao tác.';
const makeLocalDateTime = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
const dateTimeLabel = (value) => value ? new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : '—';
const trackingLabel = (mode) => mode === 'AUTO_ON_SALE' ? 'Tự trừ khi bán' : 'Theo dõi thủ công';
const stockLabel = (item) => {
  if (!item.active) return 'Ngừng sử dụng';
  if (item.stockStatus === 'OUT_OF_STOCK') return 'Hết hàng';
  if (item.stockStatus === 'LOW_STOCK') return 'Sắp hết';
  return 'Còn hàng';
};
const stockClass = (item) => !item.active ? 'archived' : item.stockStatus === 'OUT_OF_STOCK'
  ? 'out' : item.stockStatus === 'LOW_STOCK' ? 'low' : 'available';
const newId = () => window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const EMPTY_LINES = [];

export default function InventoryPage({ user }) {
  const [items, setItems] = useState([]);
  const [metadata, setMetadata] = useState({ units: [], categories: [] });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [screen, setScreen] = useState('stock');
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [stockStatus, setStockStatus] = useState('all');
  const [itemStatus, setItemStatus] = useState('active');
  const [historySearch, setHistorySearch] = useState('');
  const [historyFrom, setHistoryFrom] = useState('');
  const [historyTo, setHistoryTo] = useState('');
  const [historyKind, setHistoryKind] = useState('ALL');
  const [movements, setMovements] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [itemMode, setItemMode] = useState('create');
  const [itemDraft, setItemDraft] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [receiptDraft, setReceiptDraft] = useState(null);
  const [adjustDraft, setAdjustDraft] = useState(null);
  const [adjustItem, setAdjustItem] = useState(null);
  const [staleStock, setStaleStock] = useState(null);
  const [evidence, setEvidence] = useState(null);
  const [evidenceUrl, setEvidenceUrl] = useState('');

  const reload = async () => {
    setError('');
    setLoading(true);
    try {
      const [nextItems, nextMetadata] = await Promise.all([inventoryApi.items(), inventoryApi.metadata()]);
      setItems(nextItems);
      setMetadata(nextMetadata);
    } catch (loadError) {
      setError(displayError(loadError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); }, []);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(''), 3600);
    return () => window.clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    if (!evidence) { setEvidenceUrl(''); return undefined; }
    const url = URL.createObjectURL(evidence);
    setEvidenceUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [evidence]);
  useEffect(() => {
    if (screen !== 'history' || !selectedId) return undefined;
    let current = true;
    setHistoryLoading(true);
    const params = { kind: historyKind, q: historySearch.trim() || undefined };
    if (historyFrom) params.from = new Date(`${historyFrom}T00:00:00`).toISOString();
    if (historyTo) params.to = new Date(`${historyTo}T23:59:59.999`).toISOString();
    inventoryApi.history(selectedId, params)
      .then((rows) => { if (current) setMovements(rows); })
      .catch((loadError) => { if (current) setError(displayError(loadError)); })
      .finally(() => { if (current) setHistoryLoading(false); });
    return () => { current = false; };
  }, [screen, selectedId, historyKind, historySearch, historyFrom, historyTo]);

  const activeItems = useMemo(() => items.filter((item) => item.active), [items]);
  const visibleItems = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return activeItems.filter((item) => {
      const matchesSearch = !term || `${item.sku} ${item.name} ${item.category} ${item.unitName}`
        .toLocaleLowerCase('vi-VN').includes(term);
      const matchesCategory = category === 'all' || item.category === category;
      const matchesStatus = stockStatus === 'all' || item.stockStatus === stockStatus;
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [activeItems, search, category, stockStatus]);
  const selectedItem = items.find((item) => item.id === selectedId) || null;
  const receiptLines = useMemo(() => receiptDraft?.items || EMPTY_LINES, [receiptDraft]);
  const receiptSummary = useMemo(() => receiptLines.map((line) => {
    const item = items.find((row) => row.id === line.inventoryItemId);
    const quantity = Number(line.quantity || 0);
    const unitCost = Number(line.unitCost || 0);
    return { ...line, item, quantity, unitCost, amount: quantity * unitCost };
  }).filter((line) => line.item), [receiptLines, items]);
  const receiptTotalQuantity = receiptSummary.reduce((total, line) => total + line.quantity, 0);
  const receiptTotalValue = receiptSummary.reduce((total, line) => total + line.amount, 0);
  const adjustActual = Number(adjustDraft?.actualQuantity || 0);
  const adjustDelta = adjustItem ? adjustActual - Number(adjustItem.quantity) : 0;
  const managedItems = items.filter((item) => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return (itemStatus === 'archived' ? !item.active : item.active)
      && (!term || `${item.sku} ${item.name} ${item.category}`.toLocaleLowerCase('vi-VN').includes(term));
  });

  const setToastMessage = (message) => { setToast(message); setError(''); };

  const openHistory = (item) => {
    setSelectedId(item.id);
    setMovements([]);
    setHistorySearch('');
    setHistoryFrom('');
    setHistoryTo('');
    setHistoryKind('ALL');
    setScreen('history');
    setError('');
  };

  const openItemForm = (mode, item = null) => {
    setItemMode(mode);
    setItemDraft(item ? {
      sku: item.sku, name: item.name, category: item.category, unitId: item.unitId,
      trackingMode: item.trackingMode, minimumQuantity: String(item.minimumQuantity), active: item.active,
    } : {
      sku: '', name: '', category: metadata.categories[0] || '', unitId: metadata.units[0]?.id || '',
      trackingMode: 'AUTO_ON_SALE', minimumQuantity: '5', active: true,
    });
    setSelectedId(item?.id || null);
    setError('');
    setScreen('item-form');
  };

  const openReceipt = () => {
    setReceiptDraft({ receivedAt: makeLocalDateTime(), supplierName: '', supplierInvoice: '', items: [{ id: newId(), inventoryItemId: '', quantity: '', unitCost: '' }] });
    setEvidence(null);
    setError('');
    setScreen('receipt');
  };

  const openAdjustment = () => {
    const first = activeItems[0] || null;
    setAdjustItem(first);
    setAdjustDraft({
      adjustedAt: makeLocalDateTime(), inventoryItemId: first?.id || '', actualQuantity: '',
      reason: '',
    });
    setStaleStock(null);
    setEvidence(null);
    setError('');
    setScreen('adjustment');
  };

  const saveItem = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const command = {
        ...itemDraft,
        minimumQuantity: Number(itemDraft.minimumQuantity),
      };
      if (itemMode === 'create') await inventoryApi.createItem(command);
      else await inventoryApi.updateItem(selectedId, command);
      await reload();
      setToastMessage(itemMode === 'create' ? 'Đã tạo mặt hàng kho.' : 'Đã cập nhật thông tin mặt hàng.');
      setScreen('items');
    } catch (saveError) {
      setError(displayError(saveError));
    } finally {
      setBusy(false);
    }
  };

  const deleteItem = async (item) => {
    setBusy(true);
    setError('');
    try {
      await inventoryApi.deleteItem(item.id);
      setConfirmDeleteId(null);
      await reload();
      setToastMessage(`Đã ngừng sử dụng ${item.name}. Lịch sử kho được giữ nguyên.`);
    } catch (deleteError) {
      setError(displayError(deleteError));
    } finally {
      setBusy(false);
    }
  };

  const updateReceiptLine = (id, key, value) => {
    setReceiptDraft((draft) => ({ ...draft, items: draft.items.map((line) => line.id === id ? { ...line, [key]: value } : line) }));
  };

  const addReceiptLine = () => setReceiptDraft((draft) => ({
    ...draft, items: [...draft.items, { id: newId(), inventoryItemId: '', quantity: '', unitCost: '' }],
  }));

  const removeReceiptLine = (id) => setReceiptDraft((draft) => ({
    ...draft, items: draft.items.length > 1 ? draft.items.filter((line) => line.id !== id) : draft.items,
  }));

  const submitReceipt = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const command = {
        receivedAt: new Date(receiptDraft.receivedAt).toISOString(),
        supplierName: receiptDraft.supplierName,
        supplierInvoice: receiptDraft.supplierInvoice,
        items: receiptDraft.items.map(({ inventoryItemId, quantity, unitCost }) => ({
          inventoryItemId, quantity: Number(quantity), unitCost: Number(unitCost),
        })),
      };
      const result = await inventoryApi.receive(command, evidence);
      await reload();
      setEvidence(null);
      setScreen('stock');
      setToastMessage(`Đã ghi nhận phiếu ${result.code}; tồn kho đã được cập nhật.`);
    } catch (submitError) {
      setError(displayError(submitError));
    } finally {
      setBusy(false);
    }
  };

  const selectAdjustmentItem = (id) => {
    const item = activeItems.find((row) => row.id === id) || null;
    setAdjustItem(item);
    setAdjustDraft((draft) => ({ ...draft, inventoryItemId: id, actualQuantity: '' }));
    setStaleStock(null);
  };

  const acknowledgeLatestStock = () => setStaleStock((current) => current ? { ...current, reviewed: true } : null);

  const submitAdjustment = async (event) => {
    event.preventDefault();
    if (!adjustItem) return;
    setBusy(true);
    setError('');
    try {
      const command = {
        inventoryItemId: adjustItem.id,
        occurredAt: new Date(adjustDraft.adjustedAt).toISOString(),
        expectedStockVersion: adjustItem.stockVersion,
        expectedQuantity: Number(adjustItem.quantity),
        actualQuantity: Number(adjustDraft.actualQuantity),
        reason: adjustDraft.reason,
      };
      await inventoryApi.adjust(command, evidence);
      await reload();
      setEvidence(null);
      setScreen('stock');
      setToastMessage(`Đã điều chỉnh tồn ${adjustItem.name}.`);
    } catch (submitError) {
      const detail = submitError.response?.data?.detail || submitError.response?.data?.message || '';
      if (submitError.response?.status === 409 && detail.includes('Tồn kho đã thay đổi')) {
        try {
          const latest = await inventoryApi.item(adjustItem.id);
          setStaleStock({ previous: adjustItem.quantity, latest: latest.quantity, reviewed: false });
          setAdjustItem(latest);
          setError('');
        } catch (reloadError) {
          setError(displayError(reloadError));
        }
      } else {
        setError(displayError(submitError));
      }
    } finally {
      setBusy(false);
    }
  };

  const openMovementEvidence = async (movement) => {
    if (!selectedId || !movement.id) return;
    const preview = window.open('about:blank', '_blank');
    if (!preview) {
      setError('Trình duyệt đang chặn cửa sổ xem chứng từ. Hãy cho phép mở cửa sổ mới rồi thử lại.');
      return;
    }
    try {
      const response = await inventoryApi.evidence(selectedId, movement.id);
      const url = URL.createObjectURL(response);
      preview.location.replace(url);
      window.setTimeout(() => { URL.revokeObjectURL(url); preview.close(); }, 60_000);
    } catch (openError) {
      preview.close();
      setError(displayError(openError));
    }
  };

  const heading = (title, subtitle, actions = null) => <div className="inv-heading">
    <div><h1>{title}</h1><p>{subtitle}</p></div>
    {actions && <div className="inv-heading-actions">{actions}</div>}
  </div>;

  const errorNotice = error && <div className="inv-alert inv-alert-error" role="alert">{error}</div>;
  const toastNotice = toast && <div className="inv-toast" role="status">{toast}</div>;
  const attachmentField = <div className="inv-field inv-evidence-field">
    <label htmlFor="inventory-evidence">Chứng từ <span className="inv-optional">Không bắt buộc</span></label>
    <input key={evidence?.name || 'empty'} id="inventory-evidence" type="file" accept="image/jpeg,image/png,application/pdf,.jpg,.jpeg,.png,.pdf" onChange={(event) => setEvidence(event.target.files?.[0] || null)} />
    <small>JPG, PNG hoặc PDF · Tối đa 20 MB</small>
    {evidence && <div className="inv-evidence-preview">
      {evidence.type.startsWith('image/') && evidenceUrl
        ? <img src={evidenceUrl} alt={`Xem trước ${evidence.name}`} />
        : <span className="inv-file-mark" aria-hidden="true">PDF</span>}
      <span title={evidence.name}>{evidence.name}</span>
      <button type="button" className="inv-text-button" onClick={() => setEvidence(null)}>Gỡ</button>
    </div>}
  </div>;

  if (screen === 'history') return <main className="inventory-page">
    {heading(`Lịch sử kho · ${selectedItem?.name || 'Mặt hàng'}`, `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`,
      <button type="button" className="inv-button inv-button-primary" onClick={() => setScreen('stock')}>Quay lại tồn kho</button>)}
    {toastNotice}
    {errorNotice}
    <section className="inv-filter-panel inv-history-filters" aria-label="Bộ lọc lịch sử kho">
      <label className="inv-search"><span className="inv-sr-only">Tìm trong lịch sử kho</span><input value={historySearch} onChange={(event) => setHistorySearch(event.target.value)} placeholder="Tìm theo mã, tên hoặc nội dung..." /></label>
      <label className="inv-field"><span>Từ ngày</span><input type="date" value={historyFrom} onChange={(event) => setHistoryFrom(event.target.value)} /></label>
      <label className="inv-field"><span>Đến ngày</span><input type="date" value={historyTo} onChange={(event) => setHistoryTo(event.target.value)} /></label>
      <label className="inv-field"><span>Loại biến động</span><select value={historyKind} onChange={(event) => setHistoryKind(event.target.value)}>
        <option value="ALL">Tất cả biến động</option><option value="RECEIPT">Nhập hàng</option><option value="SALE">Bán hàng</option><option value="ADJUSTMENT">Kiểm kê</option><option value="RETURN">Hoàn hàng</option>
      </select></label>
      <small>Phạm vi: {user.branchName || 'cơ sở được phân quyền'} · {historyLoading ? 'Đang tải lịch sử…' : `${movements.length} bản ghi`}</small>
    </section>
    <section className="inv-table-card" aria-label="Các biến động tồn kho">
      {historyLoading ? <div className="inv-loading-rows" aria-label="Đang tải"><i /><i /><i /></div> : movements.length ? <div className="inv-table-scroll"><table className="inv-table inv-history-table">
        <thead><tr><th>Thời gian</th><th>Nguồn</th><th>Biến động</th><th>Tồn sau</th><th>Người ghi</th></tr></thead>
        <tbody>{movements.map((movement) => <tr key={movement.id}>
          <td className="inv-strong">{dateTimeLabel(movement.occurredAt)}</td>
          <td><strong>{movement.source}</strong>{movement.reason && <small className="inv-cell-note">{movement.reason}</small>}{movement.evidenceUrl && <button type="button" className="inv-text-button inv-evidence-link" onClick={() => openMovementEvidence(movement)}>Xem chứng từ · {movement.evidenceName}</button>}</td>
          <td className={Number(movement.quantityDelta) > 0 ? 'inv-positive' : 'inv-negative'}>{Number(movement.quantityDelta) > 0 ? '+' : ''}{formatQuantity(movement.quantityDelta)} {selectedItem?.unitName || ''}</td>
          <td>{formatQuantity(movement.quantityAfter)} {selectedItem?.unitName || ''}</td>
          <td>{movement.actor}</td>
        </tr>)}</tbody>
      </table></div> : <div className="inv-empty"><strong>Chưa có biến động phù hợp</strong><span>Thử đổi bộ lọc hoặc chọn khoảng thời gian rộng hơn.</span></div>}
    </section>
    <p className="inv-results">Hiển thị {movements.length} kết quả · Tối đa 500 biến động gần nhất</p>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Mọi điều chỉnh đều được lưu lý do và người ghi nhận</p>
  </main>;

  if (screen === 'items') return <main className="inventory-page">
    {heading('Danh mục mặt hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Danh mục mặt hàng kho`,
      <><button type="button" className="inv-button inv-button-quiet" onClick={() => setScreen('stock')}>Tồn kho</button><button type="button" className="inv-button inv-button-primary" onClick={() => openItemForm('create')}>Thêm mặt hàng</button></>)}
    {toastNotice}
    {errorNotice}
    <section className="inv-filter-panel inv-item-filters">
      <label className="inv-search"><span className="inv-sr-only">Tìm mặt hàng</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã hoặc tên mặt hàng..." /></label>
      <label className="inv-field"><span>Trạng thái</span><select value={itemStatus} onChange={(event) => setItemStatus(event.target.value)}>
        <option value="active">Đang sử dụng</option><option value="archived">Ngừng sử dụng</option>
      </select></label>
      <small>{items.length} mặt hàng · Mã hàng duy nhất trong cơ sở</small>
    </section>
    <section className="inv-table-card">
      {loading ? <div className="inv-loading-rows"><i /><i /><i /></div> : <div className="inv-table-scroll"><table className="inv-table inv-items-table">
        <thead><tr><th>Mặt hàng</th><th>Nhóm</th><th>Đơn vị</th><th>Theo dõi tồn</th><th>Tồn hiện tại</th><th>Ngưỡng</th><th>Trạng thái</th><th><span className="inv-sr-only">Thao tác</span></th></tr></thead>
        <tbody>{managedItems.map((item) => <Fragment key={item.id}>
          <tr>
            <td><strong>{item.sku} · {item.name}</strong></td><td>{item.category}</td><td>{item.unitName}</td><td>{trackingLabel(item.trackingMode)}</td>
            <td>{formatQuantity(item.quantity)}</td><td>{formatQuantity(item.minimumQuantity)}</td>
            <td><span className={`inv-status ${stockClass(item)}`}>{stockLabel(item)}</span></td>
            <td className="inv-row-actions"><button type="button" className="inv-text-button" onClick={() => openItemForm('update', item)}>Sửa</button>
              {item.active && <button type="button" className="inv-text-button inv-danger-text" onClick={() => setConfirmDeleteId(item.id)}>Xóa</button>}</td>
          </tr>
          {confirmDeleteId === item.id && <tr><td colSpan="8" className="inv-confirm-cell"><div className="inv-inline-confirm" role="group" aria-label={`Xác nhận xóa ${item.name}`}>
            <span><strong>Ngừng sử dụng mặt hàng này?</strong> Lịch sử kho sẽ được giữ nguyên. Không thể ngừng khi món F&amp;B đang bán còn liên kết.</span>
            <button type="button" className="inv-button inv-button-quiet" onClick={() => setConfirmDeleteId(null)} disabled={busy}>Hủy</button>
            <button type="button" className="inv-button inv-button-danger" onClick={() => deleteItem(item)} disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận xóa'}</button>
          </div></td></tr>}
        </Fragment>)}</tbody>
      </table></div>}
      {!loading && managedItems.length === 0 && <div className="inv-empty"><strong>{search.trim() ? 'Không tìm thấy mặt hàng phù hợp' : itemStatus === 'archived' ? 'Chưa có mặt hàng ngừng sử dụng' : 'Chưa có mặt hàng kho'}</strong><span>{search.trim() ? 'Thử đổi từ khóa tìm kiếm.' : itemStatus === 'archived' ? 'Các mặt hàng đã ngừng sử dụng sẽ xuất hiện tại đây.' : 'Tạo mặt hàng để quản lý tồn kho của cơ sở này.'}</span>{!search.trim() && itemStatus === 'active' && <button type="button" className="inv-button inv-button-primary" onClick={() => openItemForm('create')}>Tạo mặt hàng</button>}</div>}
    </section>
    <p className="inv-footnote">Cập nhật thông tin mặt hàng không làm thay đổi số tồn hoặc lịch sử kho</p>
  </main>;

  if (screen === 'item-form') return <main className="inventory-page">
    {heading(itemMode === 'create' ? 'Tạo mặt hàng kho' : 'Cập nhật mặt hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Quản lý danh mục`,
      <button type="button" className="inv-button inv-button-quiet" onClick={() => setScreen('items')}>Quay lại danh sách</button>)}
    {toastNotice}
    {errorNotice}
    <div className="inv-form-layout">
      <form className="inv-panel inv-item-form" onSubmit={saveItem}>
        <h2>Thông tin mặt hàng</h2>
        <div className="inv-form-grid">
          <label className="inv-field"><span>Mã mặt hàng *</span><input required maxLength="60" value={itemDraft?.sku || ''} onChange={(event) => setItemDraft({ ...itemDraft, sku: event.target.value })} placeholder="INV-019" /></label>
          <label className="inv-field"><span>Tên mặt hàng *</span><input required maxLength="200" value={itemDraft?.name || ''} onChange={(event) => setItemDraft({ ...itemDraft, name: event.target.value })} placeholder="Ví dụ: Nước suối" /></label>
          <label className="inv-field"><span>Nhóm hàng *</span><input required maxLength="100" list="inventory-categories" value={itemDraft?.category || ''} onChange={(event) => setItemDraft({ ...itemDraft, category: event.target.value })} placeholder="Đồ uống" />
            <datalist id="inventory-categories">{metadata.categories.map((name) => <option key={name} value={name} />)}</datalist></label>
          <label className="inv-field"><span>Đơn vị tính *</span><select required value={itemDraft?.unitId || ''} disabled={itemMode === 'update' && items.find((item) => item.id === selectedId)?.hasMovements} onChange={(event) => setItemDraft({ ...itemDraft, unitId: event.target.value })}>
            <option value="">Chọn đơn vị</option>{metadata.units.map((unit) => <option key={unit.id} value={unit.id}>{unit.name}</option>)}
          </select>{itemMode === 'update' && items.find((item) => item.id === selectedId)?.hasMovements && <small>Đơn vị được giữ nguyên để bảo toàn cách đọc lịch sử kho.</small>}</label>
          <label className="inv-field"><span>Cách theo dõi tồn *</span><select required value={itemDraft?.trackingMode || 'AUTO_ON_SALE'} onChange={(event) => setItemDraft({ ...itemDraft, trackingMode: event.target.value })}>
            <option value="AUTO_ON_SALE">Tự trừ khi bán</option><option value="MANUAL">Theo dõi thủ công</option>
          </select></label>
          <label className="inv-field"><span>Ngưỡng sắp hết *</span><div className="inv-number-with-unit"><input required type="number" min="0" step="0.001" value={itemDraft?.minimumQuantity ?? ''} onChange={(event) => setItemDraft({ ...itemDraft, minimumQuantity: event.target.value })} />{items.find((item) => item.id === selectedId)?.unitName || metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || 'đơn vị'}</div></label>
          {itemMode === 'update' && <label className="inv-check-field"><input type="checkbox" checked={Boolean(itemDraft?.active)} onChange={(event) => setItemDraft({ ...itemDraft, active: event.target.checked })} /><span>Đang sử dụng</span></label>}
        </div>
        <div className="inv-alert inv-alert-info">{itemMode === 'create' ? 'Mặt hàng mới bắt đầu với tồn kho 0. Dùng phiếu nhập để ghi nhận hàng nhận thực tế.' : 'Lưu thông tin mặt hàng không thay đổi số tồn hoặc các biến động đã ghi nhận.'}</div>
        <div className="inv-form-actions"><button type="button" className="inv-button inv-button-quiet" onClick={() => setScreen('items')} disabled={busy}>Hủy</button><button type="submit" className="inv-button inv-button-primary" disabled={busy}>{busy ? 'Đang lưu…' : itemMode === 'create' ? 'Tạo mặt hàng' : 'Lưu thay đổi'}</button></div>
      </form>
      <aside className="inv-summary inv-item-summary"><h2>Tóm tắt mặt hàng</h2>
        <p><strong>{itemDraft?.name || 'Tên mặt hàng'}</strong><span>{itemDraft?.sku || 'Mã mặt hàng'} · {metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || 'Đơn vị'}</span></p>
        <dl><div><dt>Nhóm</dt><dd>{itemDraft?.category || 'Chưa chọn'}</dd></div><div><dt>Theo dõi tồn</dt><dd>{trackingLabel(itemDraft?.trackingMode)}</dd></div>
          <div><dt>Tồn hiện tại</dt><dd>{itemMode === 'update' ? formatQuantity(items.find((item) => item.id === selectedId)?.quantity) : '0'}</dd></div>
          <div><dt>Ngưỡng sắp hết</dt><dd>{formatQuantity(itemDraft?.minimumQuantity)}</dd></div></dl>
        <p className="inv-summary-note">Không ghi nhận nhập hàng hoặc điều chỉnh tồn tại màn hình này.</p>
      </aside>
    </div>
    <p className="inv-footnote">Dữ liệu mặt hàng chỉ thuộc cơ sở đang được phân quyền</p>
  </main>;

  if (screen === 'receipt') return <main className="inventory-page">
    {heading('Ghi nhận nhập hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`)}
    {toastNotice}
    {errorNotice}
    <div className="inv-form-layout inv-receipt-layout">
      <form id="inventory-receipt-form" className="inv-panel inv-receipt-form" onSubmit={submitReceipt}>
        <h2>Thông tin phiếu nhập</h2>
        <div className="inv-form-grid">
          <label className="inv-field"><span>Ngày nhận *</span><input required type="datetime-local" value={receiptDraft?.receivedAt || ''} onChange={(event) => setReceiptDraft({ ...receiptDraft, receivedAt: event.target.value })} /></label>
          <label className="inv-field"><span>Nhà cung cấp *</span><input required maxLength="200" value={receiptDraft?.supplierName || ''} onChange={(event) => setReceiptDraft({ ...receiptDraft, supplierName: event.target.value })} placeholder="Tên nhà cung cấp" /></label>
          <label className="inv-field"><span>Tham chiếu mua hàng</span><input maxLength="200" value={receiptDraft?.supplierInvoice || ''} onChange={(event) => setReceiptDraft({ ...receiptDraft, supplierInvoice: event.target.value })} placeholder="Số đơn hoặc chứng từ" /></label>
          {attachmentField}
        </div>
        <div className="inv-line-items">
          <div className="inv-line-heading"><h3>Mặt hàng nhận</h3><span>{receiptLines.length} dòng</span></div>
          <div className="inv-receipt-lines">
            {receiptLines.map((line) => {
              const item = items.find((row) => row.id === line.inventoryItemId);
              return <div className="inv-receipt-line" key={line.id}>
                <label className="inv-field inv-line-product"><span>Mặt hàng *</span><select required value={line.inventoryItemId} onChange={(event) => updateReceiptLine(line.id, 'inventoryItemId', event.target.value)}>
                  <option value="">Chọn mặt hàng</option>{activeItems.filter((option) => !receiptLines.some((other) => other.id !== line.id && other.inventoryItemId === option.id)).map((option) => <option key={option.id} value={option.id}>{option.sku} · {option.name}</option>)}
                </select><small>{item ? `${item.sku} · ${item.unitName}` : 'Chọn mặt hàng kho đang sử dụng'}</small></label>
                <label className="inv-field"><span>SL nhận *</span><input required type="number" min="0.001" step="0.001" value={line.quantity} onChange={(event) => updateReceiptLine(line.id, 'quantity', event.target.value)} /></label>
                <label className="inv-field"><span>Đơn giá *</span><input required type="number" min="0" step="1" value={line.unitCost} onChange={(event) => updateReceiptLine(line.id, 'unitCost', event.target.value)} /></label>
                <div className="inv-line-total"><span>Thành tiền</span><strong>{formatVnd(Number(line.quantity || 0) * Number(line.unitCost || 0))}</strong></div>
                <button type="button" className="inv-remove-line" aria-label={`Bỏ dòng ${item?.name || 'mặt hàng'}`} disabled={receiptLines.length === 1} onClick={() => removeReceiptLine(line.id)}>Bỏ</button>
              </div>;
            })}
          </div>
          <button type="button" className="inv-button inv-button-wide inv-button-quiet" onClick={addReceiptLine} disabled={receiptLines.length >= activeItems.length}>+ Thêm mặt hàng</button>
        </div>
        <div className="inv-alert inv-alert-info">Chỉ ghi nhận hàng đã nhận thực tế. Không tự thanh toán nhà cung cấp khi nhập kho.</div>
      </form>
      <aside className="inv-summary inv-action-summary"><h2>Tóm tắt nhập kho</h2>
        <p>{receiptSummary.length} mặt hàng · {formatQuantity(receiptTotalQuantity)} đơn vị</p>
        <ul className="inv-summary-lines">{receiptSummary.map((line) => <li key={line.id}><span>{line.item.name}: {formatQuantity(line.quantity)} × {formatVnd(line.unitCost)}</span><strong>{formatVnd(line.amount)}</strong><small>Tồn sau: {formatQuantity(Number(line.item.quantity) + line.quantity)} {line.item.unitName}</small></li>)}</ul>
        <div className="inv-summary-total"><span>Tổng giá trị</span><strong>{formatVnd(receiptTotalValue)}</strong></div>
        <div className="inv-alert inv-alert-info">Xác nhận phiếu mới tăng số tồn. Chi phí được xử lý qua đề nghị riêng.</div>
        <div className="inv-summary-actions"><button type="submit" form="inventory-receipt-form" className="inv-button inv-button-primary" disabled={busy || !receiptLines.length}>{busy ? 'Đang ghi nhận…' : 'Xác nhận nhập kho'}</button>
          <button type="button" className="inv-button inv-button-quiet" onClick={() => { setEvidence(null); setScreen('stock'); }}>Quay lại tồn kho</button></div>
      </aside>
    </div>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Xác nhận nhập kho cập nhật ledger tồn kho</p>
  </main>;

  if (screen === 'adjustment') return <main className="inventory-page">
    {heading('Kiểm kê & điều chỉnh tồn', `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`)}
    {toastNotice}
    {errorNotice}
    <div className="inv-form-layout inv-adjust-layout">
      <form id="inventory-adjustment-form" className="inv-panel inv-adjust-form" onSubmit={submitAdjustment}>
        <h2>Thông tin ghi nhận</h2>
        <div className="inv-form-grid">
          <label className="inv-field"><span>Thời điểm kiểm kê *</span><input required type="datetime-local" value={adjustDraft?.adjustedAt || ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, adjustedAt: event.target.value })} /></label>
          <label className="inv-field"><span>Người thực hiện</span><input value={user.fullName || user.username || 'Quản lý cơ sở'} disabled /></label>
          <label className="inv-field"><span>Mặt hàng *</span><select required value={adjustDraft?.inventoryItemId || ''} onChange={(event) => selectAdjustmentItem(event.target.value)}>
            <option value="">Chọn mặt hàng</option>{activeItems.map((item) => <option key={item.id} value={item.id}>{item.sku} · {item.name}</option>)}
          </select></label>
          <label className="inv-field"><span>Đơn vị</span><input value={adjustItem?.unitName || ''} disabled /></label>
          <label className="inv-field"><span>Tồn hệ thống</span><input value={formatQuantity(adjustItem?.quantity)} disabled /></label>
          <label className="inv-field"><span>Tồn thực tế *</span><input required type="number" min="0" step="0.001" value={adjustDraft?.actualQuantity ?? ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, actualQuantity: event.target.value })} /></label>
          <label className="inv-field"><span>Lý do *</span><textarea required maxLength="1000" rows="3" value={adjustDraft?.reason || ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, reason: event.target.value })} placeholder="Giải thích chênh lệch tồn kho" /></label>
          {attachmentField}
        </div>
        {staleStock && <div className={`inv-alert ${staleStock.reviewed ? 'inv-alert-info' : 'inv-alert-warning'}`} role="status">
          <span>Tồn kho đã thay đổi từ {formatQuantity(staleStock.previous)} thành {formatQuantity(staleStock.latest)} {adjustItem?.unitName}. Kiểm tra lại số chênh lệch trước khi xác nhận.</span>
          {!staleStock.reviewed && <button type="button" className="inv-button inv-button-quiet" onClick={acknowledgeLatestStock}>Đã kiểm tra tồn mới</button>}
        </div>}
      </form>
      <aside className="inv-summary inv-action-summary"><h2>Tóm tắt &amp; xử lý</h2>
        <p>{adjustItem?.name || 'Chọn mặt hàng'} · Đơn vị {adjustItem?.unitName || '—'}</p>
        <dl className="inv-adjust-summary"><div><dt>Hệ thống</dt><dd>{formatQuantity(adjustItem?.quantity)}</dd></div><div><dt>Thực tế</dt><dd>{formatQuantity(adjustDraft?.actualQuantity)}</dd></div>
          <div><dt>Điều chỉnh</dt><dd className={adjustDelta >= 0 ? 'inv-positive' : 'inv-negative'}>{adjustDelta > 0 ? '+' : ''}{formatQuantity(adjustDelta)} {adjustItem?.unitName || ''}</dd></div>
          <div><dt>Tồn mới</dt><dd>{formatQuantity(adjustDraft?.actualQuantity)} {adjustItem?.unitName || ''}</dd></div><div><dt>Lý do</dt><dd>{adjustDraft?.reason || 'Chưa nhập'}</dd></div></dl>
        <div className="inv-alert inv-alert-warning">Không nhập số tồn thực tế âm. Nếu tồn thay đổi trước khi xác nhận, cần kiểm tra lại.</div>
        <div className="inv-summary-actions"><button type="submit" form="inventory-adjustment-form" className="inv-button inv-button-primary" disabled={busy || !adjustItem || !adjustDraft?.reason.trim() || adjustDelta === 0 || Boolean(staleStock && !staleStock.reviewed)}>{busy ? 'Đang xác nhận…' : 'Xác nhận điều chỉnh'}</button>
          <button type="button" className="inv-button inv-button-quiet" onClick={() => { setEvidence(null); setScreen('stock'); }}>Quay lại danh sách</button></div>
      </aside>
    </div>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Điều chỉnh lưu người ghi, lý do và số tồn trước/sau</p>
  </main>;

  return <main className="inventory-page">
    {heading('Tồn kho', `Cơ sở ${user.branchName || 'được phân quyền'} · Theo dõi số lượng và trạng thái mặt hàng`,
      <><button type="button" className="inv-button inv-button-manage" onClick={() => { setError(''); setScreen('items'); }}>Danh mục mặt hàng</button><button type="button" className="inv-button inv-button-quiet" onClick={openAdjustment} disabled={!activeItems.length} title={!activeItems.length ? 'Tạo mặt hàng trước khi kiểm kê' : undefined}>Kiểm kê &amp; điều chỉnh</button><button type="button" className="inv-button inv-button-primary" onClick={openReceipt} disabled={!activeItems.length} title={!activeItems.length ? 'Tạo mặt hàng trước khi nhập kho' : undefined}>Ghi nhận nhập hàng</button></>)}
    {errorNotice}
    {toastNotice}
    <section className="inv-filter-panel" aria-label="Tìm và lọc tồn kho">
      <label className="inv-search"><span className="inv-sr-only">Tìm mặt hàng</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã hoặc tên mặt hàng..." /></label>
      <label className="inv-field"><span>Nhóm hàng</span><select value={category} onChange={(event) => setCategory(event.target.value)}><option value="all">Tất cả nhóm</option>{metadata.categories.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
      <label className="inv-field"><span>Tình trạng tồn</span><select value={stockStatus} onChange={(event) => setStockStatus(event.target.value)}>
        <option value="all">Tình trạng tồn</option><option value="AVAILABLE">Còn hàng</option><option value="LOW_STOCK">Sắp hết</option><option value="OUT_OF_STOCK">Hết hàng</option>
      </select></label>
      <small>Phạm vi: {user.branchName || 'cơ sở được phân quyền'} · {loading ? 'Đang tải dữ liệu…' : `${visibleItems.length} mặt hàng`}</small>
    </section>
    <section className="inv-table-card" aria-label="Tồn kho hiện tại">
      {loading ? <div className="inv-loading-rows" aria-label="Đang tải"><i /><i /><i /><i /></div> : visibleItems.length ? <div className="inv-table-scroll"><table className="inv-table inv-stock-table">
        <thead><tr><th>Mặt hàng</th><th>Đơn vị</th><th>Tồn hiện tại</th><th>Ngưỡng</th><th>Trạng thái</th></tr></thead>
        <tbody>{visibleItems.map((item) => <tr key={item.id}>
          <td><button type="button" className="inv-item-link" aria-label={`Xem lịch sử biến động của ${item.name}`} onClick={() => openHistory(item)}><strong>{item.sku} · {item.name}</strong><span>{item.category} · {trackingLabel(item.trackingMode)}</span><span className="inv-item-action">Xem lịch sử</span></button></td>
          <td>{item.unitName}</td><td className="inv-quantity-cell">{formatQuantity(item.quantity)}</td><td>{formatQuantity(item.minimumQuantity)}</td><td><span className={`inv-status ${stockClass(item)}`}>{stockLabel(item)}</span></td>
        </tr>)}</tbody>
      </table></div> : <div className="inv-empty"><strong>{activeItems.length ? 'Không tìm thấy mặt hàng phù hợp' : 'Chưa có mặt hàng kho'}</strong><span>{activeItems.length ? 'Thử thay đổi từ khóa hoặc bộ lọc.' : 'Tạo danh mục mặt hàng trước khi ghi nhận nhập kho.'}</span>{!activeItems.length && <button type="button" className="inv-button inv-button-primary" onClick={() => openItemForm('create')}>Tạo mặt hàng</button>}</div>}
    </section>
    <p className="inv-results">Hiển thị {visibleItems.length} kết quả · Chọn mặt hàng để xem lịch sử biến động</p>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Mặt hàng tự trừ tồn khi bán dùng chung quy tắc AUTO_ON_SALE</p>
  </main>;
}
