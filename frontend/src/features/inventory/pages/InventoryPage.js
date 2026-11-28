import { formatDateLabel } from '../../../shared/utils/dateLabel';
import { PaginatedTable } from '../../../shared/components/Pagination';
import { ToastStack } from '../../../shared/components/Feedback';
import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import MoneyInput from '../../../shared/components/MoneyInput';
import inventoryApi from '../services/inventoryApi';
import menuItemApi from '../services/menuItemApi';
import { newReceiptLine, RECEIVING_UNITS, receiptAmount, receiptQuantity, receivingUnitName } from '../utils/receiptQuantity';
import './inventory.css';

const formatQuantity = (value) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(Number(value || 0));
const formatVnd = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(typeof value === 'bigint' ? value : Number(value || 0))}₫`;
const displayError = (error) => error.response?.data?.message || error.response?.data?.detail || error.message || 'Không thể hoàn tất thao tác.';
const makeLocalDateTime = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
const dateTimeLabel = (value) => value ? new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : '—';
const dateLabel = formatDateLabel;
const normalizeInventorySearch = (value) => value.toLocaleLowerCase('vi-VN').normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
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

function InventoryItemPicker({ items, categories, unavailableIds = [], onSelect, onClose }) {
  const dialogRef = useRef(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');
  const [status, setStatus] = useState('all');
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog.open) dialog.showModal();
  }, []);
  const term = query.trim().toLocaleLowerCase('vi-VN');
  const visible = items.filter((item) => !unavailableIds.includes(item.id)
    && (category === 'all' || item.category.toLocaleLowerCase('vi-VN') === category.toLocaleLowerCase('vi-VN'))
    && (status === 'all' || item.stockStatus === status)
    && (!term || `${item.sku} ${item.name} ${item.category}`.toLocaleLowerCase('vi-VN').includes(term)));
  return <dialog ref={dialogRef} className="inv-item-picker" aria-labelledby="inv-picker-title" onClose={onClose}>
    <div className="inv-picker-heading"><div><h2 id="inv-picker-title">Chọn mặt hàng kho</h2><p>Tìm theo mã hoặc tên, rồi chọn một mặt hàng trong danh sách.</p></div>
      <button type="button" className="inv-button inv-button-quiet" onClick={() => dialogRef.current?.close()}>Đóng</button></div>
    <search className="inv-picker-filters">
      <label className="inv-field"><span>Tìm mặt hàng</span><input autoFocus type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Mã hoặc tên mặt hàng…" /></label>
      <label className="inv-field"><span>Nhóm hàng</span><select value={category} onChange={(event) => setCategory(event.target.value)}><option value="all">Tất cả nhóm</option>{categories.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
      <label className="inv-field"><span>Tình trạng tồn</span><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="all">Tất cả</option><option value="AVAILABLE">Còn hàng</option><option value="LOW_STOCK">Sắp hết</option><option value="OUT_OF_STOCK">Hết hàng</option></select></label>
    </search>
    <p className="inv-picker-count" role="status">{visible.length} mặt hàng phù hợp</p>
    {visible.length ? <ul className="inv-picker-list">{visible.map((item) => <li key={item.id}><button type="button" onClick={() => { onSelect(item.id); dialogRef.current?.close(); }}>
      <span><strong>{item.sku} · {item.name}</strong><small>{item.category} · {formatQuantity(item.quantity)} {item.unitName}</small></span>
      <span className={`inv-status ${stockClass(item)}`}>{stockLabel(item)}</span>
    </button></li>)}</ul> : <p className="inv-picker-empty">Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</p>}
  </dialog>;
}

export default function InventoryPage({ user, recentReceipt, onReceiptConfirmed, onAddToMenu }) {
  const pageRef = useRef(null);
  const itemPriceRef = useRef(null);
  const receiptPriceRef = useRef(null);
  const [items, setItems] = useState([]);
  const [metadata, setMetadata] = useState({ units: [], categories: [] });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [screen, setScreen] = useState('stock');
  // Each inventory screen starts at its heading, even after scrolling a long list.
  useLayoutEffect(() => {
    pageRef.current?.scrollTo(0, 0);
  }, [screen]);
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
  const [receiptItemDraft, setReceiptItemDraft] = useState(null);
  const [addItemToMenu, setAddItemToMenu] = useState(false);
  const [menuSalePrice, setMenuSalePrice] = useState('');
  const [unlinkedMenuItem, setUnlinkedMenuItem] = useState(null);
  const [pickerTarget, setPickerTarget] = useState(null);
  const [adjustDraft, setAdjustDraft] = useState(null);
  const [adjustItem, setAdjustItem] = useState(null);
  const [adjustLoading, setAdjustLoading] = useState(false);
  const [adjustLoadAttempt, setAdjustLoadAttempt] = useState(0);
  const [staleStock, setStaleStock] = useState(null);
  const [evidence, setEvidence] = useState(null);
  const [evidenceUrl, setEvidenceUrl] = useState('');
  const adjustmentItemId = adjustDraft?.inventoryItemId || '';

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

  useEffect(() => {
    if (screen !== 'adjustment' || !adjustmentItemId) return undefined;
    let current = true;
    setAdjustLoading(true);
    setAdjustItem(null);
    setError('');
    // BR-190: lấy tồn hiện tại của mặt hàng, không dùng lại dữ liệu đã tải cho danh sách/lịch sử.
    inventoryApi.item(adjustmentItemId)
      .then((item) => {
        if (!current) return;
        setItems((rows) => rows.map((row) => row.id === item.id ? item : row));
        if (!item.active) {
          setError('Mặt hàng đã ngừng sử dụng nên không thể điều chỉnh tồn.');
          return;
        }
        setAdjustItem(item);
        setStaleStock((previous) => previous ? { ...previous, latest: item.quantity, reviewed: false } : null);
      })
      .catch((loadError) => { if (current) setError(displayError(loadError)); })
      .finally(() => { if (current) setAdjustLoading(false); });
    return () => { current = false; };
  }, [screen, adjustmentItemId, adjustLoadAttempt]);

  const activeItems = useMemo(() => items.filter((item) => item.active), [items]);
  const visibleItems = useMemo(() => {
    const terms = normalizeInventorySearch(search).split(/\s+/).filter(Boolean);
    return items.filter((item) => {
      const searchableText = terms.length ? normalizeInventorySearch([item.sku, item.name, item.category, item.unitName].join(' ')) : '';
      const matchesSearch = terms.every((term) => searchableText.includes(term));
      const matchesCategory = category === 'all' || item.category.toLocaleLowerCase('vi-VN') === category.toLocaleLowerCase('vi-VN');
      const matchesStatus = stockStatus === 'all' || item.stockStatus === stockStatus;
      const matchesUsage = itemStatus === 'all' || (itemStatus === 'archived' ? !item.active : item.active);
      return matchesSearch && matchesCategory && matchesStatus && matchesUsage;
    });
  }, [items, search, category, stockStatus, itemStatus]);
  const selectedItem = items.find((item) => item.id === selectedId) || null;
  const receiptLines = useMemo(() => receiptDraft?.items || EMPTY_LINES, [receiptDraft]);
  const receiptSummary = useMemo(() => receiptLines.map((line) => {
    const item = items.find((row) => row.id === line.inventoryItemId);
    const conversion = receiptQuantity(line);
    const quantity = Number(conversion.value || 0);
    const unitCost = Number(line.unitCost || 0);
    // BR-33: đơn giá theo đơn vị tồn kho, thành tiền theo số lượng đã quy đổi.
    return { ...line, item, receivedQuantity: line.quantity, quantity, unitCost, conversion, amount: receiptAmount(conversion.value || '0', line.unitCost) ?? 0n };
  }).filter((line) => line.item), [receiptLines, items]);
  const receiptTotalValue = receiptSummary.reduce((total, line) => total + line.amount, 0n);
  const actualQuantityInput = String(adjustDraft?.actualQuantity ?? '').trim();
  const adjustActual = actualQuantityInput === '' ? null : Number(actualQuantityInput);
  // BR-35, BR-189: chỉ tính chênh lệch sau khi nhập số kiểm đếm hợp lệ; để trống không phải là 0.
  const validActualQuantity = adjustActual !== null && Number.isFinite(adjustActual) && adjustActual >= 0;
  const actualQuantityError = actualQuantityInput !== '' && !validActualQuantity
    ? 'Tồn thực tế phải lớn hơn hoặc bằng 0.' : '';
  const adjustDelta = adjustItem && validActualQuantity ? adjustActual - Number(adjustItem.quantity) : null;
  const canSubmitAdjustment = !busy && !adjustLoading && adjustItem?.id === adjustmentItemId
    && validActualQuantity && Boolean(adjustDraft?.reason.trim()) && adjustDelta !== null && adjustDelta !== 0
    && !Boolean(staleStock && !staleStock.reviewed);
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
    setAddItemToMenu(false);
    setMenuSalePrice('');
    setItemMode(mode);
    setItemDraft(item ? {
      sku: item.sku, name: item.name,
      category: metadata.categories.find((name) => name.toLocaleLowerCase('vi-VN') === item.category.toLocaleLowerCase('vi-VN')) || item.category,
      unitId: item.unitId,
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
    setPickerTarget(null);
    setReceiptDraft({ receivedAt: makeLocalDateTime(), supplierName: '', supplierInvoice: '', items: [newReceiptLine(newId())] });
    setReceiptItemDraft(null);
    setEvidence(null);
    setError('');
    setScreen('receipt');
  };

  const openAdjustment = () => {
    setPickerTarget(null);
    const first = activeItems[0] || null;
    setAdjustItem(null);
    setAdjustLoading(Boolean(first));
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
    // BR-111, BR-198: món F&B mới cần giá bán nguyên đồng không âm.
    if (itemMode === 'create' && addItemToMenu && !/^\d{1,18}$/.test(menuSalePrice)) {
      setError('Giá bán phải là số nguyên VND không âm và tối đa 18 chữ số.');
      itemPriceRef.current?.focus();
      return;
    }
    setBusy(true);
    setError('');
    try {
      const command = {
        ...itemDraft,
        minimumQuantity: Number(itemDraft.minimumQuantity),
      };
      let menuResult = null;
      if (itemMode === 'create') {
        if (addItemToMenu) menuResult = await inventoryApi.createItemWithMenu(command, menuSalePrice);
        else await inventoryApi.createItem(command);
      } else await inventoryApi.updateItem(selectedId, command);
      await reload();
      if (menuResult) setItems((current) => current.some((item) => item.id === menuResult.item.id)
        ? current : [...current, menuResult.item]);
      if (menuResult?.menuError) setUnlinkedMenuItem({ item: menuResult.item, error: displayError(menuResult.menuError) });
      else setUnlinkedMenuItem(null);
      setToastMessage(menuResult?.menuError ? 'Đã tạo mặt hàng kho. Món F&B chưa được tạo.'
        : itemMode === 'create' ? addItemToMenu ? 'Đã tạo mặt hàng kho và món F&B.' : 'Đã tạo mặt hàng kho.' : 'Đã cập nhật thông tin mặt hàng.');
      setScreen('stock');
    } catch (saveError) {
      setError(displayError(saveError));
    } finally {
      setBusy(false);
    }
  };

  const saveReceiptItem = async () => {
    if (!receiptItemDraft || busy) return;
    // BR-111, MSG02: các trường bắt buộc không được để trống hoặc chỉ có khoảng trắng.
    if (!receiptItemDraft.name.trim()) { setError('Vui lòng bổ sung tên mặt hàng.'); return; }
    if (!receiptItemDraft.category.trim()) { setError('Vui lòng bổ sung nhóm hàng.'); return; }
    if (!receiptItemDraft.unitId) { setError('Vui lòng bổ sung đơn vị tồn kho.'); return; }
    // BR-111, BR-198: áp dụng cùng kiểm tra giá bán cho luồng tạo nhanh trong phiếu nhập.
    if (receiptItemDraft.addToMenu && !/^\d{1,18}$/.test(receiptItemDraft.salePrice)) {
      setError('Giá bán phải là số nguyên VND không âm và tối đa 18 chữ số.');
      receiptPriceRef.current?.focus();
      return;
    }
    setBusy(true);
    setError('');
    try {
      const command = {
        sku: '', name: receiptItemDraft.name.trim(), category: receiptItemDraft.category.trim(),
        unitId: receiptItemDraft.unitId, trackingMode: receiptItemDraft.trackingMode,
        minimumQuantity: Number(receiptItemDraft.minimumQuantity || 0), active: true,
      };
      const menuResult = receiptItemDraft.addToMenu
        ? await inventoryApi.createItemWithMenu(command, receiptItemDraft.salePrice)
        : null;
      const item = menuResult?.item || await inventoryApi.createItem(command);
      setItems((current) => [...current, item]);
      setMetadata((current) => ({
        ...current,
        categories: current.categories.includes(item.category)
          ? current.categories : [...current.categories, item.category].sort((left, right) => left.localeCompare(right, 'vi')),
      }));
      setReceiptDraft((draft) => {
        const emptyIndex = draft.items.findIndex((line) => !line.inventoryItemId);
        if (emptyIndex !== -1) return {
          ...draft,
          items: draft.items.map((line, index) => index === emptyIndex
            ? { ...newReceiptLine(line.id), inventoryItemId: item.id } : line),
        };
        return { ...draft, items: [...draft.items, { ...newReceiptLine(newId()), inventoryItemId: item.id }] };
      });
      setReceiptItemDraft(null);
      // MSG10: thông báo tạo mặt hàng thành công.
      if (menuResult?.menuError) setUnlinkedMenuItem({ item, error: displayError(menuResult.menuError) });
      else setUnlinkedMenuItem(null);
      setToastMessage(menuResult?.menuError ? `Đã tạo mặt hàng ${item.name}. Món F&B chưa được tạo.`
        : receiptItemDraft.addToMenu ? `Đã tạo mặt hàng ${item.name} và món F&B.` : `Đã tạo mặt hàng ${item.name}.`);
      window.requestAnimationFrame(() => {
        pageRef.current?.querySelector(`[data-receipt-unit-for="${item.id}"]`)?.focus({ preventScroll: true });
      });
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
    setReceiptDraft((draft) => ({ ...draft, items: draft.items.map((line) => {
      if (line.id !== id) return line;
      // Đổi mặt hàng phải nhập lại quy cách và giá, tránh dùng nhầm quy cách của mặt hàng trước.
      if (key === 'inventoryItemId') return { ...newReceiptLine(id), inventoryItemId: value };
      if (key === 'receivingUnit') return { ...line, receivingUnit: value, unitsPerPack: '', customUnit: '' };
      return { ...line, [key]: value };
    }) }));
  };

  const addReceiptLine = () => setReceiptDraft((draft) => ({
    ...draft, items: [...draft.items, newReceiptLine(newId())],
  }));

  const removeReceiptLine = (id) => setReceiptDraft((draft) => ({
    ...draft, items: draft.items.length > 1 ? draft.items.filter((line) => line.id !== id) : draft.items,
  }));

  const submitReceipt = async (event) => {
    event.preventDefault();
    // BR-111, MSG02: từ chối tên nhà cung cấp chỉ chứa khoảng trắng.
    if (!receiptDraft.supplierName.trim()) {
      setError('Vui lòng bổ sung nhà cung cấp.');
      return;
    }
    const missingItemLine = receiptDraft.items.findIndex((line) => !line.inventoryItemId);
    if (missingItemLine !== -1) {
      setError(`Dòng ${missingItemLine + 1}: Vui lòng chọn mặt hàng.`);
      return;
    }
    const invalidLine = receiptDraft.items.findIndex((line) => receiptQuantity(line).error);
    if (invalidLine !== -1) {
      setError(`Dòng ${invalidLine + 1}: ${receiptQuantity(receiptDraft.items[invalidLine]).error}`);
      return;
    }
    const invalidPriceLine = receiptDraft.items.findIndex((line) => receiptAmount(receiptQuantity(line).value, line.unitCost) === null);
    if (invalidPriceLine !== -1) {
      setError(`Dòng ${invalidPriceLine + 1}: Đơn giá phải là số nguyên VND không âm, tối đa 18 chữ số.`);
      return;
    }
    const receivedDate = receiptDraft.receivedAt.slice(0, 10);
    const invalidExpiryLine = receiptDraft.items.findIndex((line) => !line.expiryDate || line.expiryDate < receivedDate);
    if (invalidExpiryLine !== -1) {
      setError(`Dòng ${invalidExpiryLine + 1}: ${receiptDraft.items[invalidExpiryLine].expiryDate ? 'Hạn sử dụng không được trước ngày nhận hàng.' : 'Vui lòng bổ sung hạn sử dụng.'}`);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const command = {
        receivedAt: new Date(receiptDraft.receivedAt).toISOString(),
        supplierName: receiptDraft.supplierName,
        supplierInvoice: receiptDraft.supplierInvoice,
        // BR-32: API nhận số lượng theo đơn vị tồn kho; không cộng số thùng/lốc trực tiếp vào kho.
        items: receiptDraft.items.map((line) => ({
          inventoryItemId: line.inventoryItemId, quantity: receiptQuantity(line).value, unitCost: line.unitCost, expiryDate: line.expiryDate,
        })),
      };
      const receipt = await inventoryApi.receive(command, evidence);
      await reload();
      const menuItems = await menuItemApi.list().catch(() => null);
      onReceiptConfirmed({ ...receipt, linkedInventoryIds: menuItems?.map((item) => item.inventoryItemId).filter(Boolean) || [] });
      setEvidence(null);
      setScreen('stock');
      // MSG10: thông báo tạo phiếu nhập thành công.
      setToastMessage('Đã tạo phiếu nhập.');
    } catch (submitError) {
      setError(displayError(submitError));
    } finally {
      setBusy(false);
    }
  };

  const selectAdjustmentItem = (id) => {
    setAdjustItem(null);
    setAdjustLoading(Boolean(id));
    setAdjustDraft((draft) => ({ ...draft, inventoryItemId: id, actualQuantity: '' }));
    setStaleStock(null);
    setError('');
  };

  const acknowledgeLatestStock = () => setStaleStock((current) => current ? { ...current, reviewed: true } : null);

  const submitAdjustment = async (event) => {
    event.preventDefault();
    if (!canSubmitAdjustment) return;
    setBusy(true);
    setError('');
    try {
      const command = {
        inventoryItemId: adjustItem.id,
        occurredAt: new Date(adjustDraft.adjustedAt).toISOString(),
        expectedStockVersion: adjustItem.stockVersion,
        expectedQuantity: Number(adjustItem.quantity),
        actualQuantity: adjustActual,
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
        setStaleStock({ previous: adjustItem.quantity, latest: null, reviewed: false });
        setAdjustItem(null);
        try {
          const latest = await inventoryApi.item(adjustItem.id);
          setItems((rows) => rows.map((row) => row.id === latest.id ? latest : row));
          if (!latest.active) {
            setError('Mặt hàng đã ngừng sử dụng nên không thể điều chỉnh tồn.');
            return;
          }
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
    <div><h1>{title}</h1></div>
    {actions && <div className="inv-heading-actions">{actions}</div>}
  </div>;

  const feedbackNotice = <ToastStack error={error} notice={toast} onCloseError={() => setError('')} onCloseNotice={() => setToast('')} />;
  const menuLinkNotice = unlinkedMenuItem && <div className="inv-menu-link-notice" role="alert">
    <div><strong>{unlinkedMenuItem.item.name} đã được tạo trong kho, nhưng chưa có trong F&amp;B.</strong>
      <p>{unlinkedMenuItem.error} Chọn “Thêm vào F&amp;B” để hoàn tất mà không tạo trùng mặt hàng kho.</p></div>
    <button type="button" className="inv-button inv-button-quiet" onClick={() => {
      onAddToMenu(unlinkedMenuItem.item.id);
      setUnlinkedMenuItem(null);
    }}>Thêm vào F&amp;B</button>
  </div>;
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

  if (screen === 'history') return <main ref={pageRef} className="inventory-page">
    {heading(`Lịch sử kho · ${selectedItem?.name || 'Mặt hàng'}`, `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`,
      <button type="button" className="inv-button inv-button-primary" onClick={() => setScreen('stock')}>Quay lại kho hàng</button>)}
    {feedbackNotice}
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
      {historyLoading ? <div className="inv-loading-rows" aria-label="Đang tải"><i /><i /><i /></div> : movements.length ? <div className="inv-table-scroll"><PaginatedTable pageSize={5} className="inv-table inv-history-table">
        <thead><tr><th>Thời gian</th><th>Nguồn</th><th>Nhà cung cấp</th><th>Hạn sử dụng</th><th>Biến động</th><th>Tồn sau</th><th>Người ghi</th></tr></thead>
        <tbody>{movements.map((movement) => <tr key={movement.id}>
          <td className="inv-strong">{dateTimeLabel(movement.occurredAt)}</td>
          <td><strong>{movement.source}</strong>{movement.reason && <small className="inv-cell-note">{movement.reason}</small>}{movement.evidenceUrl && <button type="button" className="inv-text-button inv-evidence-link" onClick={() => openMovementEvidence(movement)}>Xem chứng từ · {movement.evidenceName}</button>}</td>
          <td>{movement.supplierName || '—'}</td>
          <td>{dateLabel(movement.expiryDate)}</td>
          <td className={Number(movement.quantityDelta) > 0 ? 'inv-positive' : 'inv-negative'}>{Number(movement.quantityDelta) > 0 ? '+' : ''}{formatQuantity(movement.quantityDelta)} {selectedItem?.unitName || ''}</td>
          <td>{formatQuantity(movement.quantityAfter)} {selectedItem?.unitName || ''}</td>
          <td>{movement.actor}</td>
        </tr>)}</tbody>
      </PaginatedTable></div> : <div className="inv-empty"><strong>Chưa có biến động phù hợp</strong><span>Thử đổi bộ lọc hoặc chọn khoảng thời gian rộng hơn.</span></div>}
    </section>
    <p className="inv-results">Hiển thị {movements.length} kết quả · Tối đa 500 biến động gần nhất</p>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Mọi điều chỉnh đều được lưu lý do và người ghi nhận</p>
  </main>;

  if (screen === 'item-form') return <main ref={pageRef} className="inventory-page">
    {heading(itemMode === 'create' ? 'Tạo mặt hàng kho' : 'Cập nhật mặt hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Quản lý danh mục`,
      <button type="button" className="inv-button inv-button-quiet" onClick={() => setScreen('stock')}>Quay lại danh sách</button>)}
    {feedbackNotice}
    <div className="inv-form-layout">
      <form className="inv-panel inv-item-form" onSubmit={saveItem}>
        <h2>Thông tin mặt hàng</h2>
        <div className="inv-form-grid">
          <label className="inv-field"><span>Mã mặt hàng</span><input readOnly value={itemMode === 'create' ? 'Tự cấp khi tạo' : itemDraft?.sku || ''} /><small>Mã được hệ thống tạo tự động và không cần nhập tay.</small></label>
          <label className="inv-field"><span>Tên mặt hàng *</span><input required maxLength="200" value={itemDraft?.name || ''} onChange={(event) => setItemDraft({ ...itemDraft, name: event.target.value })} placeholder="Ví dụ: Nước suối" /></label>
          <label className="inv-field"><span>Nhóm hàng *</span><select required value={itemDraft?.category || ''} onChange={(event) => setItemDraft({ ...itemDraft, category: event.target.value })}>
            <option value="">Chọn nhóm hàng</option>{metadata.categories.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
          <label className="inv-field"><span>Đơn vị tính *</span><select required value={itemDraft?.unitId || ''} disabled={itemMode === 'update' && items.find((item) => item.id === selectedId)?.hasMovements} onChange={(event) => setItemDraft({ ...itemDraft, unitId: event.target.value })}>
            <option value="">Chọn đơn vị</option>{metadata.units.map((unit) => <option key={unit.id} value={unit.id}>{unit.name}</option>)}
          </select>{itemMode === 'update' && items.find((item) => item.id === selectedId)?.hasMovements && <small>Đơn vị được giữ nguyên để bảo toàn cách đọc lịch sử kho.</small>}</label>
          <label className="inv-field"><span>Cách theo dõi tồn *</span><select required value={itemDraft?.trackingMode || 'AUTO_ON_SALE'} onChange={(event) => setItemDraft({ ...itemDraft, trackingMode: event.target.value })}>
            <option value="AUTO_ON_SALE">Tự trừ khi bán</option><option value="MANUAL">Theo dõi thủ công</option>
          </select></label>
          <label className="inv-field"><span>Ngưỡng sắp hết *</span><div className="inv-number-with-unit"><input required type="number" min="0" step="0.001" value={itemDraft?.minimumQuantity ?? ''} onChange={(event) => setItemDraft({ ...itemDraft, minimumQuantity: event.target.value })} />{items.find((item) => item.id === selectedId)?.unitName || metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || 'đơn vị'}</div></label>
          {itemMode === 'update' && <label className="inv-check-field"><input type="checkbox" checked={Boolean(itemDraft?.active)} onChange={(event) => setItemDraft({ ...itemDraft, active: event.target.checked })} /><span>Đang sử dụng</span></label>}
        </div>
        {itemMode === 'create' && <div className="inv-menu-option"><label className="inv-check-field"><input type="checkbox" checked={addItemToMenu} onChange={(event) => setAddItemToMenu(event.target.checked)} /><span>Thêm ngay vào Thực đơn F&amp;B</span></label>
          {addItemToMenu && <label className="inv-field"><span>Giá bán F&amp;B *</span><MoneyInput ref={itemPriceRef} required maxDigits={18} value={menuSalePrice} onValueChange={setMenuSalePrice} /><small>Món hàng hóa sẽ liên kết với mặt hàng kho này. {itemDraft?.trackingMode === 'AUTO_ON_SALE' ? 'Tồn tự trừ khi bán.' : 'Tồn được theo dõi thủ công.'}</small></label>}
        </div>}
        <div className="inv-alert inv-alert-info">{itemMode === 'create' ? 'Mặt hàng mới bắt đầu với tồn kho 0. Dùng phiếu nhập để ghi nhận hàng nhận thực tế.' : 'Lưu thông tin mặt hàng không thay đổi số tồn hoặc các biến động đã ghi nhận.'}</div>
        <div className="inv-form-actions"><button type="button" className="inv-button inv-button-quiet" onClick={() => setScreen('stock')} disabled={busy}>Hủy</button><button type="submit" className="inv-button inv-button-primary" disabled={busy}>{busy ? 'Đang lưu…' : itemMode === 'create' ? 'Tạo mặt hàng' : 'Lưu thay đổi'}</button></div>
      </form>
      <aside className="inv-summary inv-item-summary"><h2>Tóm tắt mặt hàng</h2>
        <div className="inv-item-summary-identity"><span>Mặt hàng kho</span><strong>{itemDraft?.name?.trim() || 'Chưa nhập tên mặt hàng'}</strong>
          <small>{itemDraft?.sku || 'Mã tự cấp khi tạo'}<span aria-hidden="true"> · </span>{metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || 'Chưa chọn đơn vị'}</small></div>
        <dl className="inv-item-summary-details"><div><dt>Nhóm hàng</dt><dd>{itemDraft?.category || 'Chưa chọn'}</dd></div><div><dt>Theo dõi tồn</dt><dd>{trackingLabel(itemDraft?.trackingMode)}</dd></div>
          <div><dt>Tồn hiện tại</dt><dd>{formatQuantity(itemMode === 'update' ? items.find((item) => item.id === selectedId)?.quantity : 0)} {metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || ''}</dd></div>
          <div><dt>Ngưỡng sắp hết</dt><dd>{formatQuantity(itemDraft?.minimumQuantity)} {metadata.units.find((unit) => unit.id === itemDraft?.unitId)?.name || ''}</dd></div>
          {itemMode === 'create' && addItemToMenu && <div><dt>Giá bán F&amp;B</dt><dd>{menuSalePrice ? `${menuSalePrice.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}₫` : 'Chưa nhập'}</dd></div>}</dl>
        <p className="inv-item-summary-note">Tồn kho được ghi nhận khi nhập hàng hoặc kiểm kê.</p>
      </aside>
    </div>
    <p className="inv-footnote">Dữ liệu mặt hàng chỉ thuộc cơ sở đang được phân quyền</p>
  </main>;

  if (screen === 'receipt') return <main ref={pageRef} className="inventory-page">
    {heading('Ghi nhận nhập hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`)}
    {feedbackNotice}
    {menuLinkNotice}
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
          <div className="inv-line-heading"><h3>Mặt hàng nhận</h3><div className="inv-line-heading-actions">
            <span>{receiptLines.length} dòng</span>
            <button type="button" className="inv-button inv-button-quiet inv-receipt-create-toggle" disabled={busy}
              aria-expanded={Boolean(receiptItemDraft)} aria-controls={receiptItemDraft ? 'inv-receipt-inline-create' : undefined}
              onClick={() => {
                if (receiptItemDraft) { setReceiptItemDraft(null); return; }
                 setReceiptItemDraft({ name: '', category: metadata.categories[0] || '', unitId: metadata.units[0]?.id || '', trackingMode: 'AUTO_ON_SALE', minimumQuantity: '5', addToMenu: false, salePrice: '' });
                setError('');
              }}>{receiptItemDraft ? 'Hủy tạo mặt hàng' : '+ Tạo mặt hàng mới'}</button>
          </div></div>
          {receiptItemDraft && <div id="inv-receipt-inline-create" className="inv-receipt-inline-create" role="group" aria-label="Tạo mặt hàng mới"
            onKeyDown={(event) => { if (event.key === 'Enter') event.preventDefault(); }}>
            <strong>Tạo mặt hàng và tiếp tục nhập</strong>
            <div className="inv-receipt-create-grid">
              <label className="inv-field"><span>Tên mặt hàng *</span><input autoFocus maxLength="200" value={receiptItemDraft.name} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, name: event.target.value })} placeholder="Ví dụ: Nước suối" /></label>
              <label className="inv-field"><span>Nhóm hàng *</span><select value={receiptItemDraft.category} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, category: event.target.value })}>
                <option value="">Chọn nhóm hàng</option>{metadata.categories.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
              <label className="inv-field"><span>Đơn vị tồn kho *</span><select value={receiptItemDraft.unitId} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, unitId: event.target.value })}>
                <option value="">Chọn đơn vị</option>{metadata.units.map((unit) => <option key={unit.id} value={unit.id}>{unit.name}</option>)}
              </select></label>
              <label className="inv-field"><span>Cách theo dõi tồn *</span><select value={receiptItemDraft.trackingMode} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, trackingMode: event.target.value })}>
                <option value="AUTO_ON_SALE">Tự trừ khi bán</option><option value="MANUAL">Theo dõi thủ công</option>
              </select></label>
              <label className="inv-field"><span>Ngưỡng sắp hết</span><input type="number" min="0" step="0.001" value={receiptItemDraft.minimumQuantity} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, minimumQuantity: event.target.value })} /></label>
            </div>
            <div className="inv-menu-option"><label className="inv-check-field"><input type="checkbox" checked={receiptItemDraft.addToMenu} onChange={(event) => setReceiptItemDraft({ ...receiptItemDraft, addToMenu: event.target.checked })} /><span>Thêm ngay vào Thực đơn F&amp;B</span></label>
              {receiptItemDraft.addToMenu && <label className="inv-field"><span>Giá bán F&amp;B *</span><MoneyInput ref={receiptPriceRef} maxDigits={18} value={receiptItemDraft.salePrice} onValueChange={(value) => setReceiptItemDraft((draft) => ({ ...draft, salePrice: value }))} /><small>Món hàng hóa sẽ liên kết với mặt hàng kho vừa tạo.</small></label>}
            </div>
            <div className="inv-receipt-create-actions"><small>Mã mặt hàng sẽ được hệ thống cấp tự động. Tồn ban đầu bằng 0; tồn chỉ tăng khi xác nhận phiếu nhập.</small>
              <button type="button" className="inv-button inv-button-primary" disabled={busy || !metadata.units.length} onClick={saveReceiptItem}>{busy ? 'Đang tạo…' : receiptItemDraft.addToMenu ? 'Tạo, thêm vào thực đơn và chọn' : 'Tạo và chọn mặt hàng'}</button>
            </div>
          </div>}
          <div className="inv-receipt-lines">
            {receiptLines.map((line) => {
              const item = items.find((row) => row.id === line.inventoryItemId);
              const unitName = receivingUnitName(line, item);
              const conversion = receiptQuantity(line);
              const quantity = Number(conversion.value || 0);
              const conversionStarted = line.quantity !== '' && (!line.receivingUnit || line.receivingUnit === 'BASE' || line.unitsPerPack !== '');
              return <div className={`inv-receipt-line${line.receivingUnit === 'CUSTOM' ? ' inv-receipt-custom-unit' : !line.receivingUnit || line.receivingUnit === 'BASE' ? ' inv-receipt-base-unit' : ''}`} key={line.id}>
                <div className="inv-line-product">
                   <div className="inv-field"><span>Mặt hàng *</span><button type="button" className="inv-picker-trigger" disabled={busy} onClick={() => setPickerTarget(line.id)}>{item ? `${item.sku} · ${item.name}` : 'Chọn mặt hàng'}</button></div>
                </div>
                <button type="button" className="inv-remove-line" aria-label={`Bỏ dòng ${item?.name || 'mặt hàng'}`} disabled={receiptLines.length === 1} onClick={() => removeReceiptLine(line.id)}>Bỏ</button>
                <label className="inv-field inv-line-unit"><span>Đơn vị nhận *</span><select required data-receipt-unit-for={line.inventoryItemId} disabled={!item || busy} value={line.receivingUnit} onChange={(event) => updateReceiptLine(line.id, 'receivingUnit', event.target.value)}>
                  <option value="">Chọn đơn vị nhận</option>
                  <option value="BASE">{item?.unitName || 'Đơn vị tồn kho'} (đơn vị tồn kho)</option>
                  {RECEIVING_UNITS.filter((unit) => unit.toLocaleLowerCase('vi-VN') !== item?.unitName.toLocaleLowerCase('vi-VN')).map((unit) => <option key={unit} value={unit}>{unit}</option>)}
                  <option value="CUSTOM">Khác…</option>
                </select></label>
                {line.receivingUnit === 'CUSTOM' && <label className="inv-field inv-line-custom-unit"><span>Tên đơn vị nhận *</span><input required disabled={busy} maxLength="30" value={line.customUnit} onChange={(event) => updateReceiptLine(line.id, 'customUnit', event.target.value)} placeholder="Ví dụ: Kiện, can…" /></label>}
                {line.receivingUnit && line.receivingUnit !== 'BASE' && <label className="inv-field inv-line-conversion"><span>Quy cách *</span>
                  <span className="inv-pack-size"><span>1 {unitName || 'đơn vị nhận'} =</span><input required disabled={busy} aria-label={`Số ${item?.unitName || 'đơn vị gốc'} trong 1 ${unitName || 'đơn vị nhận'}`} type="text" inputMode="decimal" autoComplete="off" maxLength="19" value={line.unitsPerPack} onChange={(event) => updateReceiptLine(line.id, 'unitsPerPack', event.target.value)} /><span>{item?.unitName}</span></span>
                </label>}
                <label className="inv-field inv-line-quantity"><span>SL nhận *</span><input required disabled={!item || !line.receivingUnit || busy} type="text" inputMode={line.receivingUnit === 'BASE' ? 'decimal' : 'numeric'} autoComplete="off" maxLength="19" value={line.quantity} onChange={(event) => updateReceiptLine(line.id, 'quantity', event.target.value)} /></label>
                <label className="inv-field inv-line-expiry"><span>Hạn sử dụng *</span><input required disabled={!item || busy} type="date" min={receiptDraft?.receivedAt?.slice(0, 10)} value={line.expiryDate} onChange={(event) => updateReceiptLine(line.id, 'expiryDate', event.target.value)} /></label>
                <label className="inv-field inv-line-cost"><span>Đơn giá / {item?.unitName || 'đơn vị gốc'} *</span><MoneyInput maxDigits={18} required disabled={!item || busy} autoComplete="off" value={line.unitCost} onValueChange={value => updateReceiptLine(line.id, 'unitCost', value.replace(/[^0-9]/g, ''))} /></label>
                <div className="inv-line-result" aria-live="polite">
                  <div className="inv-line-stock"><span>Số lượng nhập kho</span><strong>{!item || conversion.error ? '—' : `${formatQuantity(quantity)} ${item.unitName}`}</strong>
                    {item && line.receivingUnit && line.receivingUnit !== 'BASE' && !conversion.error && <small>{formatQuantity(line.quantity)} {unitName.toLocaleLowerCase('vi-VN')} × {formatQuantity(line.unitsPerPack)} {item.unitName}</small>}
                    {conversionStarted && conversion.error && <small className="inv-danger-text">{conversion.error}</small>}
                  </div>
                  <div className="inv-line-total"><span>Thành tiền</span><strong>{formatVnd(receiptAmount(conversion.value || '0', line.unitCost) ?? 0n)}</strong></div>
                </div>
              </div>;
            })}
          </div>
          <button type="button" className="inv-button inv-button-wide inv-button-quiet" onClick={addReceiptLine} disabled={receiptLines.length >= activeItems.length}>+ Thêm mặt hàng</button>
        </div>
        <div className="inv-alert inv-alert-info">Chỉ ghi nhận hàng đã nhận thực tế. Không tự thanh toán nhà cung cấp khi nhập kho.</div>
      </form>
      <aside className="inv-summary inv-action-summary"><h2>Tóm tắt nhập kho</h2>
        <p>{receiptSummary.length} mặt hàng · Số lượng theo đơn vị tồn kho của từng mặt hàng</p>
        <ul className="inv-summary-lines">{receiptSummary.map((line) => <li key={line.id}><span>{line.item.name}</span><small>Hạn sử dụng: {dateLabel(line.expiryDate)}</small>
          {line.conversion.error ? <small>Chưa đủ số lượng hoặc quy cách hợp lệ</small> : <><span>{formatQuantity(line.quantity)} {line.item.unitName} × {formatVnd(line.unitCost)} / {line.item.unitName}</span>
            {line.receivingUnit && line.receivingUnit !== 'BASE' && <small>Nhận {formatQuantity(line.receivedQuantity)} {receivingUnitName(line, line.item).toLocaleLowerCase('vi-VN')} · 1 {receivingUnitName(line, line.item).toLocaleLowerCase('vi-VN')} = {formatQuantity(line.unitsPerPack)} {line.item.unitName}</small>}
            <strong>{formatVnd(line.amount)}</strong><small>Tồn sau: {formatQuantity(Number(line.item.quantity) + line.quantity)} {line.item.unitName}</small></>}
        </li>)}</ul>
        <div className="inv-summary-total"><span>Tổng giá trị</span><strong>{formatVnd(receiptTotalValue)}</strong></div>
        <div className="inv-alert inv-alert-info">Xác nhận phiếu mới tăng số tồn. Chi phí được xử lý qua đề nghị riêng.</div>
        <div className="inv-summary-actions"><button type="submit" form="inventory-receipt-form" className="inv-button inv-button-primary" disabled={busy || !receiptLines.length}>{busy ? 'Đang ghi nhận…' : 'Xác nhận nhập kho'}</button>
          <button type="button" className="inv-button inv-button-quiet" onClick={() => { setEvidence(null); setScreen('stock'); }}>Quay lại tồn kho</button></div>
      </aside>
    </div>
    {pickerTarget && <InventoryItemPicker items={activeItems} categories={metadata.categories}
      unavailableIds={receiptLines.filter((line) => line.id !== pickerTarget).map((line) => line.inventoryItemId).filter(Boolean)}
      onSelect={(id) => updateReceiptLine(pickerTarget, 'inventoryItemId', id)} onClose={() => setPickerTarget(null)} />}
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Xác nhận nhập kho cập nhật ledger tồn kho</p>
  </main>;

  if (screen === 'adjustment') return <main ref={pageRef} className="inventory-page">
    {heading('Kiểm kê & điều chỉnh tồn', `Cơ sở ${user.branchName || 'được phân quyền'} · Kho hàng`)}
    {feedbackNotice}
    <div className="inv-form-layout inv-adjust-layout">
      <form id="inventory-adjustment-form" className="inv-panel inv-adjust-form" onSubmit={submitAdjustment}>
        <h2>Thông tin ghi nhận</h2>
        <div className="inv-form-grid">
          <label className="inv-field"><span>Thời điểm kiểm kê *</span><input required type="datetime-local" value={adjustDraft?.adjustedAt || ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, adjustedAt: event.target.value })} /></label>
          <label className="inv-field"><span>Người thực hiện</span><input value={user.fullName || user.username || 'Quản lý cơ sở'} disabled /></label>
           <div className="inv-field"><span>Mặt hàng *</span><button type="button" className="inv-picker-trigger" disabled={busy} onClick={() => setPickerTarget('adjustment')}>{adjustItem ? `${adjustItem.sku} · ${adjustItem.name}` : 'Chọn mặt hàng'}</button></div>
          <label className="inv-field"><span>Đơn vị</span><input value={adjustItem?.unitName || ''} disabled /></label>
          <div className="inv-field"><span id="inventory-system-stock-label">Tồn hệ thống</span><input aria-labelledby="inventory-system-stock-label" value={adjustLoading ? 'Đang tải…' : adjustItem ? formatQuantity(adjustItem.quantity) : '—'} disabled />
            {!adjustLoading && !adjustItem && adjustmentItemId && <button type="button" className="inv-text-button" disabled={busy} onClick={() => setAdjustLoadAttempt((attempt) => attempt + 1)}>Thử tải lại tồn hệ thống</button>}
          </div>
          <label className="inv-field"><span>Tồn thực tế *</span><input required type="number" min="0" step="0.001" aria-invalid={Boolean(actualQuantityError)} aria-describedby={actualQuantityError ? 'inventory-actual-quantity-error' : undefined} value={adjustDraft?.actualQuantity ?? ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, actualQuantity: event.target.value })} /></label>
          <label className="inv-field"><span>Lý do *</span><textarea required maxLength="1000" rows="3" value={adjustDraft?.reason || ''} onChange={(event) => setAdjustDraft({ ...adjustDraft, reason: event.target.value })} placeholder="Giải thích chênh lệch tồn kho" /></label>
          {attachmentField}
        </div>
        {staleStock?.latest != null && <div className={`inv-alert ${staleStock.reviewed ? 'inv-alert-info' : 'inv-alert-warning'}`} role="status">
          <span>Tồn kho đã thay đổi từ {formatQuantity(staleStock.previous)} thành {formatQuantity(staleStock.latest)} {adjustItem?.unitName}. Vui lòng kiểm tra lại trước khi xác nhận điều chỉnh.</span>
          {!staleStock.reviewed && <button type="button" className="inv-button inv-button-quiet" onClick={acknowledgeLatestStock}>Đã kiểm tra tồn mới</button>}
        </div>}
      </form>
      <aside className="inv-summary inv-action-summary"><h2>Tóm tắt &amp; xử lý</h2>
        <p>{adjustItem?.name || 'Chọn mặt hàng'} · Đơn vị {adjustItem?.unitName || '—'}</p>
        <dl className="inv-adjust-summary" aria-live="polite"><div><dt>Hệ thống</dt><dd>{adjustLoading ? 'Đang tải…' : adjustItem ? formatQuantity(adjustItem.quantity) : '—'}</dd></div><div><dt>Thực tế</dt><dd>{validActualQuantity ? formatQuantity(adjustActual) : actualQuantityInput ? 'Không hợp lệ' : 'Chưa nhập'}</dd></div>
          <div><dt>Điều chỉnh</dt><dd className={adjustDelta === null ? undefined : adjustDelta >= 0 ? 'inv-positive' : 'inv-negative'}>{adjustDelta === null ? '—' : `${adjustDelta > 0 ? '+' : ''}${formatQuantity(adjustDelta)} ${adjustItem.unitName}`}</dd></div>
          <div><dt>Tồn mới</dt><dd>{adjustDelta === null ? '—' : `${formatQuantity(adjustActual)} ${adjustItem.unitName}`}</dd></div><div><dt>Lý do</dt><dd>{adjustDraft?.reason || 'Chưa nhập'}</dd></div></dl>
        {actualQuantityError && <div id="inventory-actual-quantity-error" className="inv-alert inv-alert-error" role="alert">{actualQuantityError}</div>}
        <div className="inv-summary-actions"><button type="submit" form="inventory-adjustment-form" className="inv-button inv-button-primary" disabled={!canSubmitAdjustment}>{busy ? 'Đang xác nhận…' : 'Xác nhận điều chỉnh'}</button>
          <button type="button" className="inv-button inv-button-quiet" disabled={busy} onClick={() => { setEvidence(null); setScreen('stock'); }}>Quay lại danh sách</button></div>
      </aside>
    </div>
    {pickerTarget === 'adjustment' && <InventoryItemPicker items={activeItems} categories={metadata.categories}
      onSelect={selectAdjustmentItem} onClose={() => setPickerTarget(null)} />}
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Điều chỉnh lưu người ghi, lý do và số tồn trước/sau</p>
  </main>;

  return <main ref={pageRef} className="inventory-page">
    {heading('Kho hàng', `Cơ sở ${user.branchName || 'được phân quyền'} · Theo dõi số lượng và trạng thái mặt hàng`,
      <><button type="button" className="inv-button inv-button-quiet" onClick={openAdjustment} disabled={busy || user.readOnly || !activeItems.length} title={!activeItems.length ? 'Tạo mặt hàng trước khi kiểm kê' : undefined}>Kiểm kê &amp; điều chỉnh</button><button type="button" className="inv-button inv-button-quiet" onClick={openReceipt} disabled={busy || user.readOnly || !activeItems.length} title={!activeItems.length ? 'Tạo mặt hàng trước khi nhập kho' : undefined}>Ghi nhận nhập hàng</button><button type="button" className="inv-button inv-button-primary" disabled={busy || user.readOnly} onClick={() => openItemForm('create')}>Thêm mặt hàng</button></>)}
    {feedbackNotice}
    {menuLinkNotice}
    {recentReceipt?.items?.length > 0 && <section className="inv-panel inv-receipt-followup" aria-label="Thêm mặt hàng vừa nhập vào thực đơn">
      <h2>Phiếu {recentReceipt.code} đã nhập kho</h2>
      <p>Chọn mặt hàng để tạo món F&amp;B. Giá bán sẽ được nhập riêng trong thực đơn.</p>
      <div className="inv-receipt-followup-items">{recentReceipt.items.map((item) => <div key={item.inventoryItemId}>
        <span>{item.sku} · {item.name}</span>
        {recentReceipt.linkedInventoryIds?.includes(item.inventoryItemId)
          ? <span>Đã có trong thực đơn</span>
          : <button type="button" className="inv-button inv-button-quiet" disabled={user.readOnly} onClick={() => onAddToMenu(item.inventoryItemId)} aria-label={`Thêm ${item.name} vào Thực đơn F&B`}>Thêm vào Thực đơn F&amp;B</button>}
      </div>)}</div>
    </section>}
    <section className="inv-filter-panel inv-unified-filters" aria-label="Tìm và lọc kho hàng">
      <label className="inv-field inv-search">
        <span>Tìm kiếm mặt hàng</span>
        <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Mã, tên, nhóm hoặc đơn vị" />
      </label>
      <label className="inv-field"><span>Nhóm hàng</span><select value={category} onChange={(event) => setCategory(event.target.value)}><option value="all">Tất cả nhóm</option>{metadata.categories.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
      <label className="inv-field"><span>Tình trạng tồn</span><select value={stockStatus} onChange={(event) => setStockStatus(event.target.value)}>
        <option value="all">Tình trạng tồn</option><option value="AVAILABLE">Còn hàng</option><option value="LOW_STOCK">Sắp hết</option><option value="OUT_OF_STOCK">Hết hàng</option>
      </select></label>
      <label className="inv-field"><span>Trạng thái sử dụng</span><select value={itemStatus} onChange={(event) => setItemStatus(event.target.value)}><option value="active">Đang sử dụng</option><option value="archived">Ngừng sử dụng</option><option value="all">Tất cả</option></select></label>
      <small role="status">Phạm vi: {user.branchName || 'cơ sở được phân quyền'} · {loading ? 'Đang tải dữ liệu…' : `${visibleItems.length} mặt hàng`}</small>
    </section>
    <section className="inv-table-card" aria-label="Mặt hàng và tồn kho">
      {loading ? <div className="inv-loading-rows" aria-label="Đang tải"><i /><i /><i /><i /></div> : visibleItems.length ? <div className="inv-table-scroll"><PaginatedTable pageSize={5} className="inv-table inv-items-table inv-unified-table" resetKey={`${search}:${category}:${stockStatus}:${itemStatus}`} paginationLabel="Phân trang kho hàng">
        <thead><tr><th>Mặt hàng</th><th>Nhóm</th><th>Đơn vị</th><th>Theo dõi tồn</th><th>Tồn hiện tại</th><th>Ngưỡng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{visibleItems.map((item) => <Fragment key={item.id}>
          <tr>
            <td><strong>{item.sku} · {item.name}</strong></td><td>{item.category}</td><td>{item.unitName}</td><td>{trackingLabel(item.trackingMode)}</td>
            <td>{formatQuantity(item.quantity)}</td><td>{formatQuantity(item.minimumQuantity)}</td>
            <td><span className={`inv-status ${stockClass(item)}`}>{stockLabel(item)}</span></td>
            <td className="inv-row-actions"><button type="button" className="inv-text-button" aria-label={`Xem lịch sử biến động của ${item.name}`} onClick={() => openHistory(item)}>Xem lịch sử</button><button type="button" className="inv-text-button" disabled={busy || user.readOnly} onClick={() => openItemForm('update', item)}>Sửa</button>
              {item.active && <button type="button" className="inv-text-button inv-danger-text" disabled={busy || user.readOnly} onClick={() => setConfirmDeleteId(item.id)}>Xóa</button>}</td>
          </tr>
          {confirmDeleteId === item.id && <tr><td colSpan="8" className="inv-confirm-cell"><div className="inv-inline-confirm" role="group" aria-label={`Xác nhận xóa ${item.name}`}>
            <span><strong>Ngừng sử dụng mặt hàng này?</strong> Lịch sử kho sẽ được giữ nguyên. Không thể ngừng khi món F&amp;B đang bán còn liên kết.</span>
            <button type="button" className="inv-button inv-button-quiet" onClick={() => setConfirmDeleteId(null)} disabled={busy}>Hủy</button>
            <button type="button" className="inv-button inv-button-danger" onClick={() => deleteItem(item)} disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận xóa'}</button>
          </div></td></tr>}
        </Fragment>)}</tbody>
      </PaginatedTable></div> : <div className="inv-empty"><strong>{!items.length ? 'Chưa có mặt hàng kho' : itemStatus === 'archived' && !search.trim() && category === 'all' && stockStatus === 'all' ? 'Chưa có mặt hàng ngừng sử dụng' : 'Không tìm thấy mặt hàng phù hợp'}</strong><span>{items.length ? 'Thử thay đổi từ khóa hoặc bộ lọc.' : 'Thêm mặt hàng để quản lý tồn kho của cơ sở.'}</span>{!items.length && <button type="button" className="inv-button inv-button-primary" disabled={busy || user.readOnly} onClick={() => openItemForm('create')}>Tạo mặt hàng</button>}</div>}
    </section>
    <p className="inv-results">Tổng {visibleItems.length} mặt hàng phù hợp</p>
    <p className="inv-footnote">Dữ liệu theo cơ sở được phân quyền · Mặt hàng tự trừ tồn khi bán dùng chung quy tắc AUTO_ON_SALE</p>
  </main>;
}
